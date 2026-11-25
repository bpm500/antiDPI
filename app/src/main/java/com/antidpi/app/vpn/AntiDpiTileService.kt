package com.antidpi.app.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.antidpi.app.MainActivity
import com.antidpi.app.data.ConfigRepository
import com.antidpi.app.data.SettingsRepository
import com.antidpi.app.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AntiDpiTileService : TileService() {

    private var listenJob: Job? = null

    override fun onStartListening() {
        listenJob = CoroutineScope(Dispatchers.Main).launch {
            AntiDpiVpnService.stateFlow.collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    override fun onClick() {
        val current = AntiDpiVpnService.stateFlow.value
        if (current is VpnState.Connected || current is VpnState.Connecting) {
            AntiDpiVpnService.stop(this)
            return
        }

        if (VpnService.prepare(this) != null) {
            openAppAndStart()
            return
        }

        val configName = resolveConfigName()
        if (configName.isNullOrBlank()) {
            openAppAndStart()
            return
        }

        try {
            AntiDpiVpnService.start(this, configName)
        } catch (e: IllegalStateException) {
            Log.i(TAG, "Starting VPN via activity", e)
            openAppAndStart()
        }
    }

    private fun resolveConfigName(): String? = runBlocking(Dispatchers.IO) {
        val settingsRepo = SettingsRepository(this@AntiDpiTileService)
        val configRepo = ConfigRepository(this@AntiDpiTileService)
        configRepo.ensureInitialized()
        val selected = settingsRepo.getSettings().selectedConfigName
        if (!selected.isNullOrBlank() && configRepo.configExists(selected)) {
            selected
        } else {
            configRepo.listConfigs().firstOrNull()?.name
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppAndStart() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START_VPN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 101, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(state: VpnState) {
        val tile = qsTile ?: return
        val active = state is VpnState.Connected || state is VpnState.Connecting
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (state) {
                is VpnState.Connected -> state.configName
                is VpnState.Connecting -> state.configName
                else -> null
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val TAG = "AntiDpiTileService"
    }
}
