package com.antidpi.app.model

data class ConfigEntry(
    val name: String,
    val args: String,
    val lastOk: Int? = null,
    val lastTotal: Int? = null,
    val lastAvgMs: Int? = null
)

data class StrategyTemplate(
    val name: String,
    val args: String,
    val isDefault: Boolean = false
)

sealed interface VpnState {
    data object Disconnected : VpnState
    data class Connecting(val configName: String) : VpnState
    data class Connected(val configName: String) : VpnState
    data class Error(val message: String) : VpnState
}

data class ProbeTarget(
    val name: String,
    val url: String
)

data class ProbeResult(
    val targetName: String,
    val ok: Boolean,
    val latencyMs: Int?,
    val resolvedIp: String?,
    val error: String? = null
)

data class ConfigRating(
    val ok: Int,
    val total: Int,
    val avgMs: Int
)

data class AppSettings(
    val parallelSlots: Int = 3,
    val bgPromptDismissed: Boolean = false,
    val selectedConfigName: String? = null
)
