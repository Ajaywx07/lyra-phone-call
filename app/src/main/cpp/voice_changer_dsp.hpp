#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <vector>

namespace lyra::audio {

enum class VoiceMode : int32_t {
    NONE = 0,
    MALE = 1,
    FEMALE = 2,
    ROBOT = 3
};

inline VoiceMode voiceModeFromInt(int32_t ordinal) noexcept {
    switch (ordinal) {
        case 1:
            return VoiceMode::MALE;
        case 2:
            return VoiceMode::FEMALE;
        case 3:
            return VoiceMode::ROBOT;
        default:
            return VoiceMode::NONE;
    }
}

/**
 * Lock-free single-producer single-consumer ring buffer for real-time audio pipelines.
 * Capacity must be a power of two.
 */
template <typename T, size_t Capacity>
class LockFreeRingBuffer {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of two");

public:
    bool push(T sample) noexcept {
        const size_t writeIdx = writeIndex_.load(std::memory_order_relaxed);
        const size_t nextWrite = (writeIdx + 1) & kMask;
        if (nextWrite == readIndex_.load(std::memory_order_acquire)) {
            return false;
        }
        buffer_[writeIdx] = sample;
        writeIndex_.store(nextWrite, std::memory_order_release);
        return true;
    }

    bool pop(T& outSample) noexcept {
        const size_t readIdx = readIndex_.load(std::memory_order_relaxed);
        if (readIdx == writeIndex_.load(std::memory_order_acquire)) {
            return false;
        }
        outSample = buffer_[readIdx];
        readIndex_.store((readIdx + 1) & kMask, std::memory_order_release);
        return true;
    }

    void clear() noexcept {
        readIndex_.store(0, std::memory_order_relaxed);
        writeIndex_.store(0, std::memory_order_relaxed);
        buffer_.fill(T{});
    }

private:
    static constexpr size_t kMask = Capacity - 1;
    std::array<T, Capacity> buffer_{};
    std::atomic<size_t> readIndex_{0};
    std::atomic<size_t> writeIndex_{0};
};

/**
 * Low-latency real-time voice changer DSP processor (C++17).
 * Uses dual-head Hann-windowed overlap-add (OLA) pitch shifting for Male/Female,
 * dual-carrier ring modulation + comb timbre for Robot, and lock-free atomic mode switching
 * with click-free crossfade transitions.
 */
class VoiceChangerDsp {
public:
    static constexpr int32_t kDelayBufferSize = 4096;
    static constexpr int32_t kDelayMask = kDelayBufferSize - 1;
    static constexpr int32_t kGrainSize = 1024;
    static constexpr float kTwoPi = 6.28318530717958647692f;

    explicit VoiceChangerDsp(int32_t sampleRate) noexcept
        : sampleRate_(std::clamp(sampleRate, 8000, 96000)),
          combLength_(std::clamp(sampleRate_ / 135, 32, 1024)),
          combBuffer_(static_cast<size_t>(combLength_), 0.0f) {
        delayLine_.fill(0.0f);
        for (int32_t i = 0; i < kGrainSize; ++i) {
            const float phase = static_cast<float>(i) / static_cast<float>(kGrainSize);
            hannWindow_[static_cast<size_t>(i)] = 0.5f * (1.0f - std::cos(kTwoPi * phase));
        }
        const float lpCutoff = 2100.0f;
        lpCoeff_ = 1.0f - std::exp(-kTwoPi * lpCutoff / static_cast<float>(sampleRate_));
        const float hpCutoff = 260.0f;
        hpCoeff_ = std::exp(-kTwoPi * hpCutoff / static_cast<float>(sampleRate_));
        reset();
    }

    void setMode(VoiceMode mode) noexcept {
        targetMode_.store(static_cast<int32_t>(mode), std::memory_order_release);
    }

    VoiceMode getMode() const noexcept {
        return voiceModeFromInt(targetMode_.load(std::memory_order_acquire));
    }

    void reset() noexcept {
        delayLine_.fill(0.0f);
        std::fill(combBuffer_.begin(), combBuffer_.end(), 0.0f);
        writePos_ = 0;
        combPos_ = 0;
        phaseA_ = 0.0f;
        phaseB_ = 0.5f;
        robotPhase1_ = 0.0f;
        robotPhase2_ = 0.0f;
        lpState_ = 0.0f;
        hpState_ = 0.0f;
        hpPrevIn_ = 0.0f;
        currentPitchRatio_ = 1.0f;
        effectBlend_ = 0.0f;
        ringScratch_.clear();
    }

