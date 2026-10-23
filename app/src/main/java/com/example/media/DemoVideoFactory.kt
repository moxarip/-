package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates a real hardware-encoded H.264 + AAC Audio MP4 clip on-device so users
 * can test Keyframe Extraction, Guiding Channels, Bi-directional Blending, Audio Passthru,
 * and MediaStore Export with a single tap even if no camera videos are on the device.
 */
object DemoVideoFactory {

    private const val TIMEOUT_US = 10_000L

    fun createCinematicSampleMp4(
        context: Context,
        width: Int = 384,
        height: Int = 216,
        fps: Int = 16,
        totalFrames: Int = 32
    ): File {
        val outFile = File(context.cacheDir, "EbSynth_Studio_Sample_${System.currentTimeMillis()}.mp4")
        val encW = width and 0xFFFE
        val encH = height and 0xFFFE

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoEncoder.start()

        val sampleRate = 44100
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(0)

        // Pre-encode video packets and audio packets so we can start muxer cleanly once both formats arrive
        class EncodedPacket(val data: ByteArray, val ptsUs: Long, val flags: Int)

        val videoPackets = mutableListOf<EncodedPacket>()
        var outputVideoFormat: MediaFormat? = null
        val vInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / fps

        var vInIdx = 0
        var vInputDone = false
        var vOutputDone = false

        while (!vOutputDone) {
            if (!vInputDone) {
                val inIdx = videoEncoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    if (vInIdx >= totalFrames) {
                        videoEncoder.queueInputBuffer(
                            inIdx, 0, 0, vInIdx * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        vInputDone = true
                    } else {
                        val ptsUs = vInIdx * frameDurationUs
                        val bmp = renderProceduralSceneFrame(encW, encH, vInIdx, totalFrames)
                        val argb = IntArray(encW * encH)
                        bmp.getPixels(argb, 0, encW, 0, 0, encW, encH)

                        val inImg = videoEncoder.getInputImage(inIdx)
                        if (inImg != null) {
                            try {
                                ProactiveVideoPipeline.fillYuv420ImageFromArgbAligned(inImg, argb, encW, encH)
                            } finally {
                                inImg.close()
                            }
                            videoEncoder.queueInputBuffer(inIdx, 0, (encW * encH * 3) / 2, ptsUs, 0)
                        } else {
                            val inBuf = videoEncoder.getInputBuffer(inIdx)
                            val size = if (inBuf != null) {
                                ProactiveVideoPipeline.fillYuv420BufferFromArgb(inBuf, argb, encW, encH)
                            } else 0
                            videoEncoder.queueInputBuffer(inIdx, 0, size, ptsUs, 0)
                        }
                        vInIdx++
                    }
                }
            }

            val outIdx = videoEncoder.dequeueOutputBuffer(vInfo, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputVideoFormat = videoEncoder.outputFormat
                }
                outIdx >= 0 -> {
                    val outBuf = videoEncoder.getOutputBuffer(outIdx)
                    if ((vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        vInfo.size = 0
                    }
                    if (vInfo.size > 0 && outBuf != null) {
                        outBuf.position(vInfo.offset)
                        outBuf.limit(vInfo.offset + vInfo.size)
                        val bytes = ByteArray(vInfo.size)
                        outBuf.get(bytes)
                        videoPackets.add(EncodedPacket(bytes, vInfo.presentationTimeUs, vInfo.flags))
                    }
                    if ((vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        vOutputDone = true
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false)
                }
            }
        }

        // Encode synthesized ambient chord audio matching exact video duration
        val totalDurationUs = totalFrames * frameDurationUs
        val totalAudioSamples = ((totalDurationUs * sampleRate) / 1_000_000L).toInt()
        val audioPackets = mutableListOf<EncodedPacket>()
        var outputAudioFormat: MediaFormat? = null
        val aInfo = MediaCodec.BufferInfo()
        var samplesSubmitted = 0
        val chunkSamples = 1024
        var aInputDone = false
        var aOutputDone = false

        while (!aOutputDone) {
            if (!aInputDone) {
                val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)
                    if (samplesSubmitted >= totalAudioSamples || inBuf == null) {
                        val ptsUs = (samplesSubmitted * 1_000_000L) / sampleRate
                        audioEncoder.queueInputBuffer(
                            inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        aInputDone = true
                    } else {
                        inBuf.clear()
                        val count = minOf(chunkSamples, totalAudioSamples - samplesSubmitted)
                        val pcmBytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (s in 0 until count) {
                            val idx = samplesSubmitted + s
                            val t = idx.toDouble() / sampleRate.toDouble()
                            val wave = (sin(2.0 * Math.PI * 220.0 * t) * 0.35 +
                                    sin(2.0 * Math.PI * 329.63 * t) * 0.25 +
                                    sin(2.0 * Math.PI * 440.0 * t) * 0.20)
                            val sampleShort = (wave * 10000.0).toInt().coerceIn(-32767, 32767).toShort()
                            pcmBytes.putShort(sampleShort)
                        }
                        pcmBytes.flip()
                        inBuf.put(pcmBytes)
                        val ptsUs = (samplesSubmitted * 1_000_000L) / sampleRate
                        audioEncoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                        samplesSubmitted += count
                    }
                }
            }

            val outIdx = audioEncoder.dequeueOutputBuffer(aInfo, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputAudioFormat = audioEncoder.outputFormat
                }
                outIdx >= 0 -> {
                    val outBuf = audioEncoder.getOutputBuffer(outIdx)
                    if ((aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        aInfo.size = 0
                    }
                    if (aInfo.size > 0 && outBuf != null) {
                        outBuf.position(aInfo.offset)
                        outBuf.limit(aInfo.offset + aInfo.size)
                        val bytes = ByteArray(aInfo.size)
                        outBuf.get(bytes)
                        audioPackets.add(EncodedPacket(bytes, aInfo.presentationTimeUs, aInfo.flags))
                    }
                    if ((aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        aOutputDone = true
                    }
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                }
            }
        }

        try {
            videoEncoder.stop()
            videoEncoder.release()
        } catch (_: Throwable) {
        }
        try {
            audioEncoder.stop()
            audioEncoder.release()
        } catch (_: Throwable) {
        }

        val vTrack = muxer.addTrack(outputVideoFormat ?: videoFormat)
        val aTrack = if (outputAudioFormat != null && audioPackets.isNotEmpty()) {
            muxer.addTrack(outputAudioFormat)
        } else -1

        muxer.start()
        val muxInfo = MediaCodec.BufferInfo()
        for (pkt in videoPackets) {
            val buf = ByteBuffer.wrap(pkt.data)
            muxInfo.set(0, pkt.data.size, pkt.ptsUs, pkt.flags)
            muxer.writeSampleData(vTrack, buf, muxInfo)
        }
        if (aTrack >= 0) {
            for (pkt in audioPackets) {
                val buf = ByteBuffer.wrap(pkt.data)
                muxInfo.set(0, pkt.data.size, pkt.ptsUs, pkt.flags)
                muxer.writeSampleData(aTrack, buf, muxInfo)
            }
        }
        muxer.stop()
        muxer.release()
        return outFile
    }

    fun renderProceduralSceneFrame(
        width: Int,
        height: Int,
        frameIndex: Int,
        totalFrames: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val t = frameIndex.toFloat() / maxOf(1, totalFrames - 1).toFloat()

        // Twilight sky gradient
        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.rgb(20, 26, 62),
                    Color.rgb(52, 36, 94),
                    Color.rgb(138, 64, 112),
                    Color.rgb(225, 118, 96)
                ),
                floatArrayOf(0f, 0.42f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Glowing sun/moon translating across the sky
        val sunX = width * (0.28f + 0.44f * t)
        val sunY = height * (0.34f - 0.08f * sin(t * Math.PI.toFloat()))
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 224, 150)
        }
        canvas.drawCircle(sunX, sunY, height * 0.14f, sunPaint)

