package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.ui.overlay.FloatingOverlayView

class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private lateinit var windowOverlayParams: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        const val CHANNEL_ID = "edu_companion_overlay_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_OVERLAY = "com.example.service.STOP_OVERLAY"
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        CompanionStateManager.floatingOverlayService = this
        CompanionStateManager.updateOverlayServiceRunning(true)

        startForegroundNotification()
        setupOverlayView()
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EduCompanion Active")
            .setContentText("Floating media controls ready for educational apps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EduCompanion Floating Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating media control companion status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("FloatingOverlay", "Overlay permission not granted!")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowOverlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        val currentParams = windowOverlayParams

        overlayComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                FloatingOverlayView(
                    onClose = { stopSelf() },
                    onDragDelta = { dx, dy ->
                        currentParams.x = (currentParams.x + dx.toInt()).coerceAtLeast(0)
                        currentParams.y = (currentParams.y + dy.toInt()).coerceAtLeast(0)
                        try {
                            windowManager.updateViewLayout(this@apply, currentParams)
                        } catch (e: Exception) {
                            Log.e("FloatingOverlay", "Error updating layout", e)
                        }
                    }
                )
            }
        }

        try {
            windowManager.addView(overlayComposeView, windowOverlayParams)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            Log.e("FloatingOverlay", "Failed to add window overlay", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        if (overlayComposeView != null) {
            try {
                windowManager.removeView(overlayComposeView)
            } catch (e: Exception) {
                Log.e("FloatingOverlay", "Failed to remove overlay view", e)
            }
            overlayComposeView = null
        }

        CompanionStateManager.updateOverlayServiceRunning(false)
        if (CompanionStateManager.floatingOverlayService == this) {
            CompanionStateManager.floatingOverlayService = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
