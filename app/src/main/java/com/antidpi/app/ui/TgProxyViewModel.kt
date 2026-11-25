package com.antidpi.app.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.antidpi.app.R
import com.antidpi.app.tgproxy.TgProxyCore
import com.antidpi.app.tgproxy.TgProxyService
import com.antidpi.app.tgproxy.TgProxySettings
import com.antidpi.app.tgproxy.TgProxyStatus
import com.antidpi.app.tgproxy.TgSettingsStore
import com.antidpi.app.tgproxy.TlsStrategies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TgSettingsDraft(
    val settings: TgProxySettings,
    val port: String = settings.port.toString(),
    val poolSize: String = settings.poolSize.toString(),
    val bufKb: String = settings.bufKb.toString(),
    val dcIp: String = settings.dcIp.joinToString("\n"),
) {
    fun edit(change: TgProxySettings.() -> TgProxySettings) = copy(settings = settings.change())

    sealed interface Parsed {
        data class Valid(val settings: TgProxySettings) : Parsed
        data class NotANumber(@StringRes val field: Int) : Parsed
    }

    fun parse(): Parsed {
        val p = port.trim().toIntOrNull() ?: return Parsed.NotANumber(R.string.tg_port)
        val pool = poolSize.trim().toIntOrNull() ?: return Parsed.NotANumber(R.string.tg_pool_size)
        val buf = bufKb.trim().toIntOrNull() ?: return Parsed.NotANumber(R.string.tg_buf_kb)
        return Parsed.Valid(
            settings.copy(
                host = settings.host.trim(),
                port = p,
                secret = settings.secret.trim(),
                dcIp = dcIp.lines().map { it.trim() }.filter { it.isNotEmpty() },
                poolSize = pool,
                bufKb = buf,
                fakeTlsDomain = settings.fakeTlsDomain.trim(),
                tlsStrategy = settings.tlsStrategy.trim().ifEmpty { TlsStrategies.DEFAULT },
            )
        )
    }
}

class TgProxyViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TgSettingsStore(app)

    private val _saved = MutableStateFlow(store.load())
    val saved: StateFlow<TgProxySettings> = _saved.asStateFlow()

    private val _draft = MutableStateFlow(TgSettingsDraft(_saved.value))
    val draft: StateFlow<TgSettingsDraft> = _draft.asStateFlow()

    val status: StateFlow<TgProxyStatus> = TgProxyCore.state
    val stalled: StateFlow<Boolean> = TgProxyCore.stalled
    val cfCheck: StateFlow<TgProxyCore.CfCheck?> = TgProxyCore.cfCheck

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val loaded = store.load()
        if (loaded == _saved.value) return@OnSharedPreferenceChangeListener
        val untouched = _draft.value == TgSettingsDraft(_saved.value)
        _saved.value = loaded
        if (untouched) _draft.value = TgSettingsDraft(loaded)
    }

    init {
        store.registerListener(listener)
        TgProxyCore.refresh()
    }

    fun edit(newDraft: TgSettingsDraft) {
        _draft.value = newDraft
    }

    sealed interface SaveResult {
        data object Unchanged : SaveResult
        data class Saved(val restarted: Boolean) : SaveResult
        data class Invalid(@StringRes val field: Int) : SaveResult
        data class BadStrategy(val part: String) : SaveResult
    }

    fun save(): SaveResult {
        var parsed = when (val p = _draft.value.parse()) {
            is TgSettingsDraft.Parsed.NotANumber -> return SaveResult.Invalid(p.field)
            is TgSettingsDraft.Parsed.Valid -> p.settings
        }
        if (parsed.cfAuto && _saved.value.cfAuto) {
            parsed = parsed.withCurrentCfTransport(_saved.value.cfTransport)
        }
        if (parsed == _saved.value) return SaveResult.Unchanged
        if (parsed.tlsBypass && parsed.tlsStrategy != _saved.value.tlsStrategy) {
            TgProxyCore.badTlsStrategyPart(parsed.tlsStrategy)?.let { return SaveResult.BadStrategy(it) }
        }
        if (parsed.port != _saved.value.port) store.autoPort = false
        persist(parsed)
        _draft.value = TgSettingsDraft(parsed)
        val running = status.value.running
        if (running) {
            TgProxyService.start(getApplication(), restart = true)
        }
        return SaveResult.Saved(running)
    }

    fun resetDefaults(): SaveResult {
        val currentSecret = _saved.value.secret.ifBlank { TgProxySettings.newSecret() }
        val defaults = TgProxySettings(secret = currentSecret)
        persist(defaults)
        _draft.value = TgSettingsDraft(defaults)
        val running = status.value.running
        if (running) {
            TgProxyService.start(getApplication(), restart = true)
        }
        return SaveResult.Saved(running)
    }

    fun usePort(newPort: Int) {
        store.autoPort = false
        val updated = _saved.value.copy(port = newPort)
        persist(updated)
        _draft.update { it.edit { copy(port = newPort) }.copy(port = newPort.toString()) }
    }

    fun link(): String? = TgProxyCore.link(_saved.value)

    fun savedAddress(): String = "${_saved.value.host}:${_saved.value.port}"

    private fun persist(settings: TgProxySettings) {
        store.save(settings)
        _saved.value = settings
    }

    override fun onCleared() {
        store.unregisterListener(listener)
    }
}
