package com.antidpi.app.tgproxy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

fun isLoopbackHost(host: String): Boolean {
    val h = host.trim().lowercase()
    if (h == "localhost" || h == "::1" || h == "0:0:0:0:0:0:0:1") return true
    val parts = h.split(".")
    return parts.size == 4 && parts[0] == "127" &&
        parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) && p.length <= 3 && p.toInt() <= 255 }
}

data class TgProxySettings(
    val host: String = "127.0.0.1",
    val port: Int = 1443,
    val secret: String = "",
    val dcIp: List<String> = listOf("2:149.154.167.220", "4:149.154.167.220"),
    val poolSize: Int = 4,
    val bufKb: Int = 256,
    val cfproxy: Boolean = true,
    val h2: Boolean = true,
    val cfUserDomainEnabled: Boolean = false,
    val cfUserDomains: String = "",
    val cfWorkerEnabled: Boolean = false,
    val cfWorkerDomains: String = "",
    val fakeTlsDomain: String = "",
    val forceTestDc: Boolean = false,
    val noSecure: Boolean = false,
    val cfAuto: Boolean = true,
    val tlsBypass: Boolean = true,
    val tlsStrategy: String = TlsStrategies.DEFAULT,
    val verbose: Boolean = false,
) {
    val cfTransport: CfTransport
        get() = when {
            noSecure -> CfTransport.PLAIN
            tlsBypass -> CfTransport.BYPASS
            else -> CfTransport.TLS
        }

    fun withCfTransport(t: CfTransport): TgProxySettings = withCurrentCfTransport(t).copy(cfAuto = false)

    fun withCfAuto(): TgProxySettings = withCurrentCfTransport(CfTransport.BYPASS).copy(cfAuto = true)

    fun withCurrentCfTransport(t: CfTransport): TgProxySettings = when (t) {
        CfTransport.BYPASS -> copy(noSecure = false, tlsBypass = true)
        CfTransport.TLS -> copy(noSecure = false, tlsBypass = false)
        CfTransport.PLAIN -> copy(noSecure = true)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("host", host)
        .put("port", port)
        .put("secret", secret)
        .put("dc_ip", JSONArray(dcIp))
        .put("pool_size", poolSize)
        .put("buf_kb", bufKb)
        .put("cfproxy", cfproxy)
        .put("h2", h2)
        .put("cfproxy_user_domain_enabled", cfUserDomainEnabled)
        .put("cfproxy_user_domain", cfUserDomains)
        .put("cfproxy_worker_enabled", cfWorkerEnabled)
        .put("cfproxy_worker_domain", cfWorkerDomains)
        .put("fake_tls_domain", fakeTlsDomain)
        .put("force_test_dc", forceTestDc)
        .put("no_secure", noSecure)
        .put("tls_bypass", tlsBypass)
        .put("tls_strategy", tlsStrategy)
        .put("cf_auto", cfAuto)
        .put("verbose", verbose)

    companion object {
        fun fromJson(o: JSONObject): TgProxySettings {
            val d = TgProxySettings()
            val dc = o.optJSONArray("dc_ip")
            return TgProxySettings(
                host = o.optString("host", d.host),
                port = o.optInt("port", d.port),
                secret = o.optString("secret", d.secret),
                dcIp = if (dc == null) d.dcIp else List(dc.length()) { dc.getString(it) },
                poolSize = o.optInt("pool_size", d.poolSize),
                bufKb = o.optInt("buf_kb", d.bufKb),
                cfproxy = o.optBoolean("cfproxy", d.cfproxy),
                h2 = o.optBoolean("h2", d.h2),
                cfUserDomainEnabled = o.optBoolean("cfproxy_user_domain_enabled", d.cfUserDomainEnabled),
                cfUserDomains = o.optString("cfproxy_user_domain", d.cfUserDomains),
                cfWorkerEnabled = o.optBoolean("cfproxy_worker_enabled", d.cfWorkerEnabled),
                cfWorkerDomains = o.optString("cfproxy_worker_domain", d.cfWorkerDomains),
                fakeTlsDomain = o.optString("fake_tls_domain", d.fakeTlsDomain),
                forceTestDc = o.optBoolean("force_test_dc", d.forceTestDc),
                noSecure = o.optBoolean("no_secure", d.noSecure),
                tlsBypass = o.optBoolean("tls_bypass", d.tlsBypass),
                tlsStrategy = o.optString("tls_strategy", d.tlsStrategy),
                cfAuto = o.optBoolean("cf_auto", !o.optBoolean("no_secure", false) && o.optBoolean("tls_bypass", true)),
                verbose = o.optBoolean("verbose", d.verbose),
            )
        }

        fun newSecret(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

enum class CfTransport {
    BYPASS,
    TLS,
    PLAIN,
}

object TlsStrategies {
    const val AUTO = "auto"
    const val DEFAULT = AUTO
    const val SPLIT = "--tlsrec 0+sm --split 2+sm"
    const val RECORDS_OOB = "--tlsrec 0+sm --oob 2+sm"
    const val OOB = "--oob 0+sm"
    const val PARENT_SNI = "--sni-parent"

    val PRESETS = listOf(AUTO, SPLIT, RECORDS_OOB, OOB, PARENT_SNI)
}

val CF_AUTO_ORDER = listOf(CfTransport.BYPASS, CfTransport.TLS, CfTransport.PLAIN)

data class CfChoice(val target: CfTransport?, val cutOff: Boolean)

fun chooseCfTransport(probe: TgProxyCore.CfProbe, skip: Set<CfTransport> = emptySet()): CfChoice {
    val order = CF_AUTO_ORDER.filter { it !in skip }
    val full = order.firstOrNull { probe.worksFully(it) }
    val target = full ?: order.firstOrNull { probe.works(it) }
    return CfChoice(target, cutOff = full == null && target != null)
}

class TgSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tg_proxy_settings", Context.MODE_PRIVATE)

    fun load(): TgProxySettings {
        val json = prefs.getString(KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val stored = json?.let { runCatching { TgProxySettings.fromJson(it) }.getOrNull() }
        if (stored == null) autoPort = true
        val settings = stored ?: TgProxySettings()
        if (settings.secret.isNotBlank()) return settings
        return settings.copy(secret = TgProxySettings.newSecret()).also(::save)
    }

    fun save(settings: TgProxySettings) {
        prefs.edit { putString(KEY, settings.toJson().toString()) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    var autoPort: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PORT, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_PORT, value) }

    private companion object {
        const val KEY = "config"
        const val KEY_AUTO_PORT = "auto_port"
    }
}
