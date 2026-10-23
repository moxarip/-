package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AnimeStylePreset
import com.example.model.KeyframeAnchor
import com.example.ui.theme.AnimeMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FlowViolet
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.viewmodel.EbSynthUiState
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    uiState: EbSynthUiState,
    onPickVideoClick: () -> Unit,
    onGenerateDemoVideoClick: () -> Unit,
    onScrubberChange: (Long) -> Unit,
    onExtractKeyframeClick: () -> Unit,
    onSaveKeyframeToGalleryClick: () -> Unit,
    onImportCustomKeyframeClick: () -> Unit,
    onSelectKeyframe: (String) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onSelectStylePreset: (AnimeStylePreset) -> Unit,
    onToggleBiDirectional: (Boolean) -> Unit,
    onStartSynthesisClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeKeyframe = uiState.keyframes.find { it.id == uiState.selectedKeyframeId }
        ?: uiState.keyframes.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Studio Hero & Interactive Split-Screen Keyframe Comparison
        item {
            StudioHeroSplitComparisonCard(
                uiState = uiState,
                activeKeyframe = activeKeyframe
            )
        }

        // 2. Video Source Selection & Proactive Stride/Rotation/Audio Guarantees Card
        item {
            VideoSourceControlCard(
                uiState = uiState,
                onPickVideoClick = onPickVideoClick,
                onGenerateDemoVideoClick = onGenerateDemoVideoClick
            )
        }

        // 3. Keyframe Extractor & Multi-Keyframe Timeline (Bi-directional Blending)
        item {
            KeyframeExtractorCard(
                uiState = uiState,
                activeKeyframe = activeKeyframe,
                onScrubberChange = onScrubberChange,
                onExtractKeyframeClick = onExtractKeyframeClick,
                onSaveKeyframeToGalleryClick = onSaveKeyframeToGalleryClick,
                onSelectKeyframe = onSelectKeyframe,
                onRemoveKeyframe = onRemoveKeyframe,
                onToggleBiDirectional = onToggleBiDirectional
            )
        }

        // 4. Anime Style Replacement & Custom Keyframe Artwork Import Card
        item {
            StyleTransferCard(
                uiState = uiState,
                onImportCustomKeyframeClick = onImportCustomKeyframeClick,
                onSelectStylePreset = onSelectStylePreset
            )
        }

        // 5. Launch EbSynth Synthesis Pipeline CTA
        item {
            LaunchSynthesisCard(
                uiState = uiState,
                onStartSynthesisClick = onStartSynthesisClick
            )
        }
    }
}

