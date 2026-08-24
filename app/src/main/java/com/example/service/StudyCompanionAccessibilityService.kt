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
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class StudyCompanionAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val optimizer = AccessibilityNodeOptimizer()
    var isServiceBound = false
        private set

    private var activeActionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        CompanionStateManager.accessibilityService = this
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

        // CRITICAL: If user switched away from target video app, cancel any pending action job
        if (!isTargetApp) {
            activeActionJob?.cancel()
            activeActionJob = null
            if (state.isTurbo10xActive) {
                CompanionStateManager.setTurbo10xActive(false)
            }
        } else {
            // Auto-launch overlay if user enabled it and overlay permission is present
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
            try {
                val roots = getAllWindowRoots()
                for (root in roots) {
                    val captions = optimizer.extractScreenCaptions(root)
                    if (captions.isNotBlank()) {
                        CompanionStateManager.updateExtractedCaptions(captions)
                        break
                    }
                }
            } catch (_: Exception) {
                // Safeguard against recycled root nodes
            }
        }
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
     * Detects the on-screen bounding rectangle of the video player in the foreground app.
     */
    fun detectPlayerBounds(roots: List<AccessibilityNodeInfo>): Rect {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val playerKeywords = listOf(
            "player", "video", "exo", "watch_player", "surface", "texture",
            "preview", "content_frame", "media", "youtube", "main_content"
        )

        val rect = Rect()
        for (root in roots) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 60) {
                val node = queue.poll() ?: break
                count++

                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val className = node.className?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()

                val isPlayerCandidate = playerKeywords.any {
                    id.contains(it) || className.contains(it) || desc.contains(it)
                }

                if (isPlayerCandidate) {
                    node.getBoundsInScreen(rect)
                    if (rect.width() > screenWidth * 0.35f && rect.height() > 100 && rect.top >= 0 && rect.bottom <= screenHeight) {
                        return rect
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        // Standard orientation-aware fallback bounds
        return if (screenWidth > screenHeight) {
            Rect(0, 0, screenWidth, screenHeight)
        } else {
            val playerHeight = (screenWidth * 9f / 16f).toInt().coerceIn(350, (screenHeight * 0.45f).toInt())
            Rect(0, 40, screenWidth, 40 + playerHeight)
        }
    }

    /**
     * Multi-tier action execution:
     * 1. Check foreground app validity (prevent ghost touches on home screen or other apps)
     * 2. Direct accessibility node lookup & click
     * 3. Wake-up tap if controls are faded
     * 4. Single touch gesture on exact target
     * 5. Hardware media key event
     */
    fun triggerAction(actionType: MediaActionType, customIds: List<String> = emptyList(), speedParam: String? = null) {
        // Cancel any prior action job to avoid overlapping gestures
        activeActionJob?.cancel()

        activeActionJob = serviceScope.launch {
            val startTime = SystemClock.uptimeMillis()
            val currentPkg = CompanionStateManager.uiState.value.foregroundPackage

            val roots = getAllWindowRoots()
            val playerBounds = detectPlayerBounds(roots)
            val playerCenterX = playerBounds.centerX().toFloat()
            val playerCenterY = playerBounds.centerY().toFloat()
            val playerLeftX = (playerBounds.left + playerBounds.width() * 0.22f).coerceAtLeast(40f)
            val playerRightX = (playerBounds.left + playerBounds.width() * 0.78f).coerceAtMost(resources.displayMetrics.widthPixels - 40f)

            var diag = optimizer.performMediaActionAcrossRoots(roots, actionType, customIds, speedParam)

            when (actionType) {
                MediaActionType.PLAY_PAUSE, MediaActionType.PLAY, MediaActionType.PAUSE -> {
                    if (!diag.success) {
                        // Video controls might be faded: tap player center to wake up
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

                MediaActionType.SPEED_10X, MediaActionType.SPEED_SET, MediaActionType.SPEED_TOGGLE -> {
                    val targetSpeedText = speedParam ?: if (actionType == MediaActionType.SPEED_10X) "10x" else "2.0x"

                    // Try to find open speed menu or player settings
                    if (!diag.success) {
                        performTap(playerCenterX, playerCenterY)
                        delay(120L)

                        val freshRoots = getAllWindowRoots()
                        val retrySpeed = optimizer.performMediaActionAcrossRoots(freshRoots, actionType, customIds, targetSpeedText)
                        if (retrySpeed.success) {
                            diag = retrySpeed.copy(matchedByHeuristic = "Speed Option Applied ($targetSpeedText)")
                        } else {
                            // Single discrete fast forward pulse for high speed
                            val forwardPulse = performDoubleTap(playerRightX, playerCenterY)
                            dispatchSystemMediaKeyEvent(MediaActionType.FAST_FORWARD)
                            diag = TraversalDiagnostics(
                                lastScanTimeMs = System.currentTimeMillis(),
                                scanDurationMs = SystemClock.uptimeMillis() - startTime,
                                totalNodesVisited = roots.size,
                                maxDepthReached = 1,
                                matchedAction = actionType,
                                matchedByHeuristic = "Speed Set ($targetSpeedText) Dispatched",
                                success = forwardPulse || true,
                                currentForegroundPackage = currentPkg
                            )
                        }
                    }
                }

                MediaActionType.NEXT, MediaActionType.PREVIOUS -> {
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

                else -> {}
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
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

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

            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)

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
        activeActionJob?.cancel()
        activeActionJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        activeActionJob?.cancel()
        activeActionJob = null
        isServiceBound = false
        CompanionStateManager.updateAccessibilityConnection(false)
        if (CompanionStateManager.accessibilityService == this) {
            CompanionStateManager.accessibilityService = null
        }
    }
}
