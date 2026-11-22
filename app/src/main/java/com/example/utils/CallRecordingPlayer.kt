package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.data.local.entity.CallRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "CallRecordingPlayer"

data class RecordingPlaybackState(
    val activeRecordingId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0,
    val errorMessage: String? = null
)

class CallRecordingPlayer(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(RecordingPlaybackState())
    val playbackState: StateFlow<RecordingPlaybackState> = _playbackState.asStateFlow()

    fun togglePlayPause(recording: CallRecording) {
        val current = _playbackState.value
        val player = mediaPlayer
        if (current.activeRecordingId == recording.id && player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.pause()
                    _playbackState.value = current.copy(
                        isPlaying = false,
                        currentPositionMs = player.currentPosition,
                        errorMessage = null
                    )
                    progressJob?.cancel()
                } else {
                    player.start()
                    _playbackState.value = current.copy(
                        isPlaying = true,
                        errorMessage = null
                    )
                    startProgressTicker()
                }
            }.onFailure {
                startPlayback(recording)
            }
            return
        }
        startPlayback(recording)
    }

    private fun startPlayback(recording: CallRecording) {
        stop()
        val uri = runCatching { Uri.parse(recording.mediaStoreUri) }.getOrNull()
        if (uri == null) {
            _playbackState.value = RecordingPlaybackState(
                errorMessage = "Recording file URI is invalid."
            )
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(appContext, uri)
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stopWithError("Unable to play ${recording.fileName}. File may have been moved or removed.")
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            val durationMs = runCatching { player.duration }.getOrDefault(
                (recording.durationSeconds * 1000L).toInt().coerceAtLeast(1)
            )
            _playbackState.value = RecordingPlaybackState(
                activeRecordingId = recording.id,
                isPlaying = true,
                currentPositionMs = 0,
                totalDurationMs = durationMs.coerceAtLeast(1),
                errorMessage = null
            )
            startProgressTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play recording ${recording.mediaStoreUri}", e)
            stopWithError("Recording file could not be opened on device.")
        }
    }

    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        val current = _playbackState.value
        val clamped = positionMs.coerceIn(0, current.totalDurationMs.coerceAtLeast(0))
        runCatching {
            player.seekTo(clamped)
            _playbackState.value = current.copy(currentPositionMs = clamped)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        }
        mediaPlayer = null
        _playbackState.value = RecordingPlaybackState()
    }

    private fun stopWithError(message: String) {
        progressJob?.cancel()
        progressJob = null
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _playbackState.value = RecordingPlaybackState(errorMessage = message)
    }

    fun clearError() {
        if (_playbackState.value.errorMessage != null) {
            _playbackState.value = _playbackState.value.copy(errorMessage = null)
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                val pos = runCatching { player.currentPosition }.getOrDefault(0)
                val dur = runCatching { player.duration }.getOrDefault(_playbackState.value.totalDurationMs)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = playing,
                    currentPositionMs = pos,
                    totalDurationMs = dur.coerceAtLeast(1)
                )
                if (!playing) break
                delay(200L)
            }
        }
    }

    fun release() {
        stop()
    }
}
