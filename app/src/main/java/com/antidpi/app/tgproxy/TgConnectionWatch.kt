package com.antidpi.app.tgproxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TelephonyNetworkSpecifier
import android.os.Build
import android.telephony.TelephonyManager

internal class StallDetector(
    private val windowMs: Long = 60_000,
    private val minAttempts: Int = 3,
) {
    private var lastProgressAt = -1L
    private var totalAtProgress = 0
    private var bytesDown = 0L
    private var cfOkAtProgress = 0

    var progressed = false
        private set

    var cfOpened = 0
        private set

    fun update(status: TgProxyStatus, now: Long): Boolean {
        progressed = lastProgressAt < 0 || status.bytesDown != bytesDown
        if (progressed) {
            lastProgressAt = now
            totalAtProgress = status.total
            bytesDown = status.bytesDown
            cfOkAtProgress = status.cfOk
            cfOpened = 0
            return false
        }
        cfOpened = status.cfOk - cfOkAtProgress
        return now - lastProgressAt >= windowMs && status.total - totalAtProgress >= minAttempts
    }
}

internal class StalledWays(private val keepMs: Long = 6 * 60 * 60_000L) {
    private var network: Any? = null
    private val since = mutableMapOf<CfTransport, Long>()

    fun on(on: Any, now: Long): Set<CfTransport> =
        if (on != network) emptySet() else since.filterValues { now - it < keepMs }.keys

    fun add(on: Any, way: CfTransport, now: Long): Boolean {
        if (on != network) {
            network = on
            since.clear()
        }
        if (way in on(on, now)) return false
        since[way] = now
        return true
    }

    fun remove(on: Any, way: CfTransport) {
        if (on == network) since.remove(way)
    }
}

internal data class CfOutcome(val target: CfTransport?, val cutOff: Boolean, val takeBack: Boolean = false)

internal fun cfCheckOutcome(
    probe: TgProxyCore.CfProbe,
    stalled: Set<CfTransport>,
    current: CfTransport
): CfOutcome {
    val all = chooseCfTransport(probe)
    val rest = chooseCfTransport(probe, stalled).target
    return when {
        rest != null -> CfOutcome(rest, all.cutOff)
        all.target == null -> CfOutcome(null, all.cutOff)
        probe.works(current) -> CfOutcome(current, all.cutOff)
        else -> CfOutcome(all.target, all.cutOff, takeBack = true)
    }
}

internal enum class CfStallStep {
    RECHECK,
    SET_ASIDE,
    NONE,
}

internal fun cfStallStep(
    auto: Boolean, checkedHere: Boolean, cfOpened: Int,
    current: CfTransport, stalled: Set<CfTransport>,
): CfStallStep = when {
    !auto -> CfStallStep.NONE
    !checkedHere -> CfStallStep.RECHECK
    cfOpened > 0 && current !in stalled -> CfStallStep.SET_ASIDE
    else -> CfStallStep.NONE
}

internal class CfFailureDetector(
    private val windowMs: Long = 120_000,
    private val minFailed: Int = 20,
    private val ratio: Int = 10,
) {
    private val samples = ArrayDeque<Triple<Long, Int, Int>>()

    var failed = 0
        private set
    var opened = 0
        private set

    fun update(status: TgProxyStatus, now: Long): Boolean {
        samples.addLast(Triple(now, status.cfOk, status.cfFailed))
        while (samples.size > 1 && now - samples[1].first >= windowMs) samples.removeFirst()
        val (_, ok, failedThen) = samples.first()
        failed = status.cfFailed - failedThen
        opened = status.cfOk - ok
        return failed >= minFailed && failed >= ratio * opened
    }
}

internal fun Context.describeNetwork(network: Network?): String {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val caps = network?.let { connectivity.getNetworkCapabilities(it) } ?: return "no network"
    val kinds = listOf(
        NetworkCapabilities.TRANSPORT_VPN to "VPN",
        NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi",
        NetworkCapabilities.TRANSPORT_CELLULAR to "mobile data",
        NetworkCapabilities.TRANSPORT_ETHERNET to "Ethernet",
    ).filter { (transport, _) -> caps.hasTransport(transport) }.map { it.second }
    val name = kinds.joinToString(" over ").ifEmpty { "other network" }
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return name
    val carrier = carrierOf(caps)
    return if (carrier.isEmpty()) name else "$name ($carrier)"
}

private fun Context.carrierOf(caps: NetworkCapabilities): String = try {
    var telephony = getSystemService(TelephonyManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val subId = (caps.networkSpecifier as? TelephonyNetworkSpecifier)?.subscriptionId
        if (subId != null) telephony = telephony.createForSubscriptionId(subId)
    }
    val operator = telephony.networkOperatorName.orEmpty().ifEmpty { telephony.simOperatorName.orEmpty() }
    val roaming = Build.VERSION.SDK_INT >= 28 &&
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    listOf(operator.trim(), if (roaming) "roaming" else "").filter { it.isNotEmpty() }.joinToString(", ")
} catch (_: RuntimeException) {
    ""
}
