package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "GitHubUpdateManager"

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

internal data class SemanticVersion(
    val segments: List<Int>,
    val preRelease: String
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val maxLen = maxOf(segments.size, other.segments.size)
        for (i in 0 until maxLen) {
            val a = segments.getOrElse(i) { 0 }
            val b = other.segments.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        return preRelease.compareTo(other.preRelease, ignoreCase = true)
    }
}

/**
 * Checks GitHub Releases API for newer tagged builds and launches verified release/APK links
 * via Intent.ACTION_VIEW without requiring Google Play-restricted REQUEST_INSTALL_PACKAGES.
 */
object GitHubUpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    internal fun normalizeRepoSlug(raw: String): Pair<String, String>? {
        val cleaned = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1]
        val slugRegex = Regex("^[A-Za-z0-9._-]+$")
        if (!slugRegex.matches(owner) || !slugRegex.matches(repo)) return null
        return owner to repo
    }

    internal fun normalizeVersion(rawVersion: String): String {
        return rawVersion.trim()
            .replaceFirst(Regex("^[vV]+"), "")
            .trim()
    }

    internal fun parseSemanticVersion(rawVersion: String): SemanticVersion {
        val cleaned = normalizeVersion(rawVersion)
            .substringBefore('+')
        val mainPart = cleaned.substringBefore('-')
        val preRelease = cleaned.substringAfter('-', missingDelimiterValue = "")
        val segments = mainPart.split('.')
            .mapNotNull { token -> token.filter { it.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }
        return SemanticVersion(segments = segments, preRelease = preRelease)
    }

    internal fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        return parseSemanticVersion(remoteTag) > parseSemanticVersion(currentVersion)
    }

    internal fun isSameVersion(versionA: String, versionB: String): Boolean {
        val cleanA = normalizeVersion(versionA)
        val cleanB = normalizeVersion(versionB)
        if (cleanA.isBlank() || cleanB.isBlank()) return false
        return parseSemanticVersion(cleanA) == parseSemanticVersion(cleanB)
    }

    internal fun parseReleaseJson(jsonBody: String): GitHubReleaseInfo? {
        return runCatching {
            val obj = JSONObject(jsonBody)
            if (obj.optBoolean("draft", false)) return null
            val tag = obj.optString("tag_name", "").trim()
            if (tag.isBlank()) return null

            val title = obj.optString("name", tag).trim().ifBlank { tag }
            val notes = obj.optString("body", "").trim()
            val htmlUrl = obj.optString("html_url", "").trim()
            val publishedAt = obj.optString("published_at", "").trim()

            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "").trim()
                    if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                        if (apkUrl == null || name.startsWith("lyra-v", ignoreCase = true)) {
                            apkUrl = url
                            if (name.startsWith("lyra-v", ignoreCase = true)) break
                        }
                    }
                }
            }

            val safeHtml = if (htmlUrl.startsWith("https://")) htmlUrl else ""
            if (safeHtml.isBlank() && apkUrl.isNullOrBlank()) return null

            GitHubReleaseInfo(
                tagName = tag,
                normalizedVersion = normalizeVersion(tag),
                releaseTitle = title,
                releaseNotes = notes,
                htmlUrl = safeHtml.ifBlank { apkUrl.orEmpty() },
                apkDownloadUrl = apkUrl,
                publishedAt = publishedAt
            )
        }.getOrNull()
    }

    suspend fun checkLatestRelease(
        repoSlug: String,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (owner, repo) = normalizeRepoSlug(repoSlug)
            ?: return@withContext UpdateCheckResult.Error("Invalid GitHub repository format. Use owner/repo.")

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lyra-Android/$currentVersionName")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        404 -> "No published GitHub Releases found for $owner/$repo."
                        403 -> "GitHub API rate limit reached. Try again later."
                        else -> "GitHub update check failed (HTTP ${response.code})."
                    }
                    return@withContext UpdateCheckResult.Error(msg)
                }
                val release = parseReleaseJson(body)
                    ?: return@withContext UpdateCheckResult.Error("Unable to parse GitHub release metadata.")

                if (isNewerVersion(release.tagName, currentVersionName)) {
                    UpdateCheckResult.UpdateAvailable(release)
                } else {
                    UpdateCheckResult.UpToDate(currentVersionName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub release check failed", e)
            UpdateCheckResult.Error(e.localizedMessage ?: "Network error while checking GitHub Releases.")
        }
    }

    fun buildExternalUpdateIntent(release: GitHubReleaseInfo, preferDirectApkUrl: Boolean = true): Intent? {
        val targetUrl = if (preferDirectApkUrl && !release.apkDownloadUrl.isNullOrBlank()) {
            release.apkDownloadUrl
        } else {
            release.htmlUrl
        }
        val uri = runCatching { Uri.parse(targetUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host.orEmpty().lowercase()
        val allowedHost = host == "github.com" || host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
        if (!allowedHost) return null

        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openReleaseInBrowser(context: Context, release: GitHubReleaseInfo, preferDirectApkUrl: Boolean = true): Boolean {
        val intent = buildExternalUpdateIntent(release, preferDirectApkUrl) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
