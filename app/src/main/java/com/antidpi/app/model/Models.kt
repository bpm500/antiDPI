package com.antidpi.app.model

data class ConfigEntry(
    val name: String,
    val args: String,
    val lastOk: Int? = null,
    val lastTotal: Int? = null,
    val lastAvgMs: Int? = null,
    val networkType: String? = null
)

data class StrategyTemplate(
    val name: String,
    val args: String,
    val isDefault: Boolean = false
)

data class ExtraStrategyResult(
    val baseName: String,
    val args: String,
    val avgMs: Int,
    val networkType: String?
)

sealed interface VpnState {
    data object Disconnected : VpnState
    data class Connecting(val configName: String) : VpnState
    data class Connected(val configName: String) : VpnState
    data class Error(val message: String) : VpnState
}

data class ProbeTarget(
    val name: String,
    val url: String,
    val group: String = name,
    val okStatus: Set<Int>? = setOf(200),
    val minBodyBytes: Int = 0,
    val bodyContains: String? = null
)

data class ProbeResult(
    val targetName: String,
    val groupName: String,
    val url: String,
    val ok: Boolean,
    val statusCode: Int? = null,
    val bodyBytes: Int = 0,
    val latencyMs: Int? = null,
    val resolvedIp: String? = null,
    val error: String? = null
)

data class ConfigRating(
    val ok: Int,
    val total: Int,
    val avgMs: Int,
    val networkType: String? = null
)

data class AppSettings(
    val parallelSlots: Int = 3,
    val bgPromptDismissed: Boolean = false,
    val selectedConfigName: String? = null
)
