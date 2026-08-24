package com.example.service

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.MediaActionType
import com.example.model.NodeInfoSummary
import com.example.model.TraversalDiagnostics
import com.example.model.TraversalStrategy
import java.util.ArrayDeque

data class OptimizationConfig(
    val maxDepth: Int = 16,
    val maxNodesLimit: Int = 300,
    val strategy: TraversalStrategy = TraversalStrategy.BFS,
    val checkClickableParents: Boolean = true,
    val enableSmartFallbacks: Boolean = true,
    val throttleIntervalMs: Long = 60L
)

data class NodeActionResult(
    val node: AccessibilityNodeInfo? = null,
    val clicked: Boolean = false,
    val bounds: Rect = Rect(),
    val matchedViewId: String? = null,
    val matchedHeuristic: String? = null
)

class AccessibilityNodeOptimizer(
    var config: OptimizationConfig = OptimizationConfig()
) {

    private var lastActionTimestamp: Long = 0

    // Common Resource IDs for educational and video player apps (DIKSHA, ExoPlayer, YouTube, Coursera, Udemy, SWAYAM, BYJU'S, PhysicsWallah, etc.)
    private val commonPlayPauseIds = listOf(
        "exo_play", "exo_pause", "play_pause_button", "play_pause", "play_pause_holder",
        "btn_play", "btn_pause", "play_button", "pause_button", "button_play", "button_pause",
        "v_play", "v_pause", "toggle_play_pause", "video_play_pause", "player_control_play_pause",
        "player_view", "exo_play_pause", "exo_play_pause_container", "touch_area",
        "in.gov.diksha.app:id/exo_play", "in.gov.diksha.app:id/exo_pause",
        "in.gov.diksha.app:id/play_pause_button", "in.gov.diksha.app:id/v_play",
        "in.gov.diksha.app:id/preview_video_view", "in.gov.diksha.app:id/player_view",
        "org.khanacademy.android:id/exo_play", "org.khanacademy.android:id/exo_pause",
        "org.khanacademy.android:id/play_pause",
        "org.coursera.android:id/play_pause", "org.coursera.android:id/exo_play", "org.coursera.android:id/exo_pause",
        "com.udemy.android:id/play_pause_button", "com.udemy.android:id/exo_play", "com.udemy.android:id/exo_pause",
        "org.edx.mobile:id/exo_play", "org.edx.mobile:id/exo_pause", "org.edx.mobile:id/play_pause",
        "in.swayam.mobileapp:id/exo_play", "in.swayam.mobileapp:id/exo_pause",
        "xyz.penpencil.physicswala:id/exo_play", "xyz.penpencil.physicswala:id/exo_pause",
        "xyz.penpencil.physicswala:id/play_pause",
        "com.unacademyapp:id/exo_play", "com.unacademyapp:id/exo_pause",
        "com.google.android.youtube:id/play_pause_button",
        "com.google.android.youtube:id/player_control_play_pause",
        "com.google.android.youtube:id/touch_area",
        "com.google.android.youtube:id/watch_player",
        "com.google.android.youtube:id/player_view",
        "androidx.media3.ui:id/exo_play", "androidx.media3.ui:id/exo_pause", "androidx.media3.ui:id/exo_play_pause",
        "com.google.android.exoplayer2.ui:id/exo_play", "com.google.android.exoplayer2.ui:id/exo_pause"
    )

    private val commonForwardIds = listOf(
        "exo_ffwd", "exo_fast_forward", "fast_forward_button", "ffwd", "forward_10", "forward_30",
        "btn_ffwd", "btn_forward", "seek_forward", "exo_forward_10", "forward_button",
        "in.gov.diksha.app:id/exo_ffwd", "in.gov.diksha.app:id/exo_fast_forward",
        "org.khanacademy.android:id/exo_ffwd", "org.khanacademy.android:id/forward_10",
        "org.coursera.android:id/forward_10", "org.coursera.android:id/exo_ffwd",
        "com.udemy.android:id/exo_ffwd", "com.udemy.android:id/forward_10",
        "org.edx.mobile:id/exo_ffwd", "in.swayam.mobileapp:id/exo_ffwd",
        "xyz.penpencil.physicswala:id/exo_ffwd", "xyz.penpencil.physicswala:id/forward_10",
        "com.unacademyapp:id/exo_ffwd",
        "com.google.android.youtube:id/fast_forward_button",
        "androidx.media3.ui:id/exo_ffwd", "com.google.android.exoplayer2.ui:id/exo_ffwd"
    )

    private val commonRewindIds = listOf(
        "exo_rew", "exo_rewind", "rewind_button", "rew", "replay_10", "replay_30",
        "btn_rew", "btn_rewind", "seek_backward", "exo_rewind_10", "replay_button",
        "in.gov.diksha.app:id/exo_rew", "in.gov.diksha.app:id/exo_rewind",
        "org.khanacademy.android:id/exo_rew", "org.khanacademy.android:id/rewind_10",
        "org.coursera.android:id/replay_10", "org.coursera.android:id/exo_rew",
        "com.udemy.android:id/exo_rew", "com.udemy.android:id/replay_10",
        "org.edx.mobile:id/exo_rew", "in.swayam.mobileapp:id/exo_rew",
        "xyz.penpencil.physicswala:id/exo_rew", "xyz.penpencil.physicswala:id/rewind_10",
        "com.unacademyapp:id/exo_rew",
        "com.google.android.youtube:id/rewind_button",
        "androidx.media3.ui:id/exo_rew", "com.google.android.exoplayer2.ui:id/exo_rew"
    )

    private val commonNextIds = listOf(
        "exo_next", "next_button", "btn_next", "next_video", "next_item_button", "skip_next", "next_lecture",
        "in.gov.diksha.app:id/btn_next", "in.gov.diksha.app:id/next_video",
        "org.khanacademy.android:id/next_item_button", "org.khanacademy.android:id/next_video",
        "org.coursera.android:id/next_item", "org.coursera.android:id/next_video",
        "com.udemy.android:id/next_lecture_button", "com.udemy.android:id/exo_next",
        "org.edx.mobile:id/exo_next", "in.swayam.mobileapp:id/next_button",
        "xyz.penpencil.physicswala:id/next_video", "xyz.penpencil.physicswala:id/exo_next",
        "com.unacademyapp:id/next_button",
        "com.google.android.youtube:id/next_button",
        "androidx.media3.ui:id/exo_next", "com.google.android.exoplayer2.ui:id/exo_next"
    )

    private val commonSpeedIds = listOf(
        "exo_playback_speed", "playback_speed", "btn_speed", "speed_button", "btn_playback_speed",
        "speed_setting", "overflow_menu", "settings_button", "player_speed_btn", "speed_control",
        "player_settings", "controls_settings", "video_quality_and_speed",
        "in.gov.diksha.app:id/btn_playback_speed",
        "org.khanacademy.android:id/speed_button", "org.khanacademy.android:id/btn_speed",
        "org.coursera.android:id/speed_button", "org.coursera.android:id/playback_speed",
        "com.udemy.android:id/playback_rate", "com.udemy.android:id/speed_button",
        "org.edx.mobile:id/btn_playback_speed",
        "xyz.penpencil.physicswala:id/playback_speed", "xyz.penpencil.physicswala:id/btn_speed",
        "com.unacademyapp:id/speed_button",
        "com.google.android.youtube:id/overflow_menu",
        "com.google.android.youtube:id/quick_actions_container",
        "androidx.media3.ui:id/exo_playback_speed"
    )

    /**
     * Executes the requested media action across all available root nodes.
     */
    fun performMediaActionAcrossRoots(
        roots: List<AccessibilityNodeInfo>,
        actionType: MediaActionType,
        customIds: List<String> = emptyList(),
        speedTargetText: String? = null
    ): TraversalDiagnostics {
        val startTime = SystemClock.uptimeMillis()
        if (roots.isEmpty()) {
            return TraversalDiagnostics(
                lastScanTimeMs = System.currentTimeMillis(),
                scanDurationMs = 0,
                totalNodesVisited = 0,
                maxDepthReached = 0,
                matchedAction = actionType,
                success = false
            )
        }

        var totalVisited = 0
        var maxDepth = 0

        for (root in roots) {
            val diag = performMediaAction(root, actionType, customIds, speedTargetText)
            totalVisited += diag.totalNodesVisited
            if (diag.maxDepthReached > maxDepth) maxDepth = diag.maxDepthReached
            if (diag.success) {
                return diag.copy(
                    totalNodesVisited = totalVisited,
                    maxDepthReached = maxDepth,
                    scanDurationMs = SystemClock.uptimeMillis() - startTime
                )
            }
        }

        return TraversalDiagnostics(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = SystemClock.uptimeMillis() - startTime,
            totalNodesVisited = totalVisited,
            maxDepthReached = maxDepth,
            matchedAction = actionType,
            matchedByHeuristic = "No direct root match",
            success = false
        )
    }

    /**
     * Executes the requested media action on the active root node using optimized traversal.
     */
    fun performMediaAction(
        rootNode: AccessibilityNodeInfo?,
        actionType: MediaActionType,
        customIds: List<String> = emptyList(),
        speedTargetText: String? = null
    ): TraversalDiagnostics {
        val startTime = SystemClock.uptimeMillis()
        if (rootNode == null) {
            return TraversalDiagnostics(
                lastScanTimeMs = System.currentTimeMillis(),
                scanDurationMs = 0,
                totalNodesVisited = 0,
                maxDepthReached = 0,
                matchedAction = actionType,
                success = false
            )
        }

        // Throttle rapid repeated actions
        val now = SystemClock.uptimeMillis()
        if (now - lastActionTimestamp < config.throttleIntervalMs) {
            return TraversalDiagnostics(
                lastScanTimeMs = System.currentTimeMillis(),
                scanDurationMs = 0,
                totalNodesVisited = 0,
                maxDepthReached = 0,
                matchedAction = actionType,
                matchedByHeuristic = "Throttled",
                success = false
            )
        }
        lastActionTimestamp = now

        // Step 1: Check if target action is 10x Speed or Specific Speed
        if (actionType == MediaActionType.SPEED_10X || actionType == MediaActionType.SPEED_SET) {
            val speedLabel = speedTargetText ?: if (actionType == MediaActionType.SPEED_10X) "10x" else "2.0x"
            val speedResult = attemptSpeedSelection(rootNode, speedLabel)
            if (speedResult.clicked) {
                val duration = SystemClock.uptimeMillis() - startTime
                return TraversalDiagnostics(
                    lastScanTimeMs = System.currentTimeMillis(),
                    scanDurationMs = duration,
                    totalNodesVisited = 15,
                    maxDepthReached = 3,
                    matchedAction = actionType,
                    matchedViewId = speedResult.matchedViewId,
                    matchedByHeuristic = speedResult.matchedHeuristic ?: "Direct Speed Selection",
                    success = true
                )
            }
        }

        // Step 2: Direct Resource ID lookup (Fastest O(1) - O(k))
        val targetIds = when (actionType) {
            MediaActionType.PLAY_PAUSE, MediaActionType.PLAY, MediaActionType.PAUSE -> customIds + commonPlayPauseIds
            MediaActionType.FAST_FORWARD -> customIds + commonForwardIds
            MediaActionType.REWIND -> customIds + commonRewindIds
            MediaActionType.NEXT -> customIds + commonNextIds
            MediaActionType.SPEED_TOGGLE, MediaActionType.SPEED_10X, MediaActionType.SPEED_SET -> customIds + commonSpeedIds
            else -> customIds
        }

        for (resId in targetIds) {
            try {
                val matches = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!matches.isNullOrEmpty()) {
                    for (node in matches) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (executeClick(node)) {
                            val duration = SystemClock.uptimeMillis() - startTime
                            return TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = duration,
                                totalNodesVisited = matches.size,
                                maxDepthReached = 1,
                                matchedAction = actionType,
                                matchedViewId = resId,
                                matchedByHeuristic = "Direct Resource ID Match ($resId)",
                                success = true
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and continue fallback
            }
        }

        // Step 3: Traverse Hierarchy based on Strategy (BFS or DFS)
        val traversalResult = when (config.strategy) {
            TraversalStrategy.BFS -> traverseBfs(rootNode, actionType)
            TraversalStrategy.DFS -> traverseDfs(rootNode, actionType)
            TraversalStrategy.ID_FIRST_DIRECT -> traverseBfs(rootNode, actionType)
        }

        val duration = SystemClock.uptimeMillis() - startTime
        return traversalResult.copy(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = duration
        )
    }

    /**
     * Finds the best target node and returns its bounding coordinates for gesture fallback.
     */
    fun findTargetNodeWithBounds(
        rootNode: AccessibilityNodeInfo?,
        actionType: MediaActionType,
        customIds: List<String> = emptyList()
    ): NodeActionResult {
        if (rootNode == null) return NodeActionResult()
        val rect = Rect()

        // 1. Try resource IDs
        val targetIds = when (actionType) {
            MediaActionType.PLAY_PAUSE, MediaActionType.PLAY, MediaActionType.PAUSE -> customIds + commonPlayPauseIds
            MediaActionType.FAST_FORWARD -> customIds + commonForwardIds
            MediaActionType.REWIND -> customIds + commonRewindIds
            MediaActionType.NEXT -> customIds + commonNextIds
            MediaActionType.SPEED_TOGGLE, MediaActionType.SPEED_10X -> customIds + commonSpeedIds
            else -> customIds
        }

        for (resId in targetIds) {
            try {
                val matches = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!matches.isNullOrEmpty()) {
                    val first = matches.first()
                    first.getBoundsInScreen(rect)
                    val clicked = executeClick(first)
                    return NodeActionResult(
                        node = first,
                        clicked = clicked,
                        bounds = rect,
                        matchedViewId = resId,
                        matchedHeuristic = "ID Match: $resId"
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. BFS heuristic search
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0

        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            if (matchesActionHeuristic(curr, actionType)) {
                curr.getBoundsInScreen(rect)
                val clicked = executeClick(curr)
                return NodeActionResult(
                    node = curr,
                    clicked = clicked,
                    bounds = rect,
                    matchedViewId = curr.viewIdResourceName,
                    matchedHeuristic = "Heuristic: ${curr.className}"
                )
            }

            for (i in 0 until curr.childCount) {
                val c = curr.getChild(i)
                if (c != null) queue.add(c)
            }
        }

        return NodeActionResult()
    }

    private fun attemptSpeedSelection(root: AccessibilityNodeInfo, targetSpeed: String): NodeActionResult {
        val rect = Rect()
        // Try finding speed menu item containing "10x", "10", "2.0x", etc.
        val searchLabels = listOf(targetSpeed, targetSpeed.lowercase(), targetSpeed.replace("x", ""), "speed $targetSpeed")

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 120) {
            val curr = queue.poll() ?: break
            visited++

            val text = curr.text?.toString()?.lowercase().orEmpty()
            val desc = curr.contentDescription?.toString()?.lowercase().orEmpty()
            val id = curr.viewIdResourceName?.lowercase().orEmpty()

            val isSpeedMatch = searchLabels.any { label ->
                text.contains(label.lowercase()) || desc.contains(label.lowercase())
            }

            if (isSpeedMatch) {
                curr.getBoundsInScreen(rect)
                val clicked = executeClick(curr)
                return NodeActionResult(
                    node = curr,
                    clicked = clicked,
                    bounds = rect,
                    matchedViewId = curr.viewIdResourceName,
                    matchedHeuristic = "Speed Option Match ($targetSpeed)"
                )
            }

            // Also check if this is the speed settings trigger button
            if (id.contains("speed") || desc.contains("speed") || text.contains("speed")) {
                curr.getBoundsInScreen(rect)
                val clicked = executeClick(curr)
                return NodeActionResult(
                    node = curr,
                    clicked = clicked,
                    bounds = rect,
                    matchedViewId = curr.viewIdResourceName,
                    matchedHeuristic = "Speed Trigger Button Clicked"
                )
            }

            for (i in 0 until curr.childCount) {
                val child = curr.getChild(i)
                if (child != null) queue.add(child)
            }
        }

        return NodeActionResult()
    }

    private fun traverseBfs(root: AccessibilityNodeInfo, targetAction: MediaActionType): TraversalDiagnostics {
        var visited = 0
        var maxDepth = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))

        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val (current, depth) = queue.poll() ?: break
            visited++
            if (depth > maxDepth) maxDepth = depth

            if (matchesActionHeuristic(current, targetAction)) {
                if (executeClick(current)) {
                    return TraversalDiagnostics(
                        totalNodesVisited = visited,
                        maxDepthReached = maxDepth,
                        matchedAction = targetAction,
                        matchedViewId = current.viewIdResourceName,
                        matchedByHeuristic = "BFS Heuristic Match (${current.className})",
                        success = true
                    )
                }
            }

            if (depth < config.maxDepth) {
                val childCount = current.childCount
                for (i in 0 until childCount) {
                    val child = current.getChild(i)
                    if (child != null) {
                        queue.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return TraversalDiagnostics(
            totalNodesVisited = visited,
            maxDepthReached = maxDepth,
            matchedAction = targetAction,
            matchedByHeuristic = "None matched",
            success = false
        )
    }

    private fun traverseDfs(root: AccessibilityNodeInfo, targetAction: MediaActionType): TraversalDiagnostics {
        var visited = 0
        var maxDepth = 0

        fun dfsInternal(node: AccessibilityNodeInfo, depth: Int): Boolean {
            visited++
            if (depth > maxDepth) maxDepth = depth
            if (visited >= config.maxNodesLimit || depth > config.maxDepth) return false

            if (matchesActionHeuristic(node, targetAction)) {
                if (executeClick(node)) return true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val found = dfsInternal(child, depth + 1)
                    if (found) return true
                }
            }
            return false
        }

        val success = dfsInternal(root, 0)
        return TraversalDiagnostics(
            totalNodesVisited = visited,
            maxDepthReached = maxDepth,
            matchedAction = targetAction,
            matchedByHeuristic = if (success) "DFS Heuristic Match" else "None",
            success = success
        )
    }

    private fun matchesActionHeuristic(node: AccessibilityNodeInfo, target: MediaActionType): Boolean {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val combined = "$viewId $desc $text"

        return when (target) {
            MediaActionType.PLAY_PAUSE -> {
                combined.contains("play") || combined.contains("pause") ||
                        combined.contains("resume") || combined.contains("toggle playback") ||
                        combined.contains("play/pause") || combined.contains("video_view") ||
                        combined.contains("player_control")
            }
            MediaActionType.PLAY -> {
                (combined.contains("play") || combined.contains("resume")) && !combined.contains("pause")
            }
            MediaActionType.PAUSE -> {
                combined.contains("pause") || combined.contains("stop")
            }
            MediaActionType.FAST_FORWARD -> {
                combined.contains("forward") || combined.contains("ffwd") ||
                        combined.contains("+10") || combined.contains("10s") || combined.contains("seek forward")
            }
            MediaActionType.REWIND -> {
                combined.contains("rewind") || combined.contains("rew") ||
                        combined.contains("-10") || combined.contains("replay") || combined.contains("seek backward")
            }
            MediaActionType.NEXT -> {
                combined.contains("next") || combined.contains("skip") || combined.contains("forward to next")
            }
            MediaActionType.PREVIOUS -> {
                combined.contains("previous") || combined.contains("prev")
            }
            MediaActionType.SPEED_TOGGLE -> {
                combined.contains("speed") || combined.contains("playback rate") ||
                        combined.contains("1.0x") || combined.contains("1.25x") || combined.contains("1.5x") ||
                        combined.contains("2.0x") || combined.contains("10x")
            }
            MediaActionType.SPEED_10X -> {
                combined.contains("10x") || combined.contains("10.0x") || combined.contains("speed") || combined.contains("turbo")
            }
            MediaActionType.SPEED_SET -> {
                combined.contains("speed") || combined.contains("playback")
            }
            MediaActionType.CAPTIONS -> {
                combined.contains("caption") || combined.contains("subtitles") ||
                        combined.contains("closed caption") || combined.contains("cc")
            }
            MediaActionType.FULLSCREEN -> {
                combined.contains("fullscreen") || combined.contains("full screen") || combined.contains("expand video")
            }
            MediaActionType.EXTRACT_CAPTIONS -> false
        }
    }

    /**
     * Attempts to click the node directly or find the nearest clickable parent or child.
     */
    fun executeClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        // Check parents
        if (config.checkClickableParents) {
            var parent = node.parent
            var depthCheck = 0
            while (parent != null && depthCheck < 4) {
                if (parent.isClickable) {
                    val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
                parent = parent.parent
                depthCheck++
            }
        }

        // Check immediate children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && child.isClickable) {
                val clicked = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
        }

        return false
    }

    /**
     * Dumps the current view tree into a list of NodeInfoSummary for real-time visualization.
     */
    fun dumpNodeHierarchy(root: AccessibilityNodeInfo?, maxNodes: Int = 100): List<NodeInfoSummary> {
        if (root == null) return emptyList()
        val list = mutableListOf<NodeInfoSummary>()
        val rect = Rect()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (list.size >= maxNodes || depth > config.maxDepth) return
            node.getBoundsInScreen(rect)

            val summary = NodeInfoSummary(
                id = "${node.className}_${list.size}_$depth",
                className = node.className?.toString() ?: "UnknownView",
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewIdResourceName = node.viewIdResourceName,
                isClickable = node.isClickable,
                isVisibleToUser = node.isVisibleToUser,
                bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                depth = depth,
                childCount = node.childCount,
                detectedRole = detectRole(node)
            )
            list.add(summary)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverse(child, depth + 1)
                }
            }
        }

        traverse(root, 0)
        return list
    }

    private fun detectRole(node: AccessibilityNodeInfo): MediaActionType? {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val combined = "$viewId $desc $text"
        return when {
            combined.contains("play") && combined.contains("pause") -> MediaActionType.PLAY_PAUSE
            combined.contains("pause") -> MediaActionType.PAUSE
            combined.contains("play") -> MediaActionType.PLAY
            combined.contains("ffwd") || combined.contains("forward") -> MediaActionType.FAST_FORWARD
            combined.contains("rew") || combined.contains("rewind") -> MediaActionType.REWIND
            combined.contains("next") || combined.contains("skip") -> MediaActionType.NEXT
            combined.contains("10x") || combined.contains("turbo") -> MediaActionType.SPEED_10X
            combined.contains("speed") -> MediaActionType.SPEED_TOGGLE
            combined.contains("caption") || combined.contains("cc") -> MediaActionType.CAPTIONS
            else -> null
        }
    }

    /**
     * Extracts readable text from active video captions / subtitles container.
     */
    fun extractScreenCaptions(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val textSnippets = mutableListOf<String>()

        fun scan(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12) return
            val desc = node.contentDescription?.toString()
            val text = node.text?.toString()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            if (!text.isNullOrBlank() && (id.contains("subtitle") || id.contains("caption") || id.contains("transcript") || id.contains("content"))) {
                textSnippets.add(text)
            } else if (!desc.isNullOrBlank() && (id.contains("subtitle") || id.contains("caption"))) {
                textSnippets.add(desc)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) scan(child, depth + 1)
            }
        }

        scan(root, 0)
        return textSnippets.joinToString("\n")
    }
}
