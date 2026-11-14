package com.example.alpha.compatibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.alpha.model.CompatibilityLevel
import com.example.alpha.model.CompatibilityReport
import com.example.alpha.model.InstalledAppInfo
import com.example.alpha.model.KnownTargets
import com.example.alpha.shizuku.ShizukuUiState

class CompatibilityManager(private val context: Context) {

    private val packageManager = context.packageManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun queryLaunchableApps(): List<InstalledAppInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        val discovered = resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.ifBlank { pkg } ?: pkg
                val appInfo = info.activityInfo?.applicationInfo
                val isSystem = appInfo != null &&
                    ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                InstalledAppInfo(
                    packageName = pkg,
                    displayName = label,
                    isSystemApp = isSystem,
                    isInstalled = true,
                    isRecommendedTarget = KnownTargets.recommendedNames.containsKey(pkg)
                )
            }
            .distinctBy { it.packageName }
            .toMutableList()

        // Ensure ShareChat, WhatsApp, and Instagram appear cleanly even if not yet installed on test device
        KnownTargets.recommendedNames.forEach { (pkg, name) ->
            if (discovered.none { it.packageName == pkg }) {
                val installed = isPackageInstalled(pkg)
                discovered.add(
                    InstalledAppInfo(
                        packageName = pkg,
                        displayName = name,
                        isSystemApp = false,
                        isInstalled = installed,
                        isRecommendedTarget = true
                    )
                )
            }
        }

        return discovered.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isRecommendedTarget }
                .thenByDescending { it.isInstalled }
                .thenBy { it.displayName.lowercase() }
        )
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun evaluateCompatibility(
        packageName: String,
        displayName: String,
        hasOverlayPermission: Boolean,
        hasUsageAccess: Boolean,
        hasRecordPermission: Boolean,
        shizukuState: ShizukuUiState
    ): CompatibilityReport {
        val popupSupported = hasOverlayPermission && hasUsageAccess
        val localPreviewSupported = hasRecordPermission
        val isShareChat = packageName == KnownTargets.SHARECHAT_PACKAGE

        val summary = when {
            isShareChat -> "Popup & Local 48kHz Preview ready; live ShareChat Chat Room mic replacement is blocked by Android non-root audio isolation."
            else -> "Popup & Local 48kHz Preview supported; third-party app mic stream remains isolated on non-root stock Android."
        }

        val details = buildString {
            appendLine("Target: $displayName ($packageName)")
            appendLine("• Floating ALPHA V32.3 Popup: ${if (popupSupported) "Supported & Ready" else "Requires Overlay + Usage Access"}")
            appendLine("• Local 48kHz Audio Preview: ${if (localPreviewSupported) "Supported (AudioRecord/AudioTrack)" else "Requires Microphone Permission"}")
            appendLine("• Shizuku Status: ${if (shizukuState.hasPermission) "Authorized (UID ${shizukuState.shizukuUid}) — Diagnostics Ready" else "Fallback Mode (Rootless)"}")
            appendLine("• Live Mic Stream Injection: Unavailable on stock non-root Android (API ${Build.VERSION.SDK_INT}).")
            appendLine("  Why: Android AudioPolicy isolates VOICE_COMMUNICATION / AudioRecord buffers per app UID. Neither Usage Access nor Shizuku shell (UID 2000) can replace another app's private microphone stream without a custom system Audio HAL, root module, or external USB/TRRS audio loopback interface.")
            if (isShareChat) {
                appendLine("• ShareChat Note: When a ShareChat Chat Room acquires VOICE_COMMUNICATION capture, Android may silence concurrent background AudioRecord sessions (detected live via AudioRecordingCallback).")
            }
        }.trim()

        return CompatibilityReport(
            packageName = packageName,
            displayName = displayName,
            popupSupported = popupSupported,
            localPreviewSupported = localPreviewSupported,
            liveAudioIntegration = CompatibilityLevel.UNAVAILABLE_NON_ROOT,
            summary = summary,
            technicalDetails = details
        )
    }

    fun getHardwareAudioCapabilities(): List<Pair<String, String>> {
        val hasLowLatency = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        val outputRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "48000"
        val framesPerBuf = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "256"
        val inputCount = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).size
        val outputCount = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).size
        val externalLoopback = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG
        }

        return listOf(
            "Device / OS" to "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
            "Low-Latency Audio" to if (hasLowLatency) "Hardware Supported" else "Standard PCM Path",
            "Pro Audio HAL" to if (hasProAudio) "Supported" else "Not Advertised",
            "Native Output Rate" to "$outputRate Hz ($framesPerBuf frames/buf)",
            "Audio Endpoints" to "$inputCount inputs / $outputCount outputs",
            "External USB/Line Input" to if (externalLoopback) "Detected" else "None connected"
        )
    }
}
