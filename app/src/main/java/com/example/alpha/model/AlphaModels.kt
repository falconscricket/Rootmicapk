package com.example.alpha.model

import kotlinx.serialization.Serializable

@Serializable
data class AlphaEffectSettings(
    val presetId: String = "original",
    val presetName: String = "Original Voice",
    val enabled: Boolean = true,
    val gain: Float = 1.0f,            // 0.0f .. 3.0f
    val pitchSemitones: Float = 0.0f,  // -12.0f .. +12.0f
    val eqLowDb: Float = 0.0f,         // -12.0f .. +12.0f
    val eqMidDb: Float = 0.0f,         // -12.0f .. +12.0f
    val eqHighDb: Float = 0.0f,        // -12.0f .. +12.0f
    val echoMix: Float = 0.0f,         // 0.0f .. 1.0f
    val echoDelayMs: Int = 180,        // 40 .. 600 ms
    val echoFeedback: Float = 0.25f,   // 0.0f .. 0.80f
    val distortion: Float = 0.0f,      // 0.0f .. 1.0f
    val noiseGate: Float = 0.15f,      // 0.0f .. 1.0f
    val robotMix: Float = 0.0f,        // 0.0f .. 1.0f
    val robotFreqHz: Float = 85.0f,    // 30.0f .. 180.0f
    val monsterAmount: Float = 0.0f,   // 0.0f .. 1.0f
    val limiterEnabled: Boolean = true
)

@Serializable
data class AlphaPresetConfigDto(
    val id: String,
    val name: String,
    val badge: String = "FX",
    val enabled: Boolean = true,
    val gain: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val eqLowDb: Float = 0.0f,
    val eqMidDb: Float = 0.0f,
    val eqHighDb: Float = 0.0f,
    val echoMix: Float = 0.0f,
    val echoDelayMs: Int = 180,
    val echoFeedback: Float = 0.25f,
    val distortion: Float = 0.0f,
    val noiseGate: Float = 0.0f,
    val robotMix: Float = 0.0f,
    val robotFreqHz: Float = 85.0f,
    val monsterAmount: Float = 0.0f,
    val limiterEnabled: Boolean = true
) {
    fun toSettings(): AlphaEffectSettings = AlphaEffectSettings(
        presetId = id,
        presetName = name,
        enabled = enabled,
        gain = gain,
        pitchSemitones = pitchSemitones,
        eqLowDb = eqLowDb,
        eqMidDb = eqMidDb,
        eqHighDb = eqHighDb,
        echoMix = echoMix,
        echoDelayMs = echoDelayMs,
        echoFeedback = echoFeedback,
        distortion = distortion,
        noiseGate = noiseGate,
        robotMix = robotMix,
        robotFreqHz = robotFreqHz,
        monsterAmount = monsterAmount,
        limiterEnabled = limiterEnabled
    )
}

@Serializable
data class EffectsConfigRootDto(
    val extensionName: String = "ALPHA Mic Extension",
    val version: String = "32.3",
    val sampleRateHz: Int = 48000,
    val presets: List<AlphaPresetConfigDto> = emptyList()
)

@Serializable
data class TargetAppProfile(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val popupEnabled: Boolean = true,
    val autoExpandPopup: Boolean = true,
    val lastSettings: AlphaEffectSettings = AlphaEffectSettings(),
    val addedAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
enum class PopupBehaviorMode {
    MINIMIZE_TO_ORB,
    HIDE_COMPLETELY
}

@Serializable
data class GlobalInjectorSettings(
    val monitoringEnabled: Boolean = false,
    val autoShowPopup: Boolean = true,
    val popupBehavior: PopupBehaviorMode = PopupBehaviorMode.HIDE_COMPLETELY,
    val popupX: Int = 36,
    val popupY: Int = 220,
    val preferredSampleRateHz: Int = 48000,
    val lowLatencyMode: Boolean = true,
    val activeProfilePackage: String = KnownTargets.SHARECHAT_PACKAGE
)

data class InstalledAppInfo(
    val packageName: String,
    val displayName: String,
    val isSystemApp: Boolean,
    val isInstalled: Boolean,
    val isRecommendedTarget: Boolean
)

enum class CompatibilityLevel {
    SUPPORTED,
    PARTIAL_WARNING,
    UNAVAILABLE_NON_ROOT
}

data class CompatibilityReport(
    val packageName: String,
    val displayName: String,
    val popupSupported: Boolean,
    val localPreviewSupported: Boolean,
    val liveAudioIntegration: CompatibilityLevel,
    val summary: String,
    val technicalDetails: String
)

object KnownTargets {
    const val SHARECHAT_PACKAGE = "in.mohalla.sharechat"
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    val recommendedNames = mapOf(
        SHARECHAT_PACKAGE to "ShareChat",
        WHATSAPP_PACKAGE to "WhatsApp",
        INSTAGRAM_PACKAGE to "Instagram"
    )
}
