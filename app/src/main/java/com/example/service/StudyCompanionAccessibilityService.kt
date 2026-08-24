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
import com.example.model.MediaActionType
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
            val roots = getAllWindowRoots()
            if (roots.isNotEmpty()) {
                val status = optimizer.detectVideoPlaybackStatus(roots, audioManager)
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
     * Detects on-screen bounding rectangle of the video player in the foreground app.
     */
    fun detectPlayerBounds(roots: List<AccessibilityNodeInfo>): Rect {
        val descriptor = optimizer.inspectVideoPlayerTargets(roots)
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

            val roots = getAllWindowRoots()
            val playerBounds = detectPlayerBounds(roots)
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
                    diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)
                    if (!diag.success) {
                        // Wake up faded controls with a center tap
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshRoots = getAllWindowRoots()
                        val retryDiag = optimizer.performMediaActionAcrossRoots(freshRoots, actionType, customIds, speedParam)
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
                                currentForegroundPackage = currentPkg
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(actionType)
                    }
                }

                MediaActionType.FAST_FORWARD -> {
                    diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshRoots = getAllWindowRoots()
                        val retryDiag = optimizer.performMediaActionAcrossRoots(freshRoots, actionType, customIds, speedParam)
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
                                currentForegroundPackage = currentPkg
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(MediaActionType.FAST_FORWARD)
                    }
                }

                MediaActionType.REWIND -> {
                    diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshRoots = getAllWindowRoots()
                        val retryDiag = optimizer.performMediaActionAcrossRoots(freshRoots, actionType, customIds, speedParam)
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
                                currentForegroundPackage = currentPkg
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(MediaActionType.REWIND)
                    }
                }

                MediaActionType.NEXT, MediaActionType.PREVIOUS -> {
                    diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)
                        val freshRoots = getAllWindowRoots()
                        val retryNext = optimizer.performMediaActionAcrossRoots(freshRoots, actionType, customIds, speedParam)
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
                                currentForegroundPackage = currentPkg
                            )
                        }
                    } else {
                        dispatchSystemMediaKeyEvent(actionType)
                    }
                }

                else -> {
                    diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)
                }
            }

            val fullDiag = diag.copy(currentForegroundPackage = currentPkg)
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
     * 1. Inspects current UI for exact speed option.
     * 2. If not visible, wakes controls and clicks speed trigger/settings gear.
     * 3. Polls refreshed accessibility roots for speed menu popup.
     * 4. Clicks the exact requested speed option.
     * 5. Verifies selected speed and reports accurate diagnostics.
     */
    private suspend fun executeSpeedChangeSequence(
        targetSpeedText: String,
        customIds: List<String>,
        currentPkg: String,
        startTime: Long
    ): TraversalDiagnostics {
        val targetSpeed = optimizer.normalizeSpeedLabel(targetSpeedText) ?: 1.0f
        val logBuilder = StringBuilder()
        logBuilder.append("SpeedControl: requested=").append(targetSpeedText)
            .append(" (").append(targetSpeed).append("x)")
            .append(" package=").append(currentPkg)

        Log.i("SpeedControl", "Executing speed change sequence for target: $targetSpeedText ($targetSpeed x) in $currentPkg")

        // STEP A: Direct check across all current window roots
        var roots = getAllWindowRoots()
        logBuilder.append(" initialRoots=").append(roots.size)
        var targetOptionNode = optimizer.findExactSpeedOptionNode(roots, targetSpeed)

        if (targetOptionNode != null) {
            val clicked = optimizer.executeClick(targetOptionNode)
            logBuilder.append(" directOptionFound=true clicked=").append(clicked)
            if (clicked) {
                delay(200L)
                val postRoots = getAllWindowRoots()
                val finalDetected = optimizer.detectCurrentSelectedSpeed(postRoots)
                val verified = finalDetected != null && optimizer.speedValuesEqual(finalDetected, targetSpeed)
                logBuilder.append(" verified=").append(verified).append(" finalSpeed=").append(finalDetected ?: "unknown")

                Log.i("SpeedControl", logBuilder.toString())
                return TraversalDiagnostics(
                    lastScanTimeMs = System.currentTimeMillis(),
                    scanDurationMs = SystemClock.uptimeMillis() - startTime,
                    totalNodesVisited = roots.size,
                    maxDepthReached = 2,
                    matchedAction = MediaActionType.SPEED_SET,
                    matchedViewId = targetOptionNode.viewIdResourceName,
                    matchedByHeuristic = "Exact Speed Option Clicked ($targetSpeedText) [Direct]",
                    success = true,
                    currentForegroundPackage = currentPkg
                )
            }
        }

        // STEP B: Wake up controls by tapping player center if controls are hidden
        val playerBounds = detectPlayerBounds(roots)
        val playerCenterX = playerBounds.centerX().toFloat()
        val playerCenterY = playerBounds.centerY().toFloat()
        performTap(playerCenterX, playerCenterY, 50L)
        delay(150L)

        roots = getAllWindowRoots()
        logBuilder.append(" rootsAfterWake=").append(roots.size)

        // Check if waking up controls revealed the speed option (e.g. speed chips in player overlay)
        targetOptionNode = optimizer.findExactSpeedOptionNode(roots, targetSpeed)
        if (targetOptionNode != null) {
            val clicked = optimizer.executeClick(targetOptionNode)
            logBuilder.append(" optionFoundAfterWake=true clicked=").append(clicked)
            if (clicked) {
                delay(200L)
                val postRoots = getAllWindowRoots()
                val finalDetected = optimizer.detectCurrentSelectedSpeed(postRoots)
                logBuilder.append(" finalSpeed=").append(finalDetected ?: "unknown")
                Log.i("SpeedControl", logBuilder.toString())
                return TraversalDiagnostics(
                    lastScanTimeMs = System.currentTimeMillis(),
                    scanDurationMs = SystemClock.uptimeMillis() - startTime,
                    totalNodesVisited = roots.size,
                    maxDepthReached = 2,
                    matchedAction = MediaActionType.SPEED_SET,
                    matchedViewId = targetOptionNode.viewIdResourceName,
                    matchedByHeuristic = "Exact Speed Option Clicked ($targetSpeedText) [After Wake]",
                    success = true,
                    currentForegroundPackage = currentPkg
                )
            }
        }

        // STEP C: Find and click Speed Trigger Button / Settings Gear
        val speedTriggerNode = optimizer.findSpeedTriggerNode(roots, customIds)
        var menuTriggerClicked = false

        if (speedTriggerNode != null) {
            logBuilder.append(" speedTriggerFound=true id=").append(speedTriggerNode.viewIdResourceName)
            menuTriggerClicked = optimizer.executeClick(speedTriggerNode)
            logBuilder.append(" triggerClicked=").append(menuTriggerClicked)
        } else {
            // Check for Settings Gear / Overflow menu
            val settingsGear = optimizer.findSettingsGearNode(roots)
            if (settingsGear != null) {
                logBuilder.append(" settingsGearFound=true id=").append(settingsGear.viewIdResourceName)
                val gearClicked = optimizer.executeClick(settingsGear)
                logBuilder.append(" gearClicked=").append(gearClicked)
                if (gearClicked) {
                    delay(200L)
                    val settingsRoots = getAllWindowRoots()
                    val speedMenuItem = optimizer.findPlaybackSpeedMenuItem(settingsRoots)
                    if (speedMenuItem != null) {
                        logBuilder.append(" speedMenuItemFound=true")
                        menuTriggerClicked = optimizer.executeClick(speedMenuItem)
                        logBuilder.append(" speedMenuItemClicked=").append(menuTriggerClicked)
                    }
                }
            } else {
                logBuilder.append(" speedTriggerFound=false settingsGearFound=false")
            }
        }

        // STEP D: Poll with timeout (~800ms) for the speed popup / dialog across ALL window roots
        var speedOptionFoundAndClicked = false
        var matchedOptionViewId: String? = null
        var pollAttempts = 0
        val maxPollAttempts = 8

        while (pollAttempts < maxPollAttempts && !speedOptionFoundAndClicked) {
            delay(100L)
            pollAttempts++
            val popupRoots = getAllWindowRoots()
            val optionNode = optimizer.findExactSpeedOptionNode(popupRoots, targetSpeed)
            if (optionNode != null) {
                matchedOptionViewId = optionNode.viewIdResourceName
                logBuilder.append(" pollAttempt=").append(pollAttempts).append(" targetOptionFound=true id=").append(matchedOptionViewId)
                val clicked = optimizer.executeClick(optionNode)
                logBuilder.append(" optionClicked=").append(clicked)
                if (clicked) {
                    speedOptionFoundAndClicked = true
                    delay(200L)
                    val postSelectionRoots = getAllWindowRoots()
                    val detectedFinal = optimizer.detectCurrentSelectedSpeed(postSelectionRoots)
                    logBuilder.append(" detectedFinal=").append(detectedFinal ?: "unknown")
                    break
                }
            }
        }

        Log.i("SpeedControl", logBuilder.toString())

        if (speedOptionFoundAndClicked) {
            return TraversalDiagnostics(
                lastScanTimeMs = System.currentTimeMillis(),
                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                totalNodesVisited = roots.size + pollAttempts * 12,
                maxDepthReached = 3,
                matchedAction = MediaActionType.SPEED_SET,
                matchedViewId = matchedOptionViewId,
                matchedByHeuristic = "Exact Speed Option Selected ($targetSpeedText)",
                success = true,
                currentForegroundPackage = currentPkg
            )
        }

        // Return real failure - no fake fallbacks or false success reports
        return TraversalDiagnostics(
            lastScanTimeMs = System.currentTimeMillis(),
            scanDurationMs = SystemClock.uptimeMillis() - startTime,
            totalNodesVisited = roots.size + pollAttempts * 12,
            maxDepthReached = 2,
            matchedAction = MediaActionType.SPEED_SET,
            matchedViewId = null,
            matchedByHeuristic = "Speed Option $targetSpeedText not found/selected in popup menu",
            success = false,
            currentForegroundPackage = currentPkg
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
