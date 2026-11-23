package com.example.domain.model

data class CallRecordingCapability(
    val isSupported: Boolean = true,
    val isTwoWaySupported: Boolean = false,
    val notice: String = "Android platform restrictions limit direct two-way line-in recording on non-system dialers; audio is recorded via microphone and loudspeaker."
)

data class RecordingPlaybackState(
    val recordingId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)
