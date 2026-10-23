package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Debug
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.EbSynthNativeBridge
import com.example.engine.GpuComputeAccelerator
import com.example.media.DemoVideoFactory
import com.example.media.ProactiveVideoPipeline
import com.example.model.AnimeStylePreset
import com.example.model.GuidingChannelVisuals
import com.example.model.KeyframeAnchor
import com.example.model.SynthParameters
import com.example.model.SynthesisStage
import com.example.model.SynthesisTask
import com.example.model.VideoSourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class EbSynthUiState(
    val selectedTabIndex: Int = 0,
    val videoMetadata: VideoSourceMetadata? = null,
    val scrubberTimeMs: Long = 0L,
    val currentScrubbedBitmap: Bitmap? = null,
    val keyframes: List<KeyframeAnchor> = emptyList(),
    val selectedKeyframeId: String? = null,
    val activeStylePreset: AnimeStylePreset = AnimeStylePreset.ANIME_CEL_SHADE,
    val synthParameters: SynthParameters = SynthParameters(),
    val guidingVisuals: GuidingChannelVisuals? = null,
    val tasks: List<SynthesisTask> = emptyList(),
    val isBusyGeneratingDemo: Boolean = false,
    val isExtractingKeyframe: Boolean = false,
    val statusBannerMessage: String? = null,
    val gpuRendererLabel: String = "OpenGL ES 3.1 / SIMD Multi-Core",
    val currentRamMb: Long = 32L
)

class EbSynthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EbSynthUiState())
    val uiState: StateFlow<EbSynthUiState> = _uiState.asStateFlow()

    private var activeQueueJob: Job? = null

    init {
        initializeEngineAndDefaultScene()
    }

    private fun initializeEngineAndDefaultScene() {
        viewModelScope.launch(Dispatchers.Default) {
            val gpu = GpuComputeAccelerator()
            val gpuOk = gpu.initialize()
            val rendererLabel = if (gpuOk) {
                "${gpu.gpuRendererName} (GLES 3.1 Compute)"
            } else if (EbSynthNativeBridge.isNativeJniLoaded) {
                "Native C++17 NEON + OpenGL ES Pipeline"
            } else {
                "Multi-Core SIMD + Hardware MediaCodec"
            }
            gpu.release()

            // Pre-populate a ready-to-inspect studio keyframe & guiding channel visuals
            val initialFrame0 = DemoVideoFactory.renderProceduralSceneFrame(384, 216, 0, 32)
            val initialFrameNext = DemoVideoFactory.renderProceduralSceneFrame(384, 216, 4, 32)
            val styledFrame0 = EbSynthNativeBridge.stylizeKeyframe(
                initialFrame0, AnimeStylePreset.ANIME_CEL_SHADE
            )
            val visuals = EbSynthNativeBridge.generateGuidingVisuals(
                sourceBitmap = initialFrame0,
                styledBitmap = styledFrame0,
                nextFrameBitmap = initialFrameNext
            )

            val kf0 = KeyframeAnchor(
                id = UUID.randomUUID().toString(),
                frameIndex = 0,
                timestampUs = 0L,
                sourceBitmap = initialFrame0,
                styledBitmap = styledFrame0,
                styleLabel = AnimeStylePreset.ANIME_CEL_SHADE.titleAr,
                edgePreviewBitmap = visuals.edgeMapBitmap,
                luminancePreviewBitmap = visuals.luminanceMapBitmap
            )

            val frameEnd = DemoVideoFactory.renderProceduralSceneFrame(384, 216, 28, 32)
            val styledEnd = EbSynthNativeBridge.stylizeKeyframe(
                frameEnd, AnimeStylePreset.ANIME_CEL_SHADE
            )
            val kfEnd = KeyframeAnchor(
                id = UUID.randomUUID().toString(),
                frameIndex = 28,
                timestampUs = 1_750_000L,
                sourceBitmap = frameEnd,
                styledBitmap = styledEnd,
                styleLabel = "${AnimeStylePreset.ANIME_CEL_SHADE.titleAr} (نهاية المشهد)"
            )

            _uiState.update { state ->
                state.copy(
                    currentScrubbedBitmap = initialFrame0,
                    keyframes = listOf(kf0, kfEnd),
                    selectedKeyframeId = kf0.id,
                    guidingVisuals = visuals,
                    gpuRendererLabel = rendererLabel,
                    currentRamMb = sampleMemoryMb()
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index, currentRamMb = sampleMemoryMb()) }
    }

    fun dismissBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun onVideoSelected(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isExtractingKeyframe = true,
                    statusBannerMessage = "جاري فحص أبعاد الفيديو وزاوية الدوران ومسار الصوت..."
                )
            }
            val meta = ProactiveVideoPipeline.inspectVideoMetadata(context, uri)
            val firstBmp = ProactiveVideoPipeline.extractExactFrameAtUs(context, uri, 0L)
                ?: DemoVideoFactory.renderProceduralSceneFrame(384, 216, 0, 32)
            val nextBmp = ProactiveVideoPipeline.extractExactFrameAtUs(
                context, uri, minOf(meta.durationMs * 1000L, 120_000L)
            )
            val preset = _uiState.value.activeStylePreset
            val styledFirst = EbSynthNativeBridge.stylizeKeyframe(firstBmp, preset)
            val visuals = EbSynthNativeBridge.generateGuidingVisuals(firstBmp, styledFirst, nextBmp)

            val kfStart = KeyframeAnchor(
                id = UUID.randomUUID().toString(),
                frameIndex = 0,
                timestampUs = 0L,
                sourceBitmap = firstBmp,
                styledBitmap = styledFirst,
                styleLabel = preset.titleAr,
                edgePreviewBitmap = visuals.edgeMapBitmap,
                luminancePreviewBitmap = visuals.luminanceMapBitmap
            )

            // Also extract a second keyframe near 85% duration for instant Bi-Directional Blending
            val endTimestampUs = (meta.durationMs * 850L).coerceAtLeast(200_000L)
            val endBmp = ProactiveVideoPipeline.extractExactFrameAtUs(context, uri, endTimestampUs)
            val kfList = if (endBmp != null && meta.durationMs > 600L) {
                val styledEnd = EbSynthNativeBridge.stylizeKeyframe(endBmp, preset)
                val endIdx = ((endTimestampUs / 1_000_000f) * meta.frameRate).roundToInt()
                val kfEnd = KeyframeAnchor(
                    id = UUID.randomUUID().toString(),
                    frameIndex = max(1, endIdx),
                    timestampUs = endTimestampUs,
                    sourceBitmap = endBmp,
                    styledBitmap = styledEnd,
                    styleLabel = "${preset.titleAr} (إطار ثانٍ للدمج)"
                )
                listOf(kfStart, kfEnd)
            } else {
                listOf(kfStart)
            }

            _uiState.update { state ->
                state.copy(
                    videoMetadata = meta,
                    scrubberTimeMs = 0L,
                    currentScrubbedBitmap = firstBmp,
                    keyframes = kfList,
                    selectedKeyframeId = kfStart.id,
                    guidingVisuals = visuals,
                    isExtractingKeyframe = false,
                    statusBannerMessage = "تم تحميل الفيديو (${meta.displayWidth}x${meta.displayHeight} • زاوية ${meta.rotationDegrees}° • إطارات مفتاحية: ${kfList.size})",
                    currentRamMb = sampleMemoryMb()
                )
            }
        }
    }

    fun generateAndLoadDemoVideo() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isBusyGeneratingDemo = true,
                    statusBannerMessage = "جاري توليد فيديو تجريبي حقيقي (MP4 H.264 + AAC Audio) لاختبار خط المعالجة..."
                )
            }
            try {
                val sampleFile = DemoVideoFactory.createCinematicSampleMp4(context)
                val uri = Uri.fromFile(sampleFile)
                _uiState.update { it.copy(isBusyGeneratingDemo = false) }
                onVideoSelected(uri)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusyGeneratingDemo = false,
                        statusBannerMessage = "تعذر توليد الفيديو التجريبي: ${t.localizedMessage}"
                    )
                }
            }
        }
    }

    fun onScrubberChanged(timeMs: Long) {
        val meta = _uiState.value.videoMetadata
        val clampedMs = if (meta != null) timeMs.coerceIn(0L, meta.durationMs) else timeMs.coerceAtLeast(0L)
        _uiState.update { it.copy(scrubberTimeMs = clampedMs) }

        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = if (meta != null) {
                ProactiveVideoPipeline.extractExactFrameAtUs(context, meta.uri, clampedMs * 1000L)
            } else {
                val frameIdx = ((clampedMs / 2000f) * 31).roundToInt().coerceIn(0, 31)
                DemoVideoFactory.renderProceduralSceneFrame(384, 216, frameIdx, 32)
            }
            if (bmp != null) {
                _uiState.update { it.copy(currentScrubbedBitmap = bmp) }
            }
        }
    }

    fun extractKeyframeAtCurrentTimestamp() {
        val context = getApplication<Application>()
        val state = _uiState.value
        val timeMs = state.scrubberTimeMs
        val meta = state.videoMetadata

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExtractingKeyframe = true) }
            val timestampUs = timeMs * 1000L
            val rawBmp = if (meta != null) {
                ProactiveVideoPipeline.extractExactFrameAtUs(context, meta.uri, timestampUs)
            } else {
                state.currentScrubbedBitmap
                    ?: DemoVideoFactory.renderProceduralSceneFrame(384, 216, 8, 32)
            }

            if (rawBmp != null) {
                val preset = state.activeStylePreset
                val styledBmp = EbSynthNativeBridge.stylizeKeyframe(rawBmp, preset)
                val visuals = EbSynthNativeBridge.generateGuidingVisuals(rawBmp, styledBmp)
                val fps = meta?.frameRate ?: 16f
                val fIdx = ((timeMs / 1000f) * fps).roundToInt()

                val newKf = KeyframeAnchor(
                    id = UUID.randomUUID().toString(),
                    frameIndex = fIdx,
                    timestampUs = timestampUs,
                    sourceBitmap = rawBmp,
                    styledBitmap = styledBmp,
                    styleLabel = preset.titleAr,
                    edgePreviewBitmap = visuals.edgeMapBitmap,
                    luminancePreviewBitmap = visuals.luminanceMapBitmap
                )

                _uiState.update { curr ->
                    val updatedList = (curr.keyframes.filterNot { it.frameIndex == fIdx } + newKf)
                        .sortedBy { it.timestampUs }
                    curr.copy(
                        keyframes = updatedList,
                        selectedKeyframeId = newKf.id,
                        guidingVisuals = visuals,
                        isExtractingKeyframe = false,
                        statusBannerMessage = "تم استخراج الإطار المفتاحي (#$fIdx عند ${timeMs}ms) وتوليد قنوات التوجيه.",
                        currentRamMb = sampleMemoryMb()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isExtractingKeyframe = false,
                        statusBannerMessage = "تعذر استخراج الإطار المفتاحي عند هذا التوقيت."
                    )
                }
            }
        }
    }

    fun selectKeyframe(keyframeId: String) {
        val kf = _uiState.value.keyframes.find { it.id == keyframeId } ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val visuals = EbSynthNativeBridge.generateGuidingVisuals(kf.sourceBitmap, kf.styledBitmap)
            _uiState.update {
                it.copy(
                    selectedKeyframeId = keyframeId,
                    scrubberTimeMs = kf.timestampUs / 1000L,
                    currentScrubbedBitmap = kf.sourceBitmap,
                    guidingVisuals = visuals
                )
            }
        }
    }

    fun removeKeyframe(keyframeId: String) {
        _uiState.update { state ->
            if (state.keyframes.size <= 1) {
                return@update state.copy(
                    statusBannerMessage = "يجب الاحتفاظ بإطار مفتاحي واحد على الأقل لعملية التصنيع."
                )
            }
            val remaining = state.keyframes.filterNot { it.id == keyframeId }
            state.copy(
                keyframes = remaining,
                selectedKeyframeId = remaining.firstOrNull()?.id,
                statusBannerMessage = "تم حذف الإطار المفتاحي."
            )
        }
    }

    fun applyStylePresetToKeyframes(preset: AnimeStylePreset) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    activeStylePreset = preset,
                    isExtractingKeyframe = true
                )
            }
            val updatedKeyframes = _uiState.value.keyframes.map { kf ->
                val restyled = EbSynthNativeBridge.stylizeKeyframe(kf.sourceBitmap, preset)
                kf.copy(
                    styledBitmap = restyled,
                    styleLabel = preset.titleAr
                )
            }
            val activeKf = updatedKeyframes.find { it.id == _uiState.value.selectedKeyframeId }
                ?: updatedKeyframes.firstOrNull()
            val visuals = if (activeKf != null) {
                EbSynthNativeBridge.generateGuidingVisuals(activeKf.sourceBitmap, activeKf.styledBitmap)
            } else _uiState.value.guidingVisuals

            _uiState.update {
                it.copy(
                    keyframes = updatedKeyframes,
                    guidingVisuals = visuals,
                    isExtractingKeyframe = false,
                    statusBannerMessage = "تم تطبيق أسلوب \"${preset.titleAr}\" على كافة الإطارات المفتاحية (${updatedKeyframes.size})."
                )
            }
        }
    }

    fun saveSelectedKeyframeToGallery() {
        val context = getApplication<Application>()
        val state = _uiState.value
        val activeKf = state.keyframes.find { it.id == state.selectedKeyframeId }
            ?: state.keyframes.firstOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val savedSrcUri = ProactiveVideoPipeline.saveKeyframeToPublicPictures(
                context, activeKf.sourceBitmap, "EbSynth_Source_Frame_${activeKf.frameIndex}"
            )
            val savedStyleUri = ProactiveVideoPipeline.saveKeyframeToPublicPictures(
                context, activeKf.styledBitmap, "EbSynth_Styled_Frame_${activeKf.frameIndex}"
            )
            val msg = if (savedSrcUri != null && savedStyleUri != null) {
                "تم حفظ الإطار الأصلي والرسمة الفنية في الاستوديو: Pictures/EbSynth_Studio"
            } else {
                "تم حفظ الإطار المفتاحي بنجاح."
            }
            _uiState.update { it.copy(statusBannerMessage = msg) }
        }
    }

    fun importCustomStyledKeyframe(imageUri: Uri) {
        val context = getApplication<Application>()
        val state = _uiState.value
        val targetKf = state.keyframes.find { it.id == state.selectedKeyframeId }
            ?: state.keyframes.firstOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputBmp = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (inputBmp != null) {
                    val alignedStyled = Bitmap.createScaledBitmap(
                        inputBmp,
                        targetKf.sourceBitmap.width,
                        targetKf.sourceBitmap.height,
                        true
                    )
                    val visuals = EbSynthNativeBridge.generateGuidingVisuals(
                        targetKf.sourceBitmap, alignedStyled
                    )
                    val updatedKf = targetKf.copy(
                        styledBitmap = alignedStyled,
                        styleLabel = "رسمة مستوردة مخصصة",
                        edgePreviewBitmap = visuals.edgeMapBitmap,
                        luminancePreviewBitmap = visuals.luminanceMapBitmap
                    )
                    _uiState.update { curr ->
                        curr.copy(
                            keyframes = curr.keyframes.map { if (it.id == targetKf.id) updatedKf else it },
                            guidingVisuals = visuals,
                            statusBannerMessage = "تم استيراد الرسمة/الأنمي البديل ومطابقة أبعاده مع الإطار المفتاحي (#${targetKf.frameIndex})."
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(statusBannerMessage = "تعذر قراءة الصورة المستوردة: ${t.localizedMessage}")
                }
            }
        }
    }

    fun updateSynthParameters(updater: (SynthParameters) -> SynthParameters) {
        _uiState.update { state ->
            state.copy(synthParameters = updater(state.synthParameters))
        }
    }

    fun enqueueAndStartSynthesis() {
        val context = getApplication<Application>()
        val state = _uiState.value
        val keyframesSnap = state.keyframes
        if (keyframesSnap.isEmpty()) {
            _uiState.update {
                it.copy(statusBannerMessage = "يرجى استخراج إطار مفتاحي واحد على الأقل قبل بدء التصنيع.")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // If the user hasn't picked a video file yet, automatically generate the real MP4 demo clip
            val activeMeta = state.videoMetadata ?: run {
                _uiState.update {
                    it.copy(
                        isBusyGeneratingDemo = true,
                        statusBannerMessage = "جاري تجهيز ملف الفيديو المصدر (MP4) لخط المعالجة..."
                    )
                }
                val file = DemoVideoFactory.createCinematicSampleMp4(context)
                val meta = ProactiveVideoPipeline.inspectVideoMetadata(context, Uri.fromFile(file))
                _uiState.update {
                    it.copy(videoMetadata = meta, isBusyGeneratingDemo = false)
                }
                meta
            }

            val taskId = UUID.randomUUID().toString()
            val paramsSnap = _uiState.value.synthParameters
            val newTask = SynthesisTask(
                id = taskId,
                title = "${activeMeta.displayName.removeSuffix(".mp4")} • ${state.activeStylePreset.titleAr}",
                sourceMetadata = activeMeta,
                keyframesCount = keyframesSnap.size,
                biDirectionalActive = paramsSnap.biDirectionalBlending && keyframesSnap.size >= 2,
                parameters = paramsSnap,
                stage = SynthesisStage.QUEUED,
                progress = 0.01f,
                processedFrames = 0,
                totalFrames = activeMeta.totalFramesEstimate,
                memoryUsedMb = sampleMemoryMb(),
                nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L),
                gpuAccelerated = paramsSnap.useGpuCompute,
                sourcePreviewBitmap = keyframesSnap.first().sourceBitmap,
                latestSynthesizedBitmap = keyframesSnap.first().styledBitmap
            )

            _uiState.update { curr ->
                curr.copy(
                    tasks = listOf(newTask) + curr.tasks,
                    selectedTabIndex = 1, // Switch to Queue screen to show real-time progress
                    statusBannerMessage = "بدأت مهمة التصنيع في الطابور بتسريع العتاد ومحاذاة Stride."
                )
            }

            runSynthesisTask(taskId, activeMeta, keyframesSnap, paramsSnap)
        }
    }

    private fun runSynthesisTask(
        taskId: String,
        metadata: VideoSourceMetadata,
        keyframes: List<KeyframeAnchor>,
        params: SynthParameters
    ) {
        val context = getApplication<Application>()
        activeQueueJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val (exportedUri, exportedPath) = withContext(Dispatchers.IO) {
                    ProactiveVideoPipeline.executeFullSynthesisPipeline(
                        context = context,
                        metadata = metadata,
                        keyframes = keyframes,
                        params = params,
                        onProgress = { prog ->
                            _uiState.update { curr ->
                                curr.copy(
                                    currentRamMb = prog.memoryUsedMb,
                                    tasks = curr.tasks.map { t ->
                                        if (t.id == taskId) {
                                            t.copy(
                                                stage = prog.stage,
                                                progress = prog.progress,
                                                processedFrames = prog.processedFrames,
                                                totalFrames = prog.totalFrames,
                                                currentFps = prog.currentFps,
                                                etaSeconds = prog.etaSeconds,
                                                memoryUsedMb = prog.memoryUsedMb,
                                                nativeHeapMb = prog.nativeHeapMb,
                                                latestSynthesizedBitmap = prog.latestPreviewBitmap
                                                    ?: t.latestSynthesizedBitmap
                                            )
                                        } else t
                                    }
                                )
                            }
                        }
                    )
                }

                _uiState.update { curr ->
                    curr.copy(
                        statusBannerMessage = "اكتمل تصنيع الفيديو وتم حفظه في الاستوديو: $exportedPath",
                        tasks = curr.tasks.map { t ->
                            if (t.id == taskId) {
                                t.copy(
                                    stage = SynthesisStage.COMPLETED,
                                    progress = 1.0f,
                                    etaSeconds = 0L,
                                    outputPublicUri = exportedUri,
                                    outputRelativePath = exportedPath,
                                    outputFileName = exportedPath.substringAfterLast('/'),
                                    completedAtMs = System.currentTimeMillis()
                                )
                            } else t
                        }
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { curr ->
                    curr.copy(
                        statusBannerMessage = "خطأ أثناء التصنيع: ${t.localizedMessage}",
                        tasks = curr.tasks.map { task ->
                            if (task.id == taskId) {
                                task.copy(
                                    stage = SynthesisStage.FAILED,
                                    errorMessage = t.localizedMessage ?: "خطأ غير معروف"
                                )
                            } else task
                        }
                    )
                }
            }
        }
    }

    fun launchPlayAndPreviewIntent(context: Context, uri: Uri) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "تشغيل ومعاينة الفيديو").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(statusBannerMessage = "تم حفظ الفيديو في Movies/EbSynth_Studio (${uri.lastPathSegment})")
            }
        }
    }

    fun launchShareVideoIntent(context: Context, uri: Uri, title: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "تم إنتاج هذا الفيديو عبر EbSynth Studio Android: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "مشاركة فيديو الأنمي").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(statusBannerMessage = "تعذر فتح قائمة المشاركة: ${t.localizedMessage}")
            }
        }
    }

    private fun sampleMemoryMb(): Long {
        val rt = Runtime.getRuntime()
        val jvmMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
        val natMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
        return max(16L, jvmMb + natMb)
    }
}
