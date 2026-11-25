package com.antidpi.app.tgproxy

import org.json.JSONObject

enum class CfRoute(val key: String) { CF_PROXY("cf"), WORKER("worker") }

enum class CfFailureReason(val key: String) {
    TIMEOUT("timeout"),
    RESET("reset"),
    TLS("tls"),
    DNS("dns"),
    REFUSED("refused"),
    LIMIT("limit"),
    FORBIDDEN("forbidden"),
    SERVER_ERROR("server_error"),
    HTTP("http"),
    NETWORK("network"),
    WORKER_CLOSED("worker_closed"),
    OTHER("other");

    companion object {
        fun of(key: String): CfFailureReason = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class CfTrouble(val route: CfRoute, val reason: CfFailureReason)

private const val MIN_FAILURES = 5
private const val FAILURE_RATIO = 3

fun cfTrouble(recent: Map<String, Map<String, Int>>, workerTrouble: String? = null): CfTrouble? {
    if (workerTrouble != null) return CfTrouble(CfRoute.WORKER, CfFailureReason.of(workerTrouble))
    val counts = recent[CfRoute.CF_PROXY.key] ?: return null
    val ok = counts["ok"] ?: 0
    val failures = counts.filterKeys { it != "ok" }
    val failed = failures.values.sum()
    if (failed < MIN_FAILURES || failed < FAILURE_RATIO * ok) return null
    val top = failures.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first()
    return CfTrouble(CfRoute.CF_PROXY, CfFailureReason.of(top.key))
}

internal fun parseCfRecent(o: JSONObject?): Map<String, Map<String, Int>> {
    if (o == null) return emptyMap()
    return o.keys().asSequence().associateWith { route ->
        val counts = o.optJSONObject(route) ?: JSONObject()
        counts.keys().asSequence().associateWith { counts.optInt(it) }
    }
}
