package com.antidpi.app.data

import android.content.Context
import com.antidpi.app.model.ConfigEntry
import com.antidpi.app.model.ConfigRating
import com.antidpi.app.model.StrategyTemplate
import org.json.JSONArray
import java.io.File

class ConfigRepository(private val context: Context) {

    private val configsDir: File = File(context.filesDir, "configs").apply { mkdirs() }
    private val listsDir: File = File(context.filesDir, "lists").apply { mkdirs() }
    private val hostlistFile: File = File(listsDir, "list-general.txt")
    private val initMarker: File = File(configsDir, ".initialized")
    private val v2DefaultsMarker: File = File(configsDir, ".defaults_v2")

    fun ensureInitialized() {
        val assetBytes = context.assets.open("list-general.txt").use { it.readBytes() }
        if (!hostlistFile.exists() || !hostlistFile.readBytes().contentEquals(assetBytes)) {
            hostlistFile.writeBytes(assetBytes)
        }
        if (!initMarker.exists()) {
            val existing = configsDir.listFiles { f -> f.isFile && f.name.endsWith(".conf") }
            if (existing.isNullOrEmpty()) {
                val strategies = loadStrategies()
                val defaults = strategies.filter { it.isDefault }
                val toSeed = if (defaults.isNotEmpty()) defaults else strategies.take(8)
                for (st in toSeed) {
                    saveConfig(st.name, st.args)
                }
            }
            initMarker.writeText("1")
            v2DefaultsMarker.writeText("1")
        } else if (!v2DefaultsMarker.exists()) {
            val strategies = loadStrategies()
            for (st in strategies.filter { it.isDefault && it.name.startsWith("fake-ttl") }) {
                if (!configExists(st.name) && !isStrategyExisting(st.args, st.name)) {
                    saveConfig(st.name, st.args)
                }
            }
            v2DefaultsMarker.writeText("1")
        }
    }

    fun getHostlistFile(): File {
        ensureInitialized()
        return hostlistFile
    }

    fun loadStrategies(): List<StrategyTemplate> {
        val raw = context.assets.open("strategies.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        val out = ArrayList<StrategyTemplate>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                StrategyTemplate(
                    name = obj.getString("name"),
                    args = obj.getString("args"),
                    isDefault = obj.optBoolean("default", false)
                )
            )
        }
        return out
    }

    fun listConfigs(ratings: Map<String, ConfigRating> = emptyMap()): List<ConfigEntry> {
        ensureInitialized()
        val files = configsDir.listFiles { f -> f.isFile && f.name.endsWith(".conf") }
            ?.toList()
            ?: emptyList()
        return files.map { f ->
            val name = f.name.removeSuffix(".conf")
            val args = f.readText().trim()
            val r = ratings[name]
            ConfigEntry(
                name = name,
                args = args,
                lastOk = r?.ok,
                lastTotal = r?.total,
                lastAvgMs = r?.avgMs,
                networkType = r?.networkType
            )
        }.sortedWith(
            compareByDescending<ConfigEntry> { entry ->
                when {
                    entry.lastOk == null -> 0
                    entry.lastOk > 0 -> entry.lastOk + 100
                    else -> -1
                }
            }.thenBy { entry ->
                if ((entry.lastOk ?: 0) > 0) (entry.lastAvgMs ?: 99999) else Int.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )
    }

    fun getConfig(name: String): ConfigEntry? {
        val safe = sanitizeName(name)
        val file = File(configsDir, "$safe.conf")
        if (!file.exists()) return null
        return ConfigEntry(name = safe, args = file.readText().trim())
    }

    fun saveConfig(name: String, args: String): ConfigEntry {
        val safe = sanitizeName(name)
        require(safe.isNotEmpty()) { "Config name cannot be empty" }
        val file = File(configsDir, "$safe.conf")
        file.writeText(args.trim())
        return ConfigEntry(name = safe, args = args.trim())
    }

    fun deleteConfig(name: String): Boolean {
        val safe = sanitizeName(name)
        val file = File(configsDir, "$safe.conf")
        return if (file.exists()) file.delete() else false
    }

    fun renameConfig(oldName: String, newName: String): Boolean {
        val safeOld = sanitizeName(oldName)
        val safeNew = sanitizeName(newName)
        if (safeOld.isEmpty() || safeNew.isEmpty()) return false
        val oldFile = File(configsDir, "$safeOld.conf")
        val newFile = File(configsDir, "$safeNew.conf")
        if (!oldFile.exists() || newFile.exists()) return false
        return oldFile.renameTo(newFile)
    }

    fun configExists(name: String): Boolean {
        val safe = sanitizeName(name)
        if (safe.isEmpty()) return false
        return File(configsDir, "$safe.conf").exists()
    }

    fun nextAvailableName(baseName: String): String {
        val safeBase = sanitizeName(baseName).ifEmpty { "custom" }
        if (!configExists(safeBase)) return safeBase
        var n = 1
        while (configExists("${safeBase}_$n")) {
            n++
        }
        return "${safeBase}_$n"
    }

    fun isStrategyExisting(strategyArgs: String, strategyName: String = ""): Boolean {
        val candidateCounts = extractTokenCounts(strategyArgs)
        if (candidateCounts.isEmpty()) return false
        val current = listConfigs()
        for (cfg in current) {
            if (strategyName.isNotEmpty() && cfg.name.equals(strategyName, ignoreCase = true)) {
                return true
            }
            if (extractTokenCounts(cfg.args) == candidateCounts) {
                return true
            }
        }
        return false
    }

    private fun extractTokenCounts(args: String): Map<String, Int> {
        val tokens = tokenizeArgs(args)
        val counts = mutableMapOf<String, Int>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val normalized = if (t.startsWith("-") && !t.contains("=") && i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) {
                i++
                "$t=${tokens[i]}"
            } else {
                t
            }.lowercase()
            counts[normalized] = (counts[normalized] ?: 0) + 1
            i++
        }
        return counts
    }

    companion object {
        fun sanitizeName(raw: String): String {
            return raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        }

        fun tokenizeArgs(raw: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quote: Char? = null
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    quote != null -> {
                        if (c == quote) {
                            quote = null
                        } else if (c == '\\' && i + 1 < raw.length && quote == '"') {
                            sb.append(raw[++i])
                        } else {
                            sb.append(c)
                        }
                    }
                    c == '"' || c == '\'' -> quote = c
                    c.isWhitespace() -> {
                        if (sb.isNotEmpty()) {
                            out.add(sb.toString())
                            sb.setLength(0)
                        }
                    }
                    else -> sb.append(c)
                }
                i++
            }
            if (sb.isNotEmpty()) {
                out.add(sb.toString())
            }
            return out
        }
    }
}
