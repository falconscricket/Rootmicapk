package com.example.alpha.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.AlphaInjectorApplication
import com.example.MainActivity
import com.example.R
import com.example.alpha.audio.EngineMode
import com.example.alpha.model.AlphaEffectSettings
import com.example.alpha.model.PopupBehaviorMode
import com.example.alpha.model.TargetAppProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class FloatingServiceState(
    val isRunning: Boolean = false,
    val isOverlayShowing: Boolean = false,
    val isExpanded: Boolean = false,
    val currentForegroundPackage: String? = null,
    val matchedTargetApp: TargetAppProfile? = null
)

class FloatingPopupService : Service() {

    companion object {
        const val ACTION_START_MONITOR = "com.example.alpha.action.START_MONITOR"
        const val ACTION_STOP_MONITOR = "com.example.alpha.action.STOP_MONITOR"
        const val ACTION_SHOW_POPUP_NOW = "com.example.alpha.action.SHOW_POPUP_NOW"
        const val ACTION_HIDE_POPUP = "com.example.alpha.action.HIDE_POPUP"
        const val ACTION_TOGGLE_EXPAND = "com.example.alpha.action.TOGGLE_EXPAND"
        private const val NOTIFICATION_ID = 3230

        private val _serviceState = MutableStateFlow(FloatingServiceState())
        val serviceState: StateFlow<FloatingServiceState> = _serviceState.asStateFlow()

        fun startMonitor(context: Context) {
            val intent = Intent(context, FloatingPopupService::class.java).apply {
                action = ACTION_START_MONITOR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun showPopupNow(context: Context) {
            val intent = Intent(context, FloatingPopupService::class.java).apply {
                action = ACTION_SHOW_POPUP_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingPopupService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    private lateinit var app: AlphaInjectorApplication
    private lateinit var windowManager: WindowManager

    private var rootContainer: FrameLayout? = null
    private var orbView: LinearLayout? = null
    private var panelView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var targetApps: List<TargetAppProfile> = emptyList()
    private var currentEffects: AlphaEffectSettings = AlphaEffectSettings()
    private var popupBehavior: PopupBehaviorMode = PopupBehaviorMode.HIDE_COMPLETELY
    private var autoShowPopup: Boolean = true
    private var manualPinnedPopup: Boolean = false
    private var savedX: Int = 36
    private var savedY: Int = 220

    private var activeTargetPkg: String? = null
    private var isExpanded: Boolean = true

    // Dynamic UI references inside floating panel
    private var targetBadgeView: TextView? = null
    private var masterSwitch: Switch? = null
    private var previewBtn: TextView? = null
    private val presetChips = mutableMapOf<String, TextView>()
    private val sliderSeekBars = mutableMapOf<String, SeekBar>()
    private val sliderValueLabels = mutableMapOf<String, TextView>()

    override fun onCreate() {
        super.onCreate()
        app = application as AlphaInjectorApplication
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        serviceScope.launch {
            app.settingsManager.targetAppsFlow.collect { apps ->
                targetApps = apps
            }
        }
        serviceScope.launch {
            app.settingsManager.currentEffectsFlow.collect { effects ->
                currentEffects = effects
                app.audioEngine.updateSettings(effects)
                syncOverlayControlsWithEffects()
            }
        }
        serviceScope.launch {
            app.settingsManager.globalSettingsFlow.collect { global ->
                popupBehavior = global.popupBehavior
                autoShowPopup = global.autoShowPopup
                savedX = global.popupX
                savedY = global.popupY
            }
        }
        serviceScope.launch {
            app.audioEngine.state.collect { audioState ->
                previewBtn?.let { btn ->
                    val running = audioState.mode == EngineMode.LIVE_MONITOR
                    btn.text = if (running) "STOP MIC PREVIEW" else "LIVE MIC PREVIEW"
                    btn.background = makePillBg(
                        if (running) Color.parseColor("#FF5252") else Color.parseColor("#00E676"),
                        dp(10)
                    )
                    btn.setTextColor(Color.parseColor("#070A10"))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundNotification("Monitoring foreground apps for ALPHA V32.3")
        _serviceState.value = _serviceState.value.copy(isRunning = true)

        when (intent?.action) {
            ACTION_STOP_MONITOR -> {
                manualPinnedPopup = false
                removeOverlay()
                serviceScope.launch { app.settingsManager.setMonitoringEnabled(false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE_POPUP -> {
                manualPinnedPopup = false
                removeOverlay()
            }
            ACTION_SHOW_POPUP_NOW -> {
                manualPinnedPopup = true
                if (Settings.canDrawOverlays(this)) {
                    showOrUpdateOverlay(expanded = true, targetProfile = targetApps.firstOrNull())
                }
                startForegroundLoop()
            }
            ACTION_TOGGLE_EXPAND -> {
                if (rootContainer != null) {
                    toggleExpanded(!isExpanded)
                }
            }
            else -> {
                serviceScope.launch { app.settingsManager.setMonitoringEnabled(true) }
                startForegroundLoop()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        _serviceState.value = FloatingServiceState(isRunning = false)
    }

    private fun startForegroundLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                val fgPkg = app.foregroundMonitor.getCurrentForegroundPackage()
                val matched = if (fgPkg != null) {
                    targetApps.firstOrNull { it.packageName == fgPkg && it.enabled && it.popupEnabled }
                } else null

                _serviceState.value = _serviceState.value.copy(
                    currentForegroundPackage = fgPkg,
                    matchedTargetApp = matched
                )

                val canOverlay = Settings.canDrawOverlays(this@FloatingPopupService)
                if (canOverlay && autoShowPopup) {
                    if (matched != null) {
                        val switchedApp = activeTargetPkg != matched.packageName
                        activeTargetPkg = matched.packageName
                        if (switchedApp) {
                            app.settingsManager.saveCurrentEffects(matched.lastSettings, matched.packageName)
                        }
                        if (rootContainer == null) {
                            showOrUpdateOverlay(expanded = matched.autoExpandPopup, targetProfile = matched)
                        } else if (switchedApp) {
                            updateTargetBadge(matched)
                            if (matched.autoExpandPopup && !isExpanded) {
                                toggleExpanded(true)
                            }
                        }
                    } else if (!manualPinnedPopup) {
                        if (fgPkg != null && fgPkg != packageName) {
                            when (popupBehavior) {
                                PopupBehaviorMode.HIDE_COMPLETELY -> {
                                    activeTargetPkg = null
                                    removeOverlay()
                                }
                                PopupBehaviorMode.MINIMIZE_TO_ORB -> {
                                    if (rootContainer != null && isExpanded) {
                                        toggleExpanded(false)
                                    }
                                }
                            }
                        }
                    }
                }
                delay(650L)
            }
        }
    }

    private fun showOrUpdateOverlay(expanded: Boolean, targetProfile: TargetAppProfile?) {
        if (!Settings.canDrawOverlays(this)) return
        if (rootContainer == null) {
            buildOverlayView()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }
            layoutParams = lp
            runCatching {
                windowManager.addView(rootContainer, lp)
                animateEntrance(rootContainer!!)
            }
        }
        updateTargetBadge(targetProfile)
        toggleExpanded(expanded)
        _serviceState.value = _serviceState.value.copy(
            isOverlayShowing = true,
            isExpanded = expanded
        )
    }

    private fun removeOverlay() {
        rootContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootContainer = null
        orbView = null
        panelView = null
        layoutParams = null
        _serviceState.value = _serviceState.value.copy(
            isOverlayShowing = false,
            isExpanded = false
        )
    }

    private fun toggleExpanded(expand: Boolean) {
        isExpanded = expand
        orbView?.visibility = if (expand) View.GONE else View.VISIBLE
        panelView?.visibility = if (expand) View.VISIBLE else View.GONE
        val targetView = if (expand) panelView else orbView
        targetView?.let { animateEntrance(it) }
        _serviceState.value = _serviceState.value.copy(isExpanded = expand)
    }

    private fun updateTargetBadge(targetProfile: TargetAppProfile?) {
        val label = targetProfile?.displayName ?: "Universal Ready"
        targetBadgeView?.text = "TARGET: $label"
    }

    private fun animateEntrance(view: View) {
        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.9f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.9f, 1f)
            )
            duration = 190L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun buildOverlayView() {
        val container = FrameLayout(this)

        // 1. Floating ALPHA Orb (minimized state)
        val orb = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EE0B0F17"))
                setStroke(dp(2), Color.parseColor("#00E5FF"))
            }
            elevation = dp(10).toFloat()
            addView(TextView(context).apply {
                text = "α"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#00E5FF"))
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "V32.3"
                textSize = 8.5f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#00E676"))
                gravity = Gravity.CENTER
            })
        }
        attachDragTouchListener(orb) {
            toggleExpanded(true)
        }

        // 2. Expanded ALPHA MIC V32.3 Popup Panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50B0F17"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#00E5FF"))
            }
            elevation = dp(14).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(304), FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // Draggable Header Bar
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "ALPHA MIC V32.3"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        })
        val badge = TextView(this).apply {
            text = "TARGET: Universal Ready"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#9AA8C2"))
        }
        targetBadgeView = badge
        titleCol.addView(badge)
        header.addView(titleCol)

        val sw = Switch(this).apply {
            isChecked = currentEffects.enabled
            setOnCheckedChangeListener { _, checked ->
                val updated = currentEffects.copy(enabled = checked)
                saveEffectsAsync(updated)
            }
        }
        masterSwitch = sw
        header.addView(sw)

        val minBtn = TextView(this).apply {
            text = " — "
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F0F4FC"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = makePillBg(Color.parseColor("#1E2A40"), dp(8))
            setOnClickListener { toggleExpanded(false) }
        }
        val minLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(6)
        }
        header.addView(minBtn, minLp)

        val closeBtn = TextView(this).apply {
            text = " ✕ "
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF5252"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = makePillBg(Color.parseColor("#420E14"), dp(8))
            setOnClickListener {
                manualPinnedPopup = false
                removeOverlay()
            }
        }
        val closeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(6)
        }
        header.addView(closeBtn, closeLp)
        attachDragTouchListener(header, onClick = null)
        panel.addView(header)

        // Preset strip (HorizontalScrollView)
        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(2), 0, dp(8))
        }
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetChips.clear()
        app.settingsManager.presets.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset.name
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    saveEffectsAsync(preset.toSettings())
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
            presetChips[preset.id] = chip
            presetRow.addView(chip, lp)
        }
        presetScroll.addView(presetRow)
        panel.addView(presetScroll)

        // Scrollable DSP Controls inside popup
        val controlsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(245))
        }
        val controlsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }
        sliderSeekBars.clear()
        sliderValueLabels.clear()

        controlsCol.addView(createSliderRow("gain", "Gain", 0, 300, (currentEffects.gain * 100).roundToInt()) { p ->
            val v = p / 100f
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", gain = v))
            "${p}%"
        })
        controlsCol.addView(createSliderRow("pitch", "Pitch Shift", 0, 240, ((currentEffects.pitchSemitones + 12f) * 10).roundToInt()) { p ->
            val st = (p - 120) / 10f
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", pitchSemitones = st))
            "${if (st >= 0) "+" else ""}${"%.1f".format(st)} st"
        })
        controlsCol.addView(createSliderRow("echo", "Echo Mix", 0, 100, (currentEffects.echoMix * 100).roundToInt()) { p ->
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", echoMix = p / 100f))
            "$p%"
        })
        controlsCol.addView(createSliderRow("distortion", "Distortion", 0, 100, (currentEffects.distortion * 100).roundToInt()) { p ->
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", distortion = p / 100f))
            "$p%"
        })
        controlsCol.addView(createSliderRow("noise", "Noise Gate", 0, 100, (currentEffects.noiseGate * 100).roundToInt()) { p ->
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", noiseGate = p / 100f))
            "$p%"
        })
        controlsCol.addView(createSliderRow("robot", "Robot FX", 0, 100, (currentEffects.robotMix * 100).roundToInt()) { p ->
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", robotMix = p / 100f))
            "$p%"
        })
        controlsCol.addView(createSliderRow("monster", "Monster FX", 0, 100, (currentEffects.monsterAmount * 100).roundToInt()) { p ->
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", monsterAmount = p / 100f))
            "$p%"
        })
        controlsCol.addView(createSliderRow("eqLow", "EQ Bass", 0, 240, ((currentEffects.eqLowDb + 12f) * 10).roundToInt()) { p ->
            val db = (p - 120) / 10f
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", eqLowDb = db))
            "${if (db >= 0) "+" else ""}${"%.1f".format(db)} dB"
        })
        controlsCol.addView(createSliderRow("eqHigh", "EQ Treble", 0, 240, ((currentEffects.eqHighDb + 12f) * 10).roundToInt()) { p ->
            val db = (p - 120) / 10f
            saveEffectsAsync(currentEffects.copy(presetId = "custom", presetName = "Custom V32.3", eqHighDb = db))
            "${if (db >= 0) "+" else ""}${"%.1f".format(db)} dB"
        })

        controlsScroll.addView(controlsCol)
        panel.addView(controlsScroll)

        // Bottom Action Bar
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val preview = TextView(this).apply {
            text = "LIVE MIC PREVIEW"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = makePillBg(Color.parseColor("#00E676"), dp(10))
            setTextColor(Color.parseColor("#070A10"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (app.audioEngine.state.value.mode == EngineMode.LIVE_MONITOR) {
                    app.audioEngine.stopAll()
                } else if (app.audioEngine.hasRecordPermission()) {
                    app.audioEngine.startLiveMonitor()
                } else {
                    val launchApp = Intent(this@FloatingPopupService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(launchApp)
                }
            }
        }
        previewBtn = preview
        footer.addView(preview)

        val openAppBtn = TextView(this).apply {
            text = "OPEN APP"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = makePillBg(Color.parseColor("#1E2A40"), dp(10))
            setTextColor(Color.parseColor("#00E5FF"))
            setOnClickListener {
                startActivity(Intent(this@FloatingPopupService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        val openLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        }
        footer.addView(openAppBtn, openLp)
        panel.addView(footer)

        container.addView(orb)
        container.addView(panel)
        orbView = orb
        panelView = panel
        rootContainer = container

        syncOverlayControlsWithEffects()
    }

    private fun createSliderRow(
        key: String,
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        onValueChanged: (Int) -> String
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(4))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nameTv = TextView(this).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.parseColor("#F0F4FC"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(this).apply {
            text = formatInitialValue(key, initial)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00E5FF"))
        }
        top.addView(nameTv)
        top.addView(valTv)
        row.addView(top)

        val seekBar = SeekBar(this).apply {
            this.max = max
            progress = initial.coerceIn(min, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        valTv.text = onValueChanged(progress)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(seekBar)
        sliderSeekBars[key] = seekBar
        sliderValueLabels[key] = valTv
        return row
    }

    private fun formatInitialValue(key: String, progress: Int): String = when (key) {
        "gain", "echo", "distortion", "noise", "robot", "monster" -> "$progress%"
        "pitch" -> {
            val st = (progress - 120) / 10f
            "${if (st >= 0) "+" else ""}${"%.1f".format(st)} st"
        }
        else -> {
            val db = (progress - 120) / 10f
            "${if (db >= 0) "+" else ""}${"%.1f".format(db)} dB"
        }
    }

    private fun syncOverlayControlsWithEffects() {
        val e = currentEffects
        masterSwitch?.let { sw ->
            if (sw.isChecked != e.enabled) sw.isChecked = e.enabled
        }
        presetChips.forEach { (id, tv) ->
            val selected = id == e.presetId
            tv.background = makePillBg(
                if (selected) Color.parseColor("#00E5FF") else Color.parseColor("#151E2E"),
                dp(8)
            )
            tv.setTextColor(if (selected) Color.parseColor("#070A10") else Color.parseColor("#9AA8C2"))
        }
        updateSeekIfIdle("gain", (e.gain * 100).roundToInt())
        updateSeekIfIdle("pitch", ((e.pitchSemitones + 12f) * 10).roundToInt())
        updateSeekIfIdle("echo", (e.echoMix * 100).roundToInt())
        updateSeekIfIdle("distortion", (e.distortion * 100).roundToInt())
        updateSeekIfIdle("noise", (e.noiseGate * 100).roundToInt())
        updateSeekIfIdle("robot", (e.robotMix * 100).roundToInt())
        updateSeekIfIdle("monster", (e.monsterAmount * 100).roundToInt())
        updateSeekIfIdle("eqLow", ((e.eqLowDb + 12f) * 10).roundToInt())
        updateSeekIfIdle("eqHigh", ((e.eqHighDb + 12f) * 10).roundToInt())
    }

    private fun updateSeekIfIdle(key: String, value: Int) {
        val sb = sliderSeekBars[key] ?: return
        if (!sb.isPressed && sb.progress != value) {
            sb.progress = value.coerceIn(0, sb.max)
            sliderValueLabels[key]?.text = formatInitialValue(key, sb.progress)
        }
    }

    private fun saveEffectsAsync(updated: AlphaEffectSettings) {
        currentEffects = updated
        app.audioEngine.updateSettings(updated)
        serviceScope.launch {
            app.settingsManager.saveCurrentEffects(updated, activeTargetPkg)
        }
    }

    private fun attachDragTouchListener(view: View, onClick: (() -> Unit)?) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).roundToInt()
                    val dy = (event.rawY - touchY).roundToInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    lp.x = initialX + dx
                    lp.y = initialY + dy
                    rootContainer?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onClick?.invoke()
                    } else {
                        savedX = lp.x
                        savedY = lp.y
                        serviceScope.launch { app.settingsManager.savePopupPosition(lp.x, lp.y) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun makePillBg(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun dp(value: Number): Int {
        return (value.toFloat() * resources.displayMetrics.density).roundToInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun ensureForegroundNotification(contentText: String) {
        val openPending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val showPopupPending = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingPopupService::class.java).apply { action = ACTION_SHOW_POPUP_NOW },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            this,
            3,
            Intent(this, FloatingPopupService::class.java).apply { action = ACTION_STOP_MONITOR },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ALPHA MIC V32.3 Companion")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Show Popup", showPopupPending)
            .addAction(0, "Stop", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
