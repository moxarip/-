package com.example

import com.example.engine.EbSynthNativeBridge
import com.example.model.SynthParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun ebsynthEngine_guidingChannelsAndPatchMatchSynthesis_worksCorrectly() {
        val w = 32
        val h = 32
        val srcFrame = IntArray(w * h)
        val styleFrame = IntArray(w * h)
        val targetFrame = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val isBox = x in 8..20 && y in 8..20
                srcFrame[idx] = if (isBox) 0xFFE0E0E0.toInt() else 0xFF202020.toInt()
                styleFrame[idx] = if (isBox) 0xFF00E5FF.toInt() else 0xFF101426.toInt()
                val isTranslatedBox = x in 10..22 && y in 9..21
                targetFrame[idx] = if (isTranslatedBox) 0xFFE0E0E0.toInt() else 0xFF202020.toInt()
            }
        }

        val srcGuide = EbSynthNativeBridge.extractGuidingChannels(srcFrame, w, h)
        val tgtGuide = EbSynthNativeBridge.extractGuidingChannels(targetFrame, w, h)
        assertEquals(w * h, srcGuide.edgeMag.size)
        assertTrue(srcGuide.edgeMag.any { it > 0.1f })

        val flow = EbSynthNativeBridge.computeDenseOpticalFlow(srcGuide, tgtGuide)
        assertEquals(w * h, flow.vx.size)

        val params = SynthParameters(patchSize = 3, pyramidLevels = 2, searchIterations = 3)
        val synth = EbSynthNativeBridge.synthesizeFrame(
            styleKeyframeArgb = styleFrame,
            sourceKeyframeArgb = srcFrame,
            targetFrameArgb = targetFrame,
            prevTargetFrameArgb = srcFrame,
            prevSynthesizedArgb = styleFrame,
            width = w,
            height = h,
            params = params
        )
        assertEquals(w * h, synth.pixels.size)

        val blended = EbSynthNativeBridge.blendBiDirectional(
            forwardResult = synth,
            backwardResult = synth,
            temporalAlpha = 0.5f
        )
        assertEquals(w * h, blended.size)
    }
}
