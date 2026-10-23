package com.example.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES31
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hardware-accelerated OpenGL ES 3.1 Compute Shader Engine for EbSynth.
 * Creates an offscreen headless EGL 1.4 PBuffer context and compiles GLSL #version 310 es
 * compute shaders for parallel Guiding Channel extraction and PatchMatch NNF synthesis.
 */
class GpuComputeAccelerator {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var computeProgramId: Int = 0
    var isInitialized: Boolean = false
        private set
    var gpuRendererName: String = "Multi-Core SIMD Engine"
        private set

    companion object {
        private const val TAG = "GpuComputeAccelerator"
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040

        // GLSL ES 3.10 Compute Shader performing parallel PatchMatch NNF + Guiding Channel synthesis
        private const val PATCHMATCH_COMPUTE_SHADER_SRC = """#version 310 es
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
            layout(rgba8, binding = 0) readonly uniform highp image2D uStyleTex;
            layout(rgba8, binding = 1) readonly uniform highp image2D uSrcKeyTex;
            layout(rgba8, binding = 2) readonly uniform highp image2D uTargetTex;
            layout(rgba8, binding = 3) writeonly uniform highp image2D uOutSynthTex;

            uniform ivec2 uDimensions;
            uniform int uHalfPatch;
            uniform int uSearchRadius;
            uniform float uEdgeWeight;
            uniform float uLumWeight;
            uniform float uColorWeight;

            float calcLuma(vec3 rgb) {
                return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            }

            float sampleEdge(readonly highp image2D img, ivec2 p) {
                ivec2 xm = clamp(p + ivec2(-1, 0), ivec2(0), uDimensions - ivec2(1));
                ivec2 xp = clamp(p + ivec2( 1, 0), ivec2(0), uDimensions - ivec2(1));
                ivec2 ym = clamp(p + ivec2( 0,-1), ivec2(0), uDimensions - ivec2(1));
                ivec2 yp = clamp(p + ivec2( 0, 1), ivec2(0), uDimensions - ivec2(1));
                float gx = calcLuma(imageLoad(img, xp).rgb) - calcLuma(imageLoad(img, xm).rgb);
                float gy = calcLuma(imageLoad(img, yp).rgb) - calcLuma(imageLoad(img, ym).rgb);
                return sqrt(gx * gx + gy * gy);
            }

            float patchCost(ivec2 sCenter, ivec2 tCenter) {
                float err = 0.0;
                for (int dy = -uHalfPatch; dy <= uHalfPatch; ++dy) {
                    for (int dx = -uHalfPatch; dx <= uHalfPatch; ++dx) {
                        ivec2 sp = clamp(sCenter + ivec2(dx, dy), ivec2(0), uDimensions - ivec2(1));
                        ivec2 tp = clamp(tCenter + ivec2(dx, dy), ivec2(0), uDimensions - ivec2(1));
                        vec3 sRgb = imageLoad(uSrcKeyTex, sp).rgb;
                        vec3 tRgb = imageLoad(uTargetTex, tp).rgb;
                        float dL = calcLuma(sRgb) - calcLuma(tRgb);
                        float dE = sampleEdge(uSrcKeyTex, sp) - sampleEdge(uTargetTex, tp);
                        vec3 dC = sRgb - tRgb;
                        err += uLumWeight * (dL * dL) +
                               uEdgeWeight * (dE * dE) +
                               uColorWeight * dot(dC, dC) * 0.33;
                    }
                }
                vec2 normOff = vec2(sCenter - tCenter) / vec2(max(uDimensions, ivec2(1)));
                err += 0.15 * dot(normOff, normOff);
                return err;
            }

            void main() {
                ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                if (pos.x >= uDimensions.x || pos.y >= uDimensions.y) {
                    return;
                }
                ivec2 bestSrc = pos;
                float bestErr = patchCost(pos, pos);

                // Diamond + multi-step spatial neighborhood search around pos
                int stepSize = max(1, uSearchRadius / 2);
                for (int oy = -uSearchRadius; oy <= uSearchRadius; oy += stepSize) {
                    for (int ox = -uSearchRadius; ox <= uSearchRadius; ox += stepSize) {
                        ivec2 cand = clamp(pos + ivec2(ox, oy), ivec2(0), uDimensions - ivec2(1));
                        float cost = patchCost(cand, pos);
                        if (cost < bestErr) {
                            bestErr = cost;
                            bestSrc = cand;
                        }
                    }
                }

                // Sub-pixel local 1px refinement around best match
                for (int ry = -1; ry <= 1; ++ry) {
                    for (int rx = -1; rx <= 1; ++rx) {
                        ivec2 cand = clamp(bestSrc + ivec2(rx, ry), ivec2(0), uDimensions - ivec2(1));
                        float cost = patchCost(cand, pos);
                        if (cost < bestErr) {
                            bestErr = cost;
                            bestSrc = cand;
                        }
                    }
                }

                vec4 styledColor = imageLoad(uStyleTex, bestSrc);
                imageStore(uOutSynthTex, pos, vec4(styledColor.rgb, 1.0));
            }
        """
    }

