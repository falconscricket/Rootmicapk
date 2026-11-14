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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.alpha.audio.AudioEngineState
import com.example.alpha.audio.EngineMode
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.ui.components.AlphaSectionHeader
import com.example.alpha.ui.components.AlphaSliderControl
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.alpha.ui.components.AlphaVuMeterBar
import com.example.alpha.ui.components.AlphaWaveformVisualizer
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCrimson
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaSurfaceElevated
import com.example.ui.theme.AlphaTextSecondary
import kotlin.math.roundToInt

@Composable
fun AudioEngineScreen(
    audioState: AudioEngineState,
    effects: AlphaEffectSettings,
    hasMicPermission: Boolean,
    hardwareCapabilities: List<Pair<String, String>>,
    onToggleLiveMonitor: () -> Unit,
    onToggleRecordTest: () -> Unit,
    onPlayLastRecording: () -> Unit,
    onStopAudio: () -> Unit,
    onUpdateEffects: (AlphaEffectSettings) -> Unit,
    onRequestMicPermission: () -> Unit
) {
    val isActive = audioState.mode != EngineMode.IDLE
    val isLive = audioState.mode == EngineMode.LIVE_MONITOR
    val isRecording = audioState.mode == EngineMode.RECORDING_TEST
    val isPlaying = audioState.mode == EngineMode.PLAYING_RECORDING

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
                    title = "ALPHA Native Audio Engine",
                    subtitle = "48 kHz Low-Latency Kotlin DSP Pipeline • AudioRecord + AudioTrack",
                    trailing = {
                        AlphaStatusPill(
                            label = if (isActive) audioState.mode.name else "STANDBY",
                            accentColor = if (isActive) AlphaEmerald else AlphaCyan
                        )
                    }
                )
            }

            if (!hasMicPermission) {
                item {
                    AlphaStudioCard(borderColor = AlphaAmber, containerColor = AlphaSurfaceElevated) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AlphaAmber)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Microphone Permission Required", style = MaterialTheme.typography.titleSmall, color = AlphaAmber)
                                Text(
                                    "Grant RECORD_AUDIO to run local DSP voice preview and WAV recording tests.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlphaTextSecondary
                                )
                            }
                            Button(
                                onClick = onRequestMicPermission,
                                modifier = Modifier.testTag("audio_grant_mic_button")
                            ) {
                                Text("Grant")
                            }
                        }
                    }
                }
            }

            if (audioState.isClientSilencedBySystem) {
                item {
                    AlphaStudioCard(borderColor = AlphaCrimson) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AlphaCrimson)
                            Column {
                                Text("AudioRecord Silenced by System Policy", style = MaterialTheme.typography.titleSmall, color = AlphaCrimson)
                                Text(
                                    "Another app (e.g. ShareChat Chat Room or voice call) currently holds exclusive priority on VOICE_COMMUNICATION input.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlphaTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                AlphaStudioCard(borderColor = if (isActive) AlphaEmerald else AlphaCyan.copy(alpha = 0.5f)) {
                    AlphaWaveformVisualizer(bars = audioState.waveform, isActive = isActive)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AlphaVuMeterBar(
                            label = "INPUT RMS",
                            rms = audioState.inputRms,
                            modifier = Modifier.weight(1f)
                        )
                        AlphaVuMeterBar(
                            label = "OUTPUT RMS",
                            rms = audioState.outputRms,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${audioState.statusMessage} • In: ${audioState.activeInputDeviceName} -> Out: ${audioState.activeOutputDeviceName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AlphaTextSecondary
                    )
                    if (!audioState.isHeadsetConnected) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = AlphaAmber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Use wired/USB headphones during Live Monitor to prevent speaker-to-mic feedback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaAmber
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onToggleLiveMonitor()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("audio_live_monitor_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLive) AlphaCrimson else AlphaEmerald
                            )
                        ) {
                            Icon(
                                if (isLive) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isLive) "Stop Live" else "Live Preview")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onToggleRecordTest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("audio_record_test_button")
                        ) {
                            Icon(
                                if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                                contentDescription = null,
                                tint = if (isRecording) AlphaCrimson else AlphaAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRecording) "Save WAV" else "Rec Test")
                        }
                    }

                    if (audioState.lastRecordingPath != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { if (isPlaying) onStopAudio() else onPlayLastRecording() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("audio_play_wav_button")
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isPlaying) "Stop Playback"
                                    else "Play Processed WAV (${"%.1f".format(audioState.lastRecordingDurationSec)}s)"
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Fine-tuned DSP controls (Echo feedback, Robot freq, Limiter)
                AlphaStudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DSP Mastering & Modulation", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Active: ${effects.presetName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlphaCyan
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Limiter", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = effects.limiterEnabled,
                                onCheckedChange = {
                                    onUpdateEffects(effects.copy(limiterEnabled = it))
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AlphaSliderControl(
                        label = "Echo Feedback",
                        valueText = "${(effects.echoFeedback * 100).roundToInt()}%",
                        value = effects.echoFeedback,
                        valueRange = 0f..0.8f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", echoFeedback = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Robot Carrier Frequency",
                        valueText = "${effects.robotFreqHz.roundToInt()} Hz",
                        value = effects.robotFreqHz,
                        valueRange = 30f..180f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", robotFreqHz = it))
                        }
                    )
                }
            }

            item {
                AlphaStudioCard {
                    Text("Hardware Audio Telemetry", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    hardwareCapabilities.forEach { (k, v) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = AlphaTextSecondary)
                            Text(v, style = MaterialTheme.typography.labelSmall, color = AlphaCyan)
                        }
                    }
                }
            }
        }
    }
}
