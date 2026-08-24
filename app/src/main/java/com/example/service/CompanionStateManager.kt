package com.example.service

import com.example.model.MediaActionType
import com.example.model.NodeInfoSummary
import com.example.model.SimulatedNode
import com.example.model.TargetAppConfig
import com.example.model.TraversalDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class CompanionUiState(
    val isAccessibilityConnected: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val isOverlayServiceRunning: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackStatusSource: String = "Idle",
    val detectedTimecode: String? = null,
    val detectedSpeed: String? = null,
    val currentPlaybackSpeed: String = "1.0x",
    val isTurbo10xActive: Boolean = false,
    val foregroundPackage: String = "None",
    val foregroundAppTitle: String = "None",
    val isTargetAppActive: Boolean = false,
    val targetApps: List<TargetAppConfig> = TargetAppConfig.PRESET_APPS,
    val selectedAppForTest: TargetAppConfig = TargetAppConfig.PRESET_APPS.first(),
    val lastDiagnostics: TraversalDiagnostics = TraversalDiagnostics(),
    val liveNodeTree: List<NodeInfoSummary> = emptyList(),
    val extractedCaptions: String = "",
    val focusTimerSeconds: Int = 0,
    val isFocusTimerRunning: Boolean = false,
    val totalActionsPerformed: Int = 0,
    val autoSkipEnabled: Boolean = false,
    val autoSkipDelaySeconds: Int = 5,
    val optimizationConfig: OptimizationConfig = OptimizationConfig()
)

object CompanionStateManager {
    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    var accessibilityService: StudyCompanionAccessibilityService? = null
    var floatingOverlayService: FloatingOverlayService? = null

    fun updateAccessibilityConnection(connected: Boolean) {
        _uiState.value = _uiState.value.copy(isAccessibilityConnected = connected)
    }

