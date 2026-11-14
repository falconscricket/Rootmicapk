package com.example.alpha.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alpha.audio.AudioEngineState
import com.example.alpha.audio.EngineMode
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.model.AlphaPresetConfigDto
import com.example.alpha.ui.components.AlphaSliderControl
import com.example.alpha.ui.components.AlphaStatusPill
import com.example.alpha.ui.components.AlphaStudioCard
import com.example.alpha.ui.components.AlphaWaveformVisualizer
import com.example.ui.theme.AlphaAmber
import com.example.ui.theme.AlphaCrimson
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaEmerald
import com.example.ui.theme.AlphaObsidianBg
import com.example.ui.theme.AlphaSurfaceElevated
import com.example.ui.theme.AlphaTextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OriginalPopupScreen(
    effects: AlphaEffectSettings,
    presets: List<AlphaPresetConfigDto>,
    audioState: AudioEngineState,
    bundledAssets: List<String>,
    onReadAsset: (String) -> String,
    onApplyPreset: (AlphaPresetConfigDto) -> Unit,
    onUpdateEffects: (AlphaEffectSettings) -> Unit,
    onLaunchFloatingOverlay: () -> Unit,
    onToggleLivePreview: () -> Unit
) {
    var showSourceInspector by remember { mutableStateOf(false) }
    var selectedAsset by remember { mutableStateOf("effects-config.json") }
    val isLive = audioState.mode == EngineMode.LIVE_MONITOR

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Authentic ALPHA Mic Extension V32.3 Embedded Popup Console
                AlphaStudioCard(
                    borderColor = if (effects.enabled) AlphaCyan else AlphaAmber,
                    containerColor = AlphaObsidianBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.alpha_mic_icon_1794640215553),
                                contentDescription = "ALPHA V32.3 Logo",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ALPHA MIC",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = AlphaCyan
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AlphaCyan.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, AlphaCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "V32.3",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AlphaCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Original Extension Interface • Native Kotlin Port",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlphaTextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = effects.enabled,
                            onCheckedChange = { onUpdateEffects(effects.copy(enabled = it)) },
                            modifier = Modifier.testTag("popup_master_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    AlphaWaveformVisualizer(bars = audioState.waveform, isActive = isLive)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLaunchFloatingOverlay,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("popup_launch_overlay_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AlphaCyan)
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Float Overlay")
                        }
                        Button(
                            onClick = onToggleLivePreview,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("popup_live_preview_button"),
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
                            Text(if (isLive) "Stop Preview" else "Live Preview")
                        }
                    }
                }
            }

            item {
                // Original V32.3 Voice Effect Preset Grid
                AlphaStudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ALPHA V32.3 Voice Effects", style = MaterialTheme.typography.titleMedium)
                        AlphaStatusPill(
                            label = effects.presetName.uppercase(),
                            accentColor = if (effects.enabled) AlphaCyan else AlphaAmber
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val selected = effects.presetId == preset.id
                            FilterChip(
                                selected = selected,
                                onClick = { onApplyPreset(preset) },
                                label = {
                                    Text("${preset.name} [${preset.badge}]")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AlphaCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = AlphaCyan
                                ),
                                modifier = Modifier.testTag("preset_chip_${preset.id}")
                            )
                        }
                    }
                }
            }

            item {
                // Original ALPHA Sliders & Sound Controls
                AlphaStudioCard {
                    Text("Original ALPHA Controls & Sliders", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    AlphaSliderControl(
                        label = "Master Gain",
                        valueText = "${(effects.gain * 100).roundToInt()}%",
                        value = effects.gain,
                        valueRange = 0f..3.0f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", gain = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Pitch Shift",
                        valueText = "${if (effects.pitchSemitones >= 0) "+" else ""}${"%.1f".format(effects.pitchSemitones)} st",
                        value = effects.pitchSemitones,
                        valueRange = -12f..12f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", pitchSemitones = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Echo Mix",
                        valueText = "${(effects.echoMix * 100).roundToInt()}%",
                        value = effects.echoMix,
                        valueRange = 0f..1f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", echoMix = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Echo Delay Time",
                        valueText = "${effects.echoDelayMs} ms",
                        value = effects.echoDelayMs.toFloat(),
                        valueRange = 40f..600f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", echoDelayMs = it.roundToInt()))
                        }
                    )
                    AlphaSliderControl(
                        label = "Distortion / Overdrive",
                        valueText = "${(effects.distortion * 100).roundToInt()}%",
                        value = effects.distortion,
                        valueRange = 0f..1f,
                        accentColor = AlphaAmber,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", distortion = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Noise Processing / Gate",
                        valueText = "${(effects.noiseGate * 100).roundToInt()}%",
                        value = effects.noiseGate,
                        valueRange = 0f..1f,
                        accentColor = AlphaEmerald,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", noiseGate = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Robot Effect",
                        valueText = "${(effects.robotMix * 100).roundToInt()}% (${effects.robotFreqHz.roundToInt()} Hz)",
                        value = effects.robotMix,
                        valueRange = 0f..1f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", robotMix = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "Monster Effect",
                        valueText = "${(effects.monsterAmount * 100).roundToInt()}%",
                        value = effects.monsterAmount,
                        valueRange = 0f..1f,
                        accentColor = AlphaCrimson,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", monsterAmount = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "EQ Low (140 Hz)",
                        valueText = "${if (effects.eqLowDb >= 0) "+" else ""}${"%.1f".format(effects.eqLowDb)} dB",
                        value = effects.eqLowDb,
                        valueRange = -12f..12f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", eqLowDb = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "EQ Mid (1.05 kHz)",
                        valueText = "${if (effects.eqMidDb >= 0) "+" else ""}${"%.1f".format(effects.eqMidDb)} dB",
                        value = effects.eqMidDb,
                        valueRange = -12f..12f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", eqMidDb = it))
                        }
                    )
                    AlphaSliderControl(
                        label = "EQ High (4.4 kHz)",
                        valueText = "${if (effects.eqHighDb >= 0) "+" else ""}${"%.1f".format(effects.eqHighDb)} dB",
                        value = effects.eqHighDb,
                        valueRange = -12f..12f,
                        onValueChange = {
                            onUpdateEffects(effects.copy(presetId = "custom", presetName = "Custom V32.3", eqHighDb = it))
                        }
                    )
                }
            }

            item {
                // Bundled V32.3 Assets Inspector
                AlphaStudioCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSourceInspector = !showSourceInspector },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = AlphaCyan)
                            Column {
                                Text("Bundled V32.3 Extension Assets", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${bundledAssets.size} files in assets/alpha/original-extension-assets/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlphaTextSecondary
                                )
                            }
                        }
                        OutlinedButton(onClick = { showSourceInspector = !showSourceInspector }) {
                            Text(if (showSourceInspector) "Hide" else "Inspect")
                        }
                    }

                    AnimatedVisibility(visible = showSourceInspector) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bundledAssets.forEach { asset ->
                                    FilterChip(
                                        selected = selectedAsset == asset,
                                        onClick = { selectedAsset = asset },
                                        label = { Text(asset, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AlphaSurfaceElevated)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = onReadAsset(selectedAsset),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AlphaCyan,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
