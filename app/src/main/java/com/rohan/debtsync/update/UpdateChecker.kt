package com.rohan.debtsync.update

import com.rohan.debtsync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Rohankumarpatra/EmiGo/releases/latest"

    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EmiGo-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == 404) {
                return@withContext Result.success(null)
            }
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }

            val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tag = json.optString("tag_name", "").trim()
            val cleanLatest = tag.removePrefix("v").removePrefix("V")
            if (cleanLatest.isEmpty() || !isNewerVersion(cleanLatest, BuildConfig.VERSION_NAME)) {
                return@withContext Result.success(null)
            }

            val notes = json.optString("body", "").ifBlank { "A new version of EmiGo is available." }
            val fallbackUrl = "https://github.com/Rohankumarpatra/EmiGo/releases/latest"
            var downloadUrl = json.optString("html_url", fallbackUrl)
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val url = asset.optString("browser_download_url", "")
                        if (isTrustedHttpsUrl(url)) {
                            downloadUrl = url
                            break
                        }
                    }
                }
            }
            if (!isTrustedHttpsUrl(downloadUrl)) {
                downloadUrl = fallbackUrl
            }

            Result.success(
                AppUpdateInfo(
                    latestVersion = tag,
                    releaseNotes = notes,
                    downloadUrl = downloadUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val lParts = latest.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun isTrustedHttpsUrl(rawUrl: String): Boolean {
        return try {
            val uri = URL(rawUrl)
            uri.protocol.equals("https", ignoreCase = true) &&
                (uri.host.equals("github.com", ignoreCase = true) ||
                    uri.host.endsWith(".githubusercontent.com", ignoreCase = true))
        } catch (_: Exception) {
            false
        }
    }
}
