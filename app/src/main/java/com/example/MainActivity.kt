package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GuidingChannelsScreen
import com.example.ui.QueueScreen
import com.example.ui.StudioScreen
import com.example.ui.theme.AnimeMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EbSynthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EbSynthStudioRootApp()
                }
            }
        }
    }
}

@Composable
fun EbSynthStudioRootApp(
    viewModel: EbSynthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Back handler on non-home tabs returns to Studio tab
    BackHandler(enabled = uiState.selectedTabIndex != 0) {
        viewModel.selectTab(0)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    val keyframePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomStyledKeyframe(uri)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = StudioSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTabIndex == 0) {
                                Icons.Filled.AutoFixHigh
                            } else {
                                Icons.Outlined.AutoFixHigh
                            },
                            contentDescription = "الاستوديو"
                        )
                    },
                    label = { Text("الاستوديو") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001F26),
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_studio")
                )

                NavigationBarItem(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.tasks.isNotEmpty()) {
                                    Badge(
                                        containerColor = AnimeMagenta,
                                        contentColor = Color.White
                                    ) {
                                        Text("${uiState.tasks.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.selectedTabIndex == 1) {
                                    Icons.Filled.QueuePlayNext
                                } else {
                                    Icons.Outlined.QueuePlayNext
                                },
                                contentDescription = "طابور المهام"
                            )
                        }
                    },
                    label = { Text("طابور المهام") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001F26),
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_queue")
                )

                NavigationBarItem(
                    selected = uiState.selectedTabIndex == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTabIndex == 2) {
                                Icons.Filled.Layers
                            } else {
                                Icons.Outlined.Layers
                            },
                            contentDescription = "محرك القنوات"
                        )
                    },
                    label = { Text("محرك القنوات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001F26),
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_channels")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dismissible Status Notification Banner
            AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                uiState.statusBannerMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("status_notification_banner"),
                        shape = RoundedCornerShape(12.dp),
                        color = StudioCardElevated,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.dismissBannerMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "إغلاق التنبيه",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            when (uiState.selectedTabIndex) {
                0 -> StudioScreen(
                    uiState = uiState,
                    onPickVideoClick = { videoPickerLauncher.launch("video/*") },
                    onGenerateDemoVideoClick = { viewModel.generateAndLoadDemoVideo() },
                    onScrubberChange = { viewModel.onScrubberChanged(it) },
                    onExtractKeyframeClick = { viewModel.extractKeyframeAtCurrentTimestamp() },
                    onSaveKeyframeToGalleryClick = { viewModel.saveSelectedKeyframeToGallery() },
                    onImportCustomKeyframeClick = {
                        keyframePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSelectKeyframe = { viewModel.selectKeyframe(it) },
                    onRemoveKeyframe = { viewModel.removeKeyframe(it) },
                    onSelectStylePreset = { viewModel.applyStylePresetToKeyframes(it) },
                    onToggleBiDirectional = { enabled ->
                        viewModel.updateSynthParameters { it.copy(biDirectionalBlending = enabled) }
                    },
                    onStartSynthesisClick = { viewModel.enqueueAndStartSynthesis() },
                    modifier = Modifier.weight(1f)
                )

                1 -> QueueScreen(
                    uiState = uiState,
                    onStartNewSynthesisClick = { viewModel.enqueueAndStartSynthesis() },
                    onPlayVideoClick = { ctx, uri -> viewModel.launchPlayAndPreviewIntent(ctx, uri) },
                    onShareVideoClick = { ctx, uri, title ->
                        viewModel.launchShareVideoIntent(ctx, uri, title)
                    },
                    modifier = Modifier.weight(1f)
                )

                else -> GuidingChannelsScreen(
                    uiState = uiState,
                    onUpdateParameters = { updater -> viewModel.updateSynthParameters(updater) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
