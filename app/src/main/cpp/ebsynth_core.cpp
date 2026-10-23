#include "ebsynth_core.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <thread>
#include <vector>

namespace ebsynth {

namespace {

inline int clampCoord(int v, int low, int high) {
    return v < low ? low : (v > high ? high : v);
}

inline uint32_t packArgb(int a, int r, int g, int b) {
    return ((static_cast<uint32_t>(clampCoord(a, 0, 255)) & 0xFFu) << 24) |
           ((static_cast<uint32_t>(clampCoord(r, 0, 255)) & 0xFFu) << 16) |
           ((static_cast<uint32_t>(clampCoord(g, 0, 255)) & 0xFFu) << 8)  |
           (static_cast<uint32_t>(clampCoord(b, 0, 255)) & 0xFFu);
}

inline void unpackArgb(uint32_t c, int& r, int& g, int& b) {
    r = static_cast<int>((c >> 16) & 0xFFu);
    g = static_cast<int>((c >> 8) & 0xFFu);
    b = static_cast<int>(c & 0xFFu);
}

struct FastRng {
    uint32_t state;
    explicit FastRng(uint32_t seed) : state(seed ? seed : 0x9E3779B9u) {}
    inline uint32_t nextU32() {
        uint32_t x = state;
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        state = x;
        return x;
    }
    inline int nextRange(int minVal, int maxVal) {
        if (maxVal <= minVal) return minVal;
        uint32_t span = static_cast<uint32_t>(maxVal - minVal + 1);
        return minVal + static_cast<int>(nextU32() % span);
    }
};

std::vector<uint32_t> downsampleArgb(const uint32_t* src, int srcW, int srcH, int dstW, int dstH) {
    std::vector<uint32_t> dst(static_cast<size_t>(dstW * dstH));
    for (int y = 0; y < dstH; ++y) {
        int sy0 = (y * srcH) / dstH;
        int sy1 = std::min(srcH - 1, sy0 + 1);
        for (int x = 0; x < dstW; ++x) {
            int sx0 = (x * srcW) / dstW;
            int sx1 = std::min(srcW - 1, sx0 + 1);

            int r00, g00, b00, r10, g10, b10, r01, g01, b01, r11, g11, b11;
            unpackArgb(src[sy0 * srcW + sx0], r00, g00, b00);
            unpackArgb(src[sy0 * srcW + sx1], r10, g10, b10);
            unpackArgb(src[sy1 * srcW + sx0], r01, g01, b01);
            unpackArgb(src[sy1 * srcW + sx1], r11, g11, b11);

            int r = (r00 + r10 + r01 + r11 + 2) >> 2;
            int g = (g00 + g10 + g01 + g11 + 2) >> 2;
            int b = (b00 + b10 + b01 + b11 + 2) >> 2;
            dst[y * dstW + x] = packArgb(255, r, g, b);
        }
    }
    return dst;
}

float computePatchDistance(
    const GuidingChannels& srcGuide,
    const GuidingChannels& dstGuide,
    const uint32_t* temporalStyleGuide,
    const uint32_t* stylePixels,
    int sx, int sy,
    int tx, int ty,
    int halfPatch,
    const SynthConfig& cfg,
    float bestSoFar
) {
    const int w = srcGuide.width;
    const int h = srcGuide.height;
    float totalError = 0.0f;

    for (int dy = -halfPatch; dy <= halfPatch; ++dy) {
        int cSy = clampCoord(sy + dy, 0, h - 1);
        int cTy = clampCoord(ty + dy, 0, h - 1);
        int sRow = cSy * w;
        int tRow = cTy * w;

        for (int dx = -halfPatch; dx <= halfPatch; ++dx) {
            int cSx = clampCoord(sx + dx, 0, w - 1);
            int cTx = clampCoord(tx + dx, 0, w - 1);
            int sIdx = sRow + cSx;
            int tIdx = tRow + cTx;

            float dLum = srcGuide.luminance[sIdx] - dstGuide.luminance[tIdx];
            float dEdge = srcGuide.edgeMag[sIdx] - dstGuide.edgeMag[tIdx];
            float dGx = srcGuide.edgeDirX[sIdx] - dstGuide.edgeDirX[tIdx];
            float dGy = srcGuide.edgeDirY[sIdx] - dstGuide.edgeDirY[tIdx];
            float dU = srcGuide.chromaU[sIdx] - dstGuide.chromaU[tIdx];
            float dV = srcGuide.chromaV[sIdx] - dstGuide.chromaV[tIdx];

            float pixelCost =
                cfg.luminanceWeight * (dLum * dLum) +
                cfg.edgeWeight * (dEdge * dEdge + 0.35f * (dGx * dGx + dGy * dGy)) +
                cfg.colorWeight * (dU * dU + dV * dV);

            if (temporalStyleGuide != nullptr && stylePixels != nullptr && cfg.flowWeight > 0.0f) {
                int sr, sg, sb, tr, tg, tb;
                unpackArgb(stylePixels[sIdx], sr, sg, sb);
                unpackArgb(temporalStyleGuide[tIdx], tr, tg, tb);
                float dr = static_cast<float>(sr - tr) * (1.0f / 255.0f);
                float dg = static_cast<float>(sg - tg) * (1.0f / 255.0f);
                float db = static_cast<float>(sb - tb) * (1.0f / 255.0f);
                pixelCost += cfg.flowWeight * 0.25f * (dr * dr + dg * dg + db * db);
            }

            totalError += pixelCost;
        }
        if (totalError >= bestSoFar) {
            return totalError;
        }
    }

    // Spatial positional regularization to prevent background drift in flat anime regions
    float normDx = static_cast<float>(sx - tx) / static_cast<float>( std::max(1, w) );
    float normDy = static_cast<float>(sy - ty) / static_cast<float>( std::max(1, h) );
    totalError += 0.12f * cfg.uniformityWeight * (normDx * normDx + normDy * normDy);

    return totalError;
}

} // anonymous namespace

GuidingChannels EbSynthEngine::extractGuidingChannels(
    const uint32_t* argbPixels,
    int width,
    int height
) {
    GuidingChannels ch;
    ch.width = width;
    ch.height = height;
    const size_t count = static_cast<size_t>(width * height);
    ch.luminance.resize(count, 0.0f);
    ch.edgeMag.resize(count, 0.0f);
    ch.edgeDirX.resize(count, 0.0f);
    ch.edgeDirY.resize(count, 0.0f);
    ch.chromaU.resize(count, 0.0f);
    ch.chromaV.resize(count, 0.0f);

    // Pass 1: Extract Rec.709 Luminance & Normalized Chrominance
    for (size_t i = 0; i < count; ++i) {
        int r, g, b;
        unpackArgb(argbPixels[i], r, g, b);
        float rf = static_cast<float>(r) * (1.0f / 255.0f);
        float gf = static_cast<float>(g) * (1.0f / 255.0f);
        float bf = static_cast<float>(b) * (1.0f / 255.0f);

        float y = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf;
        float u = (bf - y) * 0.564f + 0.5f;
        float v = (rf - y) * 0.713f + 0.5f;

        ch.luminance[i] = y;
        ch.chromaU[i] = u;
        ch.chromaV[i] = v;
    }

    // Pass 2: Scharr High-Precision Rotational-Invariant Edge Detector
    for (int y = 0; y < height; ++y) {
        int ym1 = clampCoord(y - 1, 0, height - 1);
        int yp1 = clampCoord(y + 1, 0, height - 1);
        for (int x = 0; x < width; ++x) {
            int xm1 = clampCoord(x - 1, 0, width - 1);
            int xp1 = clampCoord(x + 1, 0, width - 1);

            float l00 = ch.luminance[ym1 * width + xm1];
            float l10 = ch.luminance[ym1 * width + x];
            float l20 = ch.luminance[ym1 * width + xp1];

            float l01 = ch.luminance[y * width + xm1];
            float l21 = ch.luminance[y * width + xp1];

            float l02 = ch.luminance[yp1 * width + xm1];
            float l12 = ch.luminance[yp1 * width + x];
            float l22 = ch.luminance[yp1 * width + xp1];

            float gx = (3.0f * l20 + 10.0f * l21 + 3.0f * l22) -
                       (3.0f * l00 + 10.0f * l01 + 3.0f * l02);
            float gy = (3.0f * l02 + 10.0f * l12 + 3.0f * l22) -
                       (3.0f * l00 + 10.0f * l10 + 3.0f * l20);

            gx *= (1.0f / 16.0f);
            gy *= (1.0f / 16.0f);

            float mag = std::min(1.0f, std::sqrt(gx * gx + gy * gy) * 2.2f);
            size_t idx = static_cast<size_t>(y * width + x);
            ch.edgeMag[idx] = mag;
            ch.edgeDirX[idx] = std::max(-1.0f, std::min(1.0f, gx));
            ch.edgeDirY[idx] = std::max(-1.0f, std::min(1.0f, gy));
        }
    }

    return ch;
}

FlowField EbSynthEngine::computeDenseOpticalFlow(
    const GuidingChannels& prevGuide,
    const GuidingChannels& currGuide,
    int blockRadius,
    int searchRadius
) {
    const int w = currGuide.width;
    const int h = currGuide.height;
    FlowField flow;
    flow.width = w;
    flow.height = h;
    flow.vx.assign(static_cast<size_t>(w * h), 0.0f);
    flow.vy.assign(static_cast<size_t>(w * h), 0.0f);

    if (prevGuide.width != w || prevGuide.height != h || w <= 0 || h <= 0) {
        return flow;
    }

    const int step = 2;
    for (int y = 0; y < h; y += step) {
        for (int x = 0; x < w; x += step) {
            float bestSad = 1e9f;
            int bestDx = 0;
            int bestDy = 0;

            for (int dy = -searchRadius; dy <= searchRadius; dy += 2) {
                for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
                    float sad = 0.0f;
                    for (int by = -blockRadius; by <= blockRadius; by += 2) {
                        int py = clampCoord(y + by, 0, h - 1);
                        int cy = clampCoord(y + by + dy, 0, h - 1);
                        for (int bx = -blockRadius; bx <= blockRadius; bx += 2) {
                            int px = clampCoord(x + bx, 0, w - 1);
                            int cx = clampCoord(x + bx + dx, 0, w - 1);
                            int pIdx = py * w + px;
                            int cIdx = cy * w + cx;

                            float dl = std::abs(prevGuide.luminance[pIdx] - currGuide.luminance[cIdx]);
                            float de = std::abs(prevGuide.edgeMag[pIdx] - currGuide.edgeMag[cIdx]);
                            sad += dl + 1.25f * de;
                        }
                    }
                    // Slight preference for zero motion to suppress camera sensor noise
                    sad += 0.002f * static_cast<float>(dx * dx + dy * dy);
                    if (sad < bestSad) {
                        bestSad = sad;
                        bestDx = dx;
                        bestDy = dy;
                    }
                }
            }

            // Fill 2x2 block
            for (int fy = 0; fy < step && (y + fy) < h; ++fy) {
                for (int fx = 0; fx < step && (x + fx) < w; ++fx) {
                    int idx = (y + fy) * w + (x + fx);
                    flow.vx[idx] = static_cast<float>(bestDx);
                    flow.vy[idx] = static_cast<float>(bestDy);
                }
            }
        }
    }