    fun updateOverlayPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(isOverlayPermissionGranted = granted)
    }

    fun updateOverlayServiceRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(isOverlayServiceRunning = running)
    }

    fun updateForegroundPackage(pkg: String, appName: String? = null) {
        val targetApps = _uiState.value.targetApps
        val matchingApp = targetApps.find { it.packageName == pkg && it.isEnabled }
        val isTarget = matchingApp != null
        val title = appName ?: matchingApp?.displayName ?: pkg

        _uiState.value = _uiState.value.copy(
            foregroundPackage = pkg,
            foregroundAppTitle = title,
            isTargetAppActive = isTarget
        )
    }

    fun updateDetectedPlaybackStatus(
        isPlaying: Boolean,
        source: String,
        detectedSpeed: String? = null,
        timecode: String? = null
    ) {
        _uiState.value = _uiState.value.copy(
            isPlaying = isPlaying,
            playbackStatusSource = source,
            detectedSpeed = detectedSpeed ?: _uiState.value.detectedSpeed,
            detectedTimecode = timecode ?: _uiState.value.detectedTimecode
        )
    }

    fun updateDiagnostics(diag: TraversalDiagnostics) {
        val currentCount = _uiState.value.totalActionsPerformed
        _uiState.value = _uiState.value.copy(
            lastDiagnostics = diag,
            totalActionsPerformed = if (diag.success) currentCount + 1 else currentCount
        )
    }

    fun updateLiveNodeTree(nodes: List<NodeInfoSummary>) {
        _uiState.value = _uiState.value.copy(liveNodeTree = nodes)
    }

    fun updateExtractedCaptions(text: String) {
        _uiState.value = _uiState.value.copy(extractedCaptions = text)
    }

    fun updateOptimizationConfig(config: OptimizationConfig) {
        _uiState.value = _uiState.value.copy(optimizationConfig = config)
        accessibilityService?.optimizer?.config = config
    }

    fun toggleTargetApp(packageName: String, enabled: Boolean) {
        val updated = _uiState.value.targetApps.map {
            if (it.packageName == packageName) it.copy(isEnabled = enabled) else it
        }
        _uiState.value = _uiState.value.copy(targetApps = updated)
    }

    fun toggleAutoLaunch(packageName: String, autoLaunch: Boolean) {
        val updated = _uiState.value.targetApps.map {
            if (it.packageName == packageName) it.copy(autoLaunchOverlay = autoLaunch) else it
        }
        _uiState.value = _uiState.value.copy(targetApps = updated)
    }

    fun addCustomTargetApp(app: TargetAppConfig) {
        val current = _uiState.value.targetApps.toMutableList()
        current.removeAll { it.packageName == app.packageName }
        current.add(0, app)
        _uiState.value = _uiState.value.copy(targetApps = current)
    }

    fun removeTargetApp(packageName: String) {
        val current = _uiState.value.targetApps.filterNot { it.packageName == packageName }
        _uiState.value = _uiState.value.copy(targetApps = current)
    }

    fun updateAppConfig(updatedApp: TargetAppConfig) {
        val current = _uiState.value.targetApps.map {
            if (it.packageName == updatedApp.packageName) updatedApp else it
        }
        _uiState.value = _uiState.value.copy(targetApps = current)
    }

    fun selectAppForTest(app: TargetAppConfig) {
        _uiState.value = _uiState.value.copy(selectedAppForTest = app)
    }

    fun updateFocusTimer(seconds: Int, isRunning: Boolean) {
        _uiState.value = _uiState.value.copy(
            focusTimerSeconds = seconds,
            isFocusTimerRunning = isRunning
        )
    }

    fun setPlaybackSpeed(speed: String) {
        val is10x = speed == "10x" || speed == "10.0x"
        _uiState.value = _uiState.value.copy(
            currentPlaybackSpeed = speed,
            isTurbo10xActive = is10x
        )
        if (is10x) {
            triggerMediaAction(MediaActionType.SPEED_10X, "10.0x")
        } else {
            triggerMediaAction(MediaActionType.SPEED_SET, speed)
        }
    }

    fun setTurbo10xActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isTurbo10xActive = active,
            currentPlaybackSpeed = if (active) "10.0x" else _uiState.value.currentPlaybackSpeed
        )
        if (active) {
            triggerMediaAction(MediaActionType.SPEED_10X, "10.0x")
        }
    }

    fun togglePlayPause() {
        triggerMediaAction(MediaActionType.PLAY_PAUSE)
    }

    fun triggerMediaAction(actionType: MediaActionType, param: String? = null) {
        if (actionType == MediaActionType.PLAY_PAUSE) {
            _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying)
        } else if (actionType == MediaActionType.PLAY) {
            _uiState.value = _uiState.value.copy(isPlaying = true)
        } else if (actionType == MediaActionType.PAUSE) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }

        val service = accessibilityService
        if (service != null && service.isServiceBound) {
            val app = _uiState.value.targetApps.find { it.packageName == _uiState.value.foregroundPackage }
            val customIds = when (actionType) {
                MediaActionType.PLAY_PAUSE, MediaActionType.PLAY, MediaActionType.PAUSE -> app?.customPlayPauseIds.orEmpty()
                MediaActionType.FAST_FORWARD -> app?.customForwardIds.orEmpty()
                MediaActionType.REWIND -> app?.customRewindIds.orEmpty()
                MediaActionType.NEXT -> app?.customNextIds.orEmpty()
                MediaActionType.SPEED_TOGGLE, MediaActionType.SPEED_10X, MediaActionType.SPEED_SET -> app?.customSpeedIds.orEmpty()
                MediaActionType.CAPTIONS -> app?.customCaptionsIds.orEmpty()
                else -> emptyList()
            }
            service.triggerAction(actionType, customIds, param)
        } else {
            simulateMediaAction(actionType, param)
        }
    }

    fun simulateMediaAction(actionType: MediaActionType, param: String? = null) {
        val mockTree = createMockDikshaHierarchy()
        val startTime = System.currentTimeMillis()

        val flattened = mutableListOf<SimulatedNode>()
        fun flatten(n: SimulatedNode) {
            flattened.add(n)
            n.children.forEach { flatten(it) }
        }
        flatten(mockTree)

        val optimizer = AccessibilityNodeOptimizer()
        val targetSpeedFloat = param?.let { optimizer.normalizeSpeedLabel(it) } ?: when (actionType) {
            MediaActionType.SPEED_10X -> 10.0f
            else -> null
        }

        val matchedNode = flattened.find {
            val id = it.viewIdResourceName.orEmpty().lowercase(Locale.US)
            val desc = it.contentDescription.orEmpty().lowercase(Locale.US)
            val text = it.text.orEmpty().lowercase(Locale.US)
            val str = "$id $desc $text"

            when (actionType) {
                MediaActionType.PLAY_PAUSE, MediaActionType.PLAY -> str.contains("play")
                MediaActionType.PAUSE -> str.contains("pause")
                MediaActionType.FAST_FORWARD -> str.contains("forward") || str.contains("ffwd") || str.contains("10")
                MediaActionType.REWIND -> str.contains("rewind") || str.contains("rew")
                MediaActionType.NEXT -> str.contains("next")
                MediaActionType.SPEED_10X, MediaActionType.SPEED_SET, MediaActionType.SPEED_TOGGLE -> {
                    if (targetSpeedFloat != null) {
                        optimizer.matchesRequestedSpeed(it.text, it.contentDescription, targetSpeedFloat) ||
                                str.contains("btn_playback_speed") || str.contains("speed")
                    } else {
                        str.contains("speed") || str.contains("10x")
                    }
                }
                MediaActionType.CAPTIONS -> str.contains("cc") || str.contains("caption") || str.contains("subtitles")
                else -> false
            }
        }

        val diag = TraversalDiagnostics(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = System.currentTimeMillis() - startTime + 8,
            totalNodesVisited = flattened.size,
            maxDepthReached = 4,
            matchedAction = actionType,
            matchedViewId = matchedNode?.viewIdResourceName ?: "in.gov.diksha.app:id/btn_playback_speed",
            matchedByHeuristic = when (actionType) {
                MediaActionType.SPEED_10X -> "10x Turbo Speed Selected (10.0x)"
                MediaActionType.SPEED_SET -> "Speed Option Applied (${param ?: "1.0x"})"
                else -> "Simulated Match (${matchedNode?.className ?: "ImageButton"})"
            },
            success = true,
            currentForegroundPackage = _uiState.value.selectedAppForTest.packageName
        )

        updateDiagnostics(diag)
        val summaryList = flattened.mapIndexed { idx, it -> it.toSummary(depth = idx % 4) }
        updateLiveNodeTree(summaryList)
    }

    fun createMockDikshaHierarchy(): SimulatedNode {
        return SimulatedNode(
            id = "root_diksha",
            className = "android.widget.FrameLayout",
            viewIdResourceName = "in.gov.diksha.app:id/main_content",
            children = listOf(
                SimulatedNode(
                    id = "player_container",
                    className = "android.widget.RelativeLayout",
                    viewIdResourceName = "in.gov.diksha.app:id/player_view_container",
                    children = listOf(
                        SimulatedNode(
                            id = "exo_subtitles",
                            className = "android.widget.TextView",
                            viewIdResourceName = "in.gov.diksha.app:id/exo_subtitles",
                            text = "Chapter 4: Principles of Electricity and Magnetism.",
                            isClickable = false
                        ),
                        SimulatedNode(
                            id = "exo_controls",
                            className = "android.widget.LinearLayout",
                            viewIdResourceName = "in.gov.diksha.app:id/exo_controls",
                            children = listOf(
                                SimulatedNode(
                                    id = "btn_rew",
                                    className = "android.widget.ImageButton",
                                    viewIdResourceName = "in.gov.diksha.app:id/exo_rew",
                                    contentDescription = "Rewind 10 seconds"
                                ),
                                SimulatedNode(
                                    id = "btn_play_pause",
                                    className = "android.widget.ImageButton",
                                    viewIdResourceName = "in.gov.diksha.app:id/exo_play",
                                    contentDescription = "Play video"
                                ),
                                SimulatedNode(
                                    id = "btn_ffwd",
                                    className = "android.widget.ImageButton",
                                    viewIdResourceName = "in.gov.diksha.app:id/exo_ffwd",
                                    contentDescription = "Fast forward 10 seconds"
                                ),
                                SimulatedNode(
                                    id = "btn_speed",
                                    className = "android.widget.Button",
                                    viewIdResourceName = "in.gov.diksha.app:id/btn_playback_speed",
                                    text = "1.25x",
                                    contentDescription = "Playback speed toggle"
                                ),
                                SimulatedNode(
                                    id = "btn_next",
                                    className = "android.widget.ImageButton",
                                    viewIdResourceName = "in.gov.diksha.app:id/btn_next",
                                    contentDescription = "Next Lesson"
                                )
                            )
                        )
                    )
                ),
                // Simulated Speed Selection Dialog / Bottom Sheet
                SimulatedNode(
                    id = "speed_dialog",
                    className = "android.widget.LinearLayout",
                    viewIdResourceName = "in.gov.diksha.app:id/speed_options_container",
                    children = listOf(
                        SimulatedNode(id = "sp_1_0", className = "android.widget.TextView", text = "1.0x", contentDescription = "Normal Speed"),
                        SimulatedNode(id = "sp_1_25", className = "android.widget.TextView", text = "1.25x", contentDescription = "1.25x Speed"),
                        SimulatedNode(id = "sp_1_5", className = "android.widget.TextView", text = "1.5x", contentDescription = "1.5x Speed"),
                        SimulatedNode(id = "sp_1_75", className = "android.widget.TextView", text = "1.75x", contentDescription = "1.75x Speed"),
                        SimulatedNode(id = "sp_2_0", className = "android.widget.TextView", text = "2.0x", contentDescription = "2.0x Speed"),
                        SimulatedNode(id = "sp_2_5", className = "android.widget.TextView", text = "2.5x", contentDescription = "2.5x Speed"),
                        SimulatedNode(id = "sp_3_0", className = "android.widget.TextView", text = "3.0x", contentDescription = "3.0x Speed"),
                        SimulatedNode(id = "sp_4_0", className = "android.widget.TextView", text = "4.0x", contentDescription = "4.0x Speed"),
                        SimulatedNode(id = "sp_5_0", className = "android.widget.TextView", text = "5.0x", contentDescription = "5.0x Speed"),
                        SimulatedNode(id = "sp_7_5", className = "android.widget.TextView", text = "7.5x", contentDescription = "7.5x Speed"),
                        SimulatedNode(id = "sp_10_0", className = "android.widget.TextView", text = "10.0x", contentDescription = "10.0x Turbo Speed")
                    )
                ),
                SimulatedNode(
                    id = "lesson_metadata",
                    className = "android.widget.ScrollView",
                    children = listOf(
                        SimulatedNode(
                            id = "lesson_title",
                            className = "android.widget.TextView",
                            text = "Grade 10 Science - Module 3: Electromagnetic Induction",
                            isClickable = false
                        )
                    )
                )
            )
        )
    }
}
