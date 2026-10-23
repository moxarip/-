package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SynthesisStage
import com.example.model.SynthesisTask
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AnimeMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FlowViolet
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.viewmodel.EbSynthUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun QueueScreen(
    uiState: EbSynthUiState,
    onStartNewSynthesisClick: () -> Unit,
    onPlayVideoClick: (android.content.Context, android.net.Uri) -> Unit,
    onShareVideoClick: (android.content.Context, android.net.Uri, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time Engine & Memory Telemetry Banner
        item {
            QueueTelemetryHeaderCard(uiState = uiState)
        }

        if (uiState.tasks.isEmpty()) {
            item {
                EmptyQueueStateCard(onStartNewSynthesisClick = onStartNewSynthesisClick)
            }
        } else {
            items(uiState.tasks, key = { it.id }) { task ->
                SynthesisTaskCard(
                    task = task,
                    onPlayClick = { uri -> onPlayVideoClick(context, uri) },
                    onShareClick = { uri -> onShareVideoClick(context, uri, task.title) }
                )
            }
        }
    }
}

@Composable
private fun QueueTelemetryHeaderCard(uiState: EbSynthUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "طابور مهام التصنيع (Synthesis Queue)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = uiState.gpuRendererLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioCardElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = "Scoped Storage Folder",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Movies/EbSynth_Studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueStateCard(
    onStartNewSynthesisClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Empty Queue",
                tint = CyberCyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "لا توجد مهام نشطة في الطابور حالياً",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ابدأ مهمة تصنيع جديدة لتتبع التقدم اللحظي ومعدل الإطارات (FPS) واستهلاك الذاكرة وتصدير النتيجة إلى الاستوديو.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartNewSynthesisClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_queue_start_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF001F26)
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تشغيل مهمة تصنيع فورية الآن",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SynthesisTaskCard(
    task: SynthesisTask,
    onPlayClick: (android.net.Uri) -> Unit,
    onShareClick: (android.net.Uri) -> Unit
) {
    val isCompleted = task.stage == SynthesisStage.COMPLETED
    val isFailed = task.stage == SynthesisStage.FAILED
    val borderColor = when {
        isCompleted -> EmeraldSuccess
        isFailed -> AmberWarning
        else -> CyberCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.sourceMetadata.displayWidth}x${task.sourceMetadata.displayHeight} • زاوية ${task.sourceMetadata.rotationDegrees}° • إطارات مفتاحية: ${task.keyframesCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = borderColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (isFailed) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "${(task.progress * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Side-by-side Live Frame Preview (Source vs Synthesized Anime)
            if (task.sourcePreviewBitmap != null && task.latestSynthesizedBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = remember(task.sourcePreviewBitmap) {
                                task.sourcePreviewBitmap.asImageBitmap()
                            },
                            contentDescription = "Source Frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "المصدر الأصلي",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = remember(task.latestSynthesizedBitmap) {
                                task.latestSynthesizedBitmap.asImageBitmap()
                            },
                            contentDescription = "Synthesized Anime Frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = AnimeMagenta.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "النتيجة المصنعة (EbSynth)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Stage Label & Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.stage.labelAr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted) EmeraldSuccess else CyberCyan
                )
                Text(
                    text = "الإطار ${task.processedFrames} / ${task.totalFrames}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("task_progress_bar_${task.id}"),
                color = if (isCompleted) EmeraldSuccess else CyberCyan,
                trackColor = StudioCardElevated,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Metrics Grid: FPS, ETA, Memory Consumption
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricPill(
                    icon = Icons.Default.Speed,
                    label = "السرعة (FPS)",
                    value = String.format(Locale.US, "%.1f FPS", task.currentFps),
                    accent = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    icon = Icons.Default.Timer,
                    label = "الوقت المتبقي",
                    value = if (isCompleted) "0s (مكتمل)" else "${task.etaSeconds}s",
                    accent = FlowViolet,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    icon = Icons.Default.Memory,
                    label = "الذاكرة (RAM)",
                    value = "${task.memoryUsedMb} MB",
                    accent = AnimeMagenta,
                    modifier = Modifier.weight(1f)
                )
            }

            // Error details if failed
            if (isFailed && task.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmberWarning
                )
            }

            // Completed State: Scoped Storage Path + Play/Preview (ACTION_VIEW) & Share (ACTION_SEND)
            if (isCompleted && task.outputPublicUri != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioCardElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "تم التصدير العام إلى الاستوديو (IS_PENDING = 0):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = task.outputRelativePath,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayClick(task.outputPublicUri) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("play_preview_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSuccess,
                            contentColor = Color(0xFF002411)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play and Preview",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تشغيل ومعاينة",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onShareClick(task.outputPublicUri) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_video_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, CyberCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Video",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مشاركة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = StudioCardElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