    void processPcm16(const int16_t* input, int16_t* output, int32_t frameCount) noexcept {
        if (input == nullptr || output == nullptr || frameCount <= 0) {
            return;
        }

        const VoiceMode mode = getMode();
        const float targetPitch = pitchRatioFor(mode);
        const float targetBlend = (mode == VoiceMode::NONE) ? 0.0f : 1.0f;
        const float sr = static_cast<float>(sampleRate_);
        const float robotStep1 = kTwoPi * 88.0f / sr;
        const float robotStep2 = kTwoPi * 176.0f / sr;
        const float blendStep = 1.0f / std::max(1.0f, sr * 0.015f); // 15 ms click-free crossfade

        for (int32_t i = 0; i < frameCount; ++i) {
            const float dry = static_cast<float>(input[i]) / 32768.0f;

            // Smooth crossfade & pitch interpolation
            if (effectBlend_ < targetBlend) {
                effectBlend_ = std::min(targetBlend, effectBlend_ + blendStep);
            } else if (effectBlend_ > targetBlend) {
                effectBlend_ = std::max(targetBlend, effectBlend_ - blendStep);
            }
            currentPitchRatio_ += (targetPitch - currentPitchRatio_) * 0.0025f;

            // Write dry sample into circular delay buffer
            delayLine_[static_cast<size_t>(writePos_)] = dry;

            float wet = dry;
            if (mode == VoiceMode::MALE || mode == VoiceMode::FEMALE) {
                wet = processOverlapAddPitchShift(currentPitchRatio_);
                if (mode == VoiceMode::MALE) {
                    // Warm formant smoothing + chest weight
                    lpState_ += lpCoeff_ * (wet - lpState_);
                    wet = (0.72f * lpState_) + (0.36f * wet);
                } else {
                    // Clear brighter formant presence for Female mode
                    const float hpOut = hpCoeff_ * (hpState_ + wet - hpPrevIn_);
                    hpPrevIn_ = wet;
                    hpState_ = hpOut;
                    wet = (0.78f * wet) + (0.32f * hpOut);
                }
            } else if (mode == VoiceMode::ROBOT) {
                // Dual-carrier ring modulation + metallic comb filter + subtle quantization
                const float carrier = (0.68f * std::sin(robotPhase1_)) + (0.32f * std::sin(robotPhase2_));
                robotPhase1_ += robotStep1;
                if (robotPhase1_ >= kTwoPi) robotPhase1_ -= kTwoPi;
                robotPhase2_ += robotStep2;
                if (robotPhase2_ >= kTwoPi) robotPhase2_ -= kTwoPi;

                const float ringMod = dry * carrier;
                const float delayedComb = combBuffer_[static_cast<size_t>(combPos_)];
                const float combOut = (0.65f * ringMod) + (0.45f * delayedComb);
                combBuffer_[static_cast<size_t>(combPos_)] = std::clamp(combOut * 0.72f, -1.0f, 1.0f);
                combPos_ = (combPos_ + 1) % combLength_;

                // Subtle 6-bit harmonic grit blended with dry intelligibility
                const float quantized = std::round(combOut * 24.0f) / 24.0f;
                wet = (0.78f * quantized) + (0.28f * dry);
            }

            writePos_ = (writePos_ + 1) & kDelayMask;

            const float mixed = dry + effectBlend_ * (wet - dry);
            const float softClipped = std::tanh(mixed * 1.1f);
            const float scaled = std::clamp(softClipped * 32767.0f, -32768.0f, 32767.0f);
            output[i] = static_cast<int16_t>(scaled);
        }
    }

private:
    static float pitchRatioFor(VoiceMode mode) noexcept {
        switch (mode) {
            case VoiceMode::MALE:
                return 0.76f;
            case VoiceMode::FEMALE:
                return 1.30f;
            case VoiceMode::ROBOT:
            case VoiceMode::NONE:
            default:
                return 1.0f;
        }
    }

    float readInterpolatedDelay(float delaySamples) const noexcept {
        const float clamped = std::clamp(delaySamples, 1.0f, static_cast<float>(kDelayBufferSize - 4));
        const float readPosF = static_cast<float>(writePos_) - clamped;
        const int32_t idx0 = static_cast<int32_t>(std::floor(readPosF)) & kDelayMask;
        const int32_t idx1 = (idx0 + 1) & kDelayMask;
        const float frac = readPosF - std::floor(readPosF);
        const float s0 = delayLine_[static_cast<size_t>(idx0)];
        const float s1 = delayLine_[static_cast<size_t>(idx1)];
        return s0 + frac * (s1 - s0);
    }

    float processOverlapAddPitchShift(float pitchRatio) noexcept {
        const float deltaRate = (1.0f - pitchRatio) / static_cast<float>(kGrainSize);
        phaseA_ += deltaRate;
        phaseB_ += deltaRate;

        while (phaseA_ >= 1.0f) phaseA_ -= 1.0f;
        while (phaseA_ < 0.0f) phaseA_ += 1.0f;
        while (phaseB_ >= 1.0f) phaseB_ -= 1.0f;
        while (phaseB_ < 0.0f) phaseB_ += 1.0f;

        const float delayA = 4.0f + phaseA_ * static_cast<float>(kGrainSize);
        const float delayB = 4.0f + phaseB_ * static_cast<float>(kGrainSize);

        const int32_t winIdxA = std::clamp(static_cast<int32_t>(phaseA_ * kGrainSize), 0, kGrainSize - 1);
        const int32_t winIdxB = std::clamp(static_cast<int32_t>(phaseB_ * kGrainSize), 0, kGrainSize - 1);

        const float winA = hannWindow_[static_cast<size_t>(winIdxA)];
        const float winB = hannWindow_[static_cast<size_t>(winIdxB)];

        const float sampleA = readInterpolatedDelay(delayA) * winA;
        const float sampleB = readInterpolatedDelay(delayB) * winB;
        return sampleA + sampleB;
    }

    int32_t sampleRate_;
    int32_t combLength_;
    std::array<float, kDelayBufferSize> delayLine_{};
    std::array<float, kGrainSize> hannWindow_{};
    std::vector<float> combBuffer_;
    LockFreeRingBuffer<float, 1024> ringScratch_;

    std::atomic<int32_t> targetMode_{0};
    int32_t writePos_ = 0;
    int32_t combPos_ = 0;
    float phaseA_ = 0.0f;
    float phaseB_ = 0.5f;
    float robotPhase1_ = 0.0f;
    float robotPhase2_ = 0.0f;
    float lpCoeff_ = 0.5f;
    float hpCoeff_ = 0.9f;
    float lpState_ = 0.0f;
    float hpState_ = 0.0f;
    float hpPrevIn_ = 0.0f;
    float currentPitchRatio_ = 1.0f;
    float effectBlend_ = 0.0f;
};

} // namespace lyra::audio
