package com.example.web

import android.webkit.JavascriptInterface
import com.example.model.SpeedVerificationStatus
import com.example.model.WebVideoCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DikshaWebBridge(
    private val scope: CoroutineScope,
    private val onStateUpdated: (
        hasVideo: Boolean,
        videoCount: Int,
        activeSpeed: Float?,
        isPlaying: Boolean,
        currentTime: Float,
        duration: Float,
        candidates: List<WebVideoCandidate>
    ) -> Unit,
    private val onSpeedResult: (
        success: Boolean,
        requested: Float,
        actual: Float?,
        reason: String?,
        videoCount: Int
    ) -> Unit,
    private val onIframeWarningReceived: (String) -> Unit,
    private val onLogReceived: (String) -> Unit
) {

    @JavascriptInterface
    fun onVideoStateChanged(jsonStr: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val json = JSONObject(jsonStr)
                val hasVideo = json.optBoolean("hasVideo", false)
                val videoCount = json.optInt("videoCount", 0)
                val activeSpeed = if (json.has("activeSpeed") && !json.isNull("activeSpeed")) {
                    json.optDouble("activeSpeed").toFloat()
                } else null
                val isPlaying = json.optBoolean("isPlaying", false)
                val currentTime = json.optDouble("currentTime", 0.0).toFloat()
                val duration = json.optDouble("duration", 0.0).toFloat()

                val candidatesList = mutableListOf<WebVideoCandidate>()
                val candidatesArray = json.optJSONArray("candidates")
                if (candidatesArray != null) {
                    for (i in 0 until candidatesArray.length()) {
                        val item = candidatesArray.getJSONObject(i)
                        candidatesList.add(
                            WebVideoCandidate(
                                index = item.optInt("index", i),
                                source = item.optString("source", "document"),
                                playbackRate = item.optDouble("playbackRate", 1.0).toFloat(),
                                isPlaying = !item.optBoolean("paused", true),
                                currentTime = item.optDouble("currentTime", 0.0).toFloat(),
                                duration = item.optDouble("duration", 0.0).toFloat(),
                                isMuted = item.optBoolean("muted", false),
                                src = item.optString("currentSrc", "")
                            )
                        )
                    }
                }

                onStateUpdated(hasVideo, videoCount, activeSpeed, isPlaying, currentTime, duration, candidatesList)
            } catch (e: Exception) {
                onLogReceived("Error parsing onVideoStateChanged: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun onSpeedApplied(jsonStr: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val json = JSONObject(jsonStr)
                val success = json.optBoolean("success", false)
                val requested = json.optDouble("requested", 1.0).toFloat()
                val actual = if (json.has("actual") && !json.isNull("actual")) {
                    json.optDouble("actual").toFloat()
                } else null
                val reason = json.optString("reason", null)
                val videoCount = json.optInt("videoCount", 0)

                onSpeedResult(success, requested, actual, reason, videoCount)
            } catch (e: Exception) {
                onLogReceived("Error parsing onSpeedApplied: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun onIframeWarning(warning: String) {
        scope.launch(Dispatchers.Main) {
            onIframeWarningReceived(warning)
        }
    }

    @JavascriptInterface
    fun onLog(msg: String) {
        scope.launch(Dispatchers.Main) {
            onLogReceived(msg)
        }
    }

    companion object {
        const val BRIDGE_NAME = "EduBridge"

        /**
         * Comprehensive injected JavaScript code reproducing the working Tampermonkey script
         * with MutationObserver, multi-video detection, iframe scanning, and playback rate setter/verifier.
         */
        val INJECTION_SCRIPT: String = """
            (function() {
                if (window.__eduCompanionInjected) {
                    window.__queryEduVideoStatus();
                    return;
                }
                window.__eduCompanionInjected = true;

                function findVideos() {
                    const results = [];
                    try {
                        document.querySelectorAll('video').forEach((v, idx) => {
                            results.push({ el: v, source: 'document (video #' + idx + ')' });
                        });
                    } catch(e) {}

                    // Inspect same-origin iframes
                    try {
                        const iframes = document.querySelectorAll('iframe');
                        iframes.forEach((iframe, idx) => {
                            try {
                                const doc = iframe.contentDocument || iframe.contentWindow?.document;
                                if (doc) {
                                    doc.querySelectorAll('video').forEach((v, vIdx) => {
                                        results.push({ el: v, source: 'iframe[' + idx + '] (video #' + vIdx + ')' });
                                    });
                                }
                            } catch(err) {
                                if (window.EduBridge && window.EduBridge.onIframeWarning) {
                                    window.EduBridge.onIframeWarning('Cross-origin iframe[' + idx + '] — direct DOM access unavailable: ' + err.message);
                                }
                            }
                        });
                    } catch(e) {}

                    return results;
                }

                function getActiveVideo() {
                    const list = findVideos();
                    if (list.length === 0) return null;
                    // Prefer currently playing video
                    const activePlaying = list.find(item => !item.el.paused && item.el.currentTime > 0);
                    if (activePlaying) return activePlaying.el;
                    return list[0].el;
                }

                window.__setEduVideoSpeed = function(targetSpeed) {
                    const list = findVideos();
                    if (list.length === 0) {
                        const res = {
                            success: false,
                            reason: 'NO_VIDEO',
                            requested: targetSpeed,
                            actual: null,
                            videoCount: 0
                        };
                        if (window.EduBridge && window.EduBridge.onSpeedApplied) {
                            window.EduBridge.onSpeedApplied(JSON.stringify(res));
                        }
                        return res;
                    }

                    let appliedCount = 0;
                    let lastActual = 1.0;
                    let activeVideo = getActiveVideo();

                    list.forEach(item => {
                        try {
                            item.el.playbackRate = targetSpeed;
                            item.el.defaultPlaybackRate = targetSpeed;
                            appliedCount++;
                        } catch(e) {}
                    });

                    if (activeVideo) {
                        lastActual = activeVideo.playbackRate;
                    } else if (list[0]) {
                        lastActual = list[0].el.playbackRate;
                    }

                    const isSuccess = Math.abs(lastActual - targetSpeed) < 0.05;
                    const response = {
                        success: isSuccess,
                        requested: targetSpeed,
                        actual: lastActual,
                        videoCount: list.length,
                        reason: isSuccess ? null : ('Actual rate mismatch: ' + lastActual)
                    };

                    if (window.EduBridge && window.EduBridge.onSpeedApplied) {
                        window.EduBridge.onSpeedApplied(JSON.stringify(response));
                    }
                    window.__queryEduVideoStatus();
                    return response;
                };

                window.__seekEduVideo = function(deltaSeconds) {
                    const v = getActiveVideo();
                    if (v) {
                        v.currentTime = Math.max(0, Math.min(v.duration || Infinity, v.currentTime + deltaSeconds));
                        window.__queryEduVideoStatus();
                    }
                };

                window.__toggleEduVideoPlayPause = function() {
                    const v = getActiveVideo();
                    if (v) {
                        if (v.paused) {
                            v.play();
                        } else {
                            v.pause();
                        }
                        window.__queryEduVideoStatus();
                    }
                };

                window.__queryEduVideoStatus = function() {
                    try {
                        const list = findVideos();
                        const active = getActiveVideo();

                        const candidates = list.map((item, idx) => ({
                            index: idx,
                            source: item.source,
                            playbackRate: item.el.playbackRate || 1.0,
                            paused: item.el.paused,
                            currentTime: item.el.currentTime || 0,
                            duration: item.el.duration || 0,
                            muted: item.el.muted,
                            currentSrc: item.el.currentSrc || item.el.src || 'stream/media'
                        }));

                        const status = {
                            hasVideo: active !== null,
                            videoCount: list.length,
                            activeSpeed: active ? active.playbackRate : null,
                            isPlaying: active ? (!active.paused && !active.ended) : false,
                            currentTime: active ? active.currentTime : 0,
                            duration: active ? (active.duration || 0) : 0,
                            candidates: candidates
                        };

                        if (window.EduBridge && window.EduBridge.onVideoStateChanged) {
                            window.EduBridge.onVideoStateChanged(JSON.stringify(status));
                        }
                    } catch(err) {
                        if (window.EduBridge && window.EduBridge.onLog) {
                            window.EduBridge.onLog('Error in query: ' + err.message);
                        }
                    }
                };

                function attachListeners(v) {
                    if (v.__eduHooked) return;
                    v.__eduHooked = true;
                    ['ratechange', 'play', 'pause', 'timeupdate', 'loadeddata', 'seeking', 'seeked'].forEach(evt => {
                        v.addEventListener(evt, () => {
                            window.__queryEduVideoStatus();
                        });
                    });
                }

                // Initial attach
                findVideos().forEach(item => attachListeners(item.el));

                // MutationObserver for dynamic player rendering
                const obs = new MutationObserver(() => {
                    const list = findVideos();
                    if (list.length > 0) {
                        list.forEach(item => attachListeners(item.el));
                        window.__queryEduVideoStatus();
                    }
                });

                if (document.documentElement || document.body) {
                    obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
                }

                // Periodic status query
                if (!window.__eduPoller) {
                    window.__eduPoller = setInterval(() => {
                        window.__queryEduVideoStatus();
                    }, 1200);
                }

                // Initial status probe
                window.__queryEduVideoStatus();
            })();
        """.trimIndent()
    }
}
