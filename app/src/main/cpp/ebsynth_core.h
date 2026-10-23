#ifndef EBSYNTH_CORE_H
#define EBSYNTH_CORE_H

#include <cstdint>
#include <vector>

namespace ebsynth {

struct SynthConfig {
    int patchSize = 5;            // 3, 5, or 7
    int pyramidLevels = 4;        // Coarse-to-fine pyramid depth
    int searchIterations = 5;     // PatchMatch propagation + random search passes
    float edgeWeight = 2.4f;      // Edge preservation guiding channel weight
    float luminanceWeight = 1.6f; // Shading/Luminance guiding channel weight
    float colorWeight = 1.0f;     // Chrominance guiding channel weight
    float flowWeight = 2.2f;      // Optical flow temporal coherence weight
    float uniformityWeight = 0.8f;// Brush stroke coherence regularization
    bool useGpuCompute = true;    // OpenGL ES 3.1 Compute Shader acceleration
};

struct NnfOffset {
    int16_t sx;
    int16_t sy;
    float error;
};

struct GuidingChannels {
    int width = 0;
    int height = 0;
    std::vector<float> luminance; // Normalized [0..1] Y channel
    std::vector<float> edgeMag;   // Sobel/Scharr edge magnitude [0..1]
    std::vector<float> edgeDirX;  // Normalized horizontal gradient
    std::vector<float> edgeDirY;  // Normalized vertical gradient
    std::vector<float> chromaU;   // Normalized U chrominance
    std::vector<float> chromaV;   // Normalized V chrominance
};

struct FlowField {
    int width = 0;
    int height = 0;
    std::vector<float> vx;        // Horizontal motion in pixels
    std::vector<float> vy;        // Vertical motion in pixels
};

class EbSynthEngine {
public:
    static GuidingChannels extractGuidingChannels(
        const uint32_t* argbPixels,
        int width,
        int height
    );

    static FlowField computeDenseOpticalFlow(
        const GuidingChannels& prevGuide,
        const GuidingChannels& currGuide,
        int blockRadius = 4,
        int searchRadius = 8
    );

    static void synthesizeFrame(
        const uint32_t* styleKeyframeArgb,
        const uint32_t* sourceKeyframeArgb,
        const uint32_t* targetFrameArgb,
        const uint32_t* prevTargetFrameArgb,
        const uint32_t* prevSynthesizedArgb,
        uint32_t* outSynthesizedArgb,
        float* outErrorMap,
        int width,
        int height,
        const SynthConfig& config
    );

    static void blendBiDirectional(
        const uint32_t* forwardSynthArgb,
        const float* forwardErrorMap,
        const uint32_t* backwardSynthArgb,
        const float* backwardErrorMap,
        uint32_t* outBlendedArgb,
        int width,
        int height,
        float temporalAlpha
    );
};

} // namespace ebsynth

#endif // EBSYNTH_CORE_H
