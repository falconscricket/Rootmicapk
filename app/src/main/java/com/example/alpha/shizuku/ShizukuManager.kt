package com.example.alpha.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.example.BuildConfig
import com.example.alpha.model.KnownTargets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class ShizukuConnectionStatus {
    NOT_INSTALLED,
    SERVICE_NOT_RUNNING,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    AUTHORIZED_CONNECTED
}

data class ShizukuUiState(
    val isShizukuAppInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val hasPermission: Boolean = false,
    val status: ShizukuConnectionStatus = ShizukuConnectionStatus.NOT_INSTALLED,
    val shizukuVersion: Int = -1,
    val shizukuUid: Int = -1,
    val userServiceBound: Boolean = false,
    val fallbackModeActive: Boolean = true,
    val diagnosticOutput: String = "Shizuku not connected. Running in Rootless Fallback Mode (Usage Access + Floating Popup + Local 48kHz Audio Engine)."
)

class ShizukuManager(private val context: Context) {

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 32301
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ShizukuUiState())
    val state: StateFlow<ShizukuUiState> = _state.asStateFlow()

    private var userService: IAlphaShizukuService? = null
    private var isBinding = false

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, AlphaUserService::class.java))
            .daemon(false)
            .processNameSuffix("alpha_shizuku")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = false
            userService = IAlphaShizukuService.Stub.asInterface(service)
            _state.value = _state.value.copy(
                userServiceBound = userService != null,
                fallbackModeActive = false
            )
            refreshAudioDiagnostics()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
            userService = null
            _state.value = _state.value.copy(
                userServiceBound = false,
                fallbackModeActive = true,
                diagnosticOutput = "Shizuku UserService disconnected. Fallback Mode active."
            )
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        isBinding = false
        refreshState()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            refreshState(explicitlyDenied = !granted)
        }
    }

    init {
        registerListeners()
        refreshState()
    }

    fun registerListeners() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
    }

    fun unregisterListeners() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            if (userService != null) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        }
    }

    fun refreshState(explicitlyDenied: Boolean = false) {
        val installed = isPackageInstalled(KnownTargets.SHIZUKU_PACKAGE)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        if (!installed) {
            userService = null
            _state.value = ShizukuUiState(
                isShizukuAppInstalled = false,
                isBinderAlive = false,
                hasPermission = false,
                status = ShizukuConnectionStatus.NOT_INSTALLED,
                fallbackModeActive = true,
                diagnosticOutput = "Shizuku app (moe.shizuku.privileged.api) is not installed. Fallback Mode active: Usage Access + Floating Popup + Local Audio Engine remain fully functional."
            )
            return
        }

        if (!binderAlive) {
            userService = null
            _state.value = ShizukuUiState(
                isShizukuAppInstalled = true,
                isBinderAlive = false,
                hasPermission = false,
                status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                fallbackModeActive = true,
                diagnosticOutput = "Shizuku is installed, but the Shizuku service binder is not running. Start Shizuku via Wireless Debugging (Android 11+) or ADB, or continue in Fallback Mode."
            )
            return
        }

        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        val granted = if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
            false
        } else {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        }

        val status = when {
            granted -> ShizukuConnectionStatus.AUTHORIZED_CONNECTED
            explicitlyDenied -> ShizukuConnectionStatus.PERMISSION_DENIED
            else -> ShizukuConnectionStatus.PERMISSION_REQUIRED
        }

        _state.value = _state.value.copy(
            isShizukuAppInstalled = true,
            isBinderAlive = true,
            hasPermission = granted,
            status = status,
            shizukuVersion = version,
            shizukuUid = uid,
            fallbackModeActive = !granted
        )

        if (granted && userService == null && !isBinding) {
            bindUserService()
        }
    }

    fun requestShizukuPermission() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            refreshState()
            return
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
            _state.value = _state.value.copy(
                diagnosticOutput = "Shizuku pre-v11 is not supported; please update Shizuku."
            )
            return
        }
        val alreadyGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (alreadyGranted) {
            refreshState()
        } else {
            runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
        }
    }

    fun bindUserService() {
        if (userService != null || isBinding) return
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
        if (runCatching { Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED }.getOrDefault(true)) return

        isBinding = true
        runCatching {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }.onFailure { err ->
            isBinding = false
            _state.value = _state.value.copy(
                diagnosticOutput = "Failed to bind Shizuku UserService: ${err.message}"
            )
        }
    }

    fun refreshAudioDiagnostics() {
        val service = userService
        if (service == null) {
            _state.value = _state.value.copy(
                diagnosticOutput = "Shizuku UserService not bound yet. Connect & authorize Shizuku to run privileged audio/package diagnostics."
            )
            return
        }
        scope.launch {
            val result = runCatching {
                buildString {
                    appendLine(service.deviceAudioSummary)
                    appendLine()
                    appendLine(service.runAudioDiagnostics())
                }
            }.getOrElse { "Diagnostic call error: ${it.message}" }
            _state.value = _state.value.copy(diagnosticOutput = result)
        }
    }

    fun inspectTargetPackage(packageName: String) {
        val service = userService ?: return
        scope.launch {
            val result = runCatching {
                service.inspectPackageState(packageName)
            }.getOrElse { "Package inspection error: ${it.message}" }
            _state.value = _state.value.copy(diagnosticOutput = result)
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }
}
