package com.example.audio

import android.util.Log

/**
 * JNI bridge to the C++17 NDK DSP library (`liblyra_voice_dsp.so`).
 * Safely detects library availability so non-NDK preview/unit-test environments
 * automatically fall back to [VoiceChangerDspProcessor] without throwing UnsatisfiedLinkError.
 */
object VoiceChangerNativeBridge {

    private const val TAG = "LyraVoiceNativeBridge"
    private const val LIBRARY_NAME = "lyra_voice_dsp"

    val isNativeAvailable: Boolean by lazy {
        try {
            System.loadLibrary(LIBRARY_NAME)
            true
        } catch (err: Throwable) {
            Log.w(TAG, "Native $LIBRARY_NAME library unavailable; using Kotlin DSP fallback: ${err.message}")
            false
        }
    }

    external fun nativeCreate(sampleRate: Int): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeSetMode(handle: Long, modeOrdinal: Int)

    external fun nativeReset(handle: Long)

    external fun nativeProcessPcm16(
        handle: Long,
        input: ShortArray,
        output: ShortArray,
        frameCount: Int
    )
}
