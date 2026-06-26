package com.milesxue.pixelread

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PixelReadUpdateApiUrl = "https://api.github.com/repos/Siyuan-Xue/PixelRead/releases/latest"
internal const val PixelReadProjectName = "PixelRead"

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

internal data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
)

internal data class AppUpdateInfo(
    val version: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
) {
    val actionUrl: String
        get() = apkDownloadUrl ?: releasePageUrl
}

internal sealed interface AppUpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : AppUpdateCheckResult
    data object Current : AppUpdateCheckResult
    data object Unavailable : AppUpdateCheckResult
}

internal suspend fun checkPixelReadUpdate(currentVersion: String): AppUpdateCheckResult =
    checkAppUpdate(
        apiUrl = PixelReadUpdateApiUrl,
        projectName = PixelReadProjectName,
        currentVersion = currentVersion,
    )

internal suspend fun checkAppUpdate(
    apiUrl: String,
    projectName: String,
    currentVersion: String,
): AppUpdateCheckResult {
    val release = fetchLatestRelease(apiUrl) ?: return AppUpdateCheckResult.Unavailable
    val latestVersion = normalizedSemanticVersion(release.tagName)
        ?: return AppUpdateCheckResult.Unavailable

    if (!isNewerSemanticVersion(release.tagName, currentVersion)) {
        return AppUpdateCheckResult.Current
    }

    return AppUpdateCheckResult.Available(
        AppUpdateInfo(
            version = latestVersion,
            releasePageUrl = release.htmlUrl,
            apkDownloadUrl = findReleaseApkUrl(
                release = release,
                projectName = projectName,
                version = latestVersion,
            ),
        ),
    )
}

private suspend fun fetchLatestRelease(apiUrl: String): GitHubRelease? =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", PixelReadProjectName)
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseGitHubRelease(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

internal fun findReleaseApkUrl(
    release: GitHubRelease,
    projectName: String,
    version: String,
): String? {
    val expectedName = "$projectName-${normalizedSemanticVersion(version) ?: version}-release.apk"
    return release.assets.firstOrNull { asset ->
        asset.name.equals(expectedName, ignoreCase = true)
    }?.downloadUrl
}

internal fun isNewerSemanticVersion(latestVersion: String, currentVersion: String): Boolean {
    val latestParts = semanticVersionParts(latestVersion) ?: return false
    val currentParts = semanticVersionParts(currentVersion) ?: return false
    return latestParts.zip(currentParts)
        .firstOrNull { (latest, current) -> latest != current }
        ?.let { (latest, current) -> latest > current }
        ?: false
}

internal fun normalizedSemanticVersion(version: String): String? {
    val parts = semanticVersionParts(version) ?: return null
    return parts.joinToString(".")
}

private fun semanticVersionParts(version: String): List<Int>? {
    val cleaned = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("-")
    val pieces = cleaned.split(".")
    if (pieces.isEmpty() || pieces.size > 3) return null
    val parsed = pieces.map { it.toIntOrNull() ?: return null }
    return parsed + List(3 - parsed.size) { 0 }
}

internal fun parseGitHubRelease(json: String): GitHubRelease? {
    val tagName = extractJsonString(json, "tag_name") ?: return null
    val htmlUrl = extractJsonString(json, "html_url") ?: return null
    val assets = extractJsonArray(json, "assets")
        ?.let(::extractJsonObjects)
        ?.mapNotNull { assetJson ->
            val name = extractJsonString(assetJson, "name") ?: return@mapNotNull null
            val url = extractJsonString(assetJson, "browser_download_url") ?: return@mapNotNull null
            ReleaseAsset(name = name, downloadUrl = url)
        }
        ?: emptyList()

    return GitHubRelease(
        tagName = tagName,
        htmlUrl = htmlUrl,
        assets = assets,
    )
}

private fun extractJsonString(json: String, key: String): String? {
    val keyIndex = json.indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val colonIndex = json.indexOf(':', keyIndex)
    if (colonIndex < 0) return null
    val startQuote = json.indexOf('"', colonIndex + 1)
    if (startQuote < 0) return null

    val raw = StringBuilder()
    var index = startQuote + 1
    var escaped = false
    while (index < json.length) {
        val char = json[index]
        when {
            escaped -> {
                raw.append('\\')
                raw.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == '"' -> return unescapeJsonString(raw.toString())
            else -> raw.append(char)
        }
        index += 1
    }
    return null
}

private fun extractJsonArray(json: String, key: String): String? {
    val keyIndex = json.indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val startIndex = json.indexOf('[', keyIndex)
    if (startIndex < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (index in startIndex until json.length) {
        val char = json[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '[' -> depth += 1
            ']' -> {
                depth -= 1
                if (depth == 0) return json.substring(startIndex + 1, index)
            }
        }
    }
    return null
}

private fun extractJsonObjects(jsonArray: String): List<String> {
    val objects = mutableListOf<String>()
    var startIndex = -1
    var depth = 0
    var inString = false
    var escaped = false

    for (index in jsonArray.indices) {
        val char = jsonArray[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) startIndex = index
                depth += 1
            }
            '}' -> {
                depth -= 1
                if (depth == 0 && startIndex >= 0) {
                    objects += jsonArray.substring(startIndex, index + 1)
                    startIndex = -1
                }
            }
        }
    }
    return objects
}

private fun unescapeJsonString(value: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\' || index == value.lastIndex) {
            builder.append(char)
            index += 1
            continue
        }

        val escaped = value[index + 1]
        when (escaped) {
            '"', '\\', '/' -> builder.append(escaped)
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'u' -> {
                val hex = value.substringOrNull(index + 2, index + 6)
                val codePoint = hex?.toIntOrNull(16)
                if (codePoint != null) {
                    builder.append(codePoint.toChar())
                    index += 4
                }
            }
            else -> builder.append(escaped)
        }
        index += 2
    }
    return builder.toString()
}

private fun String.substringOrNull(startIndex: Int, endIndex: Int): String? =
    if (startIndex >= 0 && endIndex <= length && startIndex <= endIndex) {
        substring(startIndex, endIndex)
    } else {
        null
    }
