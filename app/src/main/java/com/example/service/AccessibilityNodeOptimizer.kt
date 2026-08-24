package com.example.service

import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.MediaActionType
import com.example.model.NodeInfoSummary
import com.example.model.TraversalDiagnostics
import com.example.model.TraversalStrategy
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

data class OptimizationConfig(
    val maxDepth: Int = 18,
    val maxNodesLimit: Int = 350,
    val strategy: TraversalStrategy = TraversalStrategy.BFS,
    val checkClickableParents: Boolean = true,
    val enableSmartFallbacks: Boolean = true,
    val throttleIntervalMs: Long = 50L
)

data class NodeActionResult(
    val node: AccessibilityNodeInfo? = null,
    val clicked: Boolean = false,
    val bounds: Rect = Rect(),
    val matchedViewId: String? = null,
    val matchedHeuristic: String? = null,
    val isMenuTriggerOnly: Boolean = false
)

data class PlaybackStatusResult(
    val isPlaying: Boolean,
    val confidence: Float,
    val source: String,
    val detectedSpeed: String? = null,
    val timecode: String? = null
)

data class VideoPlayerTargetDescriptor(
    val playerBounds: Rect = Rect(),
    val playerContainerNode: AccessibilityNodeInfo? = null,
    val playPauseNode: AccessibilityNodeInfo? = null,
    val isPlayingDetected: Boolean? = null,
    val speedButtonNode: AccessibilityNodeInfo? = null,
    val settingsButtonNode: AccessibilityNodeInfo? = null,
    val timecodeNode: AccessibilityNodeInfo? = null,
    val speedOptionsAvailable: List<Pair<String, AccessibilityNodeInfo>> = emptyList()
)