    return flow;
}

void EbSynthEngine::synthesizeFrame(
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
) {
    const int levels = std::max(1, std::min(5, config.pyramidLevels));
    std::vector<int> pyrW(levels), pyrH(levels);
    pyrW[0] = width;
    pyrH[0] = height;
    for (int l = 1; l < levels; ++l) {
        pyrW[l] = std::max(16, pyrW[l - 1] / 2);
        pyrH[l] = std::max(16, pyrH[l - 1] / 2);
    }

    std::vector<std::vector<uint32_t>> stylePyr(levels);
    std::vector<std::vector<uint32_t>> srcPyr(levels);
    std::vector<std::vector<uint32_t>> tgtPyr(levels);
    std::vector<std::vector<uint32_t>> warpedTemporalPyr(levels);

    stylePyr[0].assign(styleKeyframeArgb, styleKeyframeArgb + width * height);
    srcPyr[0].assign(sourceKeyframeArgb, sourceKeyframeArgb + width * height);
    tgtPyr[0].assign(targetFrameArgb, targetFrameArgb + width * height);

    bool hasTemporal = (prevTargetFrameArgb != nullptr && prevSynthesizedArgb != nullptr);
    if (hasTemporal) {
        GuidingChannels prevG = extractGuidingChannels(prevTargetFrameArgb, width, height);
        GuidingChannels currG = extractGuidingChannels(targetFrameArgb, width, height);
        FlowField flow = computeDenseOpticalFlow(prevG, currG, 4, 8);
        warpedTemporalPyr[0].resize(static_cast<size_t>(width * height));
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int idx = y * width + x;
                int srcX = clampCoord(static_cast<int>(std::round(x - flow.vx[idx])), 0, width - 1);
                int srcY = clampCoord(static_cast<int>(std::round(y - flow.vy[idx])), 0, height - 1);
                warpedTemporalPyr[0][idx] = prevSynthesizedArgb[srcY * width + srcX];
            }
        }
    }

    for (int l = 1; l < levels; ++l) {
        stylePyr[l] = downsampleArgb(stylePyr[l - 1].data(), pyrW[l - 1], pyrH[l - 1], pyrW[l], pyrH[l]);
        srcPyr[l] = downsampleArgb(srcPyr[l - 1].data(), pyrW[l - 1], pyrH[l - 1], pyrW[l], pyrH[l]);
        tgtPyr[l] = downsampleArgb(tgtPyr[l - 1].data(), pyrW[l - 1], pyrH[l - 1], pyrW[l], pyrH[l]);
        if (hasTemporal) {
            warpedTemporalPyr[l] = downsampleArgb(
                warpedTemporalPyr[l - 1].data(), pyrW[l - 1], pyrH[l - 1], pyrW[l], pyrH[l]
            );
        }
    }

    std::vector<NnfOffset> nnf;

    // Coarse-to-Fine PatchMatch NNF Pyramid Loop
    for (int l = levels - 1; l >= 0; --l) {
        int lw = pyrW[l];
        int lh = pyrH[l];
        int halfPatch = std::max(1, config.patchSize / 2);

        GuidingChannels srcGuide = extractGuidingChannels(srcPyr[l].data(), lw, lh);
        GuidingChannels tgtGuide = extractGuidingChannels(tgtPyr[l].data(), lw, lh);
        const uint32_t* tempPtr = hasTemporal ? warpedTemporalPyr[l].data() : nullptr;
        const uint32_t* stylePtr = stylePyr[l].data();

        std::vector<NnfOffset> currNnf(static_cast<size_t>(lw * lh));
        FastRng rng(0x13579BDFu + static_cast<uint32_t>(l * 7919));

        if (l == levels - 1) {
            // Coarsest level: Identity initialization + random jitter
            for (int y = 0; y < lh; ++y) {
                for (int x = 0; x < lw; ++x) {
                    int idx = y * lw + x;
                    int sx = x;
                    int sy = y;
                    float err = computePatchDistance(
                        srcGuide, tgtGuide, tempPtr, stylePtr,
                        sx, sy, x, y, halfPatch, config, 1e9f
                    );
                    currNnf[idx] = {static_cast<int16_t>(sx), static_cast<int16_t>(sy), err};
                }
            }
        } else {
            // Upsample NNF from coarser level (l + 1)
            int pw = pyrW[l + 1];
            int ph = pyrH[l + 1];
            for (int y = 0; y < lh; ++y) {
                int py = std::min(ph - 1, (y * ph) / lh);
                for (int x = 0; x < lw; ++x) {
                    int px = std::min(pw - 1, (x * pw) / lw);
                    const NnfOffset& coarse = nnf[py * pw + px];
                    int sx = clampCoord(static_cast<int>(coarse.sx) * lw / pw + (x % 2), 0, lw - 1);
                    int sy = clampCoord(static_cast<int>(coarse.sy) * lh / ph + (y % 2), 0, lh - 1);
                    float err = computePatchDistance(
                        srcGuide, tgtGuide, tempPtr, stylePtr,
                        sx, sy, x, y, halfPatch, config, 1e9f
                    );
                    // Also check identity mapping as candidate
                    float idErr = computePatchDistance(
                        srcGuide, tgtGuide, tempPtr, stylePtr,
                        x, y, x, y, halfPatch, config, err
                    );
                    if (idErr < err) {
                        sx = x;
                        sy = y;
                        err = idErr;
                    }
                    currNnf[y * lw + x] = {static_cast<int16_t>(sx), static_cast<int16_t>(sy), err};
                }
            }
        }

        // PatchMatch Propagation + Random Search Passes
        const int iters = std::max(2, config.searchIterations);
        for (int iter = 0; iter < iters; ++iter) {
            bool reverseScan = (iter % 2 == 1);
            int yStart = reverseScan ? (lh - 1) : 0;
            int yEnd = reverseScan ? -1 : lh;
            int yStep = reverseScan ? -1 : 1;

            int xStart = reverseScan ? (lw - 1) : 0;
            int xEnd = reverseScan ? -1 : lw;
            int xStep = reverseScan ? -1 : 1;

            for (int y = yStart; y != yEnd; y += yStep) {
                for (int x = xStart; x != xEnd; x += xStep) {
                    int idx = y * lw + x;
                    NnfOffset best = currNnf[idx];

                    // 1. Spatial Propagation from horizontal neighbor (x - xStep)
                    int nx = x - xStep;
                    if (nx >= 0 && nx < lw) {
                        const NnfOffset& nb = currNnf[y * lw + nx];
                        int candX = clampCoord(static_cast<int>(nb.sx) + xStep, 0, lw - 1);
                        int candY = clampCoord(static_cast<int>(nb.sy), 0, lh - 1);
                        if (candX != best.sx || candY != best.sy) {
                            float err = computePatchDistance(
                                srcGuide, tgtGuide, tempPtr, stylePtr,
                                candX, candY, x, y, halfPatch, config, best.error
                            );
                            if (err < best.error) {
                                best = {static_cast<int16_t>(candX), static_cast<int16_t>(candY), err};
                            }
                        }
                    }

                    // 2. Spatial Propagation from vertical neighbor (y - yStep)
                    int ny = y - yStep;
                    if (ny >= 0 && ny < lh) {
                        const NnfOffset& nb = currNnf[ny * lw + x];
                        int candX = clampCoord(static_cast<int>(nb.sx), 0, lw - 1);
                        int candY = clampCoord(static_cast<int>(nb.sy) + yStep, 0, lh - 1);
                        if (candX != best.sx || candY != best.sy) {
                            float err = computePatchDistance(
                                srcGuide, tgtGuide, tempPtr, stylePtr,
                                candX, candY, x, y, halfPatch, config, best.error
                            );
                            if (err < best.error) {
                                best = {static_cast<int16_t>(candX), static_cast<int16_t>(candY), err};
                            }
                        }
                    }

                    // 3. Exponential Random Search around current best offset
                    int radius = std::max(lw, lh) / 4;
                    while (radius >= 1) {
                        int candX = clampCoord(
                            static_cast<int>(best.sx) + rng.nextRange(-radius, radius),
                            0, lw - 1
                        );
                        int candY = clampCoord(
                            static_cast<int>(best.sy) + rng.nextRange(-radius, radius),
                            0, lh - 1
                        );
                        float err = computePatchDistance(
                            srcGuide, tgtGuide, tempPtr, stylePtr,
                            candX, candY, x, y, halfPatch, config, best.error
                        );
                        if (err < best.error) {
                            best = {static_cast<int16_t>(candX), static_cast<int16_t>(candY), err};
                        }
                        radius /= 2;
                    }

                    currNnf[idx] = best;
                }
            }
        }

        nnf = std::move(currNnf);
    }

    // Finest Level: Omega-Weighted Patch Voting Synthesis
    const int voteHalf = std::max(1, config.patchSize / 2);
    const uint32_t* finestStyle = stylePyr[0].data();
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sumR = 0.0f;
            float sumG = 0.0f;
            float sumB = 0.0f;
            float sumW = 0.0f;

            for (int dy = -voteHalf; dy <= voteHalf; ++dy) {
                int ny = clampCoord(y + dy, 0, height - 1);
                for (int dx = -voteHalf; dx <= voteHalf; ++dx) {
                    int nx = clampCoord(x + dx, 0, width - 1);
                    const NnfOffset& off = nnf[ny * width + nx];
                    int sx = clampCoord(static_cast<int>(off.sx) - dx, 0, width - 1);
                    int sy = clampCoord(static_cast<int>(off.sy) - dy, 0, height - 1);

                    float w = 1.0f / (1.0f + off.error * 8.0f);
                    int r, g, b;
                    unpackArgb(finestStyle[sy * width + sx], r, g, b);
                    sumR += static_cast<float>(r) * w;
                    sumG += static_cast<float>(g) * w;
                    sumB += static_cast<float>(b) * w;
                    sumW += w;
                }
            }

            int idx = y * width + x;
            int outR = static_cast<int>(sumR / std::max(1e-5f, sumW));
            int outG = static_cast<int>(sumG / std::max(1e-5f, sumW));
            int outB = static_cast<int>(sumB / std::max(1e-5f, sumW));
            outSynthesizedArgb[idx] = packArgb(255, outR, outG, outB);

            if (outErrorMap != nullptr) {
                outErrorMap[idx] = nnf[idx].error;
            }
        }
    }
}

