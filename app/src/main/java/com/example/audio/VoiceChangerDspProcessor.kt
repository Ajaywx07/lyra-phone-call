package com.example.audio

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Pure Kotlin real-time PCM16 DSP processor mirroring the C++17 NDK engine.
 * Used automatically when `liblyra_voice_dsp.so` is unavailable (e.g. Robolectric JVM unit tests
 * or builds compiled without NDK). Lock-free atomic mode switching and zero per-frame allocations.
 */
class VoiceChangerDspProcessor(sampleRate: Int = 16_000) {

    companion object {
        private const val DELAY_BUFFER_SIZE = 4096
        private const val DELAY_MASK = DELAY_BUFFER_SIZE - 1
        private const val GRAIN_SIZE = 1024
        private const val TWO_PI = (2.0 * PI).toFloat()
    }

    private val clampedSampleRate = sampleRate.coerceIn(8_000, 96_000)
    private val combLength = (clampedSampleRate / 135).coerceIn(32, 1024)
    private val delayLine = FloatArray(DELAY_BUFFER_SIZE)
    private val hannWindow = FloatArray(GRAIN_SIZE) { idx ->
        val phase = idx.toFloat() / GRAIN_SIZE.toFloat()
        0.5f * (1.0f - cos(TWO_PI * phase))
    }
    private val combBuffer = FloatArray(combLength)

    private val lpCoeff = 1.0f - exp(-TWO_PI * 2100.0f / clampedSampleRate.toFloat())
    private val hpCoeff = exp(-TWO_PI * 260.0f / clampedSampleRate.toFloat())

    private val targetMode = AtomicInteger(VoiceMode.NONE.nativeOrdinal)

    private var writePos = 0
    private var combPos = 0
    private var phaseA = 0.0f
    private var phaseB = 0.5f
    private var robotPhase1 = 0.0f
    private var robotPhase2 = 0.0f
    private var lpState = 0.0f
    private var hpState = 0.0f
    private var hpPrevIn = 0.0f
    private var currentPitchRatio = 1.0f
    private var effectBlend = 0.0f

    fun setMode(mode: VoiceMode) {
        targetMode.set(mode.nativeOrdinal)
    }

    fun getMode(): VoiceMode = when (targetMode.get()) {
        VoiceMode.MALE.nativeOrdinal -> VoiceMode.MALE
        VoiceMode.FEMALE.nativeOrdinal -> VoiceMode.FEMALE
        VoiceMode.ROBOT.nativeOrdinal -> VoiceMode.ROBOT
        else -> VoiceMode.NONE
    }

    fun reset() {
        delayLine.fill(0.0f)
        combBuffer.fill(0.0f)
        writePos = 0
        combPos = 0
        phaseA = 0.0f
        phaseB = 0.5f
        robotPhase1 = 0.0f
        robotPhase2 = 0.0f
        lpState = 0.0f
        hpState = 0.0f
        hpPrevIn = 0.0f
        currentPitchRatio = 1.0f
        effectBlend = 0.0f
    }

