package com.antidpi.app.tgproxy

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.antidpi.app.AntiDpiApp
import com.antidpi.app.MainActivity
import com.antidpi.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TgProxyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: Job? = null
    private var settings = TgProxySettings()

    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private var network: Network? = null
    private var networkLost = false
    private var pendingRestart: Job? = null

    private var coreNetwork: Network? = null
    private var lostSinceStart = false
    private var startCheckedOn: Network? = null
    private var stallCheckedOn: Network? = null
    private var cfCheckJob: Job? = null
    private var freshPickNext = true
    private var cfFailureCheckAt = NEVER
    private var cutOffNoted = false
    private var autoRestartAt = NEVER

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            scope.launch { onDefaultNetwork(n) }
        }

        override fun onLost(n: Network) {
            scope.launch { onNetworkLost(n) }
        }
    }

    private var poolPause: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreen(on = true)
                Intent.ACTION_SCREEN_OFF -> onScreen(on = false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot follow network changes", e)
        }
        TgProxyCore.setPoolPaused(!getSystemService(PowerManager::class.java).isInteractive)
        val screen = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun onScreen(on: Boolean) {
        poolPause?.cancel()
        if (on) {
            TgProxyCore.setPoolPaused(false)
        } else {
            poolPause = scope.launch {
                delay(POOL_PAUSE_DELAY_MS)
                TgProxyCore.setPoolPaused(true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        settings = TgSettingsStore(this).load()
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val restart = intent?.action == ACTION_RESTART
        startCheckedOn = null
        stallCheckedOn = null
        freshPickNext = true
        cfFailureCheckAt = NEVER
        cfCheckJob?.cancel()
        session?.cancel()
        if (restart) TgProxyCore.setStalled(false)
        session = scope.launch { runSession(settings, restart) }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            connectivity.unregisterNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot stop following network changes", e)
        }
        unregisterReceiver(screenReceiver)
        scope.cancel()
        TgProxyCore.setCfCheck(null)
        TgProxyCore.stopInBackground()
        super.onDestroy()
    }

    private suspend fun runSession(current: TgProxySettings, restart: Boolean) {
        if (restart) TgProxyCore.stop(keepStalled = true)
        if (freshPickNext) {
            freshPickNext = false
            TgProxyCore.forgetCfPick()
        }
        if (TgProxyCore.start(this, current) != null) {
            stopSelf()
            return
        }
        coreNetwork = network ?: connectivity.activeNetwork
        lostSinceStart = false
        TgProxyCore.note(deviceSummary())
        TgProxyCore.note(settingsSummary(current))
        TgProxyCore.note("Network: ${describeNetwork(coreNetwork)}")
        TgProxyCore.note(networkDetails(coreNetwork))
        delay(STARTUP_POLL_MS)
        var shown: TgProxyStatus? = null
        var listened = false
        var checkCfNow = false
        val stalls = StallDetector()
        val cfFailures = CfFailureDetector()
        while (true) {
            val status = TgProxyCore.poll()
            if (!status.running) {
                val free = firstRunFreePort(status)
                if (free != null) {
                    settings = current.copy(port = free)
                    TgSettingsStore(this).save(settings)
                    runSession(settings, restart = false)
                } else {
                    stopSelf()
                }
                return
            }
            if (status.listening && !listened) {
                listened = true
                TgSettingsStore(this).autoPort = false
                checkCfNow = true
            }
            if (status.listening && watchStall(stalls, status)) return
            if (status.listening) watchCfFailures(cfFailures, status)
            if (status.active != shown?.active || status.listening != shown.listening) {
                notify(buildNotification(status))
                shown = status
            }
            if (checkCfNow) {
                checkCfNow = false
                val on = network ?: connectivity.activeNetwork
                if (on != null && on != startCheckedOn) {
                    startCheckedOn = on
                    launchCheckCf(on, fresh = false)
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun watchStall(stalls: StallDetector, status: TgProxyStatus): Boolean {
        val now = SystemClock.elapsedRealtime()
        val stuck = stalls.update(status, now)
        if (stalls.progressed && TgProxyCore.stalled.value) TgProxyCore.setStalled(false)
        if (!stuck) return false
        if (cfCheckJob?.isActive == true) return false
        val on = network ?: connectivity.activeNetwork
        val step = cfStallStep(
            auto = on != null && settings.cfproxy && settings.cfAuto,
            checkedHere = on == stallCheckedOn,
            cfOpened = stalls.cfOpened,
            current = settings.cfTransport,
            stalled = on?.let { stalledWays.on(it, now) } ?: emptySet(),
        )
        if (on != null && step == CfStallStep.RECHECK) {
            stallCheckedOn = on
            launchCheckCf(on, fresh = true)
            return false
        }
        if (on != null && step == CfStallStep.SET_ASIDE) {
            stalledWays.add(on, settings.cfTransport, now)
            TgProxyCore.note(
                "Nothing gets through over ${label(settings.cfTransport)} on ${describeNetwork(on)} " +
                    "although ${stalls.cfOpened} CF connections opened; trying another way"
            )
            launchCheckCf(on, fresh = false)
            return false
        }
        val check = TgProxyCore.cfCheck.value
        if (on != null && check?.network == on && check.cutOff) {
            if (!cutOffNoted) {
                cutOffNoted = true
                TgProxyCore.note("Not restarting: ${describeNetwork(on)} cuts CF off after ~16 KB")
            }
            return false
        }
        if (now - autoRestartAt >= AUTO_RESTART_COOLDOWN_MS) {
            autoRestartAt = now
            TgProxyCore.note("Nothing got through for a minute; restarting tg-ws-proxy")
            restartSession()
            return true
        }
        if (!TgProxyCore.stalled.value) {
            TgProxyCore.note("Still nothing gets through after restart, over ${describeNetwork(network)}")
            TgProxyCore.setStalled(true)
        }
        return false
    }

    private fun watchCfFailures(cfFailures: CfFailureDetector, status: TgProxyStatus) {
        val now = SystemClock.elapsedRealtime()
        if (!cfFailures.update(status, now)) return
        if (!settings.cfproxy || !settings.cfAuto || cfCheckJob?.isActive == true) return
        if (now - cfFailureCheckAt < CF_FAILURE_RECHECK_MS) return
        val on = network ?: connectivity.activeNetwork ?: return
        cfFailureCheckAt = now
        val reason = cfTrouble(status.cfRecent)?.takeIf { it.route == CfRoute.CF_PROXY }?.reason?.key
        TgProxyCore.note(
            "CF proxy failing (${cfFailures.failed} failed, ${cfFailures.opened} opened" +
                "${reason?.let { ", mostly $it" } ?: ""}); rechecking"
        )
        launchCheckCf(on, fresh = true)
    }

    private fun launchCheckCf(on: Network, fresh: Boolean) {
        cfCheckJob?.cancel()
        cfCheckJob = scope.launch { checkCf(on, fresh) }
    }

    private suspend fun checkCf(on: Network, fresh: Boolean) {
        if (!settings.cfproxy || !settings.cfAuto) return
        if (fresh) TgProxyCore.forgetCfPick()
        val probe = TgProxyCore.probeCf()
        if (on != (network ?: connectivity.activeNetwork)) return
        if (probe == null) {
            TgProxyCore.setCfCheck(
                TgProxyCore.CfCheck(on, describeNetwork(on), System.currentTimeMillis(), settings.cfTransport, failed = true)
            )
            return
        }
        val (target, cutOff, takeBack) =
            cfCheckOutcome(probe, stalledWays.on(on, SystemClock.elapsedRealtime()), settings.cfTransport)
        TgProxyCore.setCfCheck(
            TgProxyCore.CfCheck(on, describeNetwork(on), System.currentTimeMillis(), target, cutOff)
        )
        if (target == null) {
            TgProxyCore.note("No way to reach CF works on ${describeNetwork(on)}")
            return
        }
        if (takeBack) {
            stalledWays.remove(on, target)
        }
        if (!cutOff) cutOffNoted = false
        if (target == settings.cfTransport) return
        TgProxyCore.note("Auto-selected CF transport: ${label(target)}")
        settings = settings.withCurrentCfTransport(target)
        TgSettingsStore(this).save(settings)
        restartSession()
    }

    private fun label(t: CfTransport) = when (t) {
        CfTransport.BYPASS -> "TLS with bypass"
        CfTransport.TLS -> "plain TLS"
        CfTransport.PLAIN -> "no TLS"
    }

    private fun restartSession() {
        val old = session
        session = scope.launch {
            old?.cancel()
            runSession(settings, restart = true)
        }
    }

    private fun onDefaultNetwork(n: Network) {
        val previous = network
        network = n
        if (previous == n) return
        if (previous == null && !networkLost) return
        val back = networkLost
        networkLost = false
        forgetCfCheck()
        pendingRestart?.cancel()
        pendingRestart = scope.launch {
            delay(if (back) NETWORK_BACK_SETTLE_MS else NETWORK_SWITCH_SETTLE_MS)
            val now = network
            if (now == coreNetwork && !lostSinceStart) return@launch
            autoRestartAt = NEVER
            cfFailureCheckAt = NEVER
            freshPickNext = true
            restartSession()
        }
    }

    private fun onNetworkLost(n: Network) {
        if (n != network) return
        network = null
        networkLost = true
        lostSinceStart = true
        pendingRestart?.cancel()
        forgetCfCheck()
    }

    private fun forgetCfCheck() {
        cfCheckJob?.cancel()
        TgProxyCore.setCfCheck(null)
    }

    private fun firstRunFreePort(status: TgProxyStatus): Int? {
        val error = status.error?.takeIf { it.code == "port_in_use" } ?: return null
        val store = TgSettingsStore(this)
        if (!store.autoPort) return null
        store.autoPort = false
        return error.suggestedPort
    }

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot enter foreground", e)
        false
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun buildNotification(status: TgProxyStatus?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 11, Intent(this, TgProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (status?.running == true && status.listening) {
            getString(R.string.tg_notif_running, settings.host, settings.port, status.active)
        } else {
            getString(R.string.tg_notif_starting)
        }
        val builder = NotificationCompat.Builder(this, AntiDpiApp.TG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.tg_notif_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.tg_action_stop), stop)

        TgProxyCore.link(settings)?.let { link ->
            val tg = PendingIntent.getActivity(
                this, 12, Intent(Intent.ACTION_VIEW, link.toUri()), PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.tg_open_telegram), tg)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "TgProxyService"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_MS = 2_000L
        private const val STARTUP_POLL_MS = 500L
        private const val NETWORK_BACK_SETTLE_MS = 3_000L
        private const val NETWORK_SWITCH_SETTLE_MS = 15_000L
        private const val AUTO_RESTART_COOLDOWN_MS = 10 * 60_000L
        private const val CF_FAILURE_RECHECK_MS = 10 * 60_000L
        private const val POOL_PAUSE_DELAY_MS = 60_000L
        private const val NEVER = Long.MIN_VALUE / 2
        private const val ACTION_START = "start"
        private const val ACTION_RESTART = "restart"
        private const val ACTION_STOP = "stop"

        private val stalledWays = StalledWays()

        fun start(context: Context, restart: Boolean = false) {
            val intent = Intent(context, TgProxyService::class.java)
                .setAction(if (restart) ACTION_RESTART else ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TgProxyService::class.java))
        }
    }
}
