package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.SpeedVerificationStatus
import com.example.model.WebVideoCandidate
import com.example.web.DikshaWebController
import java.util.Locale

@Composable
fun DikshaWebPlayerScreen() {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { DikshaWebController(coroutineScope) }
    val state by controller.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var urlInput by remember { mutableStateOf(state.url) }
    var isExpandedHUD by remember { mutableStateOf(true) }
    var showDiagnosticsDrawer by remember { mutableStateOf(false) }

    val speedPresets = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 7.5f, 10.0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        // TOP APP BAR: URL navigation and quick portal presets
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { controller.goBack() },
                        enabled = state.canGoBack,
                        modifier = Modifier.size(36.dp).testTag("web_btn_back")
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (state.canGoBack) Color.White else Color(0xFF475569)
                        )
                    }

                    IconButton(
                        onClick = { controller.goForward() },
                        enabled = state.canGoForward,
                        modifier = Modifier.size(36.dp).testTag("web_btn_forward")
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (state.canGoForward) Color.White else Color(0xFF475569)
                        )
                    }

                    IconButton(
                        onClick = { controller.reload() },
                        modifier = Modifier.size(36.dp).testTag("web_btn_reload")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp)
                            .testTag("web_url_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                controller.loadUrl(urlInput)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        trailingIcon = {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            controller.loadUrl(urlInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .padding(start = 2.dp)
                            .testTag("web_btn_go")
                    ) {
                        Text("GO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { controller.toggleDesktopMode() },
                        modifier = Modifier.size(36.dp).testTag("web_btn_desktop")
                    ) {
                        Icon(
                            Icons.Default.DesktopMac,
                            contentDescription = "Desktop Mode",
                            tint = if (state.isDesktopMode) Color(0xFF22C55E) else Color(0xFF94A3B8)
                        )
                    }
                }

                // Quick Portal Presets Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Presets:", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)

                    PortalPresetChip(
                        label = "DIKSHA Learning",
                        targetUrl = "https://learning.diksha.gov.in/",
                        isSelected = state.url.contains("learning.diksha.gov.in"),
                        onClick = {
                            urlInput = "https://learning.diksha.gov.in/"
                            controller.loadUrl(urlInput)
                        }
                    )

                    PortalPresetChip(
                        label = "DIKSHA Home",
                        targetUrl = "https://diksha.gov.in/",
                        isSelected = state.url == "https://diksha.gov.in/",
                        onClick = {
                            urlInput = "https://diksha.gov.in/"
                            controller.loadUrl(urlInput)
                        }
                    )

                    PortalPresetChip(
                        label = "HTML5 Video Test",
                        targetUrl = "https://www.w3schools.com/html/html5_video.asp",
                        isSelected = state.url.contains("w3schools"),
                        onClick = {
                            urlInput = "https://www.w3schools.com/html/html5_video.asp"
                            controller.loadUrl(urlInput)
                        }
                    )

                    PortalPresetChip(
                        label = "Direct MP4 Test",
                        targetUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        isSelected = state.url.contains("BigBuckBunny"),
                        onClick = {
                            urlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                            controller.loadUrl(urlInput)
                        }
                    )
                }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(2.dp),
                        color = Color(0xFF6366F1),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        // MAIN CONTENT AREA: WebView + Floating Speed HUD Controller
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        controller.attachWebView(this)
                        controller.loadUrl(state.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // FLOATING SPEED BOOSTER HUD DOCKED AT BOTTOM
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Bar with Real-time Verification Badge & Collapse Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.speedStatus) {
                                            SpeedVerificationStatus.ACTIVE -> Color(0xFF22C55E)
                                            SpeedVerificationStatus.SETTING -> Color(0xFF38BDF8)
                                            SpeedVerificationStatus.FAILED -> Color(0xFFEF4444)
                                            SpeedVerificationStatus.NO_VIDEO -> Color(0xFFF59E0B)
                                            SpeedVerificationStatus.IDLE -> Color(0xFF94A3B8)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HTML5 Speed Controller (Tampermonkey Engine)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Diagnostics toggle button
                            IconButton(
                                onClick = { showDiagnosticsDrawer = !showDiagnosticsDrawer },
                                modifier = Modifier.size(30.dp).testTag("web_btn_toggle_diagnostics")
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Inspector",
                                    tint = if (showDiagnosticsDrawer) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Collapse / Expand HUD
                            IconButton(
                                onClick = { isExpandedHUD = !isExpandedHUD },
                                modifier = Modifier.size(30.dp).testTag("web_btn_toggle_hud")
                            ) {
                                Icon(
                                    if (isExpandedHUD) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle HUD",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // PRIMARY VERIFICATION BANNER
                    VerificationStatusBanner(state = state)

                    AnimatedVisibility(visible = isExpandedHUD) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            // SPEED PRESET BUTTONS (1x, 1.25x, 1.5x, 1.75x, 2x, 2.5x, 3x, 4x, 5x, 7.5x, 10x)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                speedPresets.forEach { speed ->
                                    val isSelected = state.verifiedSpeed?.let { Math.abs(it - speed) < 0.05f } ?: (Math.abs(state.requestedSpeed - speed) < 0.05f)
                                    val isPending = state.speedStatus == SpeedVerificationStatus.SETTING && Math.abs(state.requestedSpeed - speed) < 0.05f

                                    SpeedPresetPill(
                                        speed = speed,
                                        isSelected = isSelected,
                                        isPending = isPending,
                                        onClick = { controller.setPlaybackSpeed(speed) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // FINE-TUNE STEPPER & QUICK MEDIA ACTIONS
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Stepper: -0.25x / +0.25x
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val next = ((state.requestedSpeed - 0.25f) * 100).toInt() / 100f
                                            if (next >= 0.25f) controller.setPlaybackSpeed(next)
                                        },
                                        modifier = Modifier.size(28.dp).testTag("web_btn_speed_minus")
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-0.25x", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", state.requestedSpeed)}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA7F3D0),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val next = ((state.requestedSpeed + 0.25f) * 100).toInt() / 100f
                                            if (next <= 16.0f) controller.setPlaybackSpeed(next)
                                        },
                                        modifier = Modifier.size(28.dp).testTag("web_btn_speed_plus")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+0.25x", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Quick Video Controls
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { controller.seekBy(-10f) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .testTag("web_btn_rewind")
                                    ) {
                                        Icon(Icons.Default.FastRewind, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { controller.togglePlayPause() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4F46E5))
                                            .testTag("web_btn_play_pause")
                                    ) {
                                        Icon(
                                            if (state.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { controller.seekBy(10f) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .testTag("web_btn_forward_10s")
                                    ) {
                                        Icon(Icons.Default.FastForward, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    Button(
                                        onClick = { controller.queryVideoStatus() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("web_btn_reverify")
                                    ) {
                                        Text("Re-Verify", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // COLLAPSIBLE DIAGNOSTICS & DOM INSPECTOR DRAWER
                    AnimatedVisibility(visible = showDiagnosticsDrawer) {
                        WebDiagnosticsDrawer(state = state, onClearLogs = { controller.clearLogs() })
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationStatusBanner(state: com.example.model.WebPlayerState) {
    val bannerBg = when (state.speedStatus) {
        SpeedVerificationStatus.ACTIVE -> Color(0xFF064E3B)
        SpeedVerificationStatus.SETTING -> Color(0xFF0C4A6E)
        SpeedVerificationStatus.FAILED -> Color(0xFF7F1D1D)
        SpeedVerificationStatus.NO_VIDEO -> Color(0xFF78350F)
        SpeedVerificationStatus.IDLE -> Color(0xFF1E293B)
    }

    val bannerBorder = when (state.speedStatus) {
        SpeedVerificationStatus.ACTIVE -> Color(0xFF10B981)
        SpeedVerificationStatus.SETTING -> Color(0xFF38BDF8)
        SpeedVerificationStatus.FAILED -> Color(0xFFEF4444)
        SpeedVerificationStatus.NO_VIDEO -> Color(0xFFF59E0B)
        SpeedVerificationStatus.IDLE -> Color(0xFF334155)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, bannerBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (state.speedStatus) {
                            SpeedVerificationStatus.ACTIVE -> Icons.Default.CheckCircle
                            SpeedVerificationStatus.SETTING -> Icons.Default.Speed
                            SpeedVerificationStatus.FAILED -> Icons.Default.Error
                            SpeedVerificationStatus.NO_VIDEO -> Icons.Default.Warning
                            SpeedVerificationStatus.IDLE -> Icons.Default.Info
                        },
                        contentDescription = "Status",
                        tint = when (state.speedStatus) {
                            SpeedVerificationStatus.ACTIVE -> Color(0xFF34D399)
                            SpeedVerificationStatus.SETTING -> Color(0xFF38BDF8)
                            SpeedVerificationStatus.FAILED -> Color(0xFFF87171)
                            SpeedVerificationStatus.NO_VIDEO -> Color(0xFFFBBF24)
                            SpeedVerificationStatus.IDLE -> Color(0xFF94A3B8)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (state.speedStatus) {
                            SpeedVerificationStatus.ACTIVE -> "STATUS: ACTIVE (VERIFIED)"
                            SpeedVerificationStatus.SETTING -> "STATUS: INJECTING SPEED..."
                            SpeedVerificationStatus.FAILED -> "STATUS: FAILED"
                            SpeedVerificationStatus.NO_VIDEO -> "STATUS: NO HTML5 VIDEO DETECTED"
                            SpeedVerificationStatus.IDLE -> "STATUS: READY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "${state.videoCount} video(s) found",
                    fontSize = 10.sp,
                    color = Color(0xFFE2E8F0)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Requested: ${state.requestedSpeed}x",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF93C5FD)
                )

                Text(
                    text = "Actual video.playbackRate: ${state.verifiedSpeed?.let { String.format(Locale.US, "%.2fx", it) } ?: "None"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.speedStatus == SpeedVerificationStatus.ACTIVE) Color(0xFF34D399) else Color(0xFFFCA5A5)
                )
            }

            if (state.iframeWarning != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.iframeWarning,
                    fontSize = 9.sp,
                    color = Color(0xFFFDE68A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpeedPresetPill(
    speed: Float,
    isSelected: Boolean,
    isPending: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> Color(0xFF10B981)
        isPending -> Color(0xFF0284C7)
        else -> Color(0xFF1E293B)
    }

    val textColor = when {
        isSelected || isPending -> Color.White
        else -> Color(0xFFE2E8F0)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                1.dp,
                if (isSelected) Color(0xFF34D399) else Color(0xFF334155),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("web_preset_${speed}x")
    ) {
        Text(
            text = if (speed % 1.0f == 0f) "${speed.toInt()}x" else "${speed}x",
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun PortalPresetChip(
    label: String,
    targetUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E293B))
            .border(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFF334155), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun WebDiagnosticsDrawer(
    state: com.example.model.WebPlayerState,
    onClearLogs: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DOM Inspector & Bridge Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Text(
                    "Clear",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (state.candidates.isNotEmpty()) {
                    item {
                        Text("Detected <video> Elements (${state.candidates.size}):", fontSize = 10.sp, color = Color(0xFFA7F3D0), fontWeight = FontWeight.SemiBold)
                    }
                    items(state.candidates) { candidate ->
                        Text(
                            text = "#${candidate.index} [${candidate.source}] rate=${candidate.playbackRate}x, playing=${candidate.isPlaying}, time=${String.format(Locale.US, "%.1f", candidate.currentTime)}s/${String.format(Locale.US, "%.1f", candidate.duration)}s",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                item {
                    Text("Execution & Verification Logs:", fontSize = 10.sp, color = Color(0xFFFDE68A), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }

                if (state.logs.isEmpty()) {
                    item {
                        Text("No logs yet.", fontSize = 9.sp, color = Color(0xFF64748B))
                    }
                } else {
                    items(state.logs) { log ->
                        Text(
                            text = log,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                log.contains("VERIFIED") -> Color(0xFF34D399)
                                log.contains("FAILED") -> Color(0xFFF87171)
                                log.contains("IFRAME") -> Color(0xFFFBBF24)
                                else -> Color(0xFF94A3B8)
                            }
                        )
                    }
                }
            }
        }
    }
}