    fun processPcm16(input: ShortArray, output: ShortArray, frameCount: Int) {
        val count = min(frameCount, min(input.size, output.size))
        if (count <= 0) return

        val mode = getMode()
        val targetPitch = when (mode) {
            VoiceMode.MALE -> 0.76f
            VoiceMode.FEMALE -> 1.30f
            VoiceMode.ROBOT, VoiceMode.NONE -> 1.0f
        }
        val targetBlend = if (mode == VoiceMode.NONE) 0.0f else 1.0f
        val sr = clampedSampleRate.toFloat()
        val robotStep1 = TWO_PI * 88.0f / sr
        val robotStep2 = TWO_PI * 176.0f / sr
        val blendStep = 1.0f / max(1.0f, sr * 0.015f)

        for (i in 0 until count) {
            val dry = input[i].toFloat() / 32768.0f

            if (effectBlend < targetBlend) {
                effectBlend = min(targetBlend, effectBlend + blendStep)
            } else if (effectBlend > targetBlend) {
                effectBlend = max(targetBlend, effectBlend - blendStep)
            }
            currentPitchRatio += (targetPitch - currentPitchRatio) * 0.0025f

            delayLine[writePos] = dry

            var wet = dry
            when (mode) {
                VoiceMode.MALE, VoiceMode.FEMALE -> {
                    wet = processOverlapAddPitchShift(currentPitchRatio)
                    if (mode == VoiceMode.MALE) {
                        lpState += lpCoeff * (wet - lpState)
                        wet = (0.72f * lpState) + (0.36f * wet)
                    } else {
                        val hpOut = hpCoeff * (hpState + wet - hpPrevIn)
                        hpPrevIn = wet
                        hpState = hpOut
                        wet = (0.78f * wet) + (0.32f * hpOut)
                    }
                }
                VoiceMode.ROBOT -> {
                    val carrier = (0.68f * sin(robotPhase1)) + (0.32f * sin(robotPhase2))
                    robotPhase1 += robotStep1
                    if (robotPhase1 >= TWO_PI) robotPhase1 -= TWO_PI
                    robotPhase2 += robotStep2
                    if (robotPhase2 >= TWO_PI) robotPhase2 -= TWO_PI

                    val ringMod = dry * carrier
                    val delayedComb = combBuffer[combPos]
                    val combOut = (0.65f * ringMod) + (0.45f * delayedComb)
                    combBuffer[combPos] = (combOut * 0.72f).coerceIn(-1.0f, 1.0f)
                    combPos = (combPos + 1) % combLength

                    val quantized = round(combOut * 24.0f) / 24.0f
                    wet = (0.78f * quantized) + (0.28f * dry)
                }
                VoiceMode.NONE -> Unit
            }

            writePos = (writePos + 1) and DELAY_MASK

            val mixed = dry + effectBlend * (wet - dry)
            val softClipped = tanh((mixed * 1.1f).toDouble()).toFloat()
            val scaled = (softClipped * 32767.0f).coerceIn(-32768.0f, 32767.0f)
            output[i] = scaled.toInt().toShort()
        }
    }

    private fun readInterpolatedDelay(delaySamples: Float): Float {
        val clamped = delaySamples.coerceIn(1.0f, (DELAY_BUFFER_SIZE - 4).toFloat())
        val readPosF = writePos.toFloat() - clamped
        val floored = floor(readPosF)
        val idx0 = floored.toInt() and DELAY_MASK
        val idx1 = (idx0 + 1) and DELAY_MASK
        val frac = readPosF - floored
        val s0 = delayLine[idx0]
        val s1 = delayLine[idx1]
        return s0 + frac * (s1 - s0)
    }

    private fun processOverlapAddPitchShift(pitchRatio: Float): Float {
        val deltaRate = (1.0f - pitchRatio) / GRAIN_SIZE.toFloat()
        phaseA += deltaRate
        phaseB += deltaRate

        while (phaseA >= 1.0f) phaseA -= 1.0f
        while (phaseA < 0.0f) phaseA += 1.0f
        while (phaseB >= 1.0f) phaseB -= 1.0f
        while (phaseB < 0.0f) phaseB += 1.0f

        val delayA = 4.0f + phaseA * GRAIN_SIZE.toFloat()
        val delayB = 4.0f + phaseB * GRAIN_SIZE.toFloat()

        val winIdxA = (phaseA * GRAIN_SIZE.toFloat()).toInt().coerceIn(0, GRAIN_SIZE - 1)
        val winIdxB = (phaseB * GRAIN_SIZE.toFloat()).toInt().coerceIn(0, GRAIN_SIZE - 1)

        val winA = hannWindow[winIdxA]
        val winB = hannWindow[winIdxB]

        return (readInterpolatedDelay(delayA) * winA) + (readInterpolatedDelay(delayB) * winB)
    }
}
