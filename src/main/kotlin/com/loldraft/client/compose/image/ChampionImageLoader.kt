package com.loldraft.client.compose.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.loldraft.data.normalization.ChampionNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object ChampionImageLoader {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build()

    private fun getDiskCacheDir(version: String): File {
        val dir = File(System.getProperty("user.home"), ".cache/loldraft/champions/$version")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getInMemory(imageKey: String): ImageBitmap? {
        if (imageKey.isBlank()) return null
        return memoryCache[imageKey]
    }

    suspend fun loadBitmap(championNameOrId: String?): ImageBitmap? {
        if (ChampionNormalizer.isNoneOrEmpty(championNameOrId)) return null
        val imageKey = ChampionNormalizer.toDdragonKey(championNameOrId)
        if (imageKey.isBlank()) return null

        // 1. Check in-memory cache
        memoryCache[imageKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            val version = DdragonVersionService.getVersion()
            val diskFile = File(getDiskCacheDir(version), "$imageKey.png")

            // 2. Check disk cache
            if (diskFile.exists() && diskFile.length() > 0) {
                try {
                    val bytes = diskFile.readBytes()
                    val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    memoryCache[imageKey] = bitmap
                    return@withContext bitmap
                } catch (e: Exception) {
                    // Disk read failed, fallback to download
                }
            }

            // 3. Download from Riot Data Dragon
            try {
                val url = "https://ddragon.leagueoflegends.com/cdn/$version/img/champion/$imageKey.png"
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() == 200) {
                    val bytes = response.body()
                    try {
                        diskFile.parentFile?.mkdirs()
                        diskFile.writeBytes(bytes)
                    } catch (e: Exception) {
                        // Non-fatal disk write error
                    }
                    val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    memoryCache[imageKey] = bitmap
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                // Silently fallback if download fails or offline
            }

            null
        }
    }
}

@Composable
fun rememberChampionBitmap(championNameOrId: String?): ImageBitmap? {
    if (championNameOrId.isNullOrBlank() || ChampionNormalizer.isNoneOrEmpty(championNameOrId)) {
        return null
    }
    val imageKey = remember(championNameOrId) { ChampionNormalizer.toDdragonKey(championNameOrId) }
    var bitmap by remember(imageKey) { mutableStateOf(ChampionImageLoader.getInMemory(imageKey)) }

    LaunchedEffect(imageKey) {
        if (imageKey.isNotBlank()) {
            val loaded = ChampionImageLoader.loadBitmap(imageKey)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    return bitmap
}
