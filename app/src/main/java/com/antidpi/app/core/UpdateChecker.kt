package com.antidpi.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val downloadUrl: String,
    val releaseUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestTag: String?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object UpdateChecker {

    private const val REPO = "bpm500/antiDPI"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val API_TAGS = "https://api.github.com/repos/$REPO/tags"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val latest = fetchFromLatestApi() ?: fetchFromTagsApi() ?: fetchFromReleasesHtml()
            if (latest == null) {
                return@withContext UpdateCheckResult.UpToDate(currentVersion, null)
            }
            if (isNewerVersion(latest.tag, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(latest)
            } else {
                UpdateCheckResult.UpToDate(currentVersion, latest.tag)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Network error")
        }
    }

    private fun fetchFromLatestApi(): ReleaseInfo? {
        return try {
            val body = httpGet(API_LATEST) ?: return null
            val json = JSONObject(body)
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val htmlUrl = json.optString("html_url").ifBlank { "$RELEASES_PAGE/tag/$tag" }
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name").lowercase()
                    if (name.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            ReleaseInfo(tag = tag, downloadUrl = apkUrl.ifBlank { htmlUrl }, releaseUrl = htmlUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromTagsApi(): ReleaseInfo? {
        return try {
            val body = httpGet(API_TAGS) ?: return null
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            val tag = arr.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } ?: return null
            val url = "$RELEASES_PAGE/tag/$tag"
            ReleaseInfo(tag = tag, downloadUrl = url, releaseUrl = url)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromReleasesHtml(): ReleaseInfo? {
        return try {
            val html = httpGet(RELEASES_PAGE) ?: return null
            val match = Regex("/$REPO/releases/tag/([^\"'\\s<>]+)").find(html) ?: return null
            val tag = match.groupValues[1]
            val url = "$RELEASES_PAGE/tag/$tag"
            ReleaseInfo(tag = tag, downloadUrl = url, releaseUrl = url)
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "antiDPI-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val rParts = parseVersionParts(remoteTag)
        val cParts = parseVersionParts(currentVersion)
        if (rParts.isEmpty() || cParts.isEmpty()) return false
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        if (clean.isBlank()) return emptyList()
        return clean.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
    }
}
