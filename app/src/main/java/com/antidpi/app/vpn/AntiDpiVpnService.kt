package com.antidpi.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.antidpi.app.MainActivity
import com.antidpi.app.R
import com.antidpi.app.core.AppLogger
import com.antidpi.app.core.CiAdpiProcessManager
import com.antidpi.app.core.PortPool
import com.antidpi.app.data.ConfigRepository
import com.antidpi.app.model.VpnState
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AntiDpiVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var configRepository: ConfigRepository
    private lateinit var processManager: CiAdpiProcessManager

    private var tunInterface: ParcelFileDescriptor? = null
    private var ciadpiInstance: CiAdpiProcessManager.RunningInstance? = null
    private var activeConfigName: String? = null
    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private val startStopLock = Any()
    private val tunnelMutex = Mutex()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentUnderlyingHandle: Long? = null
    private var currentUnderlyingTransport: Int? = null
    private var lastTunnelStartMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        processManager = CiAdpiProcessManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    tunnelMutex.withLock {
                        stopTunnel()
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val cfgName = intent.getStringExtra(EXTRA_CONFIG_NAME)
                if (cfgName.isNullOrBlank()) {
                    _stateFlow.value = VpnState.Error(getString(R.string.no_config_selected))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification(cfgName, connecting = true))
                serviceScope.launch {
                    tunnelMutex.withLock {
                        startTunnel(cfgName)
                    }
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        serviceScope.launch {
            stopTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterNetworkMonitor()
        monitorJob?.cancel()
        reconnectJob?.cancel()
        stopTunnelSync()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startTunnel(configName: String, isReconnect: Boolean = false) {
        val wasActive = _stateFlow.value is VpnState.Connected || _stateFlow.value is VpnState.Connecting
        if (!isReconnect && wasActive && activeConfigName == configName) {
            return
        }
        stopTunnel(resetState = false, keepNetworkMonitor = isReconnect)
        activeConfigName = configName
        _stateFlow.value = VpnState.Connecting(configName)

        val config = configRepository.getConfig(configName)
        if (config == null) {
            _stateFlow.value = VpnState.Error(getString(R.string.no_config_selected))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val hostlist = configRepository.getHostlistFile()
        val vpnPort = if (PortPool.isPortAvailable(VPN_SOCKS_PORT)) VPN_SOCKS_PORT else null
        clearRecentDomains()

        AppLogger.i("VPN", "Starting tunnel config=$configName preferPort=${vpnPort ?: "pool"}")
        val started = processManager.start(
            args = config.args,
            hostlistFile = hostlist,
            preferPort = vpnPort,
            noIpv6 = true,
            debugLog = true,
            onLog = { line -> recordCiAdpiLine(line) }
        )

        val instance = started.getOrElse { err ->
            AppLogger.e("VPN", "ciadpi start failed for $configName", err)
            _stateFlow.value = VpnState.Error(err.message ?: getString(R.string.err_ciadpi_crashed))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        ciadpiInstance = instance
        activeInstance = instance

        val builder = Builder()
            .setSession("antiDPI: $configName")
            .setMtu(1500)
            .setBlocking(false)
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fc00::1", 128)
            .addRoute("::", 0)
            .addDnsServer("198.18.0.2")

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            AppLogger.e("VPN", "Builder.establish failed", e)
            null
        }

        if (pfd == null) {
            instance.stop()
            ciadpiInstance = null
            activeInstance = null
            _stateFlow.value = VpnState.Error(getString(R.string.err_no_vpn_permission))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tunInterface = pfd

        val tproxyConfig = File(cacheDir, "tproxy.yml")
        tproxyConfig.writeText(
            """
            tunnel:
              name: tun0
              mtu: 1500
              multi-queue: false
              ipv4: 198.18.0.1
              ipv6: 'fc00::1'
            socks5:
              port: ${instance.port}
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              task-stack-size: 86016
              connect-timeout: 5000
              tcp-read-write-timeout: 60000
              udp-read-write-timeout: 30000
              log-level: warn
            """.trimIndent()
        )

        val ok = TProxyService.TProxyStartService(tproxyConfig.absolutePath, pfd.fd)
        if (!ok) {
            AppLogger.e("VPN", "TProxyStartService returned false")
            stopTunnelSync()
            _stateFlow.value = VpnState.Error("Failed to start tunnel engine")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        AppLogger.i("VPN", "Tunnel active config=$configName port=${instance.port} mapdns=198.18.0.2")

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(configName, connecting = false))
        lastTunnelStartMs = SystemClock.elapsedRealtime()
        _stateFlow.value = VpnState.Connected(configName)

        registerNetworkMonitor()
        startProcessWatchdog(configName, instance)
    }

    private fun startProcessWatchdog(configName: String, instance: CiAdpiProcessManager.RunningInstance) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (instance.isAlive() && _stateFlow.value is VpnState.Connected) {
                delay(1500)
            }
            if (_stateFlow.value is VpnState.Connected && !instance.isAlive()) {
                val errMsg = instance.recentOutput().ifBlank { getString(R.string.err_ciadpi_crashed) }
                stopTunnelSync()
                _stateFlow.value = VpnState.Error(errMsg)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun registerNetworkMonitor() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm
        currentUnderlyingHandle = null
        currentUnderlyingTransport = null

        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network) ?: return
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 0
                    else -> null
                }
                val handle = network.networkHandle
                val prevHandle = currentUnderlyingHandle
                val prevTransport = currentUnderlyingTransport

                if (prevHandle == null) {
                    currentUnderlyingHandle = handle
                    currentUnderlyingTransport = transport
                    return
                }
                if (handle != prevHandle && transport != null && transport != prevTransport) {
                    currentUnderlyingHandle = handle
                    currentUnderlyingTransport = transport
                    if (SystemClock.elapsedRealtime() - lastTunnelStartMs > 5000L) {
                        scheduleReconnect()
                    }
                }
            }

            override fun onLost(network: Network) {
                if (network.networkHandle == currentUnderlyingHandle) {
                    currentUnderlyingHandle = null
                    currentUnderlyingTransport = null
                }
            }
        }
        networkCallback = cb
        try {
            cm.registerNetworkCallback(req, cb)
        } catch (_: Exception) {
        }
    }

    private fun scheduleReconnect() {
        val cfg = activeConfigName ?: return
        if (_stateFlow.value !is VpnState.Connected) return
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(1000)
            tunnelMutex.withLock {
                if (_stateFlow.value is VpnState.Connected) {
                    startTunnel(cfg, isReconnect = true)
                }
            }
        }
    }

    private fun unregisterNetworkMonitor() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        connectivityManager = null
        currentUnderlyingHandle = null
        currentUnderlyingTransport = null
    }

    private fun stopTunnel(resetState: Boolean = true, keepNetworkMonitor: Boolean = false) {
        monitorJob?.cancel()
        monitorJob = null
        if (!keepNetworkMonitor) {
            reconnectJob?.cancel()
            reconnectJob = null
            unregisterNetworkMonitor()
        }
        stopTunnelSync()
        if (resetState) {
            _stateFlow.value = VpnState.Disconnected
        }
    }

    private fun stopTunnelSync() {
        synchronized(startStopLock) {
            try {
                if (TProxyService.TProxyIsRunning()) {
                    TProxyService.TProxyStopService()
                }
            } catch (_: Exception) {
            }
            try {
                tunInterface?.close()
            } catch (_: Exception) {
            }
            tunInterface = null

            try {
                ciadpiInstance?.stop()
            } catch (_: Exception) {
            }
            ciadpiInstance = null
            activeInstance = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(configName: String, connecting: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AntiDpiVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (connecting) {
            getString(R.string.status_connecting, configName)
        } else {
            getString(R.string.vpn_notif_text, configName)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.vpn_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.vpn_notif_stop), stopPi)
            .build()
    }

    companion object {
        const val ACTION_START = "com.antidpi.app.vpn.START"
        const val ACTION_STOP = "com.antidpi.app.vpn.STOP"
        const val EXTRA_CONFIG_NAME = "config_name"
        private const val CHANNEL_ID = "antidpi_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_SOCKS_PORT = 10808

        private val _stateFlow = MutableStateFlow<VpnState>(VpnState.Disconnected)
        val stateFlow: StateFlow<VpnState> = _stateFlow.asStateFlow()

        @Volatile
        private var activeInstance: CiAdpiProcessManager.RunningInstance? = null

        private val recentDomainsLock = Any()
        private val recentDomainsDeque = ArrayDeque<String>(60)
        private val _recentDomainsFlow = MutableStateFlow<List<String>>(emptyList())
        val recentDomainsFlow: StateFlow<List<String>> = _recentDomainsFlow.asStateFlow()

        fun isCiAdpiRunning(): Boolean = activeInstance?.isAlive() == true
        fun activePort(): Int? = activeInstance?.port
        fun isHevRunning(): Boolean = try {
            TProxyService.TProxyIsRunning()
        } catch (_: Throwable) {
            false
        }

        private fun clearRecentDomains() {
            synchronized(recentDomainsLock) {
                recentDomainsDeque.clear()
                _recentDomainsFlow.value = emptyList()
            }
        }

        private fun recordCiAdpiLine(rawLine: String) {
            val line = rawLine.trim()
            if (line.isEmpty()) return

            val domain = when {
                line.startsWith("resolve:") -> line.removePrefix("resolve:").trim()
                line.startsWith("host:") -> line.removePrefix("host:").trim().substringBefore(" ")
                else -> null
            }?.takeIf { it.isNotBlank() && it.contains('.') }

            if (domain != null) {
                synchronized(recentDomainsLock) {
                    recentDomainsDeque.remove(domain)
                    recentDomainsDeque.addLast(domain)
                    while (recentDomainsDeque.size > 50) {
                        recentDomainsDeque.removeFirst()
                    }
                    _recentDomainsFlow.value = recentDomainsDeque.toList()
                }
                AppLogger.d("ciadpi", line)
            } else if (
                line.contains("err", ignoreCase = true) ||
                line.contains("timeout", ignoreCase = true) ||
                line.contains("exit", ignoreCase = true) ||
                line.contains("invalid", ignoreCase = true) ||
                line.startsWith("hosts count:")
            ) {
                AppLogger.d("ciadpi", line)
            }
        }

        fun start(context: Context, configName: String) {
            val intent = Intent(context, AntiDpiVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_NAME, configName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AntiDpiVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
