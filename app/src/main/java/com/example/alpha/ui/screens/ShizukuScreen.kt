package com.example.alpha.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.alpha.shizuku.ShizukuConnectionStatus
import com.example.alpha.shizuku.ShizukuUiState
import com.example.alpha.ui.components.AlphaSectionHeader
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCrimson
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaSurfaceElevated
import com.example.ui.theme.AlphaTextSecondary

@Composable
fun ShizukuScreen(
    shizukuState: ShizukuUiState,
    onRefreshStatus: () -> Unit,
    onRequestPermission: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val statusColor = when (shizukuState.status) {
        ShizukuConnectionStatus.AUTHORIZED_CONNECTED -> AlphaEmerald
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> AlphaCyan
        ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> AlphaAmber
        ShizukuConnectionStatus.PERMISSION_DENIED,
        ShizukuConnectionStatus.NOT_INSTALLED -> AlphaCrimson
    }

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
                    title = "Shizuku Privileged Bridge",
                    subtitle = "Official Shizuku API v13 • Wireless Debugging (Rootless)",
                    trailing = {
                        AlphaStatusPill(
                            label = shizukuState.status.name.replace('_', ' '),
                            accentColor = statusColor
                        )
                    }
                )
            }

            item {
                AlphaStudioCard(borderColor = statusColor) {
                    Text("Connection Telemetry", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StatusRow("Shizuku App Installed", if (shizukuState.isShizukuAppInstalled) "Yes" else "Not Detected")
                    StatusRow("Binder Alive", if (shizukuState.isBinderAlive) "Connected (v${shizukuState.shizukuVersion})" else "Offline")
                    StatusRow("Authorization", if (shizukuState.hasPermission) "Granted (UID ${shizukuState.shizukuUid})" else "Not Granted")
                    StatusRow("UserService Bridge", if (shizukuState.userServiceBound) "Bound & Ready" else "Standby")
                    StatusRow("Operating Mode", if (shizukuState.fallbackModeActive) "Rootless Fallback Mode" else "Shizuku Enhanced Diagnostics")

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestPermission,
                            enabled = shizukuState.isBinderAlive && !shizukuState.hasPermission,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("shizuku_request_permission_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AlphaCyan)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Authorize")
                        }
                        FilledTonalButton(
                            onClick = onRefreshStatus,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("shizuku_refresh_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRunDiagnostics,
                            enabled = shizukuState.userServiceBound,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("shizuku_run_diag_button")
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Audio Diag")
                        }
                        OutlinedButton(
                            onClick = onOpenShizuku,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (shizukuState.isShizukuAppInstalled) "Open Shizuku" else "Get Shizuku")
                        }
                    }
                }
            }

            item {
                AlphaStudioCard {
                    Text("Live Shizuku Shell Diagnostics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AlphaSurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = shizukuState.diagnosticOutput,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlphaCyan
                        )
                    }
                }
            }

            item {
                AlphaStudioCard {
                    Text("Wireless Debugging Setup Guide (No Root)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val steps = listOf(
                        "1. Install the official Shizuku app from Google Play or GitHub.",
                        "2. Enable Developer Options on your device (Settings -> About Phone -> tap Software Version 7 times).",
                        "3. Enable Wireless Debugging (connect to Wi-Fi first; on OriginOS 6 / iQOO Neo 10 also enable 'Disable permission monitoring' if listed).",
                        "4. Open Shizuku -> Start via Wireless Debugging -> Pair with pairing code notification -> Start.",
                        "5. Return here and tap 'Authorize' to grant ALPHA MIC read-only audio & app-state inspection.",
                        "Note: If Shizuku disconnects or stops, ALPHA MIC automatically switches to Rootless Fallback Mode so your floating popup and 48kHz audio preview keep working."
                    )
                    steps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlphaTextSecondary,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AlphaTextSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium, color = AlphaCyan)
    }
}
