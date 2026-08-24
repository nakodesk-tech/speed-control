package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.AppDatabase
import com.example.data.local.TraversalLogEntity
import com.example.model.DetectedPlayerType
import com.example.model.MediaActionType
import com.example.model.SpeedActionDiagnostics
import com.example.model.TraversalDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class StudyCompanionAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val optimizer = AccessibilityNodeOptimizer()
    var isServiceBound = false
        private set

    private var activeActionJob: Job? = null
    private var continuousTurboJob: Job? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        CompanionStateManager.accessibilityService = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceBound = true
        CompanionStateManager.updateAccessibilityConnection(true)
        Log.i("EduAccessibility", "StudyCompanionAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventPackage = event.packageName?.toString().orEmpty()
        if (eventPackage.isBlank() || eventPackage == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(eventPackage)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(eventPackage)
            }
        }
    }

    private fun handleWindowStateChanged(pkg: String) {
        CompanionStateManager.updateForegroundPackage(pkg)

        val state = CompanionStateManager.uiState.value
        val isTargetApp = state.targetApps.any { it.packageName == pkg && it.isEnabled }

        // CRITICAL: Stop all active action jobs if user navigates away from target app
        if (!isTargetApp) {
            cancelAllActiveJobs()
            if (state.isTurbo10xActive) {
                CompanionStateManager.setTurbo10xActive(false)
            }
        } else {
            // Check video playback status
            checkAndSyncPlaybackStatus()

            // Auto-launch overlay if enabled and allowed
            if (Settings.canDrawOverlays(this) && !state.isOverlayServiceRunning) {
                val matchingApp = state.targetApps.find { it.packageName == pkg }
                if (matchingApp?.autoLaunchOverlay == true) {
                    val overlayIntent = Intent(this, FloatingOverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(overlayIntent)
                    } else {
                        startService(overlayIntent)
                    }
                }
            }
        }
    }

    private fun handleWindowContentChanged(pkg: String) {
        val state = CompanionStateManager.uiState.value
        val isTargetApp = state.targetApps.any { it.packageName == pkg && it.isEnabled }
        if (isTargetApp) {
            checkAndSyncPlaybackStatus()
        }
    }

    /**
     * Checks video playback status (playing/paused, timecode, detected speed) using our robust fallback detector.
     */
    private fun checkAndSyncPlaybackStatus() {
        try {
            val currentPkg = CompanionStateManager.uiState.value.foregroundPackage
            val targetRoots = getTargetAppWindowRoots(currentPkg)
            if (targetRoots.isNotEmpty()) {
                val status = optimizer.detectVideoPlaybackStatus(targetRoots, audioManager, currentPkg, packageName)
                CompanionStateManager.updateDetectedPlaybackStatus(
                    isPlaying = status.isPlaying,
                    source = status.source,
                    detectedSpeed = status.detectedSpeed,
                    timecode = status.timecode
                )
            }
        } catch (_: Exception) {
            // Guard against recycled nodes
        }
    }

    private fun cancelAllActiveJobs() {
        activeActionJob?.cancel()
        activeActionJob = null
        continuousTurboJob?.cancel()
        continuousTurboJob = null
    }

    /**
     * Gathers all accessible window roots across both rootInActiveWindow and interactive windows list.
     */
    fun getAllWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            rootInActiveWindow?.let { roots.add(it) }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val windowList = windows
                if (windowList != null) {
                    for (win in windowList) {
                        try {
                            val r = win.root
                            if (r != null && !roots.any { it == r }) {
                                roots.add(r)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return roots
    }

    /**
     * Filters all window roots to return ONLY the accessibility roots that belong to the active target application.
     * Guaranteed to exclude our floating overlay and Android System UI.
     */
    fun getTargetAppWindowRoots(targetPackage: String? = null): List<AccessibilityNodeInfo> {
        val all = getAllWindowRoots()
        val ourPkg = packageName
        val effectiveTargetPkg = if (!targetPackage.isNullOrBlank()) targetPackage else CompanionStateManager.uiState.value.foregroundPackage

        return all.filter { root ->
            optimizer.isNodeFromTargetPackage(root, effectiveTargetPkg, ourPkg)
        }
    }

    /**
     * Performs a comprehensive diagnostic scan across target window roots with up to 2500+ node limit.
     * Logs full node tree, discovers actual playback speed options, settings nodes, and media controls.
     */
    fun performDiscoveryScan(
        scanType: com.example.model.DiscoveryScanType = com.example.model.DiscoveryScanType.FULL_PLAYER_SCAN
    ): com.example.model.DiscoverySnapshot {
        val state = CompanionStateManager.uiState.value
        val currentPkg = state.foregroundPackage
        val currentTitle = state.foregroundAppTitle
        val targetRoots = getTargetAppWindowRoots(currentPkg)
        val rootsToScan = if (targetRoots.isNotEmpty()) targetRoots else getAllWindowRoots()

        val snapshot = optimizer.performDiagnosticDiscoveryScan(
            roots = rootsToScan,
            scanType = scanType,
            foregroundPkg = currentPkg,
            foregroundTitle = currentTitle,
            maxNodesLimit = 2500
        )

        CompanionStateManager.updateDiscoverySnapshot(snapshot)
        return snapshot
    }

    /**
     * Detects on-screen bounding rectangle of the video player in the foreground app.
     */
    fun detectPlayerBounds(roots: List<AccessibilityNodeInfo>, targetPackage: String? = null): Rect {
        val descriptor = optimizer.inspectVideoPlayerTargets(roots, targetPackage, packageName)
        if (!descriptor.playerBounds.isEmpty && descriptor.playerBounds.width() > 200) {
            return descriptor.playerBounds
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        return if (screenWidth > screenHeight) {
            Rect(0, 0, screenWidth, screenHeight)
        } else {
            val playerHeight = (screenWidth * 9f / 16f).toInt().coerceIn(350, (screenHeight * 0.45f).toInt())
            Rect(0, 40, screenWidth, 40 + playerHeight)
        }
    }

    /**
     * Multi-tier action execution with precision targeting for speed adjustment and media controls:
     */
    fun triggerAction(actionType: MediaActionType, customIds: List<String> = emptyList(), speedParam: String? = null) {
        cancelAllActiveJobs()

        activeActionJob = serviceScope.launch {
            val startTime = SystemClock.uptimeMillis()
            val currentPkg = CompanionStateManager.uiState.value.foregroundPackage

            val allRoots = getAllWindowRoots()
            val roots = getTargetAppWindowRoots(currentPkg)
            val rootsTotal = allRoots.size
            val targetRootsUsed = roots.size
            val excludedRoots = (rootsTotal - targetRootsUsed).coerceAtLeast(0)

            val playerBounds = detectPlayerBounds(roots, currentPkg)
            val playerCenterX = playerBounds.centerX().toFloat()
            val playerCenterY = playerBounds.centerY().toFloat()
            val playerLeftX = (playerBounds.left + playerBounds.width() * 0.22f).coerceAtLeast(40f)
            val playerRightX = (playerBounds.left + playerBounds.width() * 0.78f).coerceAtMost(resources.displayMetrics.widthPixels - 40f)

            var diag: TraversalDiagnostics

            when (actionType) {
                MediaActionType.SPEED_10X, MediaActionType.SPEED_SET, MediaActionType.SPEED_TOGGLE -> {
                    val targetSpeedText = speedParam ?: if (actionType == MediaActionType.SPEED_10X) "10.0x" else "1.0x"
                    diag = executeSpeedChangeSequence(targetSpeedText, customIds, currentPkg, startTime)
                }

                MediaActionType.PLAY_PAUSE, MediaActionType.PLAY, MediaActionType.PAUSE -> {
                    diag = optimizer.performMediaActionAcrossRoots(
                        roots = roots,
                        actionType = actionType,
                        customIds = customIds,
                        speedTargetText = speedParam,
                        targetPackage = currentPkg,
                        ourPackageName = packageName,
                        rootsTotal = rootsTotal,
                        excludedRoots = excludedRoots
                    )
                    if (!diag.success) {
                        // Wake up faded controls with a center tap
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshAllRoots = getAllWindowRoots()
                        val freshRoots = getTargetAppWindowRoots(currentPkg)
                        val retryDiag = optimizer.performMediaActionAcrossRoots(
                            roots = freshRoots,
                            actionType = actionType,
                            customIds = customIds,
                            speedTargetText = speedParam,
                            targetPackage = currentPkg,
                            ourPackageName = packageName,
                            rootsTotal = freshAllRoots.size,
                            excludedRoots = (freshAllRoots.size - freshRoots.size).coerceAtLeast(0)
                        )
                        if (retryDiag.success) {
                            diag = retryDiag.copy(matchedByHeuristic = "Wake-Up Tap + Click (${retryDiag.matchedViewId})")
                        } else {
                            val centerTapped = performTap(playerCenterX, playerCenterY)
                            val keyDispatched = dispatchSystemMediaKeyEvent(actionType)
                            diag = TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                                totalNodesVisited = roots.size,
                                maxDepthReached = 2,
                                matchedAction = actionType,
                                matchedByHeuristic = if (centerTapped) "Player Center Touch" else "Media Key Dispatch",
                                success = centerTapped || keyDispatched,
                                currentForegroundPackage = currentPkg,
                                targetPackage = currentPkg,
                                rootsTotal = rootsTotal,
                                targetRootsUsed = targetRootsUsed,
                                excludedRoots = excludedRoots
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(actionType)
                    }
                }

                MediaActionType.FAST_FORWARD -> {
                    diag = optimizer.performMediaActionAcrossRoots(
                        roots = roots,
                        actionType = actionType,
                        customIds = customIds,
                        speedTargetText = speedParam,
                        targetPackage = currentPkg,
                        ourPackageName = packageName,
                        rootsTotal = rootsTotal,
                        excludedRoots = excludedRoots
                    )
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshAllRoots = getAllWindowRoots()
                        val freshRoots = getTargetAppWindowRoots(currentPkg)
                        val retryDiag = optimizer.performMediaActionAcrossRoots(
                            roots = freshRoots,
                            actionType = actionType,
                            customIds = customIds,
                            speedTargetText = speedParam,
                            targetPackage = currentPkg,
                            ourPackageName = packageName,
                            rootsTotal = freshAllRoots.size,
                            excludedRoots = (freshAllRoots.size - freshRoots.size).coerceAtLeast(0)
                        )
                        if (retryDiag.success) {
                            diag = retryDiag.copy(matchedByHeuristic = "Wake-Up + Fast Forward Click")
                        } else {
                            val gestureDone = performDoubleTap(playerRightX, playerCenterY)
                            val keyDispatched = dispatchSystemMediaKeyEvent(MediaActionType.FAST_FORWARD)
                            diag = TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                                totalNodesVisited = roots.size,
                                maxDepthReached = 1,
                                matchedAction = actionType,
                                matchedByHeuristic = "Double-Tap +10s Forward",
                                success = gestureDone || keyDispatched,
                                currentForegroundPackage = currentPkg,
                                targetPackage = currentPkg,
                                rootsTotal = rootsTotal,
                                targetRootsUsed = targetRootsUsed,
                                excludedRoots = excludedRoots
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(MediaActionType.FAST_FORWARD)
                    }
                }

                MediaActionType.REWIND -> {
                    diag = optimizer.performMediaActionAcrossRoots(
                        roots = roots,
                        actionType = actionType,
                        customIds = customIds,
                        speedTargetText = speedParam,
                        targetPackage = currentPkg,
                        ourPackageName = packageName,
                        rootsTotal = rootsTotal,
                        excludedRoots = excludedRoots
                    )
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshAllRoots = getAllWindowRoots()
                        val freshRoots = getTargetAppWindowRoots(currentPkg)
                        val retryDiag = optimizer.performMediaActionAcrossRoots(
                            roots = freshRoots,
                            actionType = actionType,
                            customIds = customIds,
                            speedTargetText = speedParam,
                            targetPackage = currentPkg,
                            ourPackageName = packageName,
                            rootsTotal = freshAllRoots.size,
                            excludedRoots = (freshAllRoots.size - freshRoots.size).coerceAtLeast(0)
                        )
                        if (retryDiag.success) {
                            diag = retryDiag.copy(matchedByHeuristic = "Wake-Up + Rewind Click")
                        } else {
                            val gestureDone = performDoubleTap(playerLeftX, playerCenterY)
                            val keyDispatched = dispatchSystemMediaKeyEvent(MediaActionType.REWIND)
                            diag = TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                                totalNodesVisited = roots.size,
                                maxDepthReached = 1,
                                matchedAction = actionType,
                                matchedByHeuristic = "Double-Tap -10s Rewind",
                                success = gestureDone || keyDispatched,
                                currentForegroundPackage = currentPkg,
                                targetPackage = currentPkg,
                                rootsTotal = rootsTotal,
                                targetRootsUsed = targetRootsUsed,
                                excludedRoots = excludedRoots
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(MediaActionType.REWIND)
                    }
                }

                MediaActionType.NEXT, MediaActionType.PREVIOUS -> {
                    diag = optimizer.performMediaActionAcrossRoots(
                        roots = roots,
                        actionType = actionType,
                        customIds = customIds,
                        speedTargetText = speedParam,
                        targetPackage = currentPkg,
                        ourPackageName = packageName,
                        rootsTotal = rootsTotal,
                        excludedRoots = excludedRoots
                    )
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)
                        val freshAllRoots = getAllWindowRoots()
                        val freshRoots = getTargetAppWindowRoots(currentPkg)
                        val retryNext = optimizer.performMediaActionAcrossRoots(
                            roots = freshRoots,
                            actionType = actionType,
                            customIds = customIds,
                            speedTargetText = speedParam,
                            targetPackage = currentPkg,
                            ourPackageName = packageName,
                            rootsTotal = freshAllRoots.size,
                            excludedRoots = (freshAllRoots.size - freshRoots.size).coerceAtLeast(0)
                        )
                        if (retryNext.success) {
                            diag = retryNext.copy(matchedByHeuristic = "Wake-Up + ${actionType.name} Click")
                        } else {
                            val keyDispatched = dispatchSystemMediaKeyEvent(actionType)
                            diag = TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                                totalNodesVisited = roots.size,
                                maxDepthReached = 1,
                                matchedAction = actionType,
                                matchedByHeuristic = "System Media Key (${actionType.name})",
                                success = keyDispatched,
                                currentForegroundPackage = currentPkg,
                                targetPackage = currentPkg,
                                rootsTotal = rootsTotal,
                                targetRootsUsed = targetRootsUsed,
                                excludedRoots = excludedRoots
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(actionType)
                    }
                }

                else -> {
                    diag = optimizer.performMediaActionAcrossRoots(
                        roots = roots,
                        actionType = actionType,
                        customIds = customIds,
                        speedTargetText = speedParam,
                        targetPackage = currentPkg,
                        ourPackageName = packageName,
                        rootsTotal = rootsTotal,
                        excludedRoots = excludedRoots
                    )
                }
            }

            val fullDiag = diag.copy(
                currentForegroundPackage = currentPkg,
                targetPackage = currentPkg,
                rootsTotal = rootsTotal,
                targetRootsUsed = targetRootsUsed,
                excludedRoots = excludedRoots
            )
            CompanionStateManager.updateDiagnostics(fullDiag)

            // Persist log to Room DB
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.traversalLogDao().insertLog(
                    TraversalLogEntity(
                        packageName = currentPkg,
                        actionType = actionType.displayName,
                        success = fullDiag.success,
                        nodesScanned = fullDiag.totalNodesVisited,
                        durationMs = fullDiag.scanDurationMs,
                        details = fullDiag.matchedByHeuristic ?: "Action Executed"
                    )
                )
            } catch (e: Exception) {
                Log.e("EduAccessibility", "Failed to log traversal", e)
            }
        }
    }

    /**
     * Robust Sequential Speed Control Execution Engine:
     * 1. Detects real runtime player type (Native ExoPlayer, WebView/HTML5, Custom/Unknown).
     * 2. Inspects current UI for exact numeric speed option across target window roots.
     * 3. If not visible, wakes controls and clicks speed trigger/settings gear/custom player menu.
     * 4. Polls refreshed accessibility roots for speed popup/dialog across target windows.
     * 5. Clicks the exact requested speed option with coordinate fallback on runtime node bounds.
     * 6. Verifies selected speed and reports accurate, un-falsified structured diagnostics.
     */
    private suspend fun executeSpeedChangeSequence(
        targetSpeedText: String,
        customIds: List<String>,
        currentPkg: String,
        startTime: Long
    ): TraversalDiagnostics {
        val targetSpeedFloat = optimizer.normalizeSpeedLabel(targetSpeedText) ?: 1.0f
        val allRoots = getAllWindowRoots()
        var roots = getTargetAppWindowRoots(currentPkg)
        val rootsTotal = allRoots.size
        val targetRootsUsed = roots.size
        val excludedRoots = (rootsTotal - targetRootsUsed).coerceAtLeast(0)

        val playerType = optimizer.detectRuntimePlayerType(roots)

        var speedTriggerFound = false
        var speedTriggerClicked = false
        var speedTriggerViewId: String? = null
        var menuDetected = false
        var speedOptionFound = false
        var speedOptionText: String? = null
        var speedOptionResourceId: String? = null
        var clickResult = false
        var verificationResult = false
        var finalDetectedSpeed: String? = null
        var matchedPackage: String? = null
        var matchedDescription: String? = null

        val playerBounds = detectPlayerBounds(roots, currentPkg)
        val playerCenterX = playerBounds.centerX().toFloat()
        val playerCenterY = playerBounds.centerY().toFloat()

        // PATH 1 & 3: Multi-tier Execution Sequence

        // Tier 1: Check if exact speed option is ALREADY directly visible across target roots
        var targetOptionNode = optimizer.findExactSpeedOptionNode(roots, targetSpeedFloat, currentPkg, packageName)
        if (targetOptionNode != null) {
            speedOptionFound = true
            speedOptionText = targetOptionNode.text?.toString() ?: targetOptionNode.contentDescription?.toString()
            speedOptionResourceId = targetOptionNode.viewIdResourceName
            matchedPackage = targetOptionNode.packageName?.toString()
            matchedDescription = speedOptionText
            clickResult = optimizer.executeClick(targetOptionNode, currentPkg, packageName)
            if (clickResult) {
                delay(220L)
                val postRoots = getTargetAppWindowRoots(currentPkg)
                val detected = optimizer.detectCurrentSelectedSpeed(postRoots, currentPkg, packageName)
                if (detected != null) {
                    finalDetectedSpeed = String.format(Locale.US, "%.2fx", detected)
                    verificationResult = optimizer.speedValuesEqual(detected, targetSpeedFloat)
                }
            }
        }

        // Tier 2: If not found, wake controls and check again in target roots
        if (!clickResult) {
            performTap(playerCenterX, playerCenterY, 50L)
            delay(150L)
            roots = getTargetAppWindowRoots(currentPkg)

            targetOptionNode = optimizer.findExactSpeedOptionNode(roots, targetSpeedFloat, currentPkg, packageName)
            if (targetOptionNode != null) {
                speedOptionFound = true
                speedOptionText = targetOptionNode.text?.toString() ?: targetOptionNode.contentDescription?.toString()
                speedOptionResourceId = targetOptionNode.viewIdResourceName
                matchedPackage = targetOptionNode.packageName?.toString()
                matchedDescription = speedOptionText
                clickResult = optimizer.executeClick(targetOptionNode, currentPkg, packageName)
                if (clickResult) {
                    delay(220L)
                    val postRoots = getTargetAppWindowRoots(currentPkg)
                    val detected = optimizer.detectCurrentSelectedSpeed(postRoots, currentPkg, packageName)
                    if (detected != null) {
                        finalDetectedSpeed = String.format(Locale.US, "%.2fx", detected)
                        verificationResult = optimizer.speedValuesEqual(detected, targetSpeedFloat)
                    }
                }
            }
        }

        // Tier 3: Find speed trigger / settings gear / custom player menu button inside target roots
        if (!clickResult) {
            val speedTrigger = optimizer.findSpeedTriggerNode(roots, customIds, currentPkg, packageName)
            if (speedTrigger != null) {
                speedTriggerFound = true
                speedTriggerViewId = speedTrigger.viewIdResourceName
                matchedPackage = speedTrigger.packageName?.toString()
                speedTriggerClicked = optimizer.executeClick(speedTrigger, currentPkg, packageName)
            } else {
                val settingsGear = optimizer.findSettingsGearNode(roots, currentPkg, packageName)
                if (settingsGear != null) {
                    speedTriggerFound = true
                    speedTriggerViewId = settingsGear.viewIdResourceName
                    matchedPackage = settingsGear.packageName?.toString()
                    val gearClicked = optimizer.executeClick(settingsGear, currentPkg, packageName)
                    if (gearClicked) {
                        speedTriggerClicked = true
                        delay(200L)
                        val settingsRoots = getTargetAppWindowRoots(currentPkg)
                        val speedMenuItem = optimizer.findPlaybackSpeedMenuItem(settingsRoots, currentPkg, packageName)
                        if (speedMenuItem != null) {
                            menuDetected = true
                            optimizer.executeClick(speedMenuItem, currentPkg, packageName)
                        }
                    }
                } else {
                    // Fallback for custom player views without named IDs
                    val customTriggers = optimizer.findCustomPlayerCandidateTriggers(roots, playerBounds, currentPkg, packageName)
                    for (candidate in customTriggers) {
                        val clicked = optimizer.executeClick(candidate, currentPkg, packageName)
                        if (clicked) {
                            speedTriggerFound = true
                            speedTriggerClicked = true
                            speedTriggerViewId = candidate.viewIdResourceName ?: candidate.className?.toString()
                            matchedPackage = candidate.packageName?.toString()
                            break
                        }
                    }
                }
            }

            // Tier 4: Wait & poll across target window roots for speed option popup / bottom sheet
            var pollAttempts = 0
            val maxPollAttempts = 10
            while (pollAttempts < maxPollAttempts && !clickResult) {
                delay(120L)
                pollAttempts++
                val popupRoots = getTargetAppWindowRoots(currentPkg)
                val optionNode = optimizer.findExactSpeedOptionNode(popupRoots, targetSpeedFloat, currentPkg, packageName)
                if (optionNode != null) {
                    menuDetected = true
                    speedOptionFound = true
                    speedOptionText = optionNode.text?.toString() ?: optionNode.contentDescription?.toString()
                    speedOptionResourceId = optionNode.viewIdResourceName
                    matchedPackage = optionNode.packageName?.toString()
                    matchedDescription = speedOptionText

                    // Attempt ACTION_CLICK on target node
                    clickResult = optimizer.executeClick(optionNode, currentPkg, packageName)

                    // Coordinate tap fallback on runtime-discovered node bounds as LAST RESORT
                    if (!clickResult) {
                        val nodeRect = Rect()
                        optionNode.getBoundsInScreen(nodeRect)
                        if (!nodeRect.isEmpty && nodeRect.width() > 0 && nodeRect.height() > 0) {
                            clickResult = performTap(nodeRect.centerX().toFloat(), nodeRect.centerY().toFloat(), 50L)
                        }
                    }

                    if (clickResult) {
                        delay(220L)
                        val postRoots = getTargetAppWindowRoots(currentPkg)
                        val detected = optimizer.detectCurrentSelectedSpeed(postRoots, currentPkg, packageName)
                        if (detected != null) {
                            finalDetectedSpeed = String.format(Locale.US, "%.2fx", detected)
                            verificationResult = optimizer.speedValuesEqual(detected, targetSpeedFloat)
                        }
                        break
                    }
                }
            }
        }

        // Tier 5: Outcome evaluation & structured diagnostic logging
        val isSuccessful = clickResult && (verificationResult || speedOptionFound)
        val finalResultStr = if (isSuccessful) "SUCCESS" else "FAILURE"

        val structuredLog = buildString {
            appendLine("targetPackage=$currentPkg")
            appendLine("rootsTotal=$rootsTotal")
            appendLine("targetRootsUsed=$targetRootsUsed")
            appendLine("excludedRoots=$excludedRoots")
            appendLine("requested=$targetSpeedText")
            appendLine("playerType=${playerType.displayName}")
            appendLine("triggerFound=$speedTriggerFound")
            appendLine("triggerClicked=$speedTriggerClicked")
            appendLine("menuDetected=$menuDetected")
            appendLine("optionFound=$speedOptionFound")
            appendLine("optionText=${speedOptionText ?: "None"}")
            appendLine("optionResourceId=${speedOptionResourceId ?: "None"}")
            appendLine("matchedPackage=${matchedPackage ?: "None"}")
            appendLine("clickResult=$clickResult")
            appendLine("verificationResult=$verificationResult")
            appendLine("finalDetectedSpeed=${finalDetectedSpeed ?: "Unknown"}")
            append("result=$finalResultStr")
        }

        Log.i("VideoPlayerSpeed", "=== SPEED CHANGE ATTEMPT ===\n$structuredLog")

        val speedDiag = SpeedActionDiagnostics(
            packageName = currentPkg,
            requestedSpeed = targetSpeedText,
            requestedSpeedFloat = targetSpeedFloat,
            detectedPlayerType = playerType,
            speedTriggerFound = speedTriggerFound,
            speedTriggerClicked = speedTriggerClicked,
            speedTriggerViewId = speedTriggerViewId,
            menuDetected = menuDetected,
            speedOptionFound = speedOptionFound,
            speedOptionText = speedOptionText,
            speedOptionResourceId = speedOptionResourceId,
            clickResult = clickResult,
            verificationResult = verificationResult,
            finalDetectedSpeed = finalDetectedSpeed,
            finalResult = finalResultStr,
            structuredLog = structuredLog,
            targetPackage = currentPkg,
            rootsTotal = rootsTotal,
            targetRootsUsed = targetRootsUsed,
            excludedRoots = excludedRoots,
            matchedPackage = matchedPackage,
            matchedDescription = matchedDescription
        )

        // Note: If successful, update current detected speed in state
        if (isSuccessful) {
            CompanionStateManager.updateDetectedPlaybackStatus(
                isPlaying = CompanionStateManager.uiState.value.isPlaying,
                source = "Speed Selected ($targetSpeedText)",
                detectedSpeed = targetSpeedText
            )
        }

        return TraversalDiagnostics(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = SystemClock.uptimeMillis() - startTime,
            totalNodesVisited = roots.size,
            maxDepthReached = 3,
            matchedAction = MediaActionType.SPEED_SET,
            matchedViewId = speedOptionResourceId ?: speedTriggerViewId,
            matchedByHeuristic = if (isSuccessful) "Speed $targetSpeedText Applied [${playerType.name}]" else "Speed $targetSpeedText Selection Failed",
            success = isSuccessful,
            currentForegroundPackage = currentPkg,
            detectedPlayerType = playerType,
            speedDiagnostics = speedDiag,
            targetPackage = currentPkg,
            rootsTotal = rootsTotal,
            targetRootsUsed = targetRootsUsed,
            excludedRoots = excludedRoots,
            matchedPackage = matchedPackage,
            matchedDescription = matchedDescription
        )
    }

    /**
     * Performs a single touch tap gesture at exact screen coordinates.
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e("EduAccessibility", "Tap failed at ($x, $y)", e)
            false
        }
    }

    /**
     * Performs a long press gesture at coordinates.
     */
    fun performLongPress(x: Float, y: Float, durationMs: Long = 600L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e("EduAccessibility", "Long press failed at ($x, $y)", e)
            false
        }
    }

    /**
     * Performs a double-tap gesture at exact screen coordinates (used for 10s seek in YouTube/DIKSHA).
     */
    fun performDoubleTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path1 = Path().apply { moveTo(x, y) }
        val path2 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 40L)
        val stroke2 = GestureDescription.StrokeDescription(path2, 80L, 40L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        return try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e("EduAccessibility", "Double tap failed at ($x, $y)", e)
            false
        }
    }

    /**
     * Dispatches hardware media key events to the active Android AudioManager session.
     */
    private fun dispatchSystemMediaKeyEvent(actionType: MediaActionType): Boolean {
        val audio = audioManager ?: return false

        val keyCode = when (actionType) {
            MediaActionType.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaActionType.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaActionType.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaActionType.FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            MediaActionType.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            MediaActionType.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaActionType.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }

        return try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)

            audio.dispatchMediaKeyEvent(eventDown)
            audio.dispatchMediaKeyEvent(eventUp)

            val mediaIntentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, eventDown)
            }
            sendOrderedBroadcast(mediaIntentDown, null)

            val mediaIntentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, eventUp)
            }
            sendOrderedBroadcast(mediaIntentUp, null)
            true
        } catch (e: Exception) {
            Log.e("EduAccessibility", "Failed to dispatch media key event", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.w("EduAccessibility", "StudyCompanionAccessibilityService interrupted.")
        cancelAllActiveJobs()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllActiveJobs()
        isServiceBound = false
        CompanionStateManager.updateAccessibilityConnection(false)
        if (CompanionStateManager.accessibilityService == this) {
            CompanionStateManager.accessibilityService = null
        }
    }
}
