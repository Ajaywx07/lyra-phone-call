package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private const val TAG = "AudioRecorderManager"

/**
 * Captures 16 kHz mono 16-bit PCM audio from the device microphone and builds a WAV byte payload
 * suitable for Gemini audio transcription while exposing real-time RMS amplitude for UI metering.
 */
class AudioRecorderManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Cap inline audio at ~15 MB (well under the 20 MB Gemini inline limit)
        private const val MAX_PCM_BYTES = 15 * 1024 * 1024
    }

    enum class CaptureSource(
        val audioSourceId: Int,
        val label: String,
        val isTwoWay: Boolean
    ) {
        VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL, "VOICE_CALL", true),
        VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION, "VOICE_COMMUNICATION", false),
        VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION", false),
        MIC(MediaRecorder.AudioSource.MIC, "MIC", false)
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private val pcmStream = ByteArrayOutputStream()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _activeCaptureSource = MutableStateFlow(CaptureSource.MIC)
    val activeCaptureSource: StateFlow<CaptureSource> = _activeCaptureSource.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        preferVoiceRecognition: Boolean = false,
        preferCallCapture: Boolean = false,
        onMaxLimitReached: () -> Unit = {}
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission (RECORD_AUDIO) is required.")
        }

        stopInternal()
        pcmStream.reset()
        _elapsedSeconds.value = 0L
        _amplitude.value = 0f

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Audio recording is not supported on this device configuration.")
        }

        val bufferSize = maxOf(minBufferSize * 2, 4096)
        val (recorder, chosenSource) = createBestAudioRecord(
            bufferSize = bufferSize,
            preferVoiceRecognition = preferVoiceRecognition,
            preferCallCapture = preferCallCapture
        ) ?: throw IllegalStateException("Failed to initialize microphone AudioRecord.")

        _activeCaptureSource.value = chosenSource
        audioRecord = recorder
        recorder.startRecording()
        _isRecording.value = true

        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isRecording.value) {
                delay(1000L)
                if (_isRecording.value) {
                    _elapsedSeconds.value += 1L
                }
            }
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive && _isRecording.value) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(pcmStream) {
                            if (pcmStream.size() + read <= MAX_PCM_BYTES) {
                                pcmStream.write(buffer, 0, read)
                            } else {
                                _isRecording.value = false
                                onMaxLimitReached()
                                return@launch
                            }
                        }
                        _amplitude.value = computeNormalizedRms(buffer, read)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while reading audio stream", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(
        bufferSize: Int,
        preferVoiceRecognition: Boolean,
        preferCallCapture: Boolean
    ): Pair<AudioRecord, CaptureSource>? {
        val sources = when {
            preferCallCapture -> {
                if (PermissionHelper.hasCaptureAudioOutputPermission(context)) {
                    listOf(
                        CaptureSource.VOICE_CALL,
                        CaptureSource.VOICE_COMMUNICATION,
                        CaptureSource.VOICE_RECOGNITION,
                        CaptureSource.MIC
                    )
                } else {
                    listOf(
                        CaptureSource.VOICE_COMMUNICATION,
                        CaptureSource.VOICE_RECOGNITION,
                        CaptureSource.MIC
                    )
                }
            }
            preferVoiceRecognition -> listOf(CaptureSource.VOICE_RECOGNITION, CaptureSource.MIC)
            else -> listOf(CaptureSource.MIC)
        }
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(source.audioSourceId, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate to source
            }
            runCatching { candidate.release() }
        }
        return null
    }

    fun stopAndBuildWav(): ByteArray {
        stopInternal()
        val pcmBytes = synchronized(pcmStream) {
            pcmStream.toByteArray()
        }
        return buildWavPayload(pcmBytes)
    }

    fun saveWavToInternalStorage(wavBytes: ByteArray, fileNamePrefix: String = "vm"): java.io.File {
        val dir = java.io.File(context.filesDir, "voicemails")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = java.io.File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.wav")
        file.writeBytes(wavBytes)
        return file
    }

    fun cancelRecording() {
        stopInternal()
        synchronized(pcmStream) {
            pcmStream.reset()
        }
        _elapsedSeconds.value = 0L
    }

    private fun stopInternal() {
        _isRecording.value = false
        _amplitude.value = 0f
        timerJob?.cancel()
        timerJob = null
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    private fun computeNormalizedRms(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead < 2) return 0f
        var sumSquares = 0.0
        val sampleCount = bytesRead / 2
        for (i in 0 until sampleCount) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sumSquares / sampleCount)
        // Normalize against 16-bit range with boost for normal speech levels
        return ((rms / 12000.0).toFloat()).coerceIn(0f, 1f)
    }

    private fun buildWavPayload(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return header + pcmData
    }
}
