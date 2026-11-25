package com.antidpi.app.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antidpi.app.BuildConfig
import com.antidpi.app.R
import com.antidpi.app.core.CiAdpiProcessManager
import com.antidpi.app.core.ProbeEngine
import com.antidpi.app.core.ReleaseInfo
import com.antidpi.app.core.UpdateCheckResult
import com.antidpi.app.core.UpdateChecker
import com.antidpi.app.data.ConfigRepository
import com.antidpi.app.data.SettingsRepository
import com.antidpi.app.model.AppSettings
import com.antidpi.app.model.ConfigEntry
import com.antidpi.app.model.ConfigRating
import com.antidpi.app.model.ProbeTarget
import com.antidpi.app.model.VpnState
import com.antidpi.app.vpn.AntiDpiVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val configRepo = ConfigRepository(appContext)
    private val settingsRepo = SettingsRepository(appContext)
    private val processManager = CiAdpiProcessManager(appContext)

    private val _configs = MutableStateFlow<List<ConfigEntry>>(emptyList())
    val configs: StateFlow<List<ConfigEntry>> = _configs.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val vpnState: StateFlow<VpnState> = AntiDpiVpnService.stateFlow

    private val _isIgnoringBattery = MutableStateFlow(checkIgnoringBattery())
    val isIgnoringBattery: StateFlow<Boolean> = _isIgnoringBattery.asStateFlow()

    // Test tab state
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testProgress = MutableStateFlow(0f)
    val testProgress: StateFlow<Float> = _testProgress.asStateFlow()

    private val _testLogs = MutableStateFlow<List<String>>(emptyList())
    val testLogs: StateFlow<List<String>> = _testLogs.asStateFlow()

    private val _testRanking = MutableStateFlow("")
    val testRanking: StateFlow<String> = _testRanking.asStateFlow()

    // Create tab state
    private val _targetDiscord = MutableStateFlow(true)
    val targetDiscord: StateFlow<Boolean> = _targetDiscord.asStateFlow()

    private val _targetYouTube = MutableStateFlow(true)
    val targetYouTube: StateFlow<Boolean> = _targetYouTube.asStateFlow()

    private val _numConfigsToFind = MutableStateFlow(2)
    val numConfigsToFind: StateFlow<Int> = _numConfigsToFind.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _createLogs = MutableStateFlow<List<String>>(emptyList())
    val createLogs: StateFlow<List<String>> = _createLogs.asStateFlow()

    private val _manualError = MutableStateFlow<String?>(null)
    val manualError: StateFlow<String?> = _manualError.asStateFlow()

    private val _manualValidating = MutableStateFlow(false)
    val manualValidating: StateFlow<Boolean> = _manualValidating.asStateFlow()

    // Update check state
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _pendingUpdateDialog = MutableStateFlow<ReleaseInfo?>(null)
    val pendingUpdateDialog: StateFlow<ReleaseInfo?> = _pendingUpdateDialog.asStateFlow()

    private var testJob: Job? = null
    private var createJob: Job? = null
    private val testStopFlag = AtomicBoolean(false)
    private val createStopFlag = AtomicBoolean(false)
    private val activeTestProcesses = ConcurrentLinkedQueue<CiAdpiProcessManager.RunningInstance>()
    private val activeCreateProcesses = ConcurrentLinkedQueue<CiAdpiProcessManager.RunningInstance>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            configRepo.ensureInitialized()
            settingsRepo.ratingsFlow.combine(settingsRepo.settingsFlow) { ratings, st ->
                val list = configRepo.listConfigs(ratings)
                if (st.selectedConfigName == null && list.isNotEmpty()) {
                    settingsRepo.setSelectedConfig(list.first().name)
                } else if (st.selectedConfigName != null && list.none { it.name == st.selectedConfigName }) {
                    settingsRepo.setSelectedConfig(list.firstOrNull()?.name)
                }
                list
            }.collect { list ->
                _configs.value = list
            }
        }
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (_updateState.value is UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateState.value = UpdateUiState.Available(res.release)
                    _pendingUpdateDialog.value = res.release
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateState.value = UpdateUiState.UpToDate(res.currentVersion)
                }
                is UpdateCheckResult.Error -> {
                    _updateState.value = if (manual) {
                        UpdateUiState.Error(res.message)
                    } else {
                        UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _pendingUpdateDialog.value = null
    }

    fun refreshBatteryStatus() {
        _isIgnoringBattery.value = checkIgnoringBattery()
    }

    private fun checkIgnoringBattery(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun dismissBgPrompt() {
        viewModelScope.launch {
            settingsRepo.setBgPromptDismissed(true)
        }
    }

    fun selectConfig(name: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedConfig(name)
        }
    }

    fun renameConfig(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = ConfigRepository.sanitizeName(newName)
            if (clean.isEmpty() || clean == oldName) return@launch
            if (configRepo.renameConfig(oldName, clean)) {
                settingsRepo.renameRatingKey(oldName, clean)
                if (settings.value.selectedConfigName == oldName) {
                    settingsRepo.setSelectedConfig(clean)
                }
                reloadConfigs()
            }
        }
    }

    fun deleteConfig(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configRepo.deleteConfig(name)) {
                settingsRepo.removeRatingKey(name)
                reloadConfigs()
            }
        }
    }

    fun setParallelSlots(slots: Int) {
        viewModelScope.launch { settingsRepo.setParallelSlots(slots) }
    }

    fun setTargetDiscord(v: Boolean) { _targetDiscord.value = v }
    fun setTargetYouTube(v: Boolean) { _targetYouTube.value = v }
    fun setNumConfigsToFind(n: Int) { _numConfigsToFind.value = n.coerceIn(1, 10) }

    fun clearTestLogs() { _testLogs.value = emptyList() }
    fun clearCreateLogs() { _createLogs.value = emptyList() }
    fun clearManualError() { _manualError.value = null }

    fun hasNetworkConnection(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun testSelected() {
        if (_isTesting.value) return
        val selectedName = settings.value.selectedConfigName ?: _configs.value.firstOrNull()?.name ?: return
        val cfg = _configs.value.firstOrNull { it.name == selectedName } ?: return

        testStopFlag.set(false)
        _isTesting.value = true
        _testProgress.value = 0f
        appendTestLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendTestLog("Testing selected: ${cfg.name}")

        testJob = viewModelScope.launch(Dispatchers.IO) {
            if (!hasNetworkConnection()) {
                appendTestLog("✗ ${appContext.getString(R.string.err_no_network)}")
                _isTesting.value = false
                return@launch
            }

            val hostlist = configRepo.getHostlistFile()
            val targets = ProbeEngine.DEFAULT_TARGETS
            val (okCount, avgMs, summaryLines) = testSingleConfig(cfg, hostlist, targets, testStopFlag, activeTestProcesses)

            if (!testStopFlag.get()) {
                settingsRepo.updateRating(cfg.name, okCount, targets.size, avgMs)
                summaryLines.forEach { appendTestLog("  $it") }
                appendTestLog("Result: $okCount/${targets.size} OK (${if (avgMs < 9000) "$avgMs ms" else "timeout"})")
                _testRanking.value = buildString {
                    appendLine("CONFIG: ${cfg.name}")
                    summaryLines.forEach { appendLine("  $it") }
                    append("Score: $okCount/${targets.size} OK")
                }
                _testProgress.value = 1f
            } else {
                appendTestLog("⛔ Stopped.")
            }
            _isTesting.value = false
        }
    }

    fun testAll() {
        if (_isTesting.value) return
        val allConfigs = _configs.value
        if (allConfigs.isEmpty()) return

        testStopFlag.set(false)
        _isTesting.value = true
        _testProgress.value = 0f
        _testRanking.value = ""

        val slots = settings.value.parallelSlots.coerceIn(1, 6)
        val hostlist = configRepo.getHostlistFile()
        val targets = ProbeEngine.DEFAULT_TARGETS

        appendTestLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendTestLog("Testing ${allConfigs.size} configs ($slots parallel slots)...")

        testJob = viewModelScope.launch(Dispatchers.IO) {
            if (!hasNetworkConnection()) {
                appendTestLog("✗ ${appContext.getString(R.string.err_no_network)}")
                _isTesting.value = false
                return@launch
            }

            val queue = ConcurrentLinkedQueue(allConfigs)
            val scored = ConcurrentHashMap<String, ConfigRating>()
            val done = AtomicInteger(0)
            val logMutex = Mutex()
            val total = allConfigs.size

            coroutineScope {
                val workers = (0 until slots).map {
                    async(Dispatchers.IO) {
                        while (!testStopFlag.get()) {
                            val cfg = queue.poll() ?: break
                            val (okCount, avgMs, lines) = testSingleConfig(
                                cfg, hostlist, targets, testStopFlag, activeTestProcesses
                            )
                            if (testStopFlag.get()) break
                            scored[cfg.name] = ConfigRating(okCount, targets.size, avgMs)
                            val count = done.incrementAndGet()
                            _testProgress.value = count.toFloat() / total.toFloat()
                            logMutex.withLock {
                                appendTestLog("\n▶ [$count/$total] ${cfg.name}")
                                lines.forEach { appendTestLog("  $it") }
                            }
                        }
                    }
                }
                workers.awaitAll()
            }

            if (testStopFlag.get()) {
                appendTestLog("\n⛔ Testing stopped.")
                _isTesting.value = false
                return@launch
            }

            // Persist ratings
            val existingRatings = settingsRepo.let {
                val current = mutableMapOf<String, ConfigRating>()
                scored.forEach { (k, v) -> current[k] = v }
                current
            }
            settingsRepo.setAllRatings(existingRatings)

            val sorted = scored.entries.sortedWith(
                compareByDescending<Map.Entry<String, ConfigRating>> { it.value.ok }
                    .thenBy { it.value.avgMs }
            )

            val rankingText = buildString {
                appendLine("TOP CONFIGS RANKING")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                sorted.forEachIndexed { idx, (name, rating) ->
                    val latStr = if (rating.avgMs < 9000) "${rating.avgMs} ms" else "timeout"
                    val mark = when {
                        rating.ok == 0 -> "✗"
                        idx == 0 -> "★"
                        else -> "${idx + 1}."
                    }
                    appendLine("$mark $name — ${rating.ok}/${rating.total} OK ($latStr)")
                }
            }.trimEnd()

            _testRanking.value = rankingText
            appendTestLog("\n$rankingText")

            val best = sorted.firstOrNull { it.value.ok > 0 }?.key
            if (best != null) {
                appendTestLog("\n★ Best: $best")
            } else {
                appendTestLog("\n⚠ No working configs found.")
            }

            _isTesting.value = false
        }
    }

    fun stopTesting() {
        testStopFlag.set(true)
        while (true) {
            val inst = activeTestProcesses.poll() ?: break
            inst.stop()
        }
        testJob?.cancel()
        _isTesting.value = false
    }

    private suspend fun testSingleConfig(
        cfg: ConfigEntry,
        hostlist: java.io.File?,
        targets: List<ProbeTarget>,
        stopFlag: AtomicBoolean,
        activeBag: ConcurrentLinkedQueue<CiAdpiProcessManager.RunningInstance>
    ): Triple<Int, Int, List<String>> {
        val started = processManager.start(
            args = cfg.args,
            hostlistFile = hostlist
        )
        val inst = started.getOrElse { err ->
            return Triple(0, 99999, listOf("ciadpi failed: ${err.message}"))
        }
        activeBag.add(inst)
        return try {
            val lines = mutableListOf<String>()
            var okCount = 0
            val latencies = mutableListOf<Int>()
            for (target in targets) {
                if (stopFlag.get()) break
                val res = ProbeEngine.probeThroughSocks5(
                    socksPort = inst.port,
                    target = target,
                    timeoutMs = 5000,
                    retries = 2,
                    isStopped = { stopFlag.get() }
                )
                if (res.ok) {
                    okCount++
                    res.latencyMs?.let { latencies.add(it) }
                    lines.add("${target.name}: ✓ (${res.latencyMs} ms, ip=${res.resolvedIp ?: "?"})")
                } else {
                    lines.add("${target.name}: ✗ (${res.error ?: "blocked"}, ip=${res.resolvedIp ?: "?"})")
                }
            }
            val avg = if (latencies.isNotEmpty()) latencies.average().toInt() else 9999
            Triple(okCount, avg, lines)
        } finally {
            activeBag.remove(inst)
            inst.stop()
        }
    }

    fun startCreate() {
        if (_isCreating.value) return
        val wantDiscord = _targetDiscord.value
        val wantYouTube = _targetYouTube.value
        if (!wantDiscord && !wantYouTube) {
            appendCreateLog("✗ ${appContext.getString(R.string.err_no_targets)}")
            return
        }

        val targets = buildList {
            if (wantDiscord) add(ProbeEngine.DEFAULT_TARGETS[0])
            if (wantYouTube) add(ProbeEngine.DEFAULT_TARGETS[1])
        }
        val goal = _numConfigsToFind.value.coerceIn(1, 10)
        val slots = settings.value.parallelSlots.coerceIn(1, 6)
        val hostlist = configRepo.getHostlistFile()

        createStopFlag.set(false)
        _isCreating.value = true
        appendCreateLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendCreateLog("Create started ($goal requested, targets=${targets.joinToString { it.name }})")

        createJob = viewModelScope.launch(Dispatchers.IO) {
            if (!hasNetworkConnection()) {
                appendCreateLog("✗ ${appContext.getString(R.string.err_no_network)}")
                _isCreating.value = false
                return@launch
            }

            val strategies = configRepo.loadStrategies().shuffled()
            val queue = ConcurrentLinkedQueue(strategies.withIndex().toList())
            val total = strategies.size
            val created = AtomicInteger(0)
            val enough = AtomicBoolean(false)
            val saveMutex = Mutex()
            val logMutex = Mutex()

            coroutineScope {
                val workers = (0 until slots).map {
                    async(Dispatchers.IO) {
                        while (!createStopFlag.get() && !enough.get()) {
                            val indexed = queue.poll() ?: break
                            val idx = indexed.index
                            val st = indexed.value

                            if (configRepo.isStrategyExisting(st.args, st.name)) {
                                logMutex.withLock {
                                    appendCreateLog("▶ [${idx + 1}/$total] ${st.name}: skipped (already exists)")
                                }
                                continue
                            }

                            val started = processManager.start(args = st.args, hostlistFile = hostlist)
                            val inst = started.getOrNull()
                            if (inst == null) {
                                val err = started.exceptionOrNull()
                                logMutex.withLock {
                                    appendCreateLog("▶ [${idx + 1}/$total] ${st.name}: start failed (${err?.message})")
                                }
                                continue
                            }
                            activeCreateProcesses.add(inst)

                            var allOk = true
                            val probeLines = mutableListOf<String>()
                            val latencies = mutableListOf<Int>()
                            try {
                                for (target in targets) {
                                    if (createStopFlag.get() || enough.get()) {
                                        allOk = false
                                        break
                                    }
                                    val res = ProbeEngine.probeThroughSocks5(
                                        socksPort = inst.port,
                                        target = target,
                                        timeoutMs = 5000,
                                        retries = 2,
                                        isStopped = { createStopFlag.get() || enough.get() }
                                    )
                                    if (res.ok) {
                                        res.latencyMs?.let { latencies.add(it) }
                                        probeLines.add("  ${target.name}: ✓ (${res.latencyMs} ms)")
                                    } else {
                                        probeLines.add("  ${target.name}: ✗ (${res.error ?: "blocked"})")
                                        allOk = false
                                        break
                                    }
                                }
                            } finally {
                                activeCreateProcesses.remove(inst)
                                inst.stop()
                            }

                            if (createStopFlag.get()) break

                            logMutex.withLock {
                                appendCreateLog("▶ [${idx + 1}/$total] ${st.name}")
                                probeLines.forEach { appendCreateLog(it) }
                            }

                            if (!allOk) continue

                            saveMutex.withLock {
                                if (created.get() >= goal || enough.get()) {
                                    enough.set(true)
                                    return@withLock
                                }
                                if (configRepo.isStrategyExisting(st.args, st.name)) {
                                    logMutex.withLock { appendCreateLog("  Already exists, skipping") }
                                    return@withLock
                                }
                                val finalName = numberedStrategyName(st.name)
                                configRepo.saveConfig(finalName, st.args)
                                val avg = if (latencies.isNotEmpty()) latencies.average().toInt() else 9999
                                settingsRepo.updateRating(finalName, targets.size, targets.size, avg)
                                val n = created.incrementAndGet()
                                reloadConfigs()
                                logMutex.withLock {
                                    appendCreateLog("✓ Saved #$n/$goal: $finalName")
                                }
                                if (n >= goal) {
                                    enough.set(true)
                                }
                            }
                        }
                    }
                }
                workers.awaitAll()
            }

            if (createStopFlag.get()) {
                appendCreateLog("\n⛔ Route creation stopped.")
            } else if (created.get() == 0) {
                appendCreateLog("\n✗ No working strategy found.")
            } else {
                appendCreateLog("\n✓ Finished. Created ${created.get()}/$goal config(s).")
            }
            _isCreating.value = false
        }
    }

    private fun numberedStrategyName(base: String): String {
        val safe = ConfigRepository.sanitizeName(base)
        var n = 1
        while (configRepo.configExists("$safe-$n")) {
            n++
        }
        return "$safe-$n"
    }

    fun stopCreate() {
        createStopFlag.set(true)
        while (true) {
            val inst = activeCreateProcesses.poll() ?: break
            inst.stop()
        }
        createJob?.cancel()
        _isCreating.value = false
    }

    fun validateAndSaveManual(name: String, args: String) {
        val cleanName = ConfigRepository.sanitizeName(name)
        val cleanArgs = args.trim()
        if (cleanName.isEmpty()) {
            _manualError.value = appContext.getString(R.string.err_empty_name)
            return
        }
        if (cleanArgs.isEmpty()) {
            _manualError.value = appContext.getString(R.string.err_invalid_args)
            return
        }
        if (configRepo.configExists(cleanName)) {
            _manualError.value = appContext.getString(R.string.err_config_exists)
            return
        }

        _manualValidating.value = true
        _manualError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val res = processManager.validateArgs(cleanArgs)
            res.fold(
                onSuccess = {
                    configRepo.saveConfig(cleanName, cleanArgs)
                    reloadConfigs()
                    appendCreateLog("✓ Manual config saved: $cleanName")
                    _manualValidating.value = false
                },
                onFailure = { err ->
                    _manualError.value = err.message ?: appContext.getString(R.string.err_invalid_args)
                    _manualValidating.value = false
                }
            )
        }
    }

    private suspend fun reloadConfigs() {
        val currentRatings = _configs.value.associate { c ->
            c.name to ConfigRating(c.lastOk ?: 0, c.lastTotal ?: 0, c.lastAvgMs ?: 9999)
        }.filterValues { it.total > 0 }
        _configs.value = configRepo.listConfigs(currentRatings)
    }

    private fun appendTestLog(line: String) {
        val next = (_testLogs.value + line).takeLast(1500)
        _testLogs.value = next
    }

    private fun appendCreateLog(line: String) {
        val next = (_createLogs.value + line).takeLast(1500)
        _createLogs.value = next
    }
}