class AccessibilityNodeOptimizer(
    var config: OptimizationConfig = OptimizationConfig()
) {

    private var lastActionTimestamp: Long = 0
    private var previousTimecode: String? = null
    private var lastTimecodeChangeTime: Long = 0

    // Common Resource IDs for educational and video player apps (DIKSHA, ExoPlayer, YouTube, Coursera, Udemy, SWAYAM, BYJU'S, PhysicsWallah, etc.)
    val commonPlayPauseIds = listOf(
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

    val commonForwardIds = listOf(
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

    val commonRewindIds = listOf(
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

    val commonNextIds = listOf(
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

    val commonSpeedIds = listOf(
        "in.gov.diksha.app:id/btn_playback_speed",
        "btn_playback_speed",
        "exo_playback_speed",
        "playback_speed",
        "btn_speed",
        "speed_button",
        "speed_setting",
        "player_speed_btn",
        "speed_control",
        "playback_rate",
        "org.khanacademy.android:id/speed_button",
        "org.khanacademy.android:id/btn_speed",
        "org.coursera.android:id/speed_button",
        "org.coursera.android:id/playback_speed",
        "com.udemy.android:id/playback_rate",
        "com.udemy.android:id/speed_button",
        "org.edx.mobile:id/btn_playback_speed",
        "xyz.penpencil.physicswala:id/playback_speed",
        "xyz.penpencil.physicswala:id/btn_speed",
        "com.unacademyapp:id/speed_button",
        "androidx.media3.ui:id/exo_playback_speed",
        "com.google.android.exoplayer2.ui:id/exo_playback_speed"
    )

    val commonSettingsGearIds = listOf(
        "overflow_menu",
        "settings_button",
        "player_settings",
        "controls_settings",
        "video_quality_and_speed",
        "btn_settings",
        "more_options",
        "quick_actions_container",
        "com.google.android.youtube:id/overflow_menu",
        "com.google.android.youtube:id/quick_actions_container",
        "in.gov.diksha.app:id/overflow_menu"
    )

    val commonTimecodeIds = listOf(
        "exo_position", "exo_duration", "exo_time", "time_current", "time_total",
        "current_time", "total_time", "video_current_time", "video_total_time",
        "in.gov.diksha.app:id/exo_position", "in.gov.diksha.app:id/exo_duration",
        "com.google.android.youtube:id/time_current", "com.google.android.youtube:id/time_total"
    )

    // =========================================================================
    // SPEED NORMALIZATION & EXACT NUMERIC MATCHING
    // =========================================================================

    /**
     * Normalizes a speed string (e.g. "1x", "1.0x", "1.00x", "10x", "10.0x", "Normal") into a canonical Float.
     */
    fun normalizeSpeedLabel(speedStr: String?): Float? {
        if (speedStr.isNullOrBlank()) return null
        val trimmed = speedStr.trim().lowercase(Locale.US)
        if (trimmed == "normal" || trimmed == "standard" || trimmed == "default") {
            return 1.0f
        }
        val clean = trimmed.replace("x", "").replace("speed", "").replace(":", "").trim()
        return clean.toFloatOrNull()
    }

    /**
     * Extracts exact numeric speed value from node text, contentDescription, or speed option label.
     * Prevents false substring matches like "10x" matching "1x" or "1.5x" matching "1x".
     */
    fun extractSpeedFromOptionText(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().lowercase(Locale.US)
        if (trimmed == "normal" || trimmed == "standard" || trimmed == "default") {
            return 1.0f
        }

        // 1. Direct regex for patterns like "10x", "10.0x", "10.00x", "1.25x", "0.75x", "5x", "Normal"
        // Also supports strings like "Speed 10x", "Playback speed: 1.5x", "10x (Custom)"
        val speedRegex = Regex("""(?:\b(?:playback\s+speed|speed)\s*[:\s]*)?(\d+(?:\.\d+)?)\s*x\b|^\s*(\d+(?:\.\d+)?)\s*x?\s*$""", RegexOption.IGNORE_CASE)
        val match = speedRegex.find(trimmed)
        if (match != null) {
            val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (numStr.isNotEmpty()) {
                val parsed = numStr.toFloatOrNull()
                if (parsed != null) return parsed
            }
        }

        // 2. Fallback regex to find isolated numbers with 'x' (e.g., "(1.5x)")
        val fallbackRegex = Regex("""\b(\d+(?:\.\d+)?)\s*x\b""", RegexOption.IGNORE_CASE)
        val fallbackMatch = fallbackRegex.find(trimmed)
        if (fallbackMatch != null) {
            val numStr = fallbackMatch.groupValues[1]
            return numStr.toFloatOrNull()
        }

        // 3. Fallback for pure numbers if short
        if (trimmed.length in 1..5) {
            return trimmed.toFloatOrNull()
        }

        return null
    }

    /**
     * Exact float comparison with tolerance for speed matching.
     */
    fun speedValuesEqual(speed1: Float, speed2: Float): Boolean {
        return abs(speed1 - speed2) < 0.01f
    }

    /**
     * Checks if a node's text or content description matches the requested numeric speed exactly.
     */
    fun matchesRequestedSpeed(nodeText: String?, nodeDesc: String?, targetSpeed: Float): Boolean {
        val speedFromText = extractSpeedFromOptionText(nodeText)
        if (speedFromText != null && speedValuesEqual(speedFromText, targetSpeed)) {
            return true
        }
        val speedFromDesc = extractSpeedFromOptionText(nodeDesc)
        if (speedFromDesc != null && speedValuesEqual(speedFromDesc, targetSpeed)) {
            return true
        }
        return false
    }

    // =========================================================================
    // ACCESSIBILITY NODE DISCOVERY & TARGETING
    // =========================================================================

    /**
     * Comprehensive Video Player Target Detector:
     * Identifies player surface, control bounds, settings gear, and open speed menus.
     */
    fun inspectVideoPlayerTargets(roots: List<AccessibilityNodeInfo>): VideoPlayerTargetDescriptor {
        val rect = Rect()
        var playerBounds = Rect()
        var playerNode: AccessibilityNodeInfo? = null
        var playPauseNode: AccessibilityNodeInfo? = null
        var isPlaying: Boolean? = null
        var speedBtn: AccessibilityNodeInfo? = null
        var settingsBtn: AccessibilityNodeInfo? = null
        var timecodeNode: AccessibilityNodeInfo? = null
        val speedOptions = mutableListOf<Pair<String, AccessibilityNodeInfo>>()

        val playerKeywords = listOf(
            "player", "video", "exo", "watch_player", "surface", "texture",
            "preview", "content_frame", "media", "youtube", "main_content", "diksha"
        )

        for (root in roots) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0

            while (queue.isNotEmpty() && count < config.maxNodesLimit) {
                val node = queue.poll() ?: break
                count++

                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val className = node.className?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val text = node.text?.toString()?.lowercase().orEmpty()
                val combined = "$id $desc $text $className"

                // 1. Identify Video Player Container
                if (playerNode == null) {
                    val isCandidate = playerKeywords.any { combined.contains(it) }
                    if (isCandidate) {
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 200 && rect.height() > 150) {
                            playerBounds = Rect(rect)
                            playerNode = node
                        }
                    }
                }

                // 2. Identify Play/Pause Button & Status
                if (playPauseNode == null) {
                    if (id.contains("pause") || desc.contains("pause") || text.contains("pause")) {
                        playPauseNode = node
                        isPlaying = true
                    } else if (id.contains("play") || desc.contains("play") || text.contains("play") || desc.contains("resume")) {
                        playPauseNode = node
                        isPlaying = false
                    }
                }

                // 3. Identify Speed Setting Button
                if (speedBtn == null) {
                    if (commonSpeedIds.any { id.contains(it) } ||
                        ((desc.contains("playback speed") || text.contains("playback speed") || desc.contains("playback rate")) && (node.isClickable || node.parent?.isClickable == true))
                    ) {
                        speedBtn = node
                    }
                }

                // 4. Identify Settings / Overflow Menu on Player
                if (settingsBtn == null) {
                    if (commonSettingsGearIds.any { id.contains(it) } ||
                        desc.contains("settings") || desc.contains("more options") || desc.contains("quality")
                    ) {
                        settingsBtn = node
                    }
                }

                // 5. Identify Timecode node
                if (timecodeNode == null) {
                    if (commonTimecodeIds.any { id.contains(it) } || (text.contains(":") && (text.length in 4..15))) {
                        timecodeNode = node
                    }
                }

                // 6. Check for active Speed Options in dialogs/sheets
                val speedVal = extractSpeedFromOptionText(text).let { if (it != null) String.format(Locale.US, "%.2fx", it) else null }
                    ?: extractSpeedFromOptionText(desc).let { if (it != null) String.format(Locale.US, "%.2fx", it) else null }
                if (speedVal != null) {
                    if (node.isClickable || node.parent?.isClickable == true) {
                        speedOptions.add(Pair(speedVal, node))
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        return VideoPlayerTargetDescriptor(
            playerBounds = playerBounds,
            playerContainerNode = playerNode,
            playPauseNode = playPauseNode,
            isPlayingDetected = isPlaying,
            speedButtonNode = speedBtn,
            settingsButtonNode = settingsBtn,
            timecodeNode = timecodeNode,
            speedOptionsAvailable = speedOptions
        )
    }

    /**
     * Searches all window roots for the EXACT requested speed option node (e.g. 1.25x, 2.0x, 5.0x, 10.0x).
     */
    fun findExactSpeedOptionNode(roots: List<AccessibilityNodeInfo>, targetSpeed: Float): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (root in roots) {
            queue.add(root)
        }

        var visited = 0
        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            val text = curr.text?.toString()
            val desc = curr.contentDescription?.toString()

            if (matchesRequestedSpeed(text, desc, targetSpeed)) {
                // Return the node or its clickable container
                if (curr.isClickable) {
                    return curr
                }
                // Check immediate clickable parent
                var p = curr.parent
                var depth = 0
                while (p != null && depth < 4) {
                    if (p.isClickable) return p
                    p = p.parent
                    depth++
                }
                return curr
            }

            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * Finds the speed trigger button in the video controls across all window roots.
     */
    fun findSpeedTriggerNode(
        roots: List<AccessibilityNodeInfo>,
        customIds: List<String> = emptyList()
    ): AccessibilityNodeInfo? {
        val targetIds = customIds + commonSpeedIds

        // 1. Direct Resource ID lookup across roots
        for (root in roots) {
            for (resId in targetIds) {
                try {
                    val matches = root.findAccessibilityNodeInfosByViewId(resId)
                    if (!matches.isNullOrEmpty()) {
                        for (m in matches) {
                            if (m.isClickable || m.parent?.isClickable == true) return m
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Traversal by text / content description
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (root in roots) {
            queue.add(root)
        }

        var visited = 0
        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            val id = curr.viewIdResourceName?.lowercase().orEmpty()
            val desc = curr.contentDescription?.toString()?.lowercase().orEmpty()
            val text = curr.text?.toString()?.lowercase().orEmpty()

            if (targetIds.any { id.contains(it.lowercase()) } ||
                desc.contains("playback speed") || text.contains("playback speed") ||
                desc.contains("playback rate") || text.contains("playback rate") ||
                ((desc.contains("speed") || text.contains("speed")) && (curr.isClickable || curr.parent?.isClickable == true))
            ) {
                if (curr.isClickable || curr.parent?.isClickable == true) {
                    return curr
                }
            }

            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * Finds the Settings / Gear / Overflow menu button on the video player.
     */
    fun findSettingsGearNode(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (root in roots) {
            for (resId in commonSettingsGearIds) {
                try {
                    val matches = root.findAccessibilityNodeInfosByViewId(resId)
                    if (!matches.isNullOrEmpty()) {
                        for (m in matches) {
                            if (m.isClickable || m.parent?.isClickable == true) return m
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (root in roots) {
            queue.add(root)
        }

        var visited = 0
        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            val id = curr.viewIdResourceName?.lowercase().orEmpty()
            val desc = curr.contentDescription?.toString()?.lowercase().orEmpty()

            if (commonSettingsGearIds.any { id.contains(it) } ||
                desc.contains("settings") || desc.contains("more options") || desc.contains("player options")
            ) {
                if (curr.isClickable || curr.parent?.isClickable == true) {
                    return curr
                }
            }

            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * Finds "Playback speed" item inside a settings menu popup / bottom sheet.
     */
    fun findPlaybackSpeedMenuItem(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (root in roots) {
            queue.add(root)
        }

        var visited = 0
        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            val text = curr.text?.toString()?.lowercase().orEmpty()
            val desc = curr.contentDescription?.toString()?.lowercase().orEmpty()
            val id = curr.viewIdResourceName?.lowercase().orEmpty()

            if (text.contains("playback speed") || desc.contains("playback speed") ||
                text.contains("playback rate") || desc.contains("playback rate") ||
                (text.contains("speed") && !text.contains("quality"))
            ) {
                if (curr.isClickable || curr.parent?.isClickable == true) {
                    return curr
                }
            }

            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * Detects currently active/selected playback speed from active UI nodes (speed button text, radio button, etc.)
     */
    fun detectCurrentSelectedSpeed(roots: List<AccessibilityNodeInfo>): Float? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (root in roots) {
            queue.add(root)
        }

        var visited = 0
        while (queue.isNotEmpty() && visited < config.maxNodesLimit) {
            val curr = queue.poll() ?: break
            visited++

            val id = curr.viewIdResourceName?.lowercase().orEmpty()
            val text = curr.text?.toString().orEmpty()
            val desc = curr.contentDescription?.toString().orEmpty()

            // Check if this is a speed button showing current speed (e.g. DIKSHA btn_playback_speed displaying "1.25x")
            if (commonSpeedIds.any { id.contains(it) }) {
                val speed = extractSpeedFromOptionText(text) ?: extractSpeedFromOptionText(desc)
                if (speed != null) return speed
            }

            // Check if node is a selected/checked radio button or chip
            if (curr.isSelected || curr.isChecked) {
                val speed = extractSpeedFromOptionText(text) ?: extractSpeedFromOptionText(desc)
                if (speed != null) return speed
            }

            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * Multi-tier Robust Playback Status Detector:
     * Combines Accessibility Node state, Timecode advancement, and System AudioManager output.
     */
    fun detectVideoPlaybackStatus(
        roots: List<AccessibilityNodeInfo>,
        audioManager: AudioManager?
    ): PlaybackStatusResult {
        val targets = inspectVideoPlayerTargets(roots)

        val currentTimecode = targets.timecodeNode?.text?.toString()
        val detectedSpeed: String? = targets.speedOptionsAvailable.firstOrNull()?.first

        // Heuristic 1: Inspect explicit play/pause button state in node hierarchy
        if (targets.isPlayingDetected != null) {
            val playing = targets.isPlayingDetected
            return PlaybackStatusResult(
                isPlaying = playing,
                confidence = 0.95f,
                source = if (playing) "Player Node (Active Pause Button)" else "Player Node (Active Play Button)",
                detectedSpeed = detectedSpeed,
                timecode = currentTimecode
            )
        }

        // Heuristic 2: Check Timecode advancement
        val now = SystemClock.uptimeMillis()
        if (!currentTimecode.isNullOrBlank()) {
            if (previousTimecode != null && previousTimecode != currentTimecode) {
                lastTimecodeChangeTime = now
                previousTimecode = currentTimecode
                return PlaybackStatusResult(
                    isPlaying = true,
                    confidence = 0.90f,
                    source = "Timecode Advancing ($currentTimecode)",
                    detectedSpeed = detectedSpeed,
                    timecode = currentTimecode
                )
            }
            previousTimecode = currentTimecode
        }

        // Heuristic 3: Check System AudioManager media audio stream activity
        val isMusicActive = audioManager?.isMusicActive == true
        if (isMusicActive) {
            return PlaybackStatusResult(
                isPlaying = true,
                confidence = 0.85f,
                source = "Active Audio Stream (Hardware Media Channel)",
                detectedSpeed = detectedSpeed,
                timecode = currentTimecode
            )
        }

        return PlaybackStatusResult(
            isPlaying = false,
            confidence = 0.50f,
            source = "Default (Controls Inactive / Standby)",
            detectedSpeed = detectedSpeed,
            timecode = currentTimecode
        )
    }

    /**
     * Executes the requested media action across all available root nodes with precision targeting.
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

        // STEP 1: Speed Selection
        if (actionType == MediaActionType.SPEED_10X || actionType == MediaActionType.SPEED_SET || actionType == MediaActionType.SPEED_TOGGLE) {
            val speedLabel = speedTargetText ?: if (actionType == MediaActionType.SPEED_10X) "10.0x" else "1.0x"
            val speedResult = attemptSpeedSelection(rootNode, speedLabel)
            if (speedResult.clicked && !speedResult.isMenuTriggerOnly) {
                val duration = SystemClock.uptimeMillis() - startTime
                return TraversalDiagnostics(
                    lastScanTimeMs = System.currentTimeMillis(),
                    scanDurationMs = duration,
                    totalNodesVisited = 20,
                    maxDepthReached = 3,
                    matchedAction = actionType,
                    matchedViewId = speedResult.matchedViewId,
                    matchedByHeuristic = speedResult.matchedHeuristic ?: "Direct Speed Selection ($speedLabel)",
                    success = true
                )
            }
        }

        // STEP 2: Direct Resource ID Lookup
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
                // Continue fallback
            }
        }

        // STEP 3: Traverse Hierarchy based on Strategy (BFS or DFS)
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
     * Precision Speed Selection:
     * Searches for exact speed option node and clicks it.
     * Note: Does NOT return clicked=true when only a menu trigger was clicked.
     */
    fun attemptSpeedSelection(root: AccessibilityNodeInfo, targetSpeed: String): NodeActionResult {
        val targetSpeedFloat = normalizeSpeedLabel(targetSpeed) ?: 1.0f
        val exactOption = findExactSpeedOptionNode(listOf(root), targetSpeedFloat)

        if (exactOption != null) {
            val rect = Rect()
            exactOption.getBoundsInScreen(rect)
            val clicked = executeClick(exactOption)
            if (clicked) {
                return NodeActionResult(
                    node = exactOption,
                    clicked = true,
                    bounds = rect,
                    matchedViewId = exactOption.viewIdResourceName,
                    matchedHeuristic = "Exact Speed Option Match ($targetSpeed)",
                    isMenuTriggerOnly = false
                )
            }
        }

        // If direct speed option was not visible, check for speed trigger button
        val speedSettingsNode = findSpeedTriggerNode(listOf(root))
        if (speedSettingsNode != null) {
            val rect = Rect()
            speedSettingsNode.getBoundsInScreen(rect)
            return NodeActionResult(
                node = speedSettingsNode,
                clicked = false, // Menu trigger found, but actual speed option is not yet selected!
                bounds = rect,
                matchedViewId = speedSettingsNode.viewIdResourceName,
                matchedHeuristic = "Speed Menu Trigger Found",
                isMenuTriggerOnly = true
            )
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
            MediaActionType.SPEED_TOGGLE, MediaActionType.SPEED_SET -> {
                combined.contains("playback speed") || combined.contains("playback rate") ||
                        (combined.contains("speed") && !combined.contains("time"))
            }
            MediaActionType.SPEED_10X -> {
                combined.contains("10x") || combined.contains("10.0x") || combined.contains("turbo")
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

        // Check parents up to 5 levels
        if (config.checkClickableParents) {
            var parent = node.parent
            var depthCheck = 0
            while (parent != null && depthCheck < 5) {
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

    // =========================================================================
    // FULL ACCESSIBILITY DIAGNOSTIC / DISCOVERY SCANNER (UP TO 2500+ NODES)
    // =========================================================================

    private val discoveryKeywords = listOf(
        "speed", "playback", "rate",
        "1x", "1.0x", "1.25x", "1.5x", "1.75x", "2x", "2.0x", "2.5x", "3x", "4x", "5x", "7.5x", "10x",
        "settings", "more", "options", "overflow",
        "play", "pause"
    )

    /**
     * Captures and analyzes the real Accessibility hierarchy without stopping at small node caps.
     * Allows 2000+ nodes to discover real player IDs, speed menus, and clickable controls.
     */
    fun performDiagnosticDiscoveryScan(
        roots: List<AccessibilityNodeInfo>,
        scanType: com.example.model.DiscoveryScanType = com.example.model.DiscoveryScanType.FULL_PLAYER_SCAN,
        foregroundPkg: String = "",
        foregroundTitle: String = "",
        maxNodesLimit: Int = 2500
    ): com.example.model.DiscoverySnapshot {
        val startTime = System.currentTimeMillis()
        val allNodes = mutableListOf<com.example.model.DiscoveredNodeDetail>()
        val speedCandidateNodes = mutableListOf<com.example.model.DiscoveredNodeDetail>()
        val playPauseCandidateNodes = mutableListOf<com.example.model.DiscoveredNodeDetail>()
        val settingsCandidateNodes = mutableListOf<com.example.model.DiscoveredNodeDetail>()
        val discoveredSpeedOptions = mutableListOf<com.example.model.DiscoveredSpeedOption>()
        val treeDumpBuilder = StringBuilder()
        val rect = Rect()
        var maxDepthReached = 0

        fun processNode(
            node: AccessibilityNodeInfo,
            depth: Int,
            parentInfo: AccessibilityNodeInfo?,
            rootIndex: Int
        ) {
            if (allNodes.size >= maxNodesLimit) return
            if (depth > maxDepthReached) maxDepthReached = depth

            try {
                node.getBoundsInScreen(rect)
                val boundsStr = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"
                val classNameStr = node.className?.toString().orEmpty()
                val idStr = node.viewIdResourceName
                val textStr = node.text?.toString()
                val descStr = node.contentDescription?.toString()
                val clickable = node.isClickable
                val visible = node.isVisibleToUser
                val enabled = node.isEnabled
                val selected = node.isSelected
                val checked = node.isChecked
                val childCount = node.childCount

                var parentId: String? = null
                var parentText: String? = null
                var parentClass: String? = null
                var parentClickable = false

                if (parentInfo != null) {
                    parentId = parentInfo.viewIdResourceName
                    parentText = parentInfo.text?.toString()
                    parentClass = parentInfo.className?.toString()
                    parentClickable = parentInfo.isClickable
                } else if (node.parent != null) {
                    try {
                        val p = node.parent
                        parentId = p.viewIdResourceName
                        parentText = p.text?.toString()
                        parentClass = p.className?.toString()
                        parentClickable = p.isClickable
                    } catch (_: Exception) {}
                }

                // Check keywords
                val combinedText = "${idStr.orEmpty()} ${descStr.orEmpty()} ${textStr.orEmpty()} $classNameStr".lowercase(Locale.US)
                val matched = mutableListOf<String>()
                for (kw in discoveryKeywords) {
                    if (combinedText.contains(kw)) {
                        matched.add(kw)
                    }
                }

                // Check speed option representation
                val speedFromText = extractSpeedFromOptionText(textStr)
                val speedFromDesc = extractSpeedFromOptionText(descStr)
                val detectedSpeed = speedFromText ?: speedFromDesc

                val isSpeed = combinedText.contains("speed") || combinedText.contains("playback") ||
                        combinedText.contains("rate") || detectedSpeed != null ||
                        matched.any { it.contains("x") }

                val isPlayPause = (combinedText.contains("play") || combinedText.contains("pause") || combinedText.contains("resume")) &&
                        !combinedText.contains("playback speed") && !combinedText.contains("playback rate")

                val isSettings = combinedText.contains("settings") || combinedText.contains("more options") ||
                        combinedText.contains("overflow") || combinedText.contains("quality") || combinedText.contains("menu")

                val rawLog = "ID: ${idStr ?: "<no_id>"} | TEXT: \"${textStr ?: ""}\" | DESC: \"${descStr ?: ""}\" | CLASS: $classNameStr | CLICKABLE: $clickable | VISIBLE: $visible | SELECTED: $selected | CHECKED: $checked | BOUNDS: $boundsStr | PARENT_ID: ${parentId ?: "<none>"} | PARENT_TEXT: \"${parentText ?: ""}\""

                val nodeDetail = com.example.model.DiscoveredNodeDetail(
                    id = "root_${rootIndex}_node_${allNodes.size}",
                    className = classNameStr,
                    viewIdResourceName = idStr,
                    text = textStr,
                    contentDescription = descStr,
                    isClickable = clickable,
                    isVisibleToUser = visible,
                    isEnabled = enabled,
                    isSelected = selected,
                    isChecked = checked,
                    bounds = boundsStr,
                    depth = depth,
                    childCount = childCount,
                    parentId = parentId,
                    parentText = parentText,
                    parentClass = parentClass,
                    parentClickable = parentClickable,
                    matchedKeywords = matched,
                    isSpeedCandidate = isSpeed,
                    isPlayPauseCandidate = isPlayPause,
                    isSettingsCandidate = isSettings,
                    detectedSpeedValue = detectedSpeed,
                    rawLogText = rawLog
                )

                allNodes.add(nodeDetail)

                if (isSpeed) speedCandidateNodes.add(nodeDetail)
                if (isPlayPause) playPauseCandidateNodes.add(nodeDetail)
                if (isSettings) settingsCandidateNodes.add(nodeDetail)

                // Track speed options
                if (detectedSpeed != null) {
                    val label = String.format(Locale.US, "%.2fx", detectedSpeed).replace(".00x", ".0x")
                    discoveredSpeedOptions.add(
                        com.example.model.DiscoveredSpeedOption(
                            speedLabel = label,
                            speedFloat = detectedSpeed,
                            viewId = idStr,
                            text = textStr,
                            contentDescription = descStr,
                            className = classNameStr,
                            isClickable = clickable,
                            isSelected = selected,
                            isChecked = checked,
                            bounds = boundsStr,
                            parentViewId = parentId,
                            parentText = parentText,
                            parentClass = parentClass,
                            parentClickable = parentClickable
                        )
                    )
                }

                // Append to hierarchy tree string with depth indentation
                val indent = "  ".repeat(depth)
                val nodeLabel = buildString {
                    append(indent)
                    append("├─ [")
                    append(classNameStr.substringAfterLast('.'))
                    append("]")
                    if (!idStr.isNullOrBlank()) append(" id=\"$idStr\"")
                    if (!textStr.isNullOrBlank()) append(" text=\"$textStr\"")
                    if (!descStr.isNullOrBlank()) append(" desc=\"$descStr\"")
                    if (clickable) append(" [CLICKABLE]")
                    if (selected) append(" [SELECTED]")
                    if (checked) append(" [CHECKED]")
                    append(" $boundsStr")
                }
                treeDumpBuilder.appendLine(nodeLabel)

                // Recurse children
                for (i in 0 until childCount) {
                    try {
                        val child = node.getChild(i)
                        if (child != null) {
                            processNode(child, depth + 1, node, rootIndex)
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerDiscovery", "Error processing node at depth $depth", e)
            }
        }

        // Process all roots
        for ((rootIdx, root) in roots.withIndex()) {
            treeDumpBuilder.appendLine("=== WINDOW ROOT #$rootIdx (${root.packageName}) ===")
            processNode(root, 0, null, rootIdx)
        }

        // Build human-readable formatted report
        val formattedReport = buildString {
            appendLine("==================================================")
            appendLine("ACCESSIBILITY PLAYER DISCOVERY REPORT")
            appendLine("Scan Type: ${scanType.displayName}")
            appendLine("Foreground Package: $foregroundPkg ($foregroundTitle)")
            appendLine("Total Nodes Visited: ${allNodes.size} (Limit: $maxNodesLimit)")
            appendLine("Max Tree Depth: $maxDepthReached")
            appendLine("Active Window Roots: ${roots.size}")
            appendLine("Scan Time: ${System.currentTimeMillis() - startTime}ms")
            appendLine("==================================================")
            appendLine()

            appendLine("--- DISCOVERED PLAYBACK SPEED OPTIONS (${discoveredSpeedOptions.size}) ---")
            if (discoveredSpeedOptions.isEmpty()) {
                appendLine("No direct speed options found. (Open speed menu in target app and scan again)")
            } else {
                discoveredSpeedOptions.forEachIndexed { i, opt ->
                    appendLine("[Option #${i + 1}] SPEED: ${opt.speedLabel}")
                    appendLine("  ID:           ${opt.viewId ?: "<no_id>"}")
                    appendLine("  TEXT:         \"${opt.text ?: ""}\"")
                    appendLine("  DESCRIPTION:  \"${opt.contentDescription ?: ""}\"")
                    appendLine("  CLASS:        ${opt.className}")
                    appendLine("  CLICKABLE:    ${opt.isClickable} (Parent clickable: ${opt.parentClickable})")
                    appendLine("  SELECTED:     ${opt.isSelected} | CHECKED: ${opt.isChecked}")
                    appendLine("  BOUNDS:       ${opt.bounds}")
                    appendLine("  PARENT ID:    ${opt.parentViewId ?: "<none>"}")
                    appendLine("  PARENT TEXT:  \"${opt.parentText ?: ""}\"")
                    appendLine("  PARENT CLASS: ${opt.parentClass ?: "<none>"}")
                    appendLine()
                }
            }

            appendLine("--- SPEED / RATE CANDIDATE NODES (${speedCandidateNodes.size}) ---")
            if (speedCandidateNodes.isEmpty()) {
                appendLine("No speed-related keyword nodes found.")
            } else {
                speedCandidateNodes.forEachIndexed { i, node ->
                    appendLine("[Candidate #${i + 1}]")
                    appendLine("  ID:          ${node.viewIdResourceName ?: "<no_id>"}")
                    appendLine("  TEXT:        \"${node.text ?: ""}\"")
                    appendLine("  DESCRIPTION: \"${node.contentDescription ?: ""}\"")
                    appendLine("  CLASS:       ${node.className}")
                    appendLine("  CLICKABLE:   ${node.isClickable} (Parent clickable: ${node.parentClickable})")
                    appendLine("  VISIBLE:     ${node.isVisibleToUser}")
                    appendLine("  SELECTED:    ${node.isSelected} | CHECKED: ${node.isChecked}")
                    appendLine("  BOUNDS:      ${node.bounds}")
                    appendLine("  PARENT ID:   ${node.parentId ?: "<none>"}")
                    appendLine("  PARENT TEXT: \"${node.parentText ?: ""}\"")
                    appendLine()
                }
            }

            appendLine("--- PLAY / PAUSE CANDIDATE NODES (${playPauseCandidateNodes.size}) ---")
            if (playPauseCandidateNodes.isEmpty()) {
                appendLine("No play/pause keyword nodes found.")
            } else {
                playPauseCandidateNodes.forEachIndexed { i, node ->
                    appendLine("[Media #${i + 1}] ID: ${node.viewIdResourceName ?: "<no_id>"} | TEXT: \"${node.text ?: ""}\" | DESC: \"${node.contentDescription ?: ""}\" | CLICKABLE: ${node.isClickable} | BOUNDS: ${node.bounds}")
                }
                appendLine()
            }

            appendLine("--- SETTINGS / OVERFLOW CANDIDATE NODES (${settingsCandidateNodes.size}) ---")
            if (settingsCandidateNodes.isEmpty()) {
                appendLine("No settings/more/overflow keyword nodes found.")
            } else {
                settingsCandidateNodes.forEachIndexed { i, node ->
                    appendLine("[Setting #${i + 1}] ID: ${node.viewIdResourceName ?: "<no_id>"} | DESC: \"${node.contentDescription ?: ""}\" | CLICKABLE: ${node.isClickable} | BOUNDS: ${node.bounds}")
                }
                appendLine()
            }

            appendLine("==================================================")
            appendLine("FULL NODE TREE HIERARCHY")
            appendLine("==================================================")
            append(treeDumpBuilder.toString())
        }

        // Output to Logcat
        Log.i("VideoPlayerDiscovery", "====== DISCOVERY SCAN COMPLETE: ${allNodes.size} nodes, ${discoveredSpeedOptions.size} speed options ======")
        Log.i("VideoPlayerDiscovery", formattedReport)

        return com.example.model.DiscoverySnapshot(
            scanType = scanType,
            foregroundPackage = foregroundPkg,
            foregroundAppTitle = foregroundTitle,
            totalNodesCaptured = allNodes.size,
            maxDepth = maxDepthReached,
            speedCandidateNodes = speedCandidateNodes,
            playPauseCandidateNodes = playPauseCandidateNodes,
            settingsCandidateNodes = settingsCandidateNodes,
            allDiscoveredSpeeds = discoveredSpeedOptions,
            allNodes = allNodes,
            formattedReport = formattedReport,
            rawHierarchyTreeDump = treeDumpBuilder.toString()
        )
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
            combined.contains("playback speed") || combined.contains("playback rate") -> MediaActionType.SPEED_TOGGLE
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
