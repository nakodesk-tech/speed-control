package com.example.model

enum class AppCategory(val displayName: String) {
    EDUCATION("Educational Apps"),
    GOVERNMENT_ED("Govt / National Portals"),
    ONLINE_COURSES("MOOCs & Universities"),
    STREAMING_LECTURES("Lecture Streaming"),
    LANGUAGE_LEARNING("Language & Practice"),
    CUSTOM("Custom Services")
}

data class TargetAppConfig(
    val packageName: String,
    val displayName: String,
    val category: AppCategory = AppCategory.EDUCATION,
    val description: String = "Interactive learning & video platform",
    val iconName: String = "default",
    val isEnabled: Boolean = true,
    val autoLaunchOverlay: Boolean = true,
    val autoSkipSeconds: Int = 10,
    val customPlayPauseIds: List<String> = emptyList(),
    val customForwardIds: List<String> = emptyList(),
    val customRewindIds: List<String> = emptyList(),
    val customNextIds: List<String> = emptyList(),
    val customSpeedIds: List<String> = emptyList(),
    val customCaptionsIds: List<String> = emptyList(),
    val isBuiltIn: Boolean = false
) {
    companion object {
        val PRESET_APPS = listOf(
            TargetAppConfig(
                packageName = "in.gov.diksha.app",
                displayName = "DIKSHA (National Ed Portal)",
                category = AppCategory.GOVERNMENT_ED,
                description = "Digital Infrastructure for Knowledge Sharing (India)",
                iconName = "diksha",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf(
                    "in.gov.diksha.app:id/exo_play",
                    "in.gov.diksha.app:id/exo_pause",
                    "in.gov.diksha.app:id/play_pause_button",
                    "in.gov.diksha.app:id/v_play",
                    "exo_play",
                    "exo_pause"
                ),
                customForwardIds = listOf("in.gov.diksha.app:id/exo_ffwd", "exo_ffwd"),
                customRewindIds = listOf("in.gov.diksha.app:id/exo_rew", "exo_rew"),
                customNextIds = listOf("in.gov.diksha.app:id/btn_next", "in.gov.diksha.app:id/next_video"),
                customSpeedIds = listOf(
                    "in.gov.diksha.app:id/btn_playback_speed",
                    "btn_playback_speed",
                    "exo_playback_speed"
                ),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "org.khanacademy.android",
                displayName = "Khan Academy",
                category = AppCategory.EDUCATION,
                description = "Free world-class education for anyone, anywhere",
                iconName = "khan",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf(
                    "org.khanacademy.android:id/exo_play",
                    "org.khanacademy.android:id/exo_pause",
                    "org.khanacademy.android:id/play_pause",
                    "exo_play",
                    "exo_pause"
                ),
                customForwardIds = listOf("org.khanacademy.android:id/exo_ffwd", "org.khanacademy.android:id/forward_10", "exo_ffwd"),
                customRewindIds = listOf("org.khanacademy.android:id/exo_rew", "org.khanacademy.android:id/rewind_10", "exo_rew"),
                customNextIds = listOf("org.khanacademy.android:id/next_item_button", "org.khanacademy.android:id/next_video"),
                customSpeedIds = listOf("org.khanacademy.android:id/speed_button", "org.khanacademy.android:id/btn_speed", "exo_playback_speed"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "org.coursera.android",
                displayName = "Coursera",
                category = AppCategory.ONLINE_COURSES,
                description = "Degree courses, certificates, and university lectures",
                iconName = "coursera",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("org.coursera.android:id/play_pause", "exo_play", "exo_pause"),
                customForwardIds = listOf("org.coursera.android:id/forward_10", "org.coursera.android:id/exo_ffwd", "exo_ffwd"),
                customRewindIds = listOf("org.coursera.android:id/replay_10", "org.coursera.android:id/exo_rew", "exo_rew"),
                customNextIds = listOf("org.coursera.android:id/next_item", "org.coursera.android:id/next_video"),
                customSpeedIds = listOf("org.coursera.android:id/speed_button", "org.coursera.android:id/playback_speed"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.udemy.android",
                displayName = "Udemy",
                category = AppCategory.ONLINE_COURSES,
                description = "Online courses & professional tech skills",
                iconName = "udemy",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("com.udemy.android:id/play_pause_button", "com.udemy.android:id/exo_play", "exo_play"),
                customForwardIds = listOf("com.udemy.android:id/exo_ffwd", "com.udemy.android:id/forward_10", "exo_ffwd"),
                customRewindIds = listOf("com.udemy.android:id/exo_rew", "com.udemy.android:id/replay_10", "exo_rew"),
                customNextIds = listOf("com.udemy.android:id/next_lecture_button", "com.udemy.android:id/exo_next"),
                customSpeedIds = listOf("com.udemy.android:id/playback_rate", "com.udemy.android:id/speed_button"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "org.edx.mobile",
                displayName = "edX",
                category = AppCategory.ONLINE_COURSES,
                description = "Harvard, MIT & top university courses",
                iconName = "edx",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("org.edx.mobile:id/exo_play", "exo_play"),
                customSpeedIds = listOf("org.edx.mobile:id/btn_playback_speed", "btn_playback_speed"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "in.swayam.mobileapp",
                displayName = "SWAYAM (NPTEL)",
                category = AppCategory.GOVERNMENT_ED,
                description = "MHRD Govt of India Online Courses & Lectures",
                iconName = "swayam",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("in.swayam.mobileapp:id/exo_play", "exo_play"),
                customSpeedIds = listOf("btn_playback_speed", "exo_playback_speed"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "xyz.penpencil.physicswala",
                displayName = "PhysicsWallah (PW)",
                category = AppCategory.EDUCATION,
                description = "Live and recorded interactive exam preparation",
                iconName = "pw",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("exo_play", "exo_pause", "play_pause"),
                customSpeedIds = listOf("xyz.penpencil.physicswala:id/playback_speed", "xyz.penpencil.physicswala:id/btn_speed"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.unacademyapp",
                displayName = "Unacademy",
                category = AppCategory.EDUCATION,
                description = "Live learning platform and recorded lessons",
                iconName = "unacademy",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("exo_play", "exo_pause", "player_control_play_pause"),
                customSpeedIds = listOf("com.unacademyapp:id/speed_button", "speed_button"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.byjus.thelearningapp",
                displayName = "BYJU'S Learning",
                category = AppCategory.EDUCATION,
                description = "Interactive K-12 learning lessons and animations",
                iconName = "byjus",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("exo_play", "btn_play"),
                customSpeedIds = listOf("btn_playback_speed", "speed_button"),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.google.android.youtube",
                displayName = "YouTube (Education)",
                category = AppCategory.STREAMING_LECTURES,
                description = "Educational video lectures & study playlists",
                iconName = "youtube",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf(
                    "com.google.android.youtube:id/play_pause_button",
                    "com.google.android.youtube:id/player_control_play_pause",
                    "com.google.android.youtube:id/touch_area",
                    "play_pause_button",
                    "player_control_play_pause"
                ),
                customForwardIds = listOf(
                    "com.google.android.youtube:id/fast_forward_button",
                    "fast_forward_button"
                ),
                customRewindIds = listOf(
                    "com.google.android.youtube:id/rewind_button",
                    "rewind_button"
                ),
                customNextIds = listOf(
                    "com.google.android.youtube:id/next_button",
                    "next_button"
                ),
                customSpeedIds = listOf(
                    "com.google.android.youtube:id/overflow_menu",
                    "overflow_menu",
                    "quick_actions_container"
                ),
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.duolingo",
                displayName = "Duolingo",
                category = AppCategory.LANGUAGE_LEARNING,
                description = "Interactive language learning & listening practice",
                iconName = "duolingo",
                isEnabled = false,
                autoLaunchOverlay = false,
                isBuiltIn = true
            ),
            TargetAppConfig(
                packageName = "com.ted.android",
                displayName = "TED Talks & TED-Ed",
                category = AppCategory.EDUCATION,
                description = "Thought-provoking ideas and educational animations",
                iconName = "ted",
                isEnabled = true,
                autoLaunchOverlay = true,
                customPlayPauseIds = listOf("exo_play", "play_button"),
                customSpeedIds = listOf("btn_playback_speed", "speed_button"),
                isBuiltIn = true
            )
        )
    }
}
