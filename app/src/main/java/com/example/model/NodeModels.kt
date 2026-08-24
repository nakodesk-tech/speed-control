package com.example.model

enum class MediaActionType(val displayName: String) {
    PLAY_PAUSE("Play / Pause"),
    PLAY("Play"),
    PAUSE("Pause"),
    FAST_FORWARD("Fast Forward (+10s)"),
    REWIND("Rewind (-10s)"),
    NEXT("Next Lesson / Video"),
    PREVIOUS("Previous Lesson"),
    SPEED_TOGGLE("Playback Speed Cycle"),
    SPEED_10X("10x Turbo Speed"),
    SPEED_SET("Set Playback Speed"),
    CAPTIONS("Subtitles / Captions"),
    FULLSCREEN("Fullscreen Toggle"),
    EXTRACT_CAPTIONS("Read Captions / Text")
}

enum class TraversalStrategy(val displayName: String, val description: String) {
    BFS("Breadth-First Search (Fast UI controls)", "Checks shallow media container controls first before diving into deep subtrees"),
    DFS("Depth-First Search (Deep Tree)", "Explores each view subtree fully before backtracking"),
    ID_FIRST_DIRECT("Resource ID Direct Cache", "Tries known view IDs directly before falling back to full tree scan")
}

enum class DiscoveryScanType(val displayName: String) {
    CONTROLS_VISIBLE("Controls Visible Layer (Scan A)"),
    SPEED_MENU_OPEN("Speed Menu Open (Scan B)"),
    FULL_PLAYER_SCAN("Full Video Player Scan")
}

data class DiscoveredSpeedOption(
    val speedLabel: String,
    val speedFloat: Float,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val isClickable: Boolean,
    val isSelected: Boolean,
    val isChecked: Boolean,
    val bounds: String,
    val parentViewId: String?,
    val parentText: String?,
    val parentClass: String?,
    val parentClickable: Boolean
)

data class DiscoveredNodeDetail(
    val id: String,
    val className: String,
    val viewIdResourceName: String?,
    val text: String?,
    val contentDescription: String?,
    val isClickable: Boolean,
    val isVisibleToUser: Boolean,
    val isEnabled: Boolean,
    val isSelected: Boolean,
    val isChecked: Boolean,
    val bounds: String,
    val depth: Int,
    val childCount: Int,
    val parentId: String? = null,
    val parentText: String? = null,
    val parentClass: String? = null,
    val parentClickable: Boolean = false,
    val matchedKeywords: List<String> = emptyList(),
    val isSpeedCandidate: Boolean = false,
    val isPlayPauseCandidate: Boolean = false,
    val isSettingsCandidate: Boolean = false,
    val detectedSpeedValue: Float? = null,
    val rawLogText: String = ""
)

data class DiscoverySnapshot(
    val id: String = System.currentTimeMillis().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val scanType: DiscoveryScanType = DiscoveryScanType.FULL_PLAYER_SCAN,
    val foregroundPackage: String = "",
    val foregroundAppTitle: String = "",
    val totalNodesCaptured: Int = 0,
    val maxDepth: Int = 0,
    val speedCandidateNodes: List<DiscoveredNodeDetail> = emptyList(),
    val playPauseCandidateNodes: List<DiscoveredNodeDetail> = emptyList(),
    val settingsCandidateNodes: List<DiscoveredNodeDetail> = emptyList(),
    val allDiscoveredSpeeds: List<DiscoveredSpeedOption> = emptyList(),
    val allNodes: List<DiscoveredNodeDetail> = emptyList(),
    val formattedReport: String = "",
    val rawHierarchyTreeDump: String = ""
)

data class NodeInfoSummary(
    val id: String,
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val isClickable: Boolean,
    val isVisibleToUser: Boolean,
    val bounds: String,
    val depth: Int,
    val childCount: Int,
    val detectedRole: MediaActionType? = null
)

data class TraversalDiagnostics(
    val lastScanTimeMs: Long = 0,
    val scanDurationMs: Long = 0,
    val totalNodesVisited: Int = 0,
    val maxDepthReached: Int = 0,
    val matchedAction: MediaActionType? = null,
    val matchedViewId: String? = null,
    val matchedByHeuristic: String? = null,
    val success: Boolean = false,
    val currentForegroundPackage: String = ""
)

data class SimulatedNode(
    val id: String,
    val className: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val isClickable: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val bounds: String = "[0,0][1080,2400]",
    val children: List<SimulatedNode> = emptyList()
) {
    fun toSummary(depth: Int = 0): NodeInfoSummary {
        return NodeInfoSummary(
            id = id,
            className = className,
            text = text,
            contentDescription = contentDescription,
            viewIdResourceName = viewIdResourceName,
            isClickable = isClickable,
            isVisibleToUser = isVisibleToUser,
            bounds = bounds,
            depth = depth,
            childCount = children.size,
            detectedRole = detectActionType()
        )
    }

    private fun detectActionType(): MediaActionType? {
        val combined = "${viewIdResourceName.orEmpty()} ${contentDescription.orEmpty()} ${text.orEmpty()}".lowercase()
        return when {
            combined.contains("play") && combined.contains("pause") -> MediaActionType.PLAY_PAUSE
            combined.contains("pause") -> MediaActionType.PAUSE
            combined.contains("play") -> MediaActionType.PLAY
            combined.contains("ffwd") || combined.contains("forward") || combined.contains("seek forward") -> MediaActionType.FAST_FORWARD
            combined.contains("rew") || combined.contains("rewind") || combined.contains("replay") -> MediaActionType.REWIND
            combined.contains("next") || combined.contains("skip") -> MediaActionType.NEXT
            combined.contains("10x") || combined.contains("turbo") -> MediaActionType.SPEED_10X
            combined.contains("speed") -> MediaActionType.SPEED_TOGGLE
            combined.contains("caption") || combined.contains("subtitles") || combined.contains("cc") -> MediaActionType.CAPTIONS
            else -> null
        }
    }
}
