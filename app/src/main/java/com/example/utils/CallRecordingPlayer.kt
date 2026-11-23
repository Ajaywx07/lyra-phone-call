package com.example.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.data.local.entity.CallRecording
import com.example.domain.model.RecordingPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class CallRecordingPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(RecordingPlaybackState())
    val playbackState: StateFlow<RecordingPlaybackState> = _playbackState.asStateFlow()

    fun play(context: Context, recording: CallRecording) {
        stop()

        val uri: Uri = if (recording.mediaStoreUri.isNotBlank()) {
            Uri.parse(recording.mediaStoreUri)
        } else {
            val file = File(recording.relativePath)
            if (file.exists()) Uri.fromFile(file) else Uri.EMPTY
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                start()
            }
            mediaPlayer = player

            val duration = player.duration.coerceAtLeast(0).toLong()
            _playbackState.value = RecordingPlaybackState(
                recordingId = recording.id,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = if (duration > 0) duration else recording.durationSeconds * 1000L
            )

            startProgressPolling(recording.id)

            player.setOnCompletionListener {
                stop()
            }
            player.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            progressJob?.cancel()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            player.start()
            val currentId = _playbackState.value.recordingId
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            if (currentId != null) {
                startProgressPolling(currentId)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs.toInt())
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            _playbackState.value = RecordingPlaybackState(
                recordingId = null,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
    }

    private fun startProgressPolling(recordingId: Long) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val currentPos = player.currentPosition.coerceAtLeast(0).toLong()
                    val dur = player.duration.coerceAtLeast(0).toLong()
                    _playbackState.value = _playbackState.value.copy(
                        recordingId = recordingId,
                        isPlaying = true,
                        currentPositionMs = currentPos,
                        durationMs = dur
                    )
                }
                delay(200)
            }
        }
    }

    fun release() {
        stop()
    }
}
