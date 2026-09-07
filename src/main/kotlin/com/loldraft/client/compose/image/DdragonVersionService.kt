package com.loldraft.client.compose.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object DdragonVersionService {
    private const val VERSIONS_API_URL = "https://ddragon.leagueoflegends.com/api/versions.json"
    const val DEFAULT_FALLBACK_VERSION = "16.17.1"

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build()

    private val cacheFile: File by lazy {
        val cacheDir = File(System.getProperty("user.home"), ".cache/loldraft")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        File(cacheDir, "ddragon_version.txt")
    }

    @Volatile
    private var cachedVersion: String? = null

    init {
        // Load initial version from disk cache if present, otherwise default fallback
        cachedVersion = readFromDiskCache() ?: DEFAULT_FALLBACK_VERSION
    }

    fun getVersion(): String = cachedVersion ?: DEFAULT_FALLBACK_VERSION

    suspend fun refreshLatestVersion(): String =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(VERSIONS_API_URL))
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val versions = json.decodeFromString<List<String>>(response.body())
                    val latest = versions.firstOrNull { it.isNotBlank() }
                    if (latest != null) {
                        cachedVersion = latest
                        writeToDiskCache(latest)
                        return@withContext latest
                    }
                }
            } catch (e: Exception) {
                // Silently fallback to cached or default version on connection failure
            }
            getVersion()
        }

    private fun readFromDiskCache(): String? {
        return try {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val text = cacheFile.readText().trim()
                if (text.isNotBlank()) text else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToDiskCache(version: String) {
        try {
            cacheFile.writeText(version)
        } catch (e: Exception) {
            // Ignore disk write errors
        }
    }
}
