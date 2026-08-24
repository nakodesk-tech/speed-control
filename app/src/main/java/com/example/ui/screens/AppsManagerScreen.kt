package com.example.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCategory
import com.example.model.TargetAppConfig
import com.example.service.CompanionStateManager

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystem: Boolean
)

@Composable
fun AppsManagerScreen() {
    val state by CompanionStateManager.uiState.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeviceAppsPicker by remember { mutableStateOf(false) }

    // Add Dialog fields
    var appNameInput by remember { mutableStateOf("") }
    var appPkgInput by remember { mutableStateOf("") }
    var autoLaunchOverlayInput by remember { mutableStateOf(true) }

    val filteredApps = remember(state.targetApps, searchQuery) {
        state.targetApps.filter { app ->
            searchQuery.isBlank() ||
                    app.displayName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MANAGED VIDEO APPS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${state.targetApps.count { it.isEnabled }} Active",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add any video app installed on your device. When an enabled app is launched, the speed control overlay automatically appears.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showDeviceAppsPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_installed_app_btn")
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add From Device", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                appNameInput = ""
                                appPkgInput = ""
                                autoLaunchOverlayInput = true
                                showAddDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_custom_app_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Package", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps or packages...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apps_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // List of Managed Apps
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No apps found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap 'Add From Device' to pick apps installed on your phone",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        VideoAppItemCard(
                            app = app,
                            onToggleEnabled = { enabled ->
                                CompanionStateManager.toggleTargetApp(app.packageName, enabled)
                            },
                            onLaunchApp = {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    }
                                } catch (_: Exception) {}
                            },
                            onDelete = {
                                CompanionStateManager.removeTargetApp(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Custom Package Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Video App Package") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = appNameInput,
                        onValueChange = { appNameInput = it },
                        label = { Text("App Name (e.g. My Video App)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = appPkgInput,
                        onValueChange = { appPkgInput = it },
                        label = { Text("Package Name (e.g. com.my.videoapp)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto-launch overlay when opened", fontSize = 12.sp)
                        Switch(checked = autoLaunchOverlayInput, onCheckedChange = { autoLaunchOverlayInput = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appNameInput.isNotBlank() && appPkgInput.isNotBlank()) {
                            val newApp = TargetAppConfig(
                                packageName = appPkgInput.trim(),
                                displayName = appNameInput.trim(),
                                category = AppCategory.EDUCATION,
                                description = "Custom video app",
                                iconName = "custom",
                                isEnabled = true,
                                autoLaunchOverlay = autoLaunchOverlayInput,
                                isBuiltIn = false
                            )
                            CompanionStateManager.addCustomTargetApp(newApp)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add App")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Device Installed Apps Picker Dialog
    if (showDeviceAppsPicker) {
        val installedList = remember {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages.map { appInfo ->
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    InstalledAppItem(appName = name, packageName = appInfo.packageName, isSystem = isSys)
                }.sortedBy { it.appName.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        var filterInstalledQuery by remember { mutableStateOf("") }
        val filteredInstalled = installedList.filter {
            filterInstalledQuery.isBlank() ||
                    it.appName.contains(filterInstalledQuery, ignoreCase = true) ||
                    it.packageName.contains(filterInstalledQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showDeviceAppsPicker = false },
            title = { Text("Select App From Device") },
            text = {
                Column(modifier = Modifier.height(380.dp)) {
                    OutlinedTextField(
                        value = filterInstalledQuery,
                        onValueChange = { filterInstalledQuery = it },
                        placeholder = { Text("Search installed packages...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredInstalled, key = { it.packageName }) { appInfo ->
                            val alreadyAdded = state.targetApps.any { it.packageName == appInfo.packageName }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (alreadyAdded) Color(0xFF10B981).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!alreadyAdded) {
                                            val newTarget = TargetAppConfig(
                                                packageName = appInfo.packageName,
                                                displayName = appInfo.appName,
                                                category = AppCategory.EDUCATION,
                                                description = "Installed device app",
                                                iconName = "default",
                                                isEnabled = true,
                                                autoLaunchOverlay = true
                                            )
                                            CompanionStateManager.addCustomTargetApp(newTarget)
                                            showDeviceAppsPicker = false
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(appInfo.appName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(appInfo.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (alreadyAdded) {
                                        Text("ADDED", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("+ ADD", color = Color(0xFF6366F1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceAppsPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun VideoAppItemCard(
    app: TargetAppConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onLaunchApp: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (app.isEnabled) Color(0xFF4F46E5).copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (app.isEnabled) Color(0xFF4338CA) else Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = if (app.isEnabled) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = app.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Switch(
                    checked = app.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("app_toggle_${app.packageName}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (app.isEnabled) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFF334155).copy(alpha = 0.4f)
                ) {
                    Text(
                        text = if (app.isEnabled) "Auto-Detect Overlay ON" else "Auto-Detect OFF",
                        fontSize = 10.sp,
                        color = if (app.isEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLaunchApp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Open App",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (!app.isBuiltIn) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete App",
                                tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