void EbSynthEngine::blendBiDirectional(
    const uint32_t* forwardSynthArgb,
    const float* forwardErrorMap,
    const uint32_t* backwardSynthArgb,
    const float* backwardErrorMap,
    uint32_t* outBlendedArgb,
    int width,
    int height,
    float temporalAlpha
) {
    const float clampedT = std::max(0.0f, std::min(1.0f, temporalAlpha));
    // Smoothstep S-curve for flicker-free temporal transition
    const float smoothT = clampedT * clampedT * (3.0f - 2.0f * clampedT);
    const size_t total = static_cast<size_t>(width * height);

    for (size_t i = 0; i < total; ++i) {
        int fr, fg, fb, br, bg, bb;
        unpackArgb(forwardSynthArgb[i], fr, fg, fb);
        unpackArgb(backwardSynthArgb[i], br, bg, bb);

        float fErr = (forwardErrorMap != nullptr) ? std::max(0.001f, forwardErrorMap[i]) : 1.0f;
        float bErr = (backwardErrorMap != nullptr) ? std::max(0.001f, backwardErrorMap[i]) : 1.0f;

        // Error-modulated temporal weight: favor the keyframe with higher NNF confidence
        float wForward = (1.0f - smoothT) / (0.05f + fErr);
        float wBackward = smoothT / (0.05f + bErr);
        float wSum = std::max(1e-5f, wForward + wBackward);
        float alpha = wBackward / wSum;

        int r = static_cast<int>(fr * (1.0f - alpha) + br * alpha + 0.5f);
        int g = static_cast<int>(fg * (1.0f - alpha) + bg * alpha + 0.5f);
        int b = static_cast<int>(fb * (1.0f - alpha) + bb * alpha + 0.5f);
        outBlendedArgb[i] = packArgb(255, r, g, b);
    }
}

} // namespace ebsynth
