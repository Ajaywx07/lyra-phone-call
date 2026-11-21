package com.example.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "VoicemailEngine"

/**
 * Handles local audio orchestration for Lyra's Voicemail flow:
 * 1. Speaks the custom Lyra greeting via Android TextToSpeech
 * 2. Plays a crisp audible beep via ToneGenerator
 * 3. Plays back saved local WAV voicemail recordings via MediaPlayer
 */
class VoicemailEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _playingVoicemailId = MutableStateFlow<Long?>(null)
    val playingVoicemailId: StateFlow<Long?> = _playingVoicemailId.asStateFlow()

    suspend fun speakGreeting(greetingText: String) {
        val cleanText = greetingText.trim()
        if (cleanText.isBlank()) return

        // Cap TTS wait cleanly so even devices without TTS voices progress smoothly to the beep
        val estimatedWaitMs = (cleanText.length * 65L).coerceIn(3000L, 12000L)
        withTimeoutOrNull(estimatedWaitMs) {
            speakWithTts(cleanText)
        } ?: run {
            // Fallback pacing if TTS engine couldn't synthesize audio on this device
            delay(1200L)
        }
    }

    private suspend fun speakWithTts(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        val utteranceId = "lyra_vm_${UUID.randomUUID()}"
        var localTts: TextToSpeech? = null

        fun finishSafely() {
            if (cont.isActive) cont.resume(Unit)
        }

        try {
            localTts = TextToSpeech(context.applicationContext) { status ->
                val engine = localTts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    Log.w(TAG, "TTS unavailable (status=$status); continuing after short delay")
                    finishSafely()
                    return@TextToSpeech
                }
                engine.language = Locale.US
                engine.setSpeechRate(0.96f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) finishSafely()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) finishSafely()
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) finishSafely()
                    }
                })
                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                }
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result == TextToSpeech.ERROR) {
                    finishSafely()
                }
            }
            tts = localTts
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech greeting failed", e)
            finishSafely()
        }

        cont.invokeOnCancellation {
            runCatching {
                localTts?.stop()
                localTts?.shutdown()
            }
        }
    }

    suspend fun playVoicemailBeep() = withContext(Dispatchers.Default) {
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 420)
            delay(500L)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator beep unavailable, continuing", e)
            delay(400L)
        } finally {
            runCatching {
                toneGen?.stopTone()
                toneGen?.release()
            }
        }
    }

    fun togglePlayback(voicemailId: Long, filePath: String) {
        if (_playingVoicemailId.value == voicemailId) {
            stopPlayback()
            return
        }
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "Voicemail audio file not found: $filePath")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playingVoicemailId.value = voicemailId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voicemail file", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playingVoicemailId.value = null
    }

    fun stopGreeting() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    fun release() {
        stopGreeting()
        stopPlayback()
    }
}
