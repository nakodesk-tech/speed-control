package com.example.service

import com.example.model.MediaActionType
import com.example.model.NodeInfoSummary
import com.example.model.SimulatedNode
import com.example.model.TargetAppConfig
import com.example.model.TraversalDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompanionUiState(
    val isAccessibilityConnected: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val isOverlayServiceRunning: Boolean = false,
    val isPlaying: Boolean = false,
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
        _uiState.value = _uiState.value.copy(
            currentPlaybackSpeed = speed,
            isTurbo10xActive = speed == "10x" || speed == "10.0x"
        )
        if (speed == "10x" || speed == "10.0x") {
            triggerMediaAction(MediaActionType.SPEED_10X, "10x")
        } else {
            triggerMediaAction(MediaActionType.SPEED_SET, speed)
        }
    }

    fun setTurbo10xActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isTurbo10xActive = active,
            currentPlaybackSpeed = if (active) "10x" else _uiState.value.currentPlaybackSpeed
        )
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

        val matchedNode = flattened.find {
            val str = "${it.viewIdResourceName} ${it.contentDescription} ${it.text}".lowercase()
            when (actionType) {
                MediaActionType.PLAY_PAUSE, MediaActionType.PLAY -> str.contains("play")
                MediaActionType.PAUSE -> str.contains("pause")
                MediaActionType.FAST_FORWARD -> str.contains("forward") || str.contains("ffwd") || str.contains("10")
                MediaActionType.REWIND -> str.contains("rewind") || str.contains("rew")
                MediaActionType.NEXT -> str.contains("next")
                MediaActionType.SPEED_10X -> str.contains("speed") || str.contains("10x")
                MediaActionType.SPEED_TOGGLE, MediaActionType.SPEED_SET -> str.contains("speed")
                MediaActionType.CAPTIONS -> str.contains("cc") || str.contains("caption")
                else -> false
            }
        }

        val diag = TraversalDiagnostics(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = System.currentTimeMillis() - startTime + 8,
            totalNodesVisited = flattened.size,
            maxDepthReached = 4,
            matchedAction = actionType,
            matchedViewId = matchedNode?.viewIdResourceName ?: "simulated:id/exo_play",
            matchedByHeuristic = if (actionType == MediaActionType.SPEED_10X) "10x Turbo Speed Activated" else "Simulated Match (${matchedNode?.className ?: "ImageButton"})",
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
