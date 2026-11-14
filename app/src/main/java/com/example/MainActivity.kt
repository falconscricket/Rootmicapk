package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.alpha.ui.AlphaViewModel
import com.example.alpha.ui.screens.AudioEngineScreen
import com.example.alpha.ui.screens.HomeScreen
import com.example.alpha.ui.screens.MyAppsScreen
import com.example.alpha.ui.screens.OriginalPopupScreen
import com.example.alpha.ui.screens.PermissionsScreen
import com.example.alpha.ui.screens.SettingsScreen
import com.example.alpha.ui.screens.ShizukuScreen
import com.example.ui.theme.AlphaCyan
import com.example.ui.theme.AlphaObsidianBg
import com.example.ui.theme.AlphaSurfaceDark
import com.example.ui.theme.AlphaMicTheme
import kotlinx.serialization.Serializable

@Serializable
sealed interface AlphaRoute {
    @Serializable data object Home : AlphaRoute
    @Serializable data object Popup : AlphaRoute
    @Serializable data object MyApps : AlphaRoute
    @Serializable data object AudioEngine : AlphaRoute
    @Serializable data object Shizuku : AlphaRoute
    @Serializable data object Permissions : AlphaRoute
    @Serializable data object Settings : AlphaRoute
}

private data class NavTabItem(
    val route: AlphaRoute,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlphaMicTheme {
                AlphaInjectorApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphaInjectorApp(
    viewModel: AlphaViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val globalSettings by viewModel.globalSettings.collectAsStateWithLifecycle()
    val targetApps by viewModel.targetApps.collectAsStateWithLifecycle()
    val currentEffects by viewModel.currentEffects.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val hardwareCapabilities by viewModel.hardwareCapabilities.collectAsStateWithLifecycle()
    val selectedCompatibility by viewModel.selectedCompatibility.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAllDiagnostics()
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshAllDiagnostics()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshAllDiagnostics()
    }

    val primaryTabs = remember {
        listOf(
            NavTabItem(AlphaRoute.Home, "Home", Icons.Rounded.Home, "nav_home"),
            NavTabItem(AlphaRoute.Popup, "Popup", Icons.Rounded.Layers, "nav_popup"),
            NavTabItem(AlphaRoute.MyApps, "My Apps", Icons.Rounded.Apps, "nav_apps"),
            NavTabItem(AlphaRoute.AudioEngine, "Audio", Icons.Rounded.GraphicEq, "nav_audio"),
            NavTabItem(AlphaRoute.Shizuku, "Shizuku", Icons.Rounded.Terminal, "nav_shizuku")
        )
    }

    val allTabs = remember {
        primaryTabs + listOf(
            NavTabItem(AlphaRoute.Permissions, "Perms", Icons.Rounded.VerifiedUser, "nav_permissions"),
            NavTabItem(AlphaRoute.Settings, "Settings", Icons.Rounded.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTo(route: AlphaRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentTitle = when {
        currentDestination?.hasRoute<AlphaRoute.Popup>() == true -> "Original Mic Popup"
        currentDestination?.hasRoute<AlphaRoute.MyApps>() == true -> "My Apps"
        currentDestination?.hasRoute<AlphaRoute.AudioEngine>() == true -> "Audio Engine"
        currentDestination?.hasRoute<AlphaRoute.Shizuku>() == true -> "Shizuku"
        currentDestination?.hasRoute<AlphaRoute.Permissions>() == true -> "Permissions"
        currentDestination?.hasRoute<AlphaRoute.Settings>() == true -> "Settings"
        else -> "ALPHA Home"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AlphaObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = AlphaCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = AlphaSurfaceDark
                    ),
                    actions = {
                        if (!useRail) {
                            IconButton(
                                onClick = { navigateTo(AlphaRoute.Permissions) },
                                modifier = Modifier.testTag("topbar_permissions_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = "Permissions",
                                    tint = if (permissions.hasOverlay && permissions.hasUsageAccess) AlphaCyan
                                    else MaterialTheme.colorScheme.tertiary
                                )
                            }
                            IconButton(
                                onClick = { navigateTo(AlphaRoute.Settings) },
                                modifier = Modifier.testTag("topbar_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = AlphaSurfaceDark) {
                        primaryTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = AlphaSurfaceDark) {
                        allTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AlphaRoute.Home
                    ) {
                        composable<AlphaRoute.Home> {
                            HomeScreen(
                                serviceState = serviceState,
                                shizukuState = shizukuState,
                                audioState = audioState,
                                currentEffects = currentEffects,
                                targetApps = targetApps,
                                permissions = permissions,
                                onToggleMonitor = { viewModel.toggleMonitoringService(it) },
                                onShowPopupNow = { viewModel.showFloatingPopupNow() },
                                onLaunchShareChat = { viewModel.launchTargetApp("in.mohalla.sharechat") },
                                onNavigatePopup = { navigateTo(AlphaRoute.Popup) },
                                onNavigateApps = { navigateTo(AlphaRoute.MyApps) },
                                onNavigateAudio = { navigateTo(AlphaRoute.AudioEngine) },
                                onNavigateShizuku = { navigateTo(AlphaRoute.Shizuku) },
                                onNavigatePermissions = { navigateTo(AlphaRoute.Permissions) },
                                onNavigateSettings = { navigateTo(AlphaRoute.Settings) }
                            )
                        }

                        composable<AlphaRoute.Popup> {
                            OriginalPopupScreen(
                                effects = currentEffects,
                                presets = viewModel.presets,
                                audioState = audioState,
                                bundledAssets = viewModel.bundledAssetFiles,
                                onReadAsset = { viewModel.readBundledAsset(it) },
                                onApplyPreset = { viewModel.applyPreset(it) },
                                onUpdateEffects = { viewModel.updateEffects(it) },
                                onLaunchFloatingOverlay = { viewModel.showFloatingPopupNow() },
                                onToggleLivePreview = {
                                    if (!permissions.hasRecordAudio) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleLiveMonitor()
                                    }
                                }
                            )
                        }

                        composable<AlphaRoute.MyApps> {
                            MyAppsScreen(
                                targetApps = targetApps,
                                installedApps = installedApps,
                                selectedCompatibility = selectedCompatibility,
                                onAddApp = { viewModel.addTargetApp(it) },
                                onRemoveApp = { viewModel.removeTargetApp(it) },
                                onToggleEnabled = { pkg, enabled -> viewModel.toggleTargetAppEnabled(pkg, enabled) },
                                onTogglePopupPref = { pkg, popup -> viewModel.toggleTargetAppPopup(pkg, popup) },
                                onInspectCompatibility = { pkg, name -> viewModel.inspectCompatibility(pkg, name) },
                                onDismissCompatibility = { viewModel.clearCompatibilityDialog() },
                                onLaunchApp = { viewModel.launchTargetApp(it) }
                            )
                        }

                        composable<AlphaRoute.AudioEngine> {
                            AudioEngineScreen(
                                audioState = audioState,
                                effects = currentEffects,
                                hasMicPermission = permissions.hasRecordAudio,
                                hardwareCapabilities = hardwareCapabilities,
                                onToggleLiveMonitor = { viewModel.toggleLiveMonitor() },
                                onToggleRecordTest = { viewModel.toggleRecordingTest() },
                                onPlayLastRecording = { viewModel.playLastRecording() },
                                onStopAudio = { viewModel.stopAudioEngine() },
                                onUpdateEffects = { viewModel.updateEffects(it) },
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )
                        }

                        composable<AlphaRoute.Shizuku> {
                            ShizukuScreen(
                                shizukuState = shizukuState,
                                onRefreshStatus = { viewModel.refreshAllDiagnostics() },
                                onRequestPermission = { viewModel.requestShizukuPermission() },
                                onRunDiagnostics = { viewModel.refreshShizukuDiagnostics() },
                                onOpenShizuku = { viewModel.openShizukuAppOrStore() }
                            )
                        }

                        composable<AlphaRoute.Permissions> {
                            PermissionsScreen(
                                permissions = permissions,
                                onRequestMic = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onOpenOverlaySettings = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                onOpenUsageAccessSettings = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                onOpenBatterySettings = {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                },
                                onRefresh = { viewModel.refreshAllDiagnostics() }
                            )
                        }

                        composable<AlphaRoute.Settings> {
                            SettingsScreen(
                                globalSettings = globalSettings,
                                onSetAutoShowPopup = { viewModel.setAutoShowPopup(it) },
                                onSetPopupBehavior = { viewModel.setPopupBehaviorMode(it) },
                                onSetSampleRate = { viewModel.setPreferredSampleRate(it) },
                                onSetLowLatency = { viewModel.setLowLatencyMode(it) },
                                onResetPopupPosition = { viewModel.resetPopupPosition() }
                            )
                        }
                    }
                }
            }
        }
    }
}
