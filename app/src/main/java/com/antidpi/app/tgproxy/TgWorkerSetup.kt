package com.antidpi.app.tgproxy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

object TgWorkerSetup {
    const val SCRIPT_NAME = "andmtproto"
    internal const val SCRIPT_MARKER = "AndMTProto Cloudflare Worker"
    const val TOKENS_PAGE = "https://dash.cloudflare.com/profile/api-tokens"
    private const val API = "https://api.cloudflare.com/client/v4"
    private const val ASSET = "cf-worker/worker.js"
    private const val COMPATIBILITY_DATE = "2025-01-01"

    sealed interface Result {
        data class Created(val domain: String, val account: String? = null) : Result
        data class Failed(val error: Error, val detail: String? = null) : Result
    }

    enum class Error {
        BAD_TOKEN,
        NO_ACCOUNT,
        NO_PERMISSION,
        NETWORK,
        NAME_TAKEN,
        API,
    }

    fun interface Api {
        fun call(method: String, path: String, contentType: String?, body: ByteArray?): Pair<Int, String>
    }

    fun api(token: String): Api = Api { method, path, contentType, body ->
        val conn = URL(API + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    fun workerScript(context: Context): String =
        context.assets.open(ASSET).bufferedReader().use { it.readText() }

    fun isWellFormedToken(token: String): Boolean {
        val t = token.trim()
        return t.isNotEmpty() && t.all { it in '!'..'~' }
    }

    suspend fun create(api: Api, script: String): Result = withContext(Dispatchers.IO) {
        try {
            createBlocking(api, script)
        } catch (e: IOException) {
            Result.Failed(Error.NETWORK, e.message)
        } catch (_: IllegalArgumentException) {
            Result.Failed(Error.BAD_TOKEN)
        }
    }

    private fun createBlocking(api: Api, script: String): Result {
        val accounts = api.json("GET", "/accounts?per_page=5")
        accounts.failure(firstCall = true)?.let { return it }
        val list = accounts.result<JSONArray>()
        val accountId = list?.optJSONObject(0)?.optString("id").orEmpty()
        if (accountId.isEmpty()) return Result.Failed(Error.NO_ACCOUNT)
        val accountName = if ((list?.length() ?: 0) > 1) list?.optJSONObject(0)?.optString("name") else null
        val account = "/accounts/$accountId/workers"

        val sub = api.json("GET", "$account/subdomain")
        var subdomain = sub.result<JSONObject>()?.optString("subdomain").orEmpty()
        if (subdomain.isEmpty()) {
            val made = api.json(
                "PUT", "$account/subdomain", "application/json",
                JSONObject().put("subdomain", "andmtproto-${randomSuffix()}").toString().toByteArray(),
            )
            made.failure()?.let { return it }
            subdomain = made.result<JSONObject>()?.optString("subdomain").orEmpty()
            if (subdomain.isEmpty()) return Result.Failed(Error.API, "no workers.dev subdomain")
        }

        val (existingCode, existing) = api.call("GET", "$account/scripts/$SCRIPT_NAME", null, null)
        if (existingCode == 200 && !existing.contains(SCRIPT_MARKER)) {
            if (looksLikeWebPage(existing)) return Result.Failed(Error.NETWORK, "HTTP $existingCode")
            return Result.Failed(Error.NAME_TAKEN)
        }

        val boundary = "andmtproto-${randomSuffix()}"
        val upload = api.json(
            "PUT", "$account/scripts/$SCRIPT_NAME", "multipart/form-data; boundary=$boundary",
            multipart(boundary, script)
        )
        upload.failure()?.let { return it }

        val enable = api.json(
            "POST", "$account/scripts/$SCRIPT_NAME/subdomain", "application/json",
            JSONObject().put("enabled", true).put("previews_enabled", false).toString().toByteArray()
        )
        enable.failure()?.let { return it }

        return Result.Created("$SCRIPT_NAME.$subdomain.workers.dev", accountName?.takeIf { it.isNotBlank() })
    }

    private fun looksLikeWebPage(text: String): Boolean {
        val start = text.trimStart().take(15).lowercase()
        return start.startsWith("<!doctype") || start.startsWith("<html")
    }

    private fun multipart(boundary: String, script: String): ByteArray {
        val metadata = JSONObject()
            .put("main_module", "worker.js")
            .put("compatibility_date", COMPATIBILITY_DATE)
        return buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"metadata\"\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"worker.js\"; filename=\"worker.js\"\r\n")
            append("Content-Type: application/javascript+module\r\n\r\n")
            append(script).append("\r\n")
            append("--$boundary--\r\n")
        }.toByteArray()
    }

    private fun randomSuffix(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private class Reply(val code: Int, val body: JSONObject?) {
        inline fun <reified T> result(): T? = body?.opt("result") as? T

        fun failure(firstCall: Boolean = false): Result.Failed? {
            if (code in 200..299 && body?.optBoolean("success", false) == true) return null
            if (body == null || !body.has("success")) return Result.Failed(Error.NETWORK, "HTTP $code")
            val message = body.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            return when {
                code == 401 || (firstCall && code == 400) -> Result.Failed(Error.BAD_TOKEN, message)
                code == 403 -> Result.Failed(Error.NO_PERMISSION, message)
                else -> Result.Failed(Error.API, message ?: "HTTP $code")
            }
        }
    }

    private fun Api.json(method: String, path: String, contentType: String? = null, body: ByteArray? = null): Reply {
        val (code, text) = call(method, path, contentType, body)
        return Reply(code, runCatching { JSONObject(text) }.getOrNull())
    }
}

fun TgProxySettings.withWorkerDomain(domain: String): TgProxySettings {
    val domains = (cfWorkerDomains.split(',').map { it.trim() } + domain)
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
    return copy(cfWorkerEnabled = true, cfWorkerDomains = domains.joinToString(", "))
}
