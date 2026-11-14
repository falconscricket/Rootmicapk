package com.example.alpha.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.model.AlphaPresetConfigDto
import com.example.alpha.model.EffectsConfigRootDto
import com.example.alpha.model.GlobalInjectorSettings
import com.example.alpha.model.KnownTargets
import com.example.alpha.model.PopupBehaviorMode
import com.example.alpha.model.TargetAppProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.alphaDataStore: DataStore<Preferences> by preferencesDataStore(name = "alpha_injector_prefs")

class SettingsManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private object Keys {
        val GLOBAL_SETTINGS = stringPreferencesKey("global_settings_json")
        val TARGET_APPS = stringPreferencesKey("target_apps_json")
        val CURRENT_EFFECTS = stringPreferencesKey("current_effects_json")
    }

    val bundledConfig: EffectsConfigRootDto by lazy { loadBundledEffectsConfig() }

    val presets: List<AlphaPresetConfigDto>
        get() = bundledConfig.presets

    val globalSettingsFlow: Flow<GlobalInjectorSettings> = context.alphaDataStore.data.map { prefs ->
        prefs[Keys.GLOBAL_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<GlobalInjectorSettings>(raw) }.getOrNull()
        } ?: GlobalInjectorSettings()
    }

    val targetAppsFlow: Flow<List<TargetAppProfile>> = context.alphaDataStore.data.map { prefs ->
        prefs[Keys.TARGET_APPS]?.let { raw ->
            runCatching { json.decodeFromString<List<TargetAppProfile>>(raw) }.getOrNull()
        } ?: defaultSeedApps()
    }

    val currentEffectsFlow: Flow<AlphaEffectSettings> = context.alphaDataStore.data.map { prefs ->
        prefs[Keys.CURRENT_EFFECTS]?.let { raw ->
            runCatching { json.decodeFromString<AlphaEffectSettings>(raw) }.getOrNull()
        } ?: AlphaEffectSettings()
    }

    suspend fun getGlobalSettings(): GlobalInjectorSettings = globalSettingsFlow.first()

    suspend fun getTargetApps(): List<TargetAppProfile> = targetAppsFlow.first()

    suspend fun getCurrentEffects(): AlphaEffectSettings = currentEffectsFlow.first()

    suspend fun updateGlobalSettings(transform: (GlobalInjectorSettings) -> GlobalInjectorSettings) {
        context.alphaDataStore.edit { prefs ->
            val current = prefs[Keys.GLOBAL_SETTINGS]?.let {
                runCatching { json.decodeFromString<GlobalInjectorSettings>(it) }.getOrNull()
            } ?: GlobalInjectorSettings()
            val updated = transform(current)
            prefs[Keys.GLOBAL_SETTINGS] = json.encodeToString(updated)
        }
    }

    suspend fun savePopupPosition(x: Int, y: Int) {
        updateGlobalSettings { it.copy(popupX = x, popupY = y) }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        updateGlobalSettings { it.copy(monitoringEnabled = enabled) }
    }

    suspend fun setPopupBehavior(mode: PopupBehaviorMode) {
        updateGlobalSettings { it.copy(popupBehavior = mode) }
    }

    suspend fun saveCurrentEffects(settings: AlphaEffectSettings, activePackage: String? = null) {
        context.alphaDataStore.edit { prefs ->
            prefs[Keys.CURRENT_EFFECTS] = json.encodeToString(settings)
            if (activePackage != null) {
                val apps = prefs[Keys.TARGET_APPS]?.let {
                    runCatching { json.decodeFromString<List<TargetAppProfile>>(it) }.getOrNull()
                } ?: defaultSeedApps()
                val updatedApps = apps.map { profile ->
                    if (profile.packageName == activePackage) {
                        profile.copy(lastSettings = settings)
                    } else profile
                }
                prefs[Keys.TARGET_APPS] = json.encodeToString(updatedApps)
            }
        }
    }

    suspend fun addOrUpdateTargetApp(profile: TargetAppProfile) {
        context.alphaDataStore.edit { prefs ->
            val current = prefs[Keys.TARGET_APPS]?.let {
                runCatching { json.decodeFromString<List<TargetAppProfile>>(it) }.getOrNull()
            } ?: defaultSeedApps()
            val exists = current.any { it.packageName == profile.packageName }
            val updated = if (exists) {
                current.map { if (it.packageName == profile.packageName) profile else it }
            } else {
                listOf(profile) + current
            }
            prefs[Keys.TARGET_APPS] = json.encodeToString(updated)
        }
    }

    suspend fun removeTargetApp(packageName: String) {
        context.alphaDataStore.edit { prefs ->
            val current = prefs[Keys.TARGET_APPS]?.let {
                runCatching { json.decodeFromString<List<TargetAppProfile>>(it) }.getOrNull()
            } ?: defaultSeedApps()
            prefs[Keys.TARGET_APPS] = json.encodeToString(current.filterNot { it.packageName == packageName })
        }
    }

    suspend fun setTargetAppEnabled(packageName: String, enabled: Boolean) {
        context.alphaDataStore.edit { prefs ->
            val current = prefs[Keys.TARGET_APPS]?.let {
                runCatching { json.decodeFromString<List<TargetAppProfile>>(it) }.getOrNull()
            } ?: defaultSeedApps()
            val updated = current.map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            }
            prefs[Keys.TARGET_APPS] = json.encodeToString(updated)
        }
    }

    suspend fun setTargetAppPopupPreference(packageName: String, popupEnabled: Boolean) {
        context.alphaDataStore.edit { prefs ->
            val current = prefs[Keys.TARGET_APPS]?.let {
                runCatching { json.decodeFromString<List<TargetAppProfile>>(it) }.getOrNull()
            } ?: defaultSeedApps()
            val updated = current.map {
                if (it.packageName == packageName) it.copy(popupEnabled = popupEnabled) else it
            }
            prefs[Keys.TARGET_APPS] = json.encodeToString(updated)
        }
    }

    fun readBundledAssetText(fileName: String): String {
        return runCatching {
            context.assets.open("alpha/original-extension-assets/$fileName")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "// Asset unavailable: $fileName (${it.message})" }
    }

    fun listBundledAssets(): List<String> {
        return runCatching {
            context.assets.list("alpha/original-extension-assets")?.sorted() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun loadBundledEffectsConfig(): EffectsConfigRootDto {
        val raw = readBundledAssetText("effects-config.json")
        return runCatching {
            json.decodeFromString<EffectsConfigRootDto>(raw)
        }.getOrElse {
            EffectsConfigRootDto(
                presets = listOf(
                    AlphaPresetConfigDto(id = "original", name = "Original Voice", badge = "CLEAN", enabled = false)
                )
            )
        }
    }

    private fun defaultSeedApps(): List<TargetAppProfile> {
        return listOf(
            TargetAppProfile(
                packageName = KnownTargets.SHARECHAT_PACKAGE,
                displayName = "ShareChat",
                enabled = true,
                popupEnabled = true,
                autoExpandPopup = true,
                lastSettings = AlphaEffectSettings(presetId = "studio_broadcast", presetName = "Studio Broadcast", gain = 1.35f, noiseGate = 0.28f)
            )
        )
    }
}
