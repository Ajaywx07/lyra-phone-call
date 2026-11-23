package com.example.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Process
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provides low-latency, non-blocking dialpad audio feedback:
 * 1) DTMF touch-tone button press sounds via [ToneGenerator]
 * 2) Spoken digit/symbol announcements via [TextToSpeech] using QUEUE_FLUSH
 *
 * All audio operations execute on a dedicated single-thread background audio executor
 * so rapid typing on the UI thread never blocks or lags.
 */
class DialpadAudioFeedbackManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lyra-dialpad-audio").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY - 1
        }
    }

    private var toneGenerator: ToneGenerator? = null
    private var toneInitFailed = false

    private var tts: TextToSpeech? = null
    private val ttsInitialized = AtomicBoolean(false)
    private val ttsInitStarted = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    fun warmUpVoice() {
        if (released.get() || ttsInitStarted.get()) return
        audioExecutor.execute {
            ensureTtsInitialized()
        }
    }

    fun playDigitFeedback(
        digit: String,
        playTone: Boolean,
        speakDigit: Boolean
    ) {
        if (released.get() || (!playTone && !speakDigit)) return
        audioExecutor.execute {
            if (released.get()) return@execute
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            }
            if (playTone) {
                playDtmfToneInternal(digit)
            }
            if (speakDigit) {
                speakDigitInternal(digit)
            }
        }
    }

    private fun playDtmfToneInternal(digit: String) {
        val toneType = mapDigitToDtmfTone(digit) ?: return
        if (toneInitFailed) return
        val generator = toneGenerator ?: runCatching {
            ToneGenerator(AudioManager.STREAM_DTMF, 72)
        }.onFailure {
            toneInitFailed = true
        }.getOrNull()?.also {
            toneGenerator = it
        } ?: return

        runCatching {
            generator.stopTone()
            generator.startTone(toneType, 80)
        }
    }

    private fun ensureTtsInitialized() {
        if (released.get() || ttsInitStarted.getAndSet(true)) return
        runCatching {
            var engineRef: TextToSpeech? = null
            engineRef = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS && !released.get()) {
                    runCatching {
                        engineRef?.language = Locale.getDefault()
                        engineRef?.setSpeechRate(1.25f)
                    }
                    ttsInitialized.set(true)
                }
            }
            tts = engineRef
        }
    }

    private fun speakDigitInternal(digit: String) {
        val spoken = mapDigitToSpokenWord(digit) ?: return
        if (!ttsInitStarted.get()) {
            ensureTtsInitialized()
        }
        val engine = tts
        if (engine != null && ttsInitialized.get()) {
            runCatching {
                engine.speak(
                    spoken,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "lyra_dial_${System.nanoTime()}"
                )
            }
        }
    }

    fun release() {
        if (released.getAndSet(true)) return
        runCatching {
            audioExecutor.execute {
                runCatching { toneGenerator?.stopTone() }
                runCatching { toneGenerator?.release() }
                toneGenerator = null
                runCatching { tts?.stop() }
                runCatching { tts?.shutdown() }
                tts = null
                ttsInitialized.set(false)
            }
        }
        runCatching { audioExecutor.shutdown() }
    }

    companion object {
        internal fun mapDigitToDtmfTone(digit: String): Int? = when (digit) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            "+" -> ToneGenerator.TONE_DTMF_0
            else -> null
        }

        internal fun mapDigitToSpokenWord(digit: String): String? = when (digit) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> digit
            "*" -> "star"
            "#" -> "hash"
            "+" -> "plus"
            else -> null
        }
    }
}
