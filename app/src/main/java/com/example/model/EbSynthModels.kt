package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class AnimeStylePreset(
    val id: String,
    val titleAr: String,
    val subtitleAr: String
) {
    ANIME_CEL_SHADE(
        id = "anime_cel",
        titleAr = "أنمي سينمائي (Cel-Shaded)",
        subtitleAr = "تظليل أنمي متعدد الطبقات مع خطوط حبر داكنة واضحة"
    ),
    STUDIO_WATERCOLOR(
        id = "studio_watercolor",
        titleAr = "ألوان مائية فنية (Ghibli Style)",
        subtitleAr = "ضربات فرشاة ناعمة وتدرجات لونية دافئة مع حواف مرسومة"
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        titleAr = "سايبربانك نيون (Neon Ink)",
        subtitleAr = "تباين عالٍ مع خطوط متوهجة بالسيان والماجنتا"
    ),
    MANGA_HALFTONE(
        id = "manga_halftone",
        titleAr = "مانجا يابانية (Manga Line-Art)",
        subtitleAr = "تحبير أبيض وأسود عالي الدقة مع تظليل نقطي"
    )
}

data class SynthParameters(
    val patchSize: Int = 5,
    val pyramidLevels: Int = 4,
    val searchIterations: Int = 4,
    val edgeWeight: Float = 2.5f,
    val luminanceWeight: Float = 1.6f,
    val colorWeight: Float = 1.1f,
    val flowWeight: Float = 2.2f,
    val uniformityWeight: Float = 0.85f,
    val biDirectionalBlending: Boolean = true,
    val useGpuCompute: Boolean = true,
    val processingResolutionHeight: Int = 360
)

data class KeyframeAnchor(
    val id: String,
    val frameIndex: Int,
    val timestampUs: Long,
    val sourceBitmap: Bitmap,
    val styledBitmap: Bitmap,
    val styleLabel: String,
    val edgePreviewBitmap: Bitmap? = null,
    val luminancePreviewBitmap: Bitmap? = null
)

data class VideoSourceMetadata(
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val durationMs: Long,
    val frameRate: Float,
    val totalFramesEstimate: Int,
    val hasAudioTrack: Boolean,
    val mimeType: String = "video/mp4"
) {
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    val aspectRatioFloat: Float
        get() = if (displayHeight > 0) displayWidth.toFloat() / displayHeight.toFloat() else 16f / 9f
}

enum class SynthesisStage(val labelAr: String) {
    QUEUED("في الانتظار في الطابور"),
    DECODING_AND_STRIDE_ALIGNMENT("فك التشفير ومحاذاة Stride والأبعاد"),
    OPTICAL_FLOW_AND_GUIDES("حساب التدفق البصري وقنوات التوجيه"),
    PATCHMATCH_NNF_SYNTHESIS("تطابق الرقع الذكي (PatchMatch NNF)"),
    BIDIRECTIONAL_BLENDING("الدمج ثنائي الاتجاه (Zero-Flicker Blend)"),
    HARDWARE_H264_ENCODING("الترميز العتادي وضبط التوقيت الزمني (PTS)"),
    AUDIO_PASSTHRU_MUXING("نسخ مسار الصوت الأصلي (Audio Passthru)"),
    MEDIASTORE_EXPORT("التصدير إلى Movies/EbSynth_Studio"),
    COMPLETED("مكتمل وجاهز للعرض"),
    FAILED("فشلت المعالجة")
}

data class SynthesisTask(
    val id: String,
    val title: String,
    val sourceMetadata: VideoSourceMetadata,
    val keyframesCount: Int,
    val biDirectionalActive: Boolean,
    val parameters: SynthParameters,
    val stage: SynthesisStage = SynthesisStage.QUEUED,
    val progress: Float = 0f,
    val processedFrames: Int = 0,
    val totalFrames: Int = 0,
    val currentFps: Float = 0f,
    val etaSeconds: Long = 0L,
    val memoryUsedMb: Long = 0L,
    val nativeHeapMb: Long = 0L,
    val gpuAccelerated: Boolean = true,
    val outputPublicUri: Uri? = null,
    val outputRelativePath: String = "Movies/EbSynth_Studio",
    val outputFileName: String = "",
    val sourcePreviewBitmap: Bitmap? = null,
    val latestSynthesizedBitmap: Bitmap? = null,
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
)

data class GuidingChannelVisuals(
    val sourceBitmap: Bitmap,
    val styledBitmap: Bitmap,
    val edgeMapBitmap: Bitmap,
    val luminanceMapBitmap: Bitmap,
    val opticalFlowMapBitmap: Bitmap,
    val nnfConfidenceBitmap: Bitmap
)
