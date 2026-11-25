package com.antidpi.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antidpi.app.R
import com.antidpi.app.model.ConfigEntry
import com.antidpi.app.model.VpnState
import com.antidpi.app.tgproxy.TgProxyService
import com.antidpi.app.ui.MainViewModel
import com.antidpi.app.ui.TgProxyViewModel
import com.antidpi.app.ui.screens.ConfigActionDialog
import com.antidpi.app.ui.screens.ConnectScreen
import com.antidpi.app.ui.screens.CreateScreen
import com.antidpi.app.ui.screens.SettingsDialog
import com.antidpi.app.ui.screens.TestScreen
import com.antidpi.app.ui.screens.UpdateAvailableDialog
import com.antidpi.app.ui.screens.UtilsScreen
import com.antidpi.app.ui.theme.AntiDpiColors
import com.antidpi.app.ui.theme.AntiDpiTheme
import com.antidpi.app.vpn.AntiDpiVpnService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val tgViewModel: TgProxyViewModel by viewModels()
    private val pendingTileAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        if (savedInstanceState == null) handleTileIntent(intent)
        setContent {
            AntiDpiTheme {
                AntiDpiRoot(
                    viewModel = viewModel,
                    tgViewModel = tgViewModel,
                    pendingTileAction = pendingTileAction.value,
                    onConsumeTileAction = { pendingTileAction.value = null },
                    onRequestBatteryOptimization = { openBatteryOptimizationSettings() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileIntent(intent)
    }

    private fun handleTileIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_TG_PROXY -> TgProxyService.start(this)
            ACTION_START_VPN -> pendingTileAction.value = ACTION_START_VPN
        }
    }

    companion object {
        const val ACTION_START_VPN = "com.antidpi.app.action.START_VPN"
        const val ACTION_START_TG_PROXY = "com.antidpi.app.action.START_TG_PROXY"
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        val pkgUri = Uri.parse("package:$packageName")
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        try {
            startActivity(direct)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                startActivity(appDetails)
            }
        }
    }
}

