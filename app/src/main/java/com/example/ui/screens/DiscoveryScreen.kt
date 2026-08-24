package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiscoveredNodeDetail
import com.example.model.DiscoveredSpeedOption
import com.example.model.DiscoveryScanType
import com.example.service.CompanionStateManager

enum class NodeFilterCategory(val label: String) {
    SPEED_CANDIDATES("Speed Nodes"),
    SPEED_OPTIONS("Speed Presets"),
    PLAY_PAUSE("Play/Pause"),
    SETTINGS("Settings/More"),
    ALL_NODES("All Visited")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryScreen() {
    val state by CompanionStateManager.uiState.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(NodeFilterCategory.SPEED_CANDIDATES) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Overview & Discovered Speeds, 1: Node Inspector, 2: Raw Tree Dump

    val snapshot = state.currentDiscoverySnapshot

    val filteredNodes = remember(snapshot, selectedFilter, searchQuery) {
        val baseList = when (selectedFilter) {
            NodeFilterCategory.SPEED_CANDIDATES -> snapshot?.speedCandidateNodes ?: emptyList()
            NodeFilterCategory.SPEED_OPTIONS -> snapshot?.allNodes?.filter { it.detectedSpeedValue != null } ?: emptyList()
            NodeFilterCategory.PLAY_PAUSE -> snapshot?.playPauseCandidateNodes ?: emptyList()
            NodeFilterCategory.SETTINGS -> snapshot?.settingsCandidateNodes ?: emptyList()
            NodeFilterCategory.ALL_NODES -> snapshot?.allNodes ?: emptyList()
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.lowercase()
            baseList.filter {
                it.viewIdResourceName?.lowercase()?.contains(q) == true ||
                        it.text?.lowercase()?.contains(q) == true ||
                        it.contentDescription?.lowercase()?.contains(q) == true ||
                        it.className.lowercase().contains(q)
            }
        }
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // HEADER: REAL ACCESSIBILITY DISCOVERY MODE
        // ==========================================
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF6366F1))), RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF4F46E5))))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Player Discovery",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Accessibility Node Discovery",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Real Hierarchy Inspector (Up to 2500 nodes)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (state.isScanningInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Foreground Package Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0B1120),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "FOREGROUND PACKAGE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = state.foregroundPackage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isTargetAppActive) Color(0xFF34D399) else Color(0xFFFBBF24),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.isTargetAppActive) Color(0xFF065F46) else Color(0xFF334155)
                            ) {
                                Text(
                                    text = state.foregroundAppTitle,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // SCANNING ACTION CONTROLS (SCAN A / SCAN B)
        // ==========================================
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CAPTURE REAL PLAYER HIERARCHY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Traverses all active window roots without stopping at 350 nodes. Captures up to 2500 nodes to find actual speed buttons, dialogs, and parent containers.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Scan A: Controls Visible
                        Button(
                            onClick = {
                                CompanionStateManager.triggerDiscoveryScan(DiscoveryScanType.CONTROLS_VISIBLE)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_controls_visible_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Scan Controls", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("(Controls Visible)", fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        // Scan B: Speed Menu Open
                        Button(
                            onClick = {
                                CompanionStateManager.triggerDiscoveryScan(DiscoveryScanType.SPEED_MENU_OPEN)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_speed_menu_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Scan Speed Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("(Menu Open)", fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Universal Deep Scan
                    OutlinedButton(
                        onClick = {
                            CompanionStateManager.triggerDiscoveryScan(DiscoveryScanType.FULL_PLAYER_SCAN)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_deep_full_btn")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deep Full-Window Scan (2500 Nodes)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ==========================================
        // SCAN SUMMARY & METRICS
        // ==========================================
        if (snapshot != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "LATEST SCAN RESULTS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = snapshot.scanType.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { copyToClipboard(snapshot.formattedReport, "Discovery Report") },
                                    modifier = Modifier.size(36.dp).testTag("copy_discovery_report_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Report",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { CompanionStateManager.clearDiscoverySnapshots() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Metric Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MetricBadge(
                                label = "Total Nodes",
                                value = "${snapshot.totalNodesCaptured}",
                                color = Color(0xFF0284C7),
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                label = "Speed Options",
                                value = "${snapshot.allDiscoveredSpeeds.size}",
                                color = if (snapshot.allDiscoveredSpeeds.isNotEmpty()) Color(0xFF10B981) else Color(0xFF94A3B8),
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                label = "Speed Nodes",
                                value = "${snapshot.speedCandidateNodes.size}",
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                label = "Play/Pause",
                                value = "${snapshot.playPauseCandidateNodes.size}",
                                color = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // TABS: SPEED OPTIONS / NODE INSPECTOR / RAW TREE
            // ==========================================
            item {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Speed Options (${snapshot.allDiscoveredSpeeds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Node Inspector", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Raw Tree", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // TAB 0: DISCOVERED SPEED OPTIONS
            if (activeTab == 0) {
                if (snapshot.allDiscoveredSpeeds.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No direct speed options found in this layer.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To discover speed menu items: Open the video settings/speed menu in the target app, then tap 'Scan Speed Menu'.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(snapshot.allDiscoveredSpeeds) { option ->
                        DiscoveredSpeedOptionCard(
                            option = option,
                            onCopy = { copyToClipboard(it, "Speed Option Details") }
                        )
                    }
                }
            }

            // TAB 1: NODE INSPECTOR WITH FILTER
            if (activeTab == 1) {
                item {
                    Column {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter by ID, text, class or desc...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("node_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NodeFilterCategory.values().forEach { cat ->
                                FilterChip(
                                    selected = selectedFilter == cat,
                                    onClick = { selectedFilter = cat },
                                    label = {
                                        val count = when (cat) {
                                            NodeFilterCategory.SPEED_CANDIDATES -> snapshot.speedCandidateNodes.size
                                            NodeFilterCategory.SPEED_OPTIONS -> snapshot.allDiscoveredSpeeds.size
                                            NodeFilterCategory.PLAY_PAUSE -> snapshot.playPauseCandidateNodes.size
                                            NodeFilterCategory.SETTINGS -> snapshot.settingsCandidateNodes.size
                                            NodeFilterCategory.ALL_NODES -> snapshot.allNodes.size
                                        }
                                        Text("${cat.label} ($count)", fontSize = 11.sp)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                if (filteredNodes.isEmpty()) {
                    item {
                        Text(
                            text = "No matching nodes found for selected criteria.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(filteredNodes) { node ->
                        DiscoveredNodeDetailCard(
                            node = node,
                            onCopy = { copyToClipboard(node.rawLogText, "Node Info") }
                        )
                    }
                }
            }

            // TAB 2: RAW TREE DUMP
            if (activeTab == 2) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0B1120),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "RAW HIERARCHY TREE DUMP",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Button(
                                    onClick = { copyToClipboard(snapshot.rawHierarchyTreeDump, "Raw Node Tree") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Full Dump", fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF050914), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = snapshot.rawHierarchyTreeDump.ifEmpty { "No hierarchy dump available." },
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Initial Empty State Guidance
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "No Scan Captured Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "1. Open DIKSHA LMS, iGOT Karmayogi, or your video app\n" +
                                    "2. Start playing a video and tap screen to reveal controls\n" +
                                    "3. Tap 'Scan Controls' or 'Scan Speed Menu' above\n" +
                                    "4. Inspect real IDs, classes, bounds, and discovered speed rates!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun DiscoveredSpeedOptionCard(
    option: DiscoveredSpeedOption,
    onCopy: (String) -> Unit
) {
    val reportText = buildString {
        appendLine("SPEED OPTION: ${option.speedLabel}")
        appendLine("ID: ${option.viewId ?: "<no_id>"}")
        appendLine("TEXT: \"${option.text ?: ""}\"")
        appendLine("DESC: \"${option.contentDescription ?: ""}\"")
        appendLine("CLASS: ${option.className}")
        appendLine("CLICKABLE: ${option.isClickable} (Parent clickable: ${option.parentClickable})")
        appendLine("SELECTED: ${option.isSelected} | CHECKED: ${option.isChecked}")
        appendLine("BOUNDS: ${option.bounds}")
        appendLine("PARENT ID: ${option.parentViewId ?: "<none>"}")
        appendLine("PARENT TEXT: \"${option.parentText ?: ""}\"")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF065F46)
                    ) {
                        Text(
                            text = "⚡ ${option.speedLabel}",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (option.isClickable) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = "CLICKABLE",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (option.parentClickable && !option.isClickable) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF312E81)
                        ) {
                            Text(
                                text = "PARENT CLICKABLE",
                                color = Color(0xFFA5B4FC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onCopy(reportText) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow("Resource ID:", option.viewId ?: "<no_id>", isMono = true)
            InfoRow("Text:", option.text ?: "<null>")
            InfoRow("Description:", option.contentDescription ?: "<null>")
            InfoRow("Class:", option.className.substringAfterLast('.'))
            InfoRow("Bounds:", option.bounds, isMono = true)
            if (option.parentViewId != null) {
                InfoRow("Parent ID:", option.parentViewId, isMono = true)
            }
        }
    }
}

@Composable
private fun DiscoveredNodeDetailCard(
    node: DiscoveredNodeDetail,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.className.substringAfterLast('.'),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Depth ${node.depth}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.isClickable) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "CLICK",
                                color = Color(0xFF10B981),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!node.viewIdResourceName.isNullOrBlank()) {
                InfoRow("ID:", node.viewIdResourceName, isMono = true)
            }
            if (!node.text.isNullOrBlank()) {
                InfoRow("Text:", "\"${node.text}\"")
            }
            if (!node.contentDescription.isNullOrBlank()) {
                InfoRow("Desc:", "\"${node.contentDescription}\"")
            }
            InfoRow("Bounds:", node.bounds, isMono = true)
            if (!node.parentId.isNullOrBlank()) {
                InfoRow("Parent ID:", node.parentId, isMono = true)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.width(75.dp)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = Color(0xFFCBD5E1),
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 2
        )
    }
}