        // Parallax mountain range
        val mountainPath = Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, height * 0.65f)
            lineTo(width * 0.22f, height * 0.46f)
            lineTo(width * 0.48f, height * 0.62f)
            lineTo(width * 0.74f, height * 0.42f)
            lineTo(width.toFloat(), height * 0.58f)
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 30, 64)
        }
        canvas.drawPath(mountainPath, mountainPaint)

        // Foreground moving anime hero portrait silhouette with flowing scarf & hair contours
        val charCenterX = width * (0.35f + 0.30f * t)
        val charCenterY = height * 0.62f
        val headRadius = height * 0.15f

        // Flowing cape / scarf
        val scarfWave = sin(t * Math.PI.toFloat() * 2f) * (height * 0.05f)
        val scarfPath = Path().apply {
            moveTo(charCenterX, charCenterY + headRadius * 0.7f)
            quadTo(
                charCenterX - width * 0.16f,
                charCenterY + headRadius * 0.9f + scarfWave,
                charCenterX - width * 0.26f,
                charCenterY + headRadius * 1.4f - scarfWave
            )
            lineTo(charCenterX - width * 0.22f, charCenterY + headRadius * 1.9f)
            quadTo(
                charCenterX - width * 0.10f,
                charCenterY + headRadius * 1.5f,
                charCenterX + headRadius * 0.4f,
                charCenterY + headRadius * 1.2f
            )
            close()
        }
        val scarfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 58, 112)
        }
        canvas.drawPath(scarfPath, scarfPaint)

        // Shoulders & jacket
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 168, 224)
        }
        canvas.drawRoundRect(
            charCenterX - headRadius * 1.15f,
            charCenterY + headRadius * 0.75f,
            charCenterX + headRadius * 1.15f,
            height.toFloat(),
            18f, 18f,
            bodyPaint
        )

        // Face & skin tone
        val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 218, 195)
        }
        canvas.drawCircle(charCenterX, charCenterY, headRadius, skinPaint)

        // Anime eyes for clear Edge Detection & Luminance guide features
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 34, 68)
        }
        canvas.drawCircle(charCenterX - headRadius * 0.35f, charCenterY, headRadius * 0.16f, eyePaint)
        canvas.drawCircle(charCenterX + headRadius * 0.35f, charCenterY, headRadius * 0.16f, eyePaint)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        canvas.drawCircle(
            charCenterX - headRadius * 0.30f,
            charCenterY - headRadius * 0.05f,
            headRadius * 0.06f,
            highlightPaint
        )
        canvas.drawCircle(
            charCenterX + headRadius * 0.40f,
            charCenterY - headRadius * 0.05f,
            headRadius * 0.06f,
            highlightPaint
        )

        // Spiky hair crest
        val hairPath = Path().apply {
            moveTo(charCenterX - headRadius * 1.1f, charCenterY - headRadius * 0.2f)
            lineTo(charCenterX - headRadius * 0.7f, charCenterY - headRadius * 1.35f)
            lineTo(charCenterX - headRadius * 0.15f, charCenterY - headRadius * 0.9f)
            lineTo(charCenterX + headRadius * 0.25f, charCenterY - headRadius * 1.45f)
            lineTo(charCenterX + headRadius * 0.65f, charCenterY - headRadius * 0.85f)
            lineTo(charCenterX + headRadius * 1.15f, charCenterY - headRadius * 1.1f)
            lineTo(charCenterX + headRadius * 1.05f, charCenterY - headRadius * 0.1f)
            close()
        }
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 38, 86)
        }
        canvas.drawPath(hairPath, hairPaint)

        return bmp
    }
}
