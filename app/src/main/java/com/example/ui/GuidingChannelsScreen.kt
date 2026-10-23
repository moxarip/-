package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SynthParameters
import com.example.ui.theme.AnimeMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FlowViolet
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.viewmodel.EbSynthUiState
import java.util.Locale

@Composable
fun GuidingChannelsScreen(
    uiState: EbSynthUiState,
    onUpdateParameters: ((SynthParameters) -> SynthParameters) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = uiState.guidingVisuals
    val params = uiState.synthParameters

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("guiding_channels_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Multi-Layer Guiding Channels Visual Grid
        if (visuals != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Guiding Channels",
                                tint = CyberCyan
                            )
                            Column {
                                Text(
                                    text = "قنوات التوجيه متعددة الطبقات (Multi-Layer Guiding Channels)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "معاينة حية لقنوات التوجيه المستخرجة من الإطار المفتاحي النشط",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChannelPreviewTile(
                                bitmap = visuals.opticalFlowMapBitmap,
                                title = "التدفق البصري (Optical Flow)",
                                subtitle = "تتبع الحركة ثنائية الأبعاد (Vx, Vy)",
                                badgeColor = FlowViolet,
                                modifier = Modifier.weight(1f)
                            )
                            ChannelPreviewTile(
                                bitmap = visuals.edgeMapBitmap,
                                title = "كشف الحواف (Scharr Edge)",
                                subtitle = "منع سيلان الألوان وحفظ الملامح",
                                badgeColor = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChannelPreviewTile(
                                bitmap = visuals.luminanceMapBitmap,
                                title = "السطوع والظلال (Luminance)",
                                subtitle = "اتساق الإضاءة مع المشهد الأصلي",
                                badgeColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            ChannelPreviewTile(
                                bitmap = visuals.nnfConfidenceBitmap,
                                title = "تطابق الرقع (PatchMatch NNF)",
                                subtitle = "خريطة ثقة الرقع المتطابقة",
                                badgeColor = AnimeMagenta,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Guiding Channel Weight Sliders
        item {
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Weights",
                            tint = AnimeMagenta
                        )
                        Text(
                            text = "أوزان قنوات التوجيه (Guiding Channel Weights)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    WeightControlSlider(
                        label = "وزن قناة كشف الحواف (Edge Preservation)",
                        value = params.edgeWeight,
                        valueRange = 0.5f..5.0f,
                        accent = CyberCyan,
                        testTag = "edge_weight_slider",
                        onValueChange = { v -> onUpdateParameters { it.copy(edgeWeight = v) } }
                    )

                    WeightControlSlider(
                        label = "وزن قناة التدفق البصري (Optical Flow Weight)",
                        value = params.flowWeight,
                        valueRange = 0.0f..5.0f,
                        accent = FlowViolet,
                        testTag = "flow_weight_slider",
                        onValueChange = { v -> onUpdateParameters { it.copy(flowWeight = v) } }
                    )

                    WeightControlSlider(
                        label = "وزن قناة السطوع والظلال (Luminance Guide)",
                        value = params.luminanceWeight,
                        valueRange = 0.5f..4.0f,
                        accent = EmeraldSuccess,
                        testTag = "luminance_weight_slider",
                        onValueChange = { v -> onUpdateParameters { it.copy(luminanceWeight = v) } }
                    )

                    WeightControlSlider(
                        label = "وزن تجانس ضربات الفرشاة (Uniformity Coherence)",
                        value = params.uniformityWeight,
                        valueRange = 0.1f..2.5f,
                        accent = AnimeMagenta,
                        testTag = "uniformity_weight_slider",
                        onValueChange = { v -> onUpdateParameters { it.copy(uniformityWeight = v) } }
                    )
                }
            }
        }

        // 3. PatchMatch NNF Pyramid & GPU Hardware Acceleration Settings
        item {
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
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "PatchMatch NNF Config",
                            tint = CyberCyan
                        )
                        Text(
                            text = "إعدادات خوارزمية PatchMatch NNF والتسريع العتادي",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "حجم رقعة البحث (Patch Size):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(3, 5, 7).forEach { sizeOption ->
                            val selected = params.patchSize == sizeOption
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateParameters { it.copy(patchSize = sizeOption) }
                                },
                                label = {
                                    Text(
                                        text = "${sizeOption}x${sizeOption} px",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = Color(0xFF001F26)
                                ),
                                modifier = Modifier.testTag("patch_size_chip_$sizeOption")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "مستويات الهرم متعدد المقاييس (Pyramid Levels: ${params.pyramidLevels}) • تكرارات البحث (${params.searchIterations}):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(2, 3, 4).forEach { lvl ->
                            FilterChip(
                                selected = params.pyramidLevels == lvl,
                                onClick = {
                                    onUpdateParameters { it.copy(pyramidLevels = lvl) }
                                },
                                label = { Text("$lvl مستويات هرمية") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AnimeMagenta,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioCardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تسريع معالج الرسوميات (OpenGL ES 3.1 Compute / SIMD)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = uiState.gpuRendererLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberCyan
                                )
                            }
                            Switch(
                                checked = params.useGpuCompute,
                                onCheckedChange = { enabled ->
                                    onUpdateParameters { it.copy(useGpuCompute = enabled) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("gpu_compute_switch")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPreviewTile(
    bitmap: Bitmap,
    title: String,
    subtitle: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = StudioCardElevated,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightControlSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = StudioCardElevated
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
