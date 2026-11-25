package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the real-time Voice Changer DSP pipeline (`AudioRecord` -> C++17 JNI / Kotlin DSP -> `AudioTrack`).
 *
 * Android Platform Constraint:
 * On standard non-rooted Android devices, third-party `InCallService` apps cannot inject processed
 * audio directly into the cellular GSM/VoLTE modem uplink (`VOICE_CALL` hardware path isolation).
 * This engine runs a low-latency local/VoIP voice monitoring & loopback pipeline via
 * `USAGE_VOICE_COMMUNICATION` when microphone capture is permitted by the OS/device, and
 * gracefully reports modem lockouts without interrupting the active call or crashing.
 */
class VoiceChangerEngine(context: Context) {

    companion object {
        private const val TAG = "LyraVoiceChangerEngine"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SIZE_SAMPLES = 320 // 20 ms frames @ 16 kHz
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentMode = MutableStateFlow(VoiceMode.NONE)
    val currentMode: StateFlow<VoiceMode> = _currentMode.asStateFlow()

    private val _isPipelineActive = MutableStateFlow(false)
    val isPipelineActive: StateFlow<Boolean> = _isPipelineActive.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private val stateLock = Any()

    @Volatile
    private var nativeHandle: Long = 0L

    private var kotlinFallbackDsp: VoiceChangerDspProcessor? = null
    private var workerThread: Thread? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun setMode(mode: VoiceMode, isCallOngoing: Boolean) {
        _currentMode.value = mode
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                runCatching {
                    VoiceChangerNativeBridge.nativeSetMode(nativeHandle, mode.nativeOrdinal)
                }
            }
            kotlinFallbackDsp?.setMode(mode)
        }

        if (mode == VoiceMode.NONE) {
            stopAudioPipeline()
            _statusMessage.value = null
            return
        }

        if (!isCallOngoing) {
            _statusMessage.value = "${mode.displayName} preset armed"
            return
        }

        if (!hasMicrophonePermission()) {
            _isPipelineActive.value = false
            _statusMessage.value = "Grant Microphone permission to run real-time voice DSP"
            return
        }

        startAudioPipelineIfNeeded(mode)
    }

    fun stopSession(resetMode: Boolean = true) {
        stopAudioPipeline()
        if (resetMode) {
            _currentMode.value = VoiceMode.NONE
        }
        _statusMessage.value = null
    }

    fun release() {
        stopSession(resetMode = true)
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                runCatching { VoiceChangerNativeBridge.nativeDestroy(nativeHandle) }
                nativeHandle = 0L
            }
            kotlinFallbackDsp = null
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureDspInitialized(initialMode: VoiceMode) {
        synchronized(stateLock) {
            if (VoiceChangerNativeBridge.isNativeAvailable) {
                if (nativeHandle == 0L) {
                    nativeHandle = runCatching {
                        VoiceChangerNativeBridge.nativeCreate(SAMPLE_RATE)
                    }.getOrDefault(0L)
                }
                if (nativeHandle != 0L) {
                    VoiceChangerNativeBridge.nativeSetMode(nativeHandle, initialMode.nativeOrdinal)
                    return
                }
            }
            val fallback = kotlinFallbackDsp ?: VoiceChangerDspProcessor(SAMPLE_RATE).also {
                kotlinFallbackDsp = it
            }
            fallback.setMode(initialMode)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioPipelineIfNeeded(mode: VoiceMode) {
        synchronized(stateLock) {
            ensureDspInitialized(mode)
            if (isRunning.get()) {
                _statusMessage.value = "${mode.displayName} DSP active (local/VoIP monitor)"
                return
            }

            isRunning.set(true)
            val thread = Thread(
                { runAudioLoop() },
                "LyraVoiceChangerAudioThread"
            ).apply {
                isDaemon = true
            }
            workerThread = thread
            thread.start()
        }
    }

    private fun stopAudioPipeline() {
        val threadToJoin: Thread?
        synchronized(stateLock) {
            isRunning.set(false)
            threadToJoin = workerThread
            workerThread = null
        }
        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            runCatching { threadToJoin.join(350L) }
        }
        abandonAudioFocus()
        _isPipelineActive.value = false
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }

        var recorder: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            val minRecBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val minPlayBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minRecBytes <= 0 || minPlayBytes <= 0) {
                _statusMessage.value = "Audio hardware unavailable for real-time voice DSP"
                return
            }

            val bufferBytes = maxOf(minRecBytes, minPlayBytes, FRAME_SIZE_SAMPLES * 4)
            recorder = createAudioRecord(bufferBytes)
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _statusMessage.value =
                    "Cellular modem locked mic capture; local DSP monitor paused"
                return
            }

            track = createAudioTrack(bufferBytes)
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                _statusMessage.value = "Voice output track unavailable"
                return
            }

            requestAudioFocus()
            recorder.startRecording()
            track.play()

            val activeMode = _currentMode.value
            _isPipelineActive.value = true
            _statusMessage.value = if (VoiceChangerNativeBridge.isNativeAvailable && nativeHandle != 0L) {
                "${activeMode.displayName} C++ DSP active (local/VoIP monitor)"
            } else {
                "${activeMode.displayName} DSP active (local/VoIP monitor)"
            }

            val inputBuffer = ShortArray(FRAME_SIZE_SAMPLES)
            val outputBuffer = ShortArray(FRAME_SIZE_SAMPLES)

            while (isRunning.get()) {
                val readCount = recorder.read(inputBuffer, 0, inputBuffer.size)
                if (readCount <= 0) {
                    if (readCount < 0) {
                        _statusMessage.value =
                            "Audio capture interrupted by system call routing"
                        break
                    }
                    continue
                }

                processFrame(inputBuffer, outputBuffer, readCount)
                track.write(outputBuffer, 0, readCount)
            }
        } catch (sec: SecurityException) {
            Log.w(TAG, "Microphone permission denied during DSP loop: ${sec.message}")
            _statusMessage.value = "Microphone permission required for Voice Changer"
        } catch (t: Throwable) {
            Log.w(TAG, "Voice Changer audio pipeline stopped: ${t.message}")
            _statusMessage.value = "Cellular audio path locked by device modem; effect paused"
        } finally {
            isRunning.set(false)
            _isPipelineActive.value = false
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
            abandonAudioFocus()
        }
    }

    private fun processFrame(input: ShortArray, output: ShortArray, frameCount: Int) {
        val handle = nativeHandle
        if (handle != 0L && VoiceChangerNativeBridge.isNativeAvailable) {
            VoiceChangerNativeBridge.nativeProcessPcm16(handle, input, output, frameCount)
        } else {
            kotlinFallbackDsp?.processPcm16(input, output, frameCount)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferBytes: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
        }
        return null
    }

    private fun createAudioTrack(bufferBytes: Int): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        runCatching { am.requestAudioFocus(request) }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val req = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { am.abandonAudioFocusRequest(req) }
    }
}
