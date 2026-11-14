package com.example.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alpha.model.CompatibilityLevel
import com.example.alpha.model.CompatibilityReport
import com.example.alpha.model.InstalledAppInfo
import com.example.alpha.model.TargetAppProfile
import com.example.alpha.ui.components.AlphaSectionHeader
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.alpha.ui.components.AppIconThumbnail
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCrimson
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaTextSecondary

@Composable
fun MyAppsScreen(
    targetApps: List<TargetAppProfile>,
    installedApps: List<InstalledAppInfo>,
    selectedCompatibility: CompatibilityReport?,
    onAddApp: (InstalledAppInfo) -> Unit,
    onRemoveApp: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onTogglePopupPref: (String, Boolean) -> Unit,
    onInspectCompatibility: (String, String) -> Unit,
    onDismissCompatibility: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(0) } // 0 = My Selected, 1 = All Installed

    val filteredInstalled = remember(installedApps, searchQuery, targetApps) {
        val q = searchQuery.trim().lowercase()
        installedApps.filter { app ->
            q.isEmpty() || app.displayName.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        }
    }

    val filteredSelected = remember(targetApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        targetApps.filter { app ->
            q.isEmpty() || app.displayName.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AlphaSectionHeader(
                    title = "Universal App Selector",
                    subtitle = "Select apps where ALPHA V32.3 popup appears automatically"
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_search_input"),
                    placeholder = { Text("Search ShareChat, WhatsApp, Instagram, or package...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterTab == 0,
                        onClick = { filterTab = 0 },
                        label = { Text("My Apps (${targetApps.size})") },
                        modifier = Modifier.testTag("apps_tab_selected")
                    )
                    FilterChip(
                        selected = filterTab == 1,
                        onClick = { filterTab = 1 },
                        label = { Text("Installed Apps (${installedApps.size})") },
                        modifier = Modifier.testTag("apps_tab_installed")
                    )
                }
            }

            if (filterTab == 0) {
                if (filteredSelected.isEmpty()) {
                    item {
                        AlphaStudioCard {
                            Text("No apps selected yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Switch to 'Installed Apps' above to add ShareChat, WhatsApp, Instagram, or any installed application.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                    }
                } else {
                    items(filteredSelected, key = { it.packageName }) { profile ->
                        AlphaStudioCard(
                            borderColor = if (profile.enabled) AlphaCyan else AlphaTextSecondary.copy(alpha = 0.3f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconThumbnail(
                                    packageName = profile.packageName,
                                    contentDescription = "${profile.displayName} icon"
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = profile.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AlphaCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Last preset: ${profile.lastSettings.presetName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlphaTextSecondary
                                    )
                                }
                                Switch(
                                    checked = profile.enabled,
                                    onCheckedChange = { onToggleEnabled(profile.packageName, it) }
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AlphaStatusPill(
                                    label = if (profile.popupEnabled) "POPUP READY" else "POPUP OFF",
                                    accentColor = if (profile.popupEnabled) AlphaEmerald else AlphaAmber
                                )
                                AlphaStatusPill(
                                    label = "LOCAL PREVIEW",
                                    accentColor = AlphaCyan
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onLaunchApp(profile.packageName) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open & Test")
                                }
                                OutlinedButton(
                                    onClick = { onInspectCompatibility(profile.packageName, profile.displayName) }
                                ) {
                                    Icon(Icons.Rounded.Info, contentDescription = "Compatibility info", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Compat")
                                }
                                OutlinedButton(
                                    onClick = { onTogglePopupPref(profile.packageName, !profile.popupEnabled) }
                                ) {
                                    Text(if (profile.popupEnabled) "Mute Pop" else "Auto Pop")
                                }
                                IconButton(
                                    onClick = { onRemoveApp(profile.packageName) },
                                    modifier = Modifier.testTag("remove_app_${profile.packageName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove ${profile.displayName}",
                                        tint = AlphaCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredInstalled, key = { it.packageName }) { appInfo ->
                    val alreadyAdded = targetApps.any { it.packageName == appInfo.packageName }
                    AlphaStudioCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconThumbnail(
                                packageName = appInfo.packageName,
                                contentDescription = "${appInfo.displayName} icon"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = appInfo.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!appInfo.isInstalled) {
                                        AlphaStatusPill(label = "NOT INSTALLED", accentColor = AlphaAmber)
                                    }
                                }
                                Text(
                                    text = appInfo.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AlphaTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (alreadyAdded) {
                                OutlinedButton(
                                    onClick = { onRemoveApp(appInfo.packageName) },
                                    modifier = Modifier.testTag("app_remove_${appInfo.packageName}")
                                ) {
                                    Text("Remove", color = AlphaCrimson)
                                }
                            } else {
                                Button(
                                    onClick = { onAddApp(appInfo) },
                                    modifier = Modifier.testTag("app_add_${appInfo.packageName}")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCompatibility != null) {
        AlertDialog(
            onDismissRequest = onDismissCompatibility,
            title = { Text("${selectedCompatibility.displayName} Compatibility") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AlphaStatusPill(
                            label = if (selectedCompatibility.popupSupported) "POPUP: YES" else "POPUP: NEEDS PERM",
                            accentColor = if (selectedCompatibility.popupSupported) AlphaEmerald else AlphaAmber
                        )
                        AlphaStatusPill(
                            label = if (selectedCompatibility.localPreviewSupported) "PREVIEW: YES" else "PREVIEW: NEEDS MIC",
                            accentColor = if (selectedCompatibility.localPreviewSupported) AlphaEmerald else AlphaAmber
                        )
                    }
                    AlphaStatusPill(
                        label = when (selectedCompatibility.liveAudioIntegration) {
                            CompatibilityLevel.SUPPORTED -> "LIVE INJECTION: SUPPORTED"
                            CompatibilityLevel.PARTIAL_WARNING -> "LIVE INJECTION: PARTIAL"
                            CompatibilityLevel.UNAVAILABLE_NON_ROOT -> "LIVE MIC INJECTION: UNAVAILABLE (NON-ROOT)"
                        },
                        accentColor = AlphaCrimson
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = selectedCompatibility.technicalDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlphaTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissCompatibility) {
                    Text("Close")
                }
            }
        )
    }
}