@Composable
private fun StudioHeroSplitComparisonCard(
    uiState: EbSynthUiState,
    activeKeyframe: KeyframeAnchor?
) {
    var splitFraction by remember { mutableFloatStateOf(0.52f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(CyberCyan.copy(alpha = 0.6f), AnimeMagenta.copy(alpha = 0.6f)))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "استوديو EbSynth لتحويل الفيديو لأنمي",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Example-Based Video Synthesis • PatchMatch NNF",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = StudioCardElevated,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Memory and GPU status",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${uiState.currentRamMb} MB",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Before / After Split-Screen Canvas
            if (activeKeyframe != null) {
                val aspect = (activeKeyframe.sourceBitmap.width.toFloat() /
                        activeKeyframe.sourceBitmap.height.coerceAtLeast(1).toFloat())
                    .coerceIn(0.75f, 2.0f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(16.dp))
                        .clipToBounds()
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val w = size.width.coerceAtLeast(1).toFloat()
                                splitFraction = (change.position.x / w).coerceIn(0.08f, 0.92f)
                            }
                        }
                        .testTag("split_comparison_preview")
                ) {
                    val srcImg = remember(activeKeyframe.sourceBitmap) {
                        activeKeyframe.sourceBitmap.asImageBitmap()
                    }
                    val styleImg = remember(activeKeyframe.styledBitmap) {
                        activeKeyframe.styledBitmap.asImageBitmap()
                    }

                    // Full styled anime image on base
                    Image(
                        bitmap = styleImg,
                        contentDescription = "Styled Anime Keyframe",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Clipped source frame on left portion + divider line
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val splitX = size.width * splitFraction
                        clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                            drawImage(
                                image = srcImg,
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    size.width.roundToInt(),
                                    size.height.roundToInt()
                                )
                            )
                        }
                        drawLine(
                            color = CyberCyan,
                            start = Offset(splitX, 0f),
                            end = Offset(splitX, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(
                            color = AnimeMagenta,
                            radius = 12.dp.toPx(),
                            center = Offset(splitX, size.height * 0.5f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(splitX, size.height * 0.5f)
                        )
                    }

                    // Overlay Badges
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "الأصلي (Source)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = AnimeMagenta.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = activeKeyframe.styleLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Drag to compare",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اسحب للمقارنة بين الإطار الأصلي ورسمة الأنمي",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSourceControlCard(
    uiState: EbSynthUiState,
    onPickVideoClick: () -> Unit,
    onGenerateDemoVideoClick: () -> Unit
) {
    val meta = uiState.videoMetadata

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Video Source",
                    tint = CyberCyan
                )
                Text(
                    text = "1. مصدر الفيديو وضمانات الأبعاد والصوت",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickVideoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("select_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF001F26)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = "Select Video",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "اختيار فيديو",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onGenerateDemoVideoClick,
                    enabled = !uiState.isBusyGeneratingDemo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_demo_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AnimeMagenta)
                ) {
                    if (uiState.isBusyGeneratingDemo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AnimeMagenta
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = "Generate Demo MP4",
                            tint = AnimeMagenta,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "توليد مشهد تجريبي",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Proactive Fixes Status Chips
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val infoLine = if (meta != null) {
                        "${meta.displayName} • ${meta.displayWidth}x${meta.displayHeight} • ${meta.frameRate.roundToInt()} FPS • ${(meta.durationMs / 1000f)}s"
                    } else {
                        "مشهد الاستوديو الافتراضي جاهز (384x216 • 16 FPS • مسار صوت AAC متزامن)"
                    }
                    Text(
                        text = infoLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GuaranteeBadge(text = "Row Stride Aligned")
                        GuaranteeBadge(
                            text = "Rotation ${meta?.rotationDegrees ?: 0}°"
                        )
                        GuaranteeBadge(text = "Strict PTS + Audio Sync")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuaranteeBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = EmeraldSuccess.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldSuccess,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun KeyframeExtractorCard(
    uiState: EbSynthUiState,
    activeKeyframe: KeyframeAnchor?,
    onScrubberChange: (Long) -> Unit,
    onExtractKeyframeClick: () -> Unit,
    onSaveKeyframeToGalleryClick: () -> Unit,
    onSelectKeyframe: (String) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onToggleBiDirectional: (Boolean) -> Unit
) {
    val maxDurationMs = (uiState.videoMetadata?.durationMs ?: 2000L).coerceAtLeast(500L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Keyframe Extractor",
                        tint = AnimeMagenta
                    )
                    Text(
                        text = "2. مستخرج الإطارات المفتاحية (Keyframe Extractor)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "التوقيت الحالي: ${uiState.scrubberTimeMs} ms من أصل $maxDurationMs ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Slider(
                value = uiState.scrubberTimeMs.toFloat().coerceIn(0f, maxDurationMs.toFloat()),
                onValueChange = { onScrubberChange(it.toLong()) },
                valueRange = 0f..maxDurationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = CyberCyan,
                    activeTrackColor = CyberCyan,
                    inactiveTrackColor = StudioCardElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keyframe_scrubber_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExtractKeyframeClick,
                    enabled = !uiState.isExtractingKeyframe,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("extract_keyframe_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnimeMagenta,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Extract Keyframe",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "استخراج إطار مفتاحي",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onSaveKeyframeToGalleryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_keyframe_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save Keyframe",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "حفظ الإطار بالمعرض",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bi-directional Blending Header & Toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Bi-directional Blending",
                            tint = FlowViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "الدمج ثنائي الاتجاه (Bi-Directional Zero-Flicker)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "دمج سلس بين ${uiState.keyframes.size} إطارات مفتاحية لمنع الوميض",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.synthParameters.biDirectionalBlending,
                        onCheckedChange = onToggleBiDirectional,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FlowViolet
                        ),
                        modifier = Modifier.testTag("bidirectional_blend_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Keyframes List
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uiState.keyframes, key = { it.id }) { kf ->
                    val isSelected = kf.id == activeKeyframe?.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) StudioCardElevated else StudioSurface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier
                            .width(156.dp)
                            .clickable { onSelectKeyframe(kf.id) }
                            .testTag("keyframe_item_${kf.frameIndex}")
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                Image(
                                    bitmap = remember(kf.styledBitmap) { kf.styledBitmap.asImageBitmap() },
                                    contentDescription = "Keyframe #${kf.frameIndex}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "إطار #${kf.frameIndex}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (uiState.keyframes.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveKeyframe(kf.id) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "حذف الإطار",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = kf.styleLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${kf.timestampUs / 1000L} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleTransferCard(
    uiState: EbSynthUiState,
    onImportCustomKeyframeClick: () -> Unit,
    onSelectStylePreset: (AnimeStylePreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Anime Style Transfer",
                    tint = CyberCyan
                )
                Text(
                    text = "3. استيراد الرسمة البديلة أو توليد أسلوب الأنمي",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onImportCustomKeyframeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_styled_keyframe_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, CyberCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Import Custom Styled Keyframe",
                    tint = CyberCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "استيراد رسمة / إطار أنمي مرسوم خارجياً من الجهاز",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "أو اختر نمط الفرشاة الفنية لتطبيقه فوراً على الإطارات المفتاحية:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimeStylePreset.entries.forEach { preset ->
                    val selected = uiState.activeStylePreset == preset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) StudioCardElevated else StudioSurface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) AnimeMagenta else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStylePreset(preset) }
                            .testTag("preset_chip_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.titleAr,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) CyberCyan else Color.White
                                )
                                Text(
                                    text = preset.subtitleAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected Preset",
                                    tint = AnimeMagenta
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchSynthesisCard(
    uiState: EbSynthUiState,
    onStartSynthesisClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
        border = BorderStroke(1.5.dp, CyberCyan)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "جاهز للتصنيع عبر محرك PatchMatch NNF + القنوات الموجهة",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "سيتم تصدير الفيديو تلقائياً بصيغة MP4 مع نسخ مسار الصوت الأصلي إلى مجلد Movies/EbSynth_Studio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStartSynthesisClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_synthesis_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF001F26)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start EbSynth Synthesis",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "بدء تصنيع الفيديو ونقل الأسلوب (EbSynth)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
