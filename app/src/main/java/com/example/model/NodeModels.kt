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
