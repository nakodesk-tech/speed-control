package com.example

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.CompanionStateManager
import com.example.ui.screens.AppsManagerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DikshaWebPlayerScreen
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.theme.EduCompanionTheme

enum class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    WEB_PLAYER("DIKSHA Web (10x)", Icons.Default.Language, "tab_web_player"),
    SPEED_CONTROLLER("Accessibility", Icons.Default.Speed, "tab_dashboard"),
    VIDEO_APPS("Video Apps", Icons.Default.Apps, "tab_apps"),
    DIAGNOSTICS("Diagnostics", Icons.Default.Search, "tab_diagnostics")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissionsState()

        setContent {
            EduCompanionTheme {
                MainAppContainer()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsState()
    }

    private fun checkPermissionsState() {
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        CompanionStateManager.updateOverlayPermission(overlayGranted)

        val serviceBound = CompanionStateManager.accessibilityService?.isServiceBound == true
        CompanionStateManager.updateAccessibilityConnection(serviceBound)
    }
}

@Composable
fun MainAppContainer() {
    var selectedTab by remember { mutableStateOf(MainTab.SPEED_CONTROLLER) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFF818CF8),
                            indicatorColor = Color(0xFF4F46E5),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Crossfade(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                MainTab.WEB_PLAYER -> DikshaWebPlayerScreen()
                MainTab.SPEED_CONTROLLER -> DashboardScreen()
                MainTab.VIDEO_APPS -> AppsManagerScreen()
                MainTab.DIAGNOSTICS -> DiscoveryScreen()
            }
        }
    }
}
