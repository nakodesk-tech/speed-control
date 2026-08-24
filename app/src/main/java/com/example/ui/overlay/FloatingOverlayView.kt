package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaActionType
import com.example.service.CompanionStateManager
import com.example.ui.theme.EduCompanionTheme
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingOverlayView(
    onClose: () -> Unit,
    onDragDelta: (Float, Float) -> Unit
) {
    val state by CompanionStateManager.uiState.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    val presetSpeeds = listOf("1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "2.5x", "3.0x", "4.0x", "5.0x", "7.5x", "10.0x")

    // Parse current speed to float for slider
    val currentSpeedFloat = remember(state.currentPlaybackSpeed) {
        state.currentPlaybackSpeed.replace("x", "").toFloatOrNull() ?: 1.0f
    }

    // Subtle pulse for active turbo speed
    val infiniteTransition = rememberInfiniteTransition(label = "overlay_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state.isTurbo10xActive) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state.isTurbo10xActive) 350 else 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    EduCompanionTheme(darkTheme = true) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            // ==========================================
            // COMPACT FLOATING SPEED CONTROLLER BAR
            // ==========================================
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.96f),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            if (state.isTurbo10xActive) listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
                            else listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    }
                    .testTag("floating_overlay_bubble")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // Speed Badge with Bolt icon (Click to toggle expand)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (state.isTurbo10xActive) Color(0xFFDC2626) else Color(0xFF1E1B4B),
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .border(
                                1.dp,
                                if (state.isTurbo10xActive) Color(0xFFFBBF24) else Color(0xFF6366F1),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("mini_speed_badge")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Speed",
                                tint = if (state.isTurbo10xActive) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = state.currentPlaybackSpeed,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Step - 0.25x Speed
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                val next = (currentSpeedFloat - 0.25f).coerceIn(1.0f, 10.0f)
                                val formatted = if (next == 10f) "10.0x" else String.format(Locale.US, "%.2fx", next).replace(".00x", ".0x")
                                CompanionStateManager.setPlaybackSpeed(formatted)
                            }
                            .testTag("mini_speed_minus_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Speed",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Quick Play/Pause Button
                    Surface(
                        shape = CircleShape,
                        color = if (state.isPlaying) Color(0xFF10B981) else Color(0xFF4F46E5),
                        modifier = Modifier
                            .size(34.dp)
                            .scale(if (state.isPlaying) pulseScale else 1f)
                            .clickable { CompanionStateManager.togglePlayPause() }
                            .testTag("mini_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Step + 0.25x Speed
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                val next = (currentSpeedFloat + 0.25f).coerceIn(1.0f, 10.0f)
                                val formatted = if (next == 10f) "10.0x" else String.format(Locale.US, "%.2fx", next).replace(".00x", ".0x")
                                CompanionStateManager.setPlaybackSpeed(formatted)
                            }
                            .testTag("mini_speed_plus_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Speed",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Direct 10x Max Speed Switch
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isTurbo10xActive) Color(0xFFDC2626) else Color(0xFF312E81),
                        modifier = Modifier
                            .clickable {
                                if (state.isTurbo10xActive) {
                                    CompanionStateManager.setTurbo10xActive(false)
                                    CompanionStateManager.setPlaybackSpeed("1.0x")
                                } else {
                                    CompanionStateManager.setPlaybackSpeed("10.0x")
                                }
                            }
                            .border(
                                1.dp,
                                if (state.isTurbo10xActive) Color(0xFFFBBF24) else Color(0xFF6366F1),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("mini_10x_speed_button")
                    ) {
                        Text(
                            text = if (state.isTurbo10xActive) "10x ON" else "⚡ 10x",
                            color = if (state.isTurbo10xActive) Color.White else Color(0xFFA5B4FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Expand / Collapse Chevron
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(26.dp).testTag("expand_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Expand",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Close Overlay Button (stops overlay service)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp).testTag("close_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Overlay",
                            tint = Color(0xFFEF4444).copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // ==========================================
            // EXPANDED SPEED & PLAYBACK CONTROLLER
            // ==========================================
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0B1120).copy(alpha = 0.98f)
                    ),
                    modifier = Modifier
                        .width(290.dp)
                        .padding(top = 8.dp)
                        .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Section 1: Big Speed Readout Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "VIDEO SPEED CONTROLLER",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (state.isTargetAppActive) state.foregroundAppTitle else "All Video Apps",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Big glowing speed pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (state.isTurbo10xActive) Color(0xFFDC2626) else Color(0xFF4F46E5),
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = "⚡ ${state.currentPlaybackSpeed}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Section 2: Continuous Speed Slider (1.0x to 10.0x)
                        Text(
                            text = "Adjust Speed: ${String.format(Locale.US, "%.1fx", currentSpeedFloat)}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Slider(
                            value = currentSpeedFloat,
                            onValueChange = { newValue ->
                                val stepped = (newValue * 4).roundToInt() / 4.0f
                                val formatted = if (stepped >= 10f) "10.0x" else String.format(Locale.US, "%.2fx", stepped).replace(".00x", ".0x")
                                CompanionStateManager.setPlaybackSpeed(formatted)
                            },
                            valueRange = 1.0f..10.0f,
                            steps = 35, // 0.25x step resolution
                            colors = SliderDefaults.colors(
                                thumbColor = if (state.isTurbo10xActive) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                activeTrackColor = if (state.isTurbo10xActive) Color(0xFFEF4444) else Color(0xFF6366F1),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("overlay_speed_slider")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Section 3: Preset Speed Buttons (1x to 10x)
                        Text(
                            text = "QUICK SPEED PRESETS",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetSpeeds.forEach { speed ->
                                val isSelected = state.currentPlaybackSpeed == speed
                                val is10x = speed == "10.0x"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        if (is10x) Color(0xFFDC2626) else Color(0xFF4F46E5)
                                    } else {
                                        if (is10x) Color(0xFF312E81) else Color(0xFF1E293B)
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            CompanionStateManager.setPlaybackSpeed(speed)
                                        }
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                        .testTag("preset_speed_$speed")
                                ) {
                                    Text(
                                        text = if (is10x) "⚡ 10x" else speed,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected || is10x) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 4: Media Playback Buttons (Rewind -10s, Play/Pause, Forward +10s)
                        Text(
                            text = "PLAYBACK CONTROLS",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // -10s Rewind
                            OverlayActionButton(
                                icon = Icons.Default.FastRewind,
                                label = "-10s",
                                tag = "overlay_rewind_btn",
                                onClick = { CompanionStateManager.triggerMediaAction(MediaActionType.REWIND) }
                            )

                            // Play / Pause Hero Button
                            Surface(
                                shape = CircleShape,
                                color = if (state.isPlaying) Color(0xFF10B981) else Color(0xFF4F46E5),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(52.dp)
                                    .scale(if (state.isPlaying) pulseScale else 1f)
                                    .clickable { CompanionStateManager.togglePlayPause() }
                                    .testTag("overlay_play_pause_hero_btn")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (state.isPlaying) "Pause" else "Play",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // +10s Forward
                            OverlayActionButton(
                                icon = Icons.Default.FastForward,
                                label = "+10s",
                                tag = "overlay_forward_btn",
                                onClick = { CompanionStateManager.triggerMediaAction(MediaActionType.FAST_FORWARD) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 5: Real Video Player Hierarchy Scanner
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ACCESSIBILITY SCANNER",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (state.currentDiscoverySnapshot != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF065F46)
                                ) {
                                    Text(
                                        text = "${state.currentDiscoverySnapshot?.totalNodesCaptured} nodes scanned",
                                        color = Color(0xFF34D399),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        CompanionStateManager.triggerDiscoveryScan(com.example.model.DiscoveryScanType.CONTROLS_VISIBLE)
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("overlay_scan_controls_btn")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Scan Controls (A)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4F46E5),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        CompanionStateManager.triggerDiscoveryScan(com.example.model.DiscoveryScanType.SPEED_MENU_OPEN)
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("overlay_scan_menu_btn")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Scan Speed Menu (B)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Note
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (state.isTargetAppActive) "Target: ${state.foregroundAppTitle}" else "Ready for video playback",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                            Text(
                                text = if (state.allDiscoveredSpeedList.isNotEmpty()) "${state.allDiscoveredSpeedList.size} speeds found" else "1x – 10x Ready",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayActionButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() }
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