@Composable
private fun AntiDpiRoot(
    viewModel: MainViewModel,
    tgViewModel: TgProxyViewModel,
    pendingTileAction: String?,
    onConsumeTileAction: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    val context = LocalContext.current
    val configs by viewModel.configs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val vpnState by viewModel.vpnState.collectAsState()
    val isIgnoringBattery by viewModel.isIgnoringBattery.collectAsState()

    val isTesting by viewModel.isTesting.collectAsState()
    val testProgress by viewModel.testProgress.collectAsState()
    val testLogs by viewModel.testLogs.collectAsState()
    val testRanking by viewModel.testRanking.collectAsState()

    val targetDiscord by viewModel.targetDiscord.collectAsState()
    val targetYouTube by viewModel.targetYouTube.collectAsState()
    val numConfigsToFind by viewModel.numConfigsToFind.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val createLogs by viewModel.createLogs.collectAsState()
    val manualError by viewModel.manualError.collectAsState()
    val manualValidating by viewModel.manualValidating.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val pendingUpdate by viewModel.pendingUpdateDialog.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var actionTargetConfig by remember { mutableStateOf<ConfigEntry?>(null) }
    var pendingConfigToStart by remember { mutableStateOf<String?>(null) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val cfg = pendingConfigToStart
        pendingConfigToStart = null
        if (res.resultCode == Activity.RESULT_OK && cfg != null) {
            AntiDpiVpnService.start(context, cfg)
        } else if (res.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, R.string.err_no_vpn_permission, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVpn() {
        if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) {
            AntiDpiVpnService.stop(context)
            return
        }
        val cfgName = settings.selectedConfigName ?: configs.firstOrNull()?.name
        if (cfgName.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_config_selected, Toast.LENGTH_SHORT).show()
            return
        }
        if (!viewModel.hasNetworkConnection()) {
            Toast.makeText(context, R.string.err_no_network, Toast.LENGTH_SHORT).show()
            return
        }
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingConfigToStart = cfgName
            vpnLauncher.launch(prepareIntent)
        } else {
            AntiDpiVpnService.start(context, cfgName)
        }
    }

    fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("antiDPI", text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(pendingTileAction, configs) {
        if (pendingTileAction == MainActivity.ACTION_START_VPN && configs.isNotEmpty()) {
            onConsumeTileAction()
            if (vpnState !is VpnState.Connected && vpnState !is VpnState.Connecting) {
                toggleVpn()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntiDpiColors.Background)
            .systemBarsPadding()
    ) {
        // Top Tab Bar: Connect | Create | Test
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(AntiDpiColors.Surface)
        ) {
            val tabs = listOf(
                stringResource(R.string.tab_connect),
                stringResource(R.string.tab_create),
                stringResource(R.string.tab_test),
                stringResource(R.string.tab_utils)
            )
            tabs.forEachIndexed { idx, label ->
                val active = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(
                            if (active) AntiDpiColors.InvertedBg
                            else AntiDpiColors.Surface
                        )
                        .clickable { selectedTab = idx },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) AntiDpiColors.InvertedText else AntiDpiColors.TextSecondary
                    )
                }
            }
        }
        HorizontalDivider(color = AntiDpiColors.Border)

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ConnectScreen(
                    configs = configs,
                    selectedConfigName = settings.selectedConfigName,
                    vpnState = vpnState,
                    showBgPrompt = !isIgnoringBattery && !settings.bgPromptDismissed,
                    onPowerClick = { toggleVpn() },
                    onSelectConfig = { viewModel.selectConfig(it) },
                    onLongPressConfig = { actionTargetConfig = it },
                    onOpenSettings = { showSettings = true },
                    onEnableBackground = onRequestBatteryOptimization,
                    onDismissBgPrompt = { viewModel.dismissBgPrompt() }
                )

                1 -> CreateScreen(
                    targetDiscord = targetDiscord,
                    targetYouTube = targetYouTube,
                    numConfigsToFind = numConfigsToFind,
                    isCreating = isCreating,
                    logs = createLogs,
                    manualError = manualError,
                    manualValidating = manualValidating,
                    onTargetDiscordChange = { viewModel.setTargetDiscord(it) },
                    onTargetYouTubeChange = { viewModel.setTargetYouTube(it) },
                    onNumConfigsChange = { viewModel.setNumConfigsToFind(it) },
                    onStartCreate = { viewModel.startCreate() },
                    onStopCreate = { viewModel.stopCreate() },
                    onCopyLogs = { copyToClipboard(createLogs.joinToString("\n")) },
                    onClearLogs = { viewModel.clearCreateLogs() },
                    onManualSave = { n, a -> viewModel.validateAndSaveManual(n, a) },
                    onClearManualError = { viewModel.clearManualError() }
                )

                2 -> TestScreen(
                    isTesting = isTesting,
                    progress = testProgress,
                    logs = testLogs,
                    ranking = testRanking,
                    hasConfigs = configs.isNotEmpty(),
                    onTestAll = { viewModel.testAll() },
                    onTestSelected = { viewModel.testSelected() },
                    onStop = { viewModel.stopTesting() },
                    onCopyLogs = { copyToClipboard(testLogs.joinToString("\n")) },
                    onClearLogs = { viewModel.clearTestLogs() }
                )

                3 -> UtilsScreen(vm = tgViewModel)
            }
        }

        // Single-line bottom status bar
        HorizontalDivider(color = AntiDpiColors.Border)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(AntiDpiColors.Surface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val statusBarText = when (val st = vpnState) {
                is VpnState.Connected -> stringResource(R.string.status_connected, st.configName)
                is VpnState.Connecting -> stringResource(R.string.status_connecting, st.configName)
                is VpnState.Error -> stringResource(R.string.status_error, st.message)
                VpnState.Disconnected -> stringResource(R.string.status_disconnected)
            }
            Text(
                text = statusBarText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (vpnState is VpnState.Connected) AntiDpiColors.TextPrimary else AntiDpiColors.TextSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            isIgnoringBattery = isIgnoringBattery,
            updateState = updateState,
            onParallelSlotsChange = { viewModel.setParallelSlots(it) },
            onCheckUpdate = { viewModel.checkForUpdates(manual = true) },
            onOpenAbout = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bpm500/antiDPI"))
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
            onEnableBackground = onRequestBatteryOptimization,
            onDismiss = { showSettings = false }
        )
    }

    val releaseToUpdate = pendingUpdate
    if (releaseToUpdate != null) {
        UpdateAvailableDialog(
            release = releaseToUpdate,
            onDownload = {
                viewModel.dismissUpdateDialog()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseToUpdate.downloadUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    val targetCfg = actionTargetConfig
    if (targetCfg != null) {
        ConfigActionDialog(
            config = targetCfg,
            onRename = { newName -> viewModel.renameConfig(targetCfg.name, newName) },
            onDelete = { viewModel.deleteConfig(targetCfg.name) },
            onCopyArgs = { args -> copyToClipboard(args) },
            onDismiss = { actionTargetConfig = null }
        )
    }
}
