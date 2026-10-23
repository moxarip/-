#include <jni.h>
#include <android/log.h>
#include "ebsynth_core.h"

#define LOG_TAG "EbSynthNativeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_engine_EbSynthNativeBridge_nativeSynthesizeFrame(
    JNIEnv* env,
    jobject /* thiz */,
    jintArray styleKeyframeArgb,
    jintArray sourceKeyframeArgb,
    jintArray targetFrameArgb,
    jintArray prevTargetFrameArgb,
    jintArray prevSynthesizedArgb,
    jintArray outSynthesizedArgb,
    jfloatArray outErrorMap,
    jint width,
    jint height,
    jint patchSize,
    jint pyramidLevels,
    jint searchIterations,
    jfloat edgeWeight,
    jfloat luminanceWeight,
    jfloat colorWeight,
    jfloat flowWeight,
    jfloat uniformityWeight
) {
    jint* stylePtr = env->GetIntArrayElements(styleKeyframeArgb, nullptr);
    jint* srcPtr = env->GetIntArrayElements(sourceKeyframeArgb, nullptr);
    jint* tgtPtr = env->GetIntArrayElements(targetFrameArgb, nullptr);
    jint* prevTgtPtr = (prevTargetFrameArgb != nullptr)
        ? env->GetIntArrayElements(prevTargetFrameArgb, nullptr) : nullptr;
    jint* prevSynthPtr = (prevSynthesizedArgb != nullptr)
        ? env->GetIntArrayElements(prevSynthesizedArgb, nullptr) : nullptr;
    jint* outPtr = env->GetIntArrayElements(outSynthesizedArgb, nullptr);
    jfloat* errPtr = (outErrorMap != nullptr)
        ? env->GetFloatArrayElements(outErrorMap, nullptr) : nullptr;

    ebsynth::SynthConfig cfg;
    cfg.patchSize = patchSize;
    cfg.pyramidLevels = pyramidLevels;
    cfg.searchIterations = searchIterations;
    cfg.edgeWeight = edgeWeight;
    cfg.luminanceWeight = luminanceWeight;
    cfg.colorWeight = colorWeight;
    cfg.flowWeight = flowWeight;
    cfg.uniformityWeight = uniformityWeight;

    ebsynth::EbSynthEngine::synthesizeFrame(
        reinterpret_cast<const uint32_t*>(stylePtr),
        reinterpret_cast<const uint32_t*>(srcPtr),
        reinterpret_cast<const uint32_t*>(tgtPtr),
        reinterpret_cast<const uint32_t*>(prevTgtPtr),
        reinterpret_cast<const uint32_t*>(prevSynthPtr),
        reinterpret_cast<uint32_t*>(outPtr),
        errPtr,
        width,
        height,
        cfg
    );

    env->ReleaseIntArrayElements(styleKeyframeArgb, stylePtr, JNI_ABORT);
    env->ReleaseIntArrayElements(sourceKeyframeArgb, srcPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(targetFrameArgb, tgtPtr, JNI_ABORT);
    if (prevTgtPtr != nullptr) {
        env->ReleaseIntArrayElements(prevTargetFrameArgb, prevTgtPtr, JNI_ABORT);
    }
    if (prevSynthPtr != nullptr) {
        env->ReleaseIntArrayElements(prevSynthesizedArgb, prevSynthPtr, JNI_ABORT);
    }
    env->ReleaseIntArrayElements(outSynthesizedArgb, outPtr, 0);
    if (errPtr != nullptr) {
        env->ReleaseFloatArrayElements(outErrorMap, errPtr, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_example_engine_EbSynthNativeBridge_nativeBlendBiDirectional(
    JNIEnv* env,
    jobject /* thiz */,
    jintArray forwardSynthArgb,
    jfloatArray forwardErrorMap,
    jintArray backwardSynthArgb,
    jfloatArray backwardErrorMap,
    jintArray outBlendedArgb,
    jint width,
    jint height,
    jfloat temporalAlpha
) {
    jint* fwdPtr = env->GetIntArrayElements(forwardSynthArgb, nullptr);
    jfloat* fwdErr = (forwardErrorMap != nullptr)
        ? env->GetFloatArrayElements(forwardErrorMap, nullptr) : nullptr;
    jint* bwdPtr = env->GetIntArrayElements(backwardSynthArgb, nullptr);
    jfloat* bwdErr = (backwardErrorMap != nullptr)
        ? env->GetFloatArrayElements(backwardErrorMap, nullptr) : nullptr;
    jint* outPtr = env->GetIntArrayElements(outBlendedArgb, nullptr);

    ebsynth::EbSynthEngine::blendBiDirectional(
        reinterpret_cast<const uint32_t*>(fwdPtr),
        fwdErr,
        reinterpret_cast<const uint32_t*>(bwdPtr),
        bwdErr,
        reinterpret_cast<uint32_t*>(outPtr),
        width,
        height,
        temporalAlpha
    );

    env->ReleaseIntArrayElements(forwardSynthArgb, fwdPtr, JNI_ABORT);
    if (fwdErr != nullptr) {
        env->ReleaseFloatArrayElements(forwardErrorMap, fwdErr, JNI_ABORT);
    }
    env->ReleaseIntArrayElements(backwardSynthArgb, bwdPtr, JNI_ABORT);
    if (bwdErr != nullptr) {
        env->ReleaseFloatArrayElements(backwardErrorMap, bwdErr, JNI_ABORT);
    }
    env->ReleaseIntArrayElements(outBlendedArgb, outPtr, 0);
}

} // extern "C"