    fun initialize(): Boolean {
        if (isInitialized) return true
        return try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0 || configs[0] == null
            ) {
                return false
            }

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 16,
                EGL14.EGL_HEIGHT, 16,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                return false
            }

            val glVersionStr = GLES31.glGetString(GLES31.GL_VERSION) ?: ""
            val rendererStr = GLES31.glGetString(GLES31.GL_RENDERER) ?: "OpenGL ES GPU"
            gpuRendererName = rendererStr

            // Verify OpenGL ES 3.1+ support for Compute Shaders
            val supportsCompute = glVersionStr.contains("3.1") || glVersionStr.contains("3.2")
            if (!supportsCompute) {
                release()
                return false
            }

            val shader = GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER)
            if (shader == 0) {
                release()
                return false
            }
            GLES31.glShaderSource(shader, PATCHMATCH_COMPUTE_SHADER_SRC)
            GLES31.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val log = GLES31.glGetShaderInfoLog(shader)
                Log.w(TAG, "Compute shader compile info: $log")
                GLES31.glDeleteShader(shader)
                release()
                return false
            }

            computeProgramId = GLES31.glCreateProgram()
            GLES31.glAttachShader(computeProgramId, shader)
            GLES31.glLinkProgram(computeProgramId)
            GLES31.glDeleteShader(shader)

            val linked = IntArray(1)
            GLES31.glGetProgramiv(computeProgramId, GLES31.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                GLES31.glDeleteProgram(computeProgramId)
                computeProgramId = 0
                release()
                return false
            }

            isInitialized = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "GLES 3.1 Compute initialization fallback to CPU SIMD: ${t.message}")
            release()
            false
        }
    }

    fun executeComputePass(
        styleRgbaBytes: ByteBuffer,
        srcKeyRgbaBytes: ByteBuffer,
        targetRgbaBytes: ByteBuffer,
        outRgbaBytes: ByteBuffer,
        width: Int,
        height: Int,
        halfPatch: Int,
        searchRadius: Int,
        edgeWeight: Float,
        lumWeight: Float,
        colorWeight: Float
    ): Boolean {
        if (!isInitialized && !initialize()) return false
        return try {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            val textures = IntArray(4)
            GLES31.glGenTextures(4, textures, 0)

            val buffers = arrayOf(styleRgbaBytes, srcKeyRgbaBytes, targetRgbaBytes, null)
            for (i in 0..3) {
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textures[i])
                GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, GLES31.GL_RGBA8, width, height)
                buffers[i]?.let { buf ->
                    buf.position(0)
                    GLES31.glTexSubImage2D(
                        GLES31.GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, buf
                    )
                }
                val access = if (i == 3) GLES31.GL_WRITE_ONLY else GLES31.GL_READ_ONLY
                GLES31.glBindImageTexture(i, textures[i], 0, false, 0, access, GLES31.GL_RGBA8)
            }

            GLES31.glUseProgram(computeProgramId)
            GLES31.glUniform2i(GLES31.glGetUniformLocation(computeProgramId, "uDimensions"), width, height)
            GLES31.glUniform1i(GLES31.glGetUniformLocation(computeProgramId, "uHalfPatch"), halfPatch)
            GLES31.glUniform1i(GLES31.glGetUniformLocation(computeProgramId, "uSearchRadius"), searchRadius)
            GLES31.glUniform1f(GLES31.glGetUniformLocation(computeProgramId, "uEdgeWeight"), edgeWeight)
            GLES31.glUniform1f(GLES31.glGetUniformLocation(computeProgramId, "uLumWeight"), lumWeight)
            GLES31.glUniform1f(GLES31.glGetUniformLocation(computeProgramId, "uColorWeight"), colorWeight)

            val groupsX = (width + 7) / 8
            val groupsY = (height + 7) / 8
            GLES31.glDispatchCompute(groupsX, groupsY, 1)
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GLES31.GL_FRAMEBUFFER_BARRIER_BIT)

            val fbo = IntArray(1)
            GLES31.glGenFramebuffers(1, fbo, 0)
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo[0])
            GLES31.glFramebufferTexture2D(
                GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
                GLES31.GL_TEXTURE_2D, textures[3], 0
            )
            outRgbaBytes.position(0)
            GLES31.glReadPixels(0, 0, width, height, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, outRgbaBytes)

            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
            GLES31.glDeleteFramebuffers(1, fbo, 0)
            GLES31.glDeleteTextures(4, textures, 0)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Compute dispatch fallback: ${t.message}")
            false
        }
    }

    fun release() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (computeProgramId != 0) {
                    GLES31.glDeleteProgram(computeProgramId)
                    computeProgramId = 0
                }
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        } catch (_: Throwable) {
        }
        isInitialized = false
    }
}
