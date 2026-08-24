package com.example.model

enum class SpeedVerificationStatus {
    IDLE,
    SETTING,
    ACTIVE,
    FAILED,
    NO_VIDEO
}

data class WebVideoCandidate(
    val index: Int = 0,
    val source: String = "document",
    val playbackRate: Float = 1.0f,
    val isPlaying: Boolean = false,
    val currentTime: Float = 0f,
    val duration: Float = 0f,
    val isMuted: Boolean = false,
    val src: String = ""
)

data class WebPlayerState(
    val url: String = "https://learning.diksha.gov.in/",
    val title: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val videoCount: Int = 0,
    val hasActiveVideo: Boolean = false,
    val isVideoPlaying: Boolean = false,
    val currentTime: Float = 0f,
    val duration: Float = 0f,
    val requestedSpeed: Float = 1.0f,
    val verifiedSpeed: Float? = null,
    val speedStatus: SpeedVerificationStatus = SpeedVerificationStatus.IDLE,
    val lastVerificationMessage: String = "Ready. Navigate to DIKSHA course and play video.",
    val candidates: List<WebVideoCandidate> = emptyList(),
    val iframeWarning: String? = null,
    val logs: List<String> = emptyList()
)
