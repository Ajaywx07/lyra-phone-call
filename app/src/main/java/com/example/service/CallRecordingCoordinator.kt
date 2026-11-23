package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CallRecording
import com.example.utils.CallRecordingStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object CallRecordingCoordinator {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentDurationSeconds = MutableStateFlow(0L)
    val currentDurationSeconds: StateFlow<Long> = _currentDurationSeconds.asStateFlow()

    private val _activeFileName = MutableStateFlow<String?>(null)
    val activeFileName: StateFlow<String?> = _activeFileName.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    private var activeCallerName: String = "Unknown"
    private var activeCallerNumber: String = "Unknown"
    private var activeStartTimeMs: Long = 0L
    private var tempPcmFile: File? = null

    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    fun isRecording(): Boolean = _isRecording.value

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context, callerName: String, callerNumber: String): Boolean {
        if (_isRecording.value) return true

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        // Prefer VOICE_COMMUNICATION for voice calls with acoustic echo cancellation; fallback to MIC
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }.getOrNull() ?: runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }.getOrNull()

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return false
        }

        activeCallerName = if (callerName.isNotBlank()) callerName else "Unknown"
        activeCallerNumber = if (callerNumber.isNotBlank()) callerNumber else "Unknown"
        activeStartTimeMs = System.currentTimeMillis()

        val fileName = CallRecordingStorage.generateFileName(activeCallerName, activeCallerNumber)
        _activeFileName.value = fileName
        _currentDurationSeconds.value = 0L

        val pcmFile = File(context.cacheDir, "call_recording_${System.currentTimeMillis()}.pcm")
        tempPcmFile = pcmFile
        audioRecord = record

        try {
            record.startRecording()
        } catch (_: Exception) {
            record.release()
            audioRecord = null
            return false
        }

        _isRecording.value = true

        timerJob = scope.launch {
            while (isActive && _isRecording.value) {
                delay(1000)
                _currentDurationSeconds.value += 1
            }
        }

        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            FileOutputStream(pcmFile).use { fos ->
                while (isActive && _isRecording.value) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                    }
                }
            }
        }

        return true
    }

    suspend fun stopRecording(context: Context): CallRecording? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null

        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null

        val pcm = tempPcmFile
        val record = audioRecord
        val caller = activeCallerName
        val number = activeCallerNumber
        val startTime = activeStartTimeMs
        val fileName = _activeFileName.value ?: CallRecordingStorage.generateFileName(caller, number)

        try {
            record?.stop()
        } catch (_: Exception) {
        } finally {
            record?.release()
            audioRecord = null
        }

        recordingJob?.cancel()
        recordingJob = null

        val durationSeconds = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(1L)

        if (pcm == null || !pcm.exists() || pcm.length() == 0L) {
            pcm?.delete()
            tempPcmFile = null
            _activeFileName.value = null
            _currentDurationSeconds.value = 0L
            return@withContext null
        }

        val target = CallRecordingStorage.createRecordingTarget(context, fileName, caller, number)
        val rawPcmLen = pcm.length()
        val totalDataLen = rawPcmLen + 36

        try {
            val out = target.outputStream ?: return@withContext null
            out.use { fos ->
                CallRecordingStorage.writeWavHeader(
                    out = fos,
                    totalAudioLen = rawPcmLen,
                    totalDataLen = totalDataLen,
                    longSampleRate = SAMPLE_RATE.toLong(),
                    channels = 1,
                    byteRate = SAMPLE_RATE.toLong() * 2L * 1L
                )
                FileInputStream(pcm).use { fis ->
                    fis.copyTo(fos)
                }
                fos.flush()
            }
            if (target.isPendingSupported) {
                CallRecordingStorage.finishPendingRecording(context, target.uri)
            }
        } catch (_: Exception) {
            pcm.delete()
            tempPcmFile = null
            return@withContext null
        }

        pcm.delete()
        tempPcmFile = null
        _activeFileName.value = null
        _currentDurationSeconds.value = 0L

        val recording = CallRecording(
            callerName = caller,
            callerNumber = number,
            fileName = fileName,
            mediaStoreUri = target.uri.toString(),
            relativePath = target.relativePath,
            durationSeconds = durationSeconds,
            fileSizeBytes = rawPcmLen + 44,
            timestamp = startTime,
            captureSource = "MIC",
            isTwoWayAudio = false
        )

        val id = LyraDatabase.getDatabase(context).callRecordingDao().insertRecording(recording)
        recording.copy(id = id)
    }

    fun cancelRecording(context: Context) {
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        } finally {
            audioRecord?.release()
            audioRecord = null
        }
        tempPcmFile?.delete()
        tempPcmFile = null
        _activeFileName.value = null
        _currentDurationSeconds.value = 0L
    }
}
