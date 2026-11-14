package com.example.alpha.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AlphaInjectorApplication
import com.example.alpha.audio.AudioEngineState
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.model.AlphaPresetConfigDto
import com.example.alpha.model.CompatibilityReport
import com.example.alpha.model.GlobalInjectorSettings
import com.example.alpha.model.InstalledAppInfo
import com.example.alpha.model.KnownTargets
import com.example.alpha.model.PopupBehaviorMode
import com.example.alpha.model.TargetAppProfile
import com.example.alpha.service.FloatingPopupService
import com.example.alpha.service.FloatingServiceState
import com.example.alpha.shizuku.ShizukuUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionChecklistState(
    val hasRecordAudio: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasPostNotifications: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = false
)

class AlphaViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AlphaInjectorApplication
    private val settingsManager = app.settingsManager
    private val audioEngine = app.audioEngine
    private val shizukuManager = app.shizukuManager
    private val foregroundMonitor = app.foregroundMonitor
    private val compatibilityManager = app.compatibilityManager

    val globalSettings: StateFlow<GlobalInjectorSettings> = settingsManager.globalSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalInjectorSettings())

    val targetApps: StateFlow<List<TargetAppProfile>> = settingsManager.targetAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentEffects: StateFlow<AlphaEffectSettings> = settingsManager.currentEffectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlphaEffectSettings())

    val audioState: StateFlow<AudioEngineState> = audioEngine.state
    val shizukuState: StateFlow<ShizukuUiState> = shizukuManager.state
    val serviceState: StateFlow<FloatingServiceState> = FloatingPopupService.serviceState

    val presets: List<AlphaPresetConfigDto> = settingsManager.presets
    val bundledAssetFiles: List<String> = settingsManager.listBundledAssets()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionChecklistState())
    val permissions: StateFlow<PermissionChecklistState> = _permissions.asStateFlow()

    private val _hardwareCapabilities = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val hardwareCapabilities: StateFlow<List<Pair<String, String>>> = _hardwareCapabilities.asStateFlow()

    private val _selectedCompatibility = MutableStateFlow<CompatibilityReport?>(null)
    val selectedCompatibility: StateFlow<CompatibilityReport?> = _selectedCompatibility.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.currentEffectsFlow.collect { effects ->
                audioEngine.updateSettings(effects)
            }
        }
        refreshAllDiagnostics()
        loadInstalledApps()
    }

    fun refreshAllDiagnostics() {
        val ctx = getApplication<Application>()
        val recordGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val overlayGranted = Settings.canDrawOverlays(ctx)
        val usageGranted = foregroundMonitor.hasUsageStatsPermission()
        val pm = ctx.getSystemService(PowerManager::class.java)
        val ignoringBattery = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

        _permissions.value = PermissionChecklistState(
            hasRecordAudio = recordGranted,
            hasOverlay = overlayGranted,
            hasUsageAccess = usageGranted,
            hasPostNotifications = notifGranted,
            isBatteryOptimizationIgnored = ignoringBattery
        )
        _hardwareCapabilities.value = compatibilityManager.getHardwareAudioCapabilities()
        audioEngine.refreshDeviceInfo()
        shizukuManager.refreshState()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = compatibilityManager.queryLaunchableApps()
        }
    }

    fun applyPreset(preset: AlphaPresetConfigDto) {
        updateEffects(preset.toSettings())
    }

    fun updateEffects(newSettings: AlphaEffectSettings) {
        audioEngine.updateSettings(newSettings)
        viewModelScope.launch {
            val activePkg = serviceState.value.matchedTargetApp?.packageName
                ?: globalSettings.value.activeProfilePackage
            settingsManager.saveCurrentEffects(newSettings, activePkg)
        }
    }

    fun addTargetApp(info: InstalledAppInfo) {
        viewModelScope.launch {
            settingsManager.addOrUpdateTargetApp(
                TargetAppProfile(
                    packageName = info.packageName,
                    displayName = info.displayName,
                    enabled = true,
                    popupEnabled = true,
                    autoExpandPopup = true,
                    lastSettings = currentEffects.value
                )
            )
            _statusBanner.value = "Added ${info.displayName} to My Apps"
        }
    }

    fun removeTargetApp(packageName: String) {
        viewModelScope.launch {
            settingsManager.removeTargetApp(packageName)
            _statusBanner.value = "Removed app from My Apps"
        }
    }

    fun toggleTargetAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTargetAppEnabled(packageName, enabled)
        }
    }

    fun toggleTargetAppPopup(packageName: String, popupEnabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTargetAppPopupPreference(packageName, popupEnabled)
        }
    }

    fun inspectCompatibility(packageName: String, displayName: String) {
        val perms = _permissions.value
        _selectedCompatibility.value = compatibilityManager.evaluateCompatibility(
            packageName = packageName,
            displayName = displayName,
            hasOverlayPermission = perms.hasOverlay,
            hasUsageAccess = perms.hasUsageAccess,
            hasRecordPermission = perms.hasRecordAudio,
            shizukuState = shizukuState.value
        )
        if (shizukuState.value.userServiceBound) {
            shizukuManager.inspectTargetPackage(packageName)
        }
    }

    fun clearCompatibilityDialog() {
        _selectedCompatibility.value = null
    }

    fun toggleMonitoringService(enable: Boolean) {
        val ctx = getApplication<Application>()
        if (enable) {
            if (!Settings.canDrawOverlays(ctx)) {
                _statusBanner.value = "Grant 'Display over other apps' permission first."
                return
            }
            FloatingPopupService.startMonitor(ctx)
            _statusBanner.value = "Foreground app monitor started"
        } else {
            FloatingPopupService.stopService(ctx)
            _statusBanner.value = "Foreground app monitor stopped"
        }
    }

    fun showFloatingPopupNow() {
        val ctx = getApplication<Application>()
        if (!Settings.canDrawOverlays(ctx)) {
            _statusBanner.value = "Enable 'Display over other apps' in Permissions to launch floating popup."
            return
        }
        FloatingPopupService.showPopupNow(ctx)
    }

    fun launchTargetApp(packageName: String) {
        val ctx = getApplication<Application>()
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            _statusBanner.value = "$packageName is not installed on this device"
            return
        }
        if (Settings.canDrawOverlays(ctx)) {
            FloatingPopupService.startMonitor(ctx)
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launchIntent)
    }

    fun toggleLiveMonitor() {
        if (audioState.value.mode == com.example.alpha.audio.EngineMode.LIVE_MONITOR) {
            audioEngine.stopAll()
        } else {
            val gs = globalSettings.value
            audioEngine.startLiveMonitor(gs.preferredSampleRateHz, gs.lowLatencyMode)
        }
    }

    fun toggleRecordingTest() {
        if (audioState.value.mode == com.example.alpha.audio.EngineMode.RECORDING_TEST) {
            audioEngine.stopAll()
        } else {
            val gs = globalSettings.value
            audioEngine.startRecordingTest(gs.preferredSampleRateHz, gs.lowLatencyMode)
        }
    }

    fun playLastRecording() {
        audioEngine.playLastRecording()
    }

    fun stopAudioEngine() {
        audioEngine.stopAll()
    }

    fun requestShizukuPermission() {
        shizukuManager.requestShizukuPermission()
    }

    fun refreshShizukuDiagnostics() {
        shizukuManager.refreshAudioDiagnostics()
    }

    fun openShizukuAppOrStore() {
        val ctx = getApplication<Application>()
        val launch = ctx.packageManager.getLaunchIntentForPackage(KnownTargets.SHIZUKU_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
        } else {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(web)
        }
    }

    fun setPopupBehaviorMode(mode: PopupBehaviorMode) {
        viewModelScope.launch { settingsManager.setPopupBehavior(mode) }
    }

    fun setAutoShowPopup(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateGlobalSettings { it.copy(autoShowPopup = enabled) } }
    }

    fun setPreferredSampleRate(rate: Int) {
        viewModelScope.launch { settingsManager.updateGlobalSettings { it.copy(preferredSampleRateHz = rate) } }
    }

    fun setLowLatencyMode(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateGlobalSettings { it.copy(lowLatencyMode = enabled) } }
    }

    fun resetPopupPosition() {
        viewModelScope.launch {
            settingsManager.savePopupPosition(36, 220)
            _statusBanner.value = "Popup coordinates reset to (36, 220)"
        }
    }

    fun readBundledAsset(fileName: String): String = settingsManager.readBundledAssetText(fileName)

    fun clearBanner() {
        _statusBanner.value = null
    }
}
