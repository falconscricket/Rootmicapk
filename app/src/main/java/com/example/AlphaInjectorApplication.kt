package com.example

import android.app.Application
import com.example.alpha.audio.AlphaAudioEngine
import com.example.alpha.compatibility.CompatibilityManager
import com.example.alpha.data.SettingsManager
import com.example.alpha.monitor.ForegroundAppMonitor
import com.example.alpha.shizuku.ShizukuManager

class AlphaInjectorApplication : Application() {

    lateinit var settingsManager: SettingsManager
        private set
    lateinit var audioEngine: AlphaAudioEngine
        private set
    lateinit var shizukuManager: ShizukuManager
        private set
    lateinit var foregroundMonitor: ForegroundAppMonitor
        private set
    lateinit var compatibilityManager: CompatibilityManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        audioEngine = AlphaAudioEngine(this)
        shizukuManager = ShizukuManager(this)
        foregroundMonitor = ForegroundAppMonitor(this)
        compatibilityManager = CompatibilityManager(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        audioEngine.stopAll()
        shizukuManager.unregisterListeners()
    }
}
