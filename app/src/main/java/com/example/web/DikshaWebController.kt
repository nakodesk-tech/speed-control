package com.example.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.SpeedVerificationStatus
import com.example.model.WebPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DikshaWebController(
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(WebPlayerState())
    val uiState: StateFlow<WebPlayerState> = _uiState.asStateFlow()

    private var webViewRef: WeakReference<WebView>? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val bridge = DikshaWebBridge(
        scope = scope,
        onStateUpdated = { hasVideo, videoCount, activeSpeed, isPlaying, currentTime, duration, candidates ->
            _uiState.update { current ->
                val updatedStatus = if (!hasVideo) {
                    if (current.speedStatus == SpeedVerificationStatus.SETTING) SpeedVerificationStatus.NO_VIDEO else current.speedStatus
                } else {
                    if (current.verifiedSpeed != null && activeSpeed != null) {
                        if (Math.abs(activeSpeed - current.requestedSpeed) < 0.05f) {
                            SpeedVerificationStatus.ACTIVE
                        } else {
                            current.speedStatus
                        }
                    } else current.speedStatus
                }

                current.copy(
                    hasActiveVideo = hasVideo,
                    videoCount = videoCount,
                    verifiedSpeed = activeSpeed ?: current.verifiedSpeed,
                    isVideoPlaying = isPlaying,
                    currentTime = currentTime,
                    duration = duration,
                    candidates = candidates,
                    speedStatus = updatedStatus
                )
            }
        },
        onSpeedResult = { success, requested, actual, reason, videoCount ->
            val timestamp = timeFormatter.format(Date())
            val message = if (success && actual != null) {
                "[$timestamp] VERIFIED: Requested ${requested}x -> Actual ${String.format(Locale.US, "%.2fx", actual)} (Matched across $videoCount video elements)"
            } else if (actual != null) {
                "[$timestamp] FAILED: Requested ${requested}x but video returned ${String.format(Locale.US, "%.2fx", actual)} ($reason)"
            } else {
                "[$timestamp] FAILED: No HTML5 video found on page ($reason)"
            }

            _uiState.update { current ->
                val newStatus = if (success) {
                    SpeedVerificationStatus.ACTIVE
                } else if (videoCount == 0 || actual == null) {
                    SpeedVerificationStatus.NO_VIDEO
                } else {
                    SpeedVerificationStatus.FAILED
                }

                val updatedLogs = (listOf(message) + current.logs).take(50)
                current.copy(
                    requestedSpeed = requested,
                    verifiedSpeed = actual,
                    speedStatus = newStatus,
                    lastVerificationMessage = message,
                    logs = updatedLogs
                )
            }
        },
        onIframeWarningReceived = { warning ->
            val timestamp = timeFormatter.format(Date())
            val logEntry = "[$timestamp] [IFRAME] $warning"
            _uiState.update { current ->
                current.copy(
                    iframeWarning = warning,
                    logs = (listOf(logEntry) + current.logs).take(50)
                )
            }
        },
        onLogReceived = { msg ->
            val timestamp = timeFormatter.format(Date())
            val logEntry = "[$timestamp] [JS] $msg"
            _uiState.update { current ->
                current.copy(logs = (listOf(logEntry) + current.logs).take(50))
            }
        }
    )

    fun attachWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
        configureWebSettings(webView)
        setupClients(webView)
    }

    private fun configureWebSettings(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
        }

        // Enable cookies & third-party cookies (essential for DIKSHA authentication and video CDNs)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Add JavaScript Bridge
        webView.addJavascriptInterface(bridge, DikshaWebBridge.BRIDGE_NAME)
    }

    private fun setupClients(webView: WebView) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _uiState.update { it.copy(progress = newProgress, isLoading = newProgress < 100) }
                if (newProgress > 60) {
                    injectScript()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    _uiState.update { it.copy(title = title) }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                _uiState.update {
                    it.copy(
                        url = url ?: it.url,
                        isLoading = true,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                _uiState.update {
                    it.copy(
                        url = url ?: it.url,
                        isLoading = false,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false
                    )
                }
                injectScript()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
    }

    fun injectScript() {
        val webView = webViewRef?.get() ?: return
        scope.launch(Dispatchers.Main) {
            webView.evaluateJavascript(DikshaWebBridge.INJECTION_SCRIPT, null)
        }
    }

    fun loadUrl(url: String) {
        val webView = webViewRef?.get() ?: return
        val target = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        _uiState.update { it.copy(url = target, isLoading = true) }
        webView.loadUrl(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val timestamp = timeFormatter.format(Date())
        _uiState.update {
            it.copy(
                requestedSpeed = speed,
                speedStatus = SpeedVerificationStatus.SETTING,
                lastVerificationMessage = "[$timestamp] Commanding video.playbackRate = ${speed}x..."
            )
        }

        val webView = webViewRef?.get() ?: return
        scope.launch(Dispatchers.Main) {
            // First ensure script is injected
            webView.evaluateJavascript(
                """
                (function() {
                    if (typeof window.__setEduVideoSpeed === 'function') {
                        return window.__setEduVideoSpeed($speed);
                    } else {
                        ${DikshaWebBridge.INJECTION_SCRIPT}
                        if (typeof window.__setEduVideoSpeed === 'function') {
                            return window.__setEduVideoSpeed($speed);
                        }
                        return { success: false, reason: 'SCRIPT_NOT_INITIALIZED' };
                    }
                })();
                """.trimIndent()
            ) { result ->
                logEntry("Evaluated speed change ($speed): $result")
            }
        }
    }

    fun queryVideoStatus() {
        val webView = webViewRef?.get() ?: return
        scope.launch(Dispatchers.Main) {
            webView.evaluateJavascript(
                """
                (function() {
                    if (typeof window.__queryEduVideoStatus === 'function') {
                        window.__queryEduVideoStatus();
                    } else {
                        ${DikshaWebBridge.INJECTION_SCRIPT}
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun togglePlayPause() {
        val webView = webViewRef?.get() ?: return
        scope.launch(Dispatchers.Main) {
            webView.evaluateJavascript("window.__toggleEduVideoPlayPause && window.__toggleEduVideoPlayPause();", null)
        }
    }

    fun seekBy(seconds: Float) {
        val webView = webViewRef?.get() ?: return
        scope.launch(Dispatchers.Main) {
            webView.evaluateJavascript("window.__seekEduVideo && window.__seekEduVideo($seconds);", null)
        }
    }

    fun reload() {
        webViewRef?.get()?.reload()
    }

    fun goBack() {
        val webView = webViewRef?.get()
        if (webView?.canGoBack() == true) {
            webView.goBack()
        }
    }

    fun goForward() {
        val webView = webViewRef?.get()
        if (webView?.canGoForward() == true) {
            webView.goForward()
        }
    }

    fun toggleDesktopMode() {
        val webView = webViewRef?.get() ?: return
        _uiState.update { current ->
            val newMode = !current.isDesktopMode
            val userAgent = if (newMode) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                null // Default mobile WebView User-Agent
            }
            webView.settings.userAgentString = userAgent
            webView.reload()
            current.copy(isDesktopMode = newMode)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), iframeWarning = null) }
    }

    private fun logEntry(msg: String) {
        val timestamp = timeFormatter.format(Date())
        _uiState.update {
            it.copy(logs = (listOf("[$timestamp] $msg") + it.logs).take(50))
        }
    }
}
