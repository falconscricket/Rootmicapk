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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.alpha.model.GlobalInjectorSettings
import com.example.alpha.model.PopupBehaviorMode
import com.example.alpha.ui.PermissionChecklistState
import com.example.alpha.ui.components.AlphaSectionHeader
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaTextSecondary

@Composable
fun PermissionsScreen(
    permissions: PermissionChecklistState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit
) {
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
                    title = "Permissions & System Access",
                    subtitle = "Transparent, least-privilege permissions for overlay and local DSP",
                    trailing = {
                        OutlinedButton(onClick = onRefresh) { Text("Recheck") }
                    }
                )
            }

            item {
                PermissionRowCard(
                    title = "Display Over Other Apps (Overlay)",
                    description = "Required to show the draggable ALPHA V32.3 floating popup over ShareChat and selected apps.",
                    granted = permissions.hasOverlay,
                    buttonLabel = if (permissions.hasOverlay) "Configured" else "Grant Overlay",
                    testTag = "perm_overlay_button",
                    onAction = onOpenOverlaySettings
                )
            }

            item {
                PermissionRowCard(
                    title = "Usage Access (Foreground Detection)",
                    description = "Used solely to detect when a selected target app opens or leaves the foreground so the ALPHA popup appears/hides automatically.",
                    granted = permissions.hasUsageAccess,
                    buttonLabel = if (permissions.hasUsageAccess) "Configured" else "Grant Usage Access",
                    testTag = "perm_usage_button",
                    onAction = onOpenUsageAccessSettings
                )
            }

            item {
                PermissionRowCard(
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required only when you start Local Live Preview or Processed WAV Recording Tests inside ALPHA Audio Engine.",
                    granted = permissions.hasRecordAudio,
                    buttonLabel = if (permissions.hasRecordAudio) "Granted" else "Grant Mic",
                    testTag = "perm_mic_button",
                    onAction = onRequestMic
                )
            }

            item {
                PermissionRowCard(
                    title = "Foreground Notification (POST_NOTIFICATIONS)",
                    description = "Keeps the Floating Popup monitor visible and gives 1-tap Show/Stop controls.",
                    granted = permissions.hasPostNotifications,
                    buttonLabel = if (permissions.hasPostNotifications) "Granted" else "Allow Alerts",
                    testTag = "perm_notif_button",
                    onAction = onRequestNotifications
                )
            }

            item {
                PermissionRowCard(
                    title = "Battery Optimization & OriginOS Auto-Start",
                    description = "Recommended on iQOO Neo 10 / OriginOS 6 so background power-savers do not kill the foreground popup monitor while switching apps.",
                    granted = permissions.isBatteryOptimizationIgnored,
                    buttonLabel = if (permissions.isBatteryOptimizationIgnored) "Unrestricted" else "Battery Settings",
                    testTag = "perm_battery_button",
                    onAction = onOpenBatterySettings
                )
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    description: String,
    granted: Boolean,
    buttonLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    AlphaStudioCard(borderColor = if (granted) AlphaEmerald else AlphaAmber) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AlphaStatusPill(
                label = if (granted) "GRANTED" else "REQUIRED",
                accentColor = if (granted) AlphaEmerald else AlphaAmber
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = AlphaTextSecondary)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.testTag(testTag)
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
fun SettingsScreen(
    globalSettings: GlobalInjectorSettings,
    onSetAutoShowPopup: (Boolean) -> Unit,
    onSetPopupBehavior: (PopupBehaviorMode) -> Unit,
    onSetSampleRate: (Int) -> Unit,
    onSetLowLatency: (Boolean) -> Unit,
    onResetPopupPosition: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AlphaSectionHeader(
                    title = "Injector Settings",
                    subtitle = "Floating popup behavior, 48 kHz engine tuning & OriginOS 6 optimization"
                )
            }

            item {
                AlphaStudioCard {
                    Text("Floating ALPHA Popup Behavior", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Show When Selected App Opens", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Displays ALPHA V32.3 automatically on target foreground switch",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                        Switch(
                            checked = globalSettings.autoShowPopup,
                            onCheckedChange = onSetAutoShowPopup
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("When Leaving Selected App:", style = MaterialTheme.typography.labelLarge, color = AlphaCyan)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = globalSettings.popupBehavior == PopupBehaviorMode.HIDE_COMPLETELY,
                            onClick = { onSetPopupBehavior(PopupBehaviorMode.HIDE_COMPLETELY) },
                            label = { Text("Hide Completely") }
                        )
                        FilterChip(
                            selected = globalSettings.popupBehavior == PopupBehaviorMode.MINIMIZE_TO_ORB,
                            onClick = { onSetPopupBehavior(PopupBehaviorMode.MINIMIZE_TO_ORB) },
                            label = { Text("Minimize to Orb") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Coordinates: (${globalSettings.popupX}, ${globalSettings.popupY})",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlphaTextSecondary
                        )
                        OutlinedButton(
                            onClick = onResetPopupPosition,
                            modifier = Modifier.testTag("settings_reset_pos_button")
                        ) {
                            Text("Reset Position")
                        }
                    }
                }
            }

            item {
                AlphaStudioCard {
                    Text("Audio Engine Preferences", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Target Sample Rate", style = MaterialTheme.typography.labelLarge, color = AlphaCyan)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = globalSettings.preferredSampleRateHz == 48000,
                            onClick = { onSetSampleRate(48000) },
                            label = { Text("48,000 Hz (Studio)") }
                        )
                        FilterChip(
                            selected = globalSettings.preferredSampleRateHz == 44100,
                            onClick = { onSetSampleRate(44100) },
                            label = { Text("44,100 Hz (Compat)") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low-Latency AudioTrack Mode", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Uses 512-frame PCM buffers and PERFORMANCE_MODE_LOW_LATENCY",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                        Switch(
                            checked = globalSettings.lowLatencyMode,
                            onCheckedChange = onSetLowLatency
                        )
                    }
                }
            }

            item {
                AlphaStudioCard {
                    Text("iQOO Neo 10 / OriginOS 6 Checklist", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• Enable 'Allow background activity / High background power consumption' for Alpha Mic in OriginOS Battery settings.\n" +
                            "• Enable 'Floating window' permission in OriginOS App Info.\n" +
                            "• Lock Alpha Mic in Recent Apps if aggressive RAM cleanup closes the monitor service.\n" +
                            "• All selected apps, popup coordinates, and ALPHA V32.3 preset settings persist across restarts and reboots via Jetpack DataStore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlphaTextSecondary
                    )
                }
            }
        }
    }
}
