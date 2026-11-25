package com.antidpi.app.tgproxy

import android.content.Context
import android.net.Network
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors

data class TgProxyError(
    val code: String,
    val detail: String? = null,
    val suggestedPort: Int? = null
) {
    companion object {
        fun fromJson(o: JSONObject) = TgProxyError(
            code = o.getString("code"),
            detail = if (o.isNull("detail")) null else o.getString("detail"),
            suggestedPort = if (o.isNull("suggested_port")) null else o.getInt("suggested_port"),
        )
    }
}

data class TgProxyStatus(
    val running: Boolean = false,
    val listening: Boolean = false,
    val error: TgProxyError? = null,
    val coreVersion: String = "",
    val active: Int = 0,
    val total: Int = 0,
    val lastHour: Int = 0,
    val badRecent: Int = 0,
    val ws: Int = 0,
    val cf: Int = 0,
    val routed: Int = 0,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val cfOk: Int = 0,
    val cfFailed: Int = 0,
    val cfRecent: Map<String, Map<String, Int>> = emptyMap(),
    val workerTrouble: String? = null,
)

enum class TgProxyRunState {
    STOPPED,
    STARTING,
    RUNNING
}

val TgProxyStatus.runState: TgProxyRunState
    get() = when {
        !running -> TgProxyRunState.STOPPED
        listening -> TgProxyRunState.RUNNING
        else -> TgProxyRunState.STARTING
    }

object TgProxyCore {
    private const val TAG = "TgProxyCore"
    private const val LOG_TAIL_BYTES = 64 * 1024

    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "tg-python") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val module by lazy { Python.getInstance().getModule("android_entry") }

    private val _state = MutableStateFlow(TgProxyStatus())
    val state: StateFlow<TgProxyStatus> = _state.asStateFlow()

    private val _stalled = MutableStateFlow(false)
    val stalled: StateFlow<Boolean> = _stalled.asStateFlow()

    fun setStalled(value: Boolean) {
        _stalled.value = value
    }

    data class CfCheck(
        val network: Network?,
        val networkLabel: String,
        val atMillis: Long,
        val chosen: CfTransport?,
        val cutOff: Boolean = false,
        val failed: Boolean = false,
    )

    private val _cfCheck = MutableStateFlow<CfCheck?>(null)
    val cfCheck: StateFlow<CfCheck?> = _cfCheck.asStateFlow()

    fun setCfCheck(value: CfCheck?) {
        _cfCheck.value = value
    }

    fun note(message: String) {
        scope.launch {
            try {
                module.callAttr("note", message)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot write to tg proxy log", e)
            }
        }
    }

    fun setPoolPaused(paused: Boolean) {
        scope.launch {
            try {
                module.callAttr("set_pool_paused", paused)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot pause/resume ws pool", e)
            }
        }
    }

    suspend fun start(context: Context, settings: TgProxySettings): TgProxyError? = withContext(dispatcher) {
        val logDir = logDir(context).absolutePath
        val error = module.callAttr("start", settings.toJson().toString(), logDir)
            ?.let { TgProxyError.fromJson(JSONObject(it.toString())) }
        readStatus()
        error
    }

    suspend fun forgetCfPick() = withContext(dispatcher) {
        try {
            module.callAttr("forget_cf_pick")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot reset CF pick", e)
        }
    }

    suspend fun stop(keepStalled: Boolean = false) = withContext(dispatcher) {
        if (!keepStalled) _stalled.value = false
        module.callAttr("stop")
        readStatus()
    }

    data class CfProbe(
        val tls: Boolean, val bypass: Boolean, val plain: Boolean,
        val tlsFull: Boolean = tls, val bypassFull: Boolean = bypass, val plainFull: Boolean = plain,
    ) {
        fun works(t: CfTransport) = when (t) {
            CfTransport.BYPASS -> bypass
            CfTransport.TLS -> tls
            CfTransport.PLAIN -> plain
        }

        fun worksFully(t: CfTransport) = works(t) && when (t) {
            CfTransport.BYPASS -> bypassFull
            CfTransport.TLS -> tlsFull
            CfTransport.PLAIN -> plainFull
        }
    }

    suspend fun probeCf(): CfProbe? = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(module.callAttr("probe_cf").toString())
            if (o.has("tls")) {
                val tls = o.getBoolean("tls")
                val bypass = o.getBoolean("bypass")
                val plain = o.getBoolean("plain")
                CfProbe(
                    tls, bypass, plain,
                    o.optBoolean("tls_full", tls), o.optBoolean("bypass_full", bypass),
                    o.optBoolean("plain_full", plain),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "CF probe failed", e)
            null
        }
    }

    suspend fun poll(): TgProxyStatus = withContext(dispatcher) { readStatus() }

    fun refresh() {
        scope.launch { readStatus() }
    }

    fun stopInBackground() {
        scope.launch { stop() }
    }

    fun badTlsStrategyPart(text: String): String? = try {
        module.callAttr("check_tls_strategy", text).toString().ifEmpty { null }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot check TLS strategy", e)
        null
    }

    fun link(settings: TgProxySettings): String? = try {
        module.callAttr("proxy_link", settings.toJson().toString()).toString()
    } catch (e: Exception) {
        Log.w(TAG, "Cannot build proxy link", e)
        null
    }

    fun logDir(context: Context): File = File(context.filesDir, "tg_logs")

    fun readLogTail(context: Context, maxLines: Int = 300): String {
        val file = File(logDir(context), "proxy.log")
        if (!file.exists()) return ""
        val text = RandomAccessFile(file, "r").use { raf ->
            val start = (raf.length() - LOG_TAIL_BYTES).coerceAtLeast(0)
            val bytes = ByteArray((raf.length() - start).toInt())
            raf.seek(start)
            raf.readFully(bytes)
            String(bytes, Charsets.UTF_8).let { if (start > 0) it.substringAfter('\n') else it }
        }
        return text.lines().takeLast(maxLines).joinToString("\n").trimEnd()
    }

    fun clearLogs(context: Context) {
        listOf("proxy.log", "proxy.log.1").forEach { name ->
            runCatching { File(logDir(context), name).delete() }
        }
    }

    private fun readStatus(): TgProxyStatus {
        val o = JSONObject(module.callAttr("status").toString())
        return TgProxyStatus(
            running = o.getBoolean("running"),
            listening = o.getBoolean("listening"),
            error = if (o.isNull("error")) null else TgProxyError.fromJson(o.getJSONObject("error")),
            coreVersion = o.getString("core_version"),
            active = o.getInt("active"),
            total = o.getInt("total"),
            lastHour = o.getInt("last_hour"),
            badRecent = o.getInt("bad_recent"),
            ws = o.getInt("ws"),
            cf = o.getInt("cf"),
            routed = o.getInt("routed"),
            bytesUp = o.getLong("bytes_up"),
            bytesDown = o.getLong("bytes_down"),
            cfOk = o.optInt("cf_ok"),
            cfFailed = o.optInt("cf_failed"),
            cfRecent = parseCfRecent(o.optJSONObject("cf_recent")),
            workerTrouble = if (o.isNull("worker_trouble")) null else o.optString("worker_trouble"),
        ).also { _state.value = it }
    }
}
