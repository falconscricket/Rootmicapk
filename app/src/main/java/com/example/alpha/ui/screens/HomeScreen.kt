package com.example.alpha.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alpha.audio.AudioEngineState
import com.example.alpha.audio.EngineMode
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.model.KnownTargets
import com.example.alpha.model.TargetAppProfile
import com.example.alpha.service.FloatingServiceState
import com.example.alpha.shizuku.ShizukuConnectionStatus
import com.example.alpha.shizuku.ShizukuUiState
import com.example.alpha.ui.PermissionChecklistState
import com.example.alpha.ui.components.AlphaSectionHeader
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.alpha.ui.components.AppIconThumbnail
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCrimson
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaObsidianBg
import com.example.ui.theme.AlphaSurfaceElevated
import com.example.ui.theme.AlphaTextSecondary

@Composable
fun HomeScreen(
    serviceState: FloatingServiceState,
    shizukuState: ShizukuUiState,
    audioState: AudioEngineState,
    currentEffects: AlphaEffectSettings,
    targetApps: List<TargetAppProfile>,
    permissions: PermissionChecklistState,
    onToggleMonitor: (Boolean) -> Unit,
    onShowPopupNow: () -> Unit,
    onLaunchShareChat: () -> Unit,
    onNavigatePopup: () -> Unit,
    onNavigateApps: () -> Unit,
    onNavigateAudio: () -> Unit,
    onNavigateShizuku: () -> Unit,
    onNavigatePermissions: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val allCorePermissionsReady = permissions.hasOverlay && permissions.hasUsageAccess && permissions.hasRecordAudio
    val enabledCount = targetApps.count { it.enabled }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Hero Studio Banner Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.alpha_studio_hero_1794640216943),
                        contentDescription = "ALPHA Mic Universal Studio Console",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        AlphaObsidianBg.copy(alpha = 0.25f),
                                        AlphaObsidianBg.copy(alpha = 0.92f)
                                    )
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlphaStatusPill(
                                    label = "V32.3 CORE",
                                    accentColor = AlphaCyan
                                )
                                AlphaStatusPill(
                                    label = if (shizukuState.status == ShizukuConnectionStatus.AUTHORIZED_CONNECTED) {
                                        "SHIZUKU ACTIVE"
                                    } else {
                                        "ROOTLESS FALLBACK"
                                    },
                                    accentColor = if (shizukuState.status == ShizukuConnectionStatus.AUTHORIZED_CONNECTED) {
                                        AlphaEmerald
                                    } else {
                                        AlphaAmber
                                    }
                                )
                            }
                            Text(
                                text = "ALPHA MIC UNIVERSAL INJECTOR",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = "Preset: ${currentEffects.presetName} • ${audioState.sampleRateHz / 1000} kHz PCM • $enabledCount active app(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                // Automatic Foreground Popup Monitor Card
                AlphaStudioCard(
                    borderColor = if (serviceState.isRunning) AlphaEmerald else AlphaCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic ALPHA Popup Monitor",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (serviceState.isRunning) {
                                    "Active • Foreground: ${serviceState.currentForegroundPackage ?: "Listening..."}"
                                } else {
                                    "Idle • Enable to auto-show ALPHA V32.3 when selected apps open"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                        Switch(
                            checked = serviceState.isRunning,
                            onCheckedChange = onToggleMonitor,
                            modifier = Modifier.testTag("home_monitor_switch")
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onShowPopupNow,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_show_popup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AlphaCyan)
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Launch Popup")
                        }
                        FilledTonalButton(
                            onClick = onNavigatePopup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_open_console_button")
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("V32.3 Panel")
                        }
                    }
                }
            }

            item {
                // Primary Target: ShareChat Chat Room Companion Card
                AlphaStudioCard(borderColor = AlphaCyan.copy(alpha = 0.7f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconThumbnail(
                            packageName = KnownTargets.SHARECHAT_PACKAGE,
                            contentDescription = "ShareChat icon"
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ShareChat Target Companion",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = KnownTargets.SHARECHAT_PACKAGE,
                                style = MaterialTheme.typography.labelSmall,
                                color = AlphaCyan
                            )
                        }
                        AlphaStatusPill(
                            label = if (targetApps.any { it.packageName == KnownTargets.SHARECHAT_PACKAGE && it.enabled }) {
                                "ENABLED"
                            } else "DISABLED",
                            accentColor = AlphaEmerald
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Opening ShareChat automatically triggers the ALPHA V32.3 floating popup. Important: Stock non-root Android isolates ShareChat's private AudioRecord stream — local 48kHz DSP preview works directly, while live Chat Room mic injection requires root/HAL or external USB/TRRS loopback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlphaTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLaunchShareChat,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_launch_sharechat_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open ShareChat")
                        }
                        OutlinedButton(
                            onClick = onNavigateApps,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Apps")
                        }
                    }
                }
            }

            if (!allCorePermissionsReady) {
                item {
                    AlphaStudioCard(
                        borderColor = AlphaAmber,
                        containerColor = AlphaSurfaceElevated
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Permissions needed",
                                tint = AlphaAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Setup Permissions Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AlphaAmber
                                )
                                Text(
                                    text = "Grant Overlay, Usage Access, and Microphone permissions for automatic popup detection and 48kHz audio preview.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlphaTextSecondary
                                )
                            }
                            FilledTonalButton(
                                onClick = onNavigatePermissions,
                                modifier = Modifier.testTag("home_setup_permissions_button")
                            ) {
                                Text("Setup")
                            }
                        }
                    }
                }
            }

            item {
                AlphaSectionHeader(
                    title = "System Telemetry & Modules",
                    subtitle = "Direct access to all 7 ALPHA Injector subsystems"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AlphaStudioCard(
                        modifier = Modifier.weight(1f),
                        borderColor = if (audioState.mode != EngineMode.IDLE) AlphaEmerald else AlphaCyan.copy(alpha = 0.4f)
                    ) {
                        Text("Audio DSP", style = MaterialTheme.typography.labelLarge, color = AlphaCyan)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (audioState.mode == EngineMode.IDLE) "Standby" else audioState.mode.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${audioState.sampleRateHz} Hz • ${"%.1f".format(audioState.estimatedLatencyMs)} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlphaTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onNavigateAudio, modifier = Modifier.fillMaxWidth()) {
                            Text("Audio Engine")
                        }
                    }

                    AlphaStudioCard(
                        modifier = Modifier.weight(1f),
                        borderColor = if (shizukuState.hasPermission) AlphaEmerald else AlphaAmber.copy(alpha = 0.6f)
                    ) {
                        Text("Shizuku API", style = MaterialTheme.typography.labelLarge, color = AlphaAmber)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (shizukuState.status) {
                                ShizukuConnectionStatus.AUTHORIZED_CONNECTED -> "Authorized"
                                ShizukuConnectionStatus.PERMISSION_REQUIRED -> "Needs Auth"
                                ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> "Stopped"
                                ShizukuConnectionStatus.PERMISSION_DENIED -> "Denied"
                                ShizukuConnectionStatus.NOT_INSTALLED -> "Fallback"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (shizukuState.userServiceBound) "UserService UID ${shizukuState.shizukuUid}" else "Rootless mode active",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlphaTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onNavigateShizuku, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shizuku")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigatePermissions,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_permissions_nav_button")
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Permissions")
                    }
                    FilledTonalButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_settings_nav_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings")
                    }
                }
            }

            item {
                AlphaStudioCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = AlphaEmerald)
                        Column {
                            Text("Privacy & Security Verified", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Zero hidden recording, zero network transmission, no binary modification. Audio stays 100% local unless you explicitly test preview or WAV export.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
