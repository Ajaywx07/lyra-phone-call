#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <new>
#include "voice_changer_dsp.hpp"

#define LYRA_LOG_TAG "LyraVoiceDsp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LYRA_LOG_TAG, __VA_ARGS__)

namespace {

using lyra::audio::VoiceChangerDsp;
using lyra::audio::voiceModeFromInt;

VoiceChangerDsp* fromHandle(jlong handle) noexcept {
    if (handle == 0L) return nullptr;
    return reinterpret_cast<VoiceChangerDsp*>(static_cast<uintptr_t>(handle));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audio_VoiceChangerNativeBridge_nativeCreate(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jint sampleRate) {
    auto* dsp = new (std::nothrow) VoiceChangerDsp(static_cast<int32_t>(sampleRate));
    if (dsp != nullptr) {
        LOGI("Created native VoiceChangerDsp at %d Hz", static_cast<int>(sampleRate));
    }
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(dsp));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_VoiceChangerNativeBridge_nativeDestroy(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {
    auto* dsp = fromHandle(handle);
    delete dsp;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_VoiceChangerNativeBridge_nativeSetMode(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle,
        jint modeOrdinal) {
    if (auto* dsp = fromHandle(handle)) {
        dsp->setMode(voiceModeFromInt(static_cast<int32_t>(modeOrdinal)));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_VoiceChangerNativeBridge_nativeReset(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {
    if (auto* dsp = fromHandle(handle)) {
        dsp->reset();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_VoiceChangerNativeBridge_nativeProcessPcm16(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jshortArray input,
        jshortArray output,
        jint frameCount) {
    auto* dsp = fromHandle(handle);
    if (dsp == nullptr || input == nullptr || output == nullptr || frameCount <= 0) {
        return;
    }

    jshort* inPtr = env->GetShortArrayElements(input, nullptr);
    jshort* outPtr = env->GetShortArrayElements(output, nullptr);
    if (inPtr != nullptr && outPtr != nullptr) {
        dsp->processPcm16(
            reinterpret_cast<const int16_t*>(inPtr),
            reinterpret_cast<int16_t*>(outPtr),
            static_cast<int32_t>(frameCount)
        );
    }
    if (inPtr != nullptr) {
        env->ReleaseShortArrayElements(input, inPtr, JNI_ABORT);
    }
    if (outPtr != nullptr) {
        env->ReleaseShortArrayElements(output, outPtr, 0);
    }
}
