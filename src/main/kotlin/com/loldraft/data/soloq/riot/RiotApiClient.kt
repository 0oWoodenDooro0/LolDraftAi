package com.loldraft.data.soloq.riot

import com.loldraft.data.models.Role
import com.loldraft.data.sources.DefaultHttpTransport
import com.loldraft.data.sources.HttpTransport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class RiotApiException(message: String, val statusCode: Int? = null) : RuntimeException(message) {
    class MissingApiKey : RiotApiException("尚未設定金鑰，請先點擊上方按鈕輸入金鑰")

    class InvalidApiKey(statusCode: Int) : RiotApiException(
        if (statusCode == 401) {
            "API 金鑰無效或已過期，開發者金鑰每 24 小時需至開發者網站重新產生"
        } else {
            "API 金鑰無權限訪問此端點 HTTP $statusCode"
        },
        statusCode,
    )

    class AccountNotFound(val gameName: String, val tagLine: String, val region: String) : RiotApiException(
        "在伺服器 $region 找不到帳號 $gameName#$tagLine，請確認名稱、TagLine 與伺服器設定",
        404,
    )

    class RateLimitExceeded : RiotApiException("已達到請求頻率上限，請稍候 1 至 2 分鐘再試", 429)

    class NetworkError(message: String, statusCode: Int? = null) : RiotApiException("連線錯誤：$message", statusCode)
}


class RiotApiClient(
    private val transport: HttpTransport = DefaultHttpTransport(),
    apiKey: String? = null,
    private val keyConfigFile: File = File("config/riot_api_key.txt"),
) {
    var apiKey: String? = apiKey ?: loadPersistedApiKey()
        set(value) {
            field = value
            saveApiKey(value)
        }

    private fun loadPersistedApiKey(): String? {
        val envKey = System.getenv("RIOT_API_KEY")
        if (!envKey.isNullOrBlank()) return envKey.trim()

        try {
            if (keyConfigFile.exists()) {
                val text = keyConfigFile.readText().trim()
                if (text.isNotBlank()) return text
            }
        } catch (_: Exception) {}
        return null
    }

    private fun saveApiKey(key: String?) {
        try {
            keyConfigFile.parentFile?.mkdirs()
            if (key.isNullOrBlank()) {
                if (keyConfigFile.exists()) keyConfigFile.delete()
            } else {
                keyConfigFile.writeText(key.trim())
            }
        } catch (_: Exception) {}
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun extractStatusCode(msg: String): Int? {
        val regex = Regex("""HTTP\s+(\d{3})""")
        val match = regex.find(msg)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun handleHttpException(e: Exception, contextInfo: String): Nothing {
        val msg = e.message ?: ""
        val status = extractStatusCode(msg)
        when (status) {
            401, 403 -> throw RiotApiException.InvalidApiKey(status)
            404 -> throw RiotApiException.NetworkError("查無資料 $contextInfo", 404)
            429 -> throw RiotApiException.RateLimitExceeded()
            else -> throw RiotApiException.NetworkError(if (msg.isNotBlank()) msg else "網路請求失敗", status)
        }
    }

    suspend fun getAccountByRiotId(
        gameName: String,
        tagLine: String,
        region: String = "asia",
    ): RiotAccountDto {
        val key = apiKey?.trim()
        if (key.isNullOrBlank()) throw RiotApiException.MissingApiKey()

        val encodedName = URLEncoder.encode(gameName.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val encodedTag = URLEncoder.encode(tagLine.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val url = "https://$region.api.riotgames.com/riot/account/v1/accounts/by-riot-id/$encodedName/$encodedTag"

        return try {
            val response = transport.get(url, headers = mapOf("X-Riot-Token" to key))
            json.decodeFromString<RiotAccountDto>(response)
        } catch (e: Exception) {
            val status = extractStatusCode(e.message ?: "")
            if (status == 404) {
                throw RiotApiException.AccountNotFound(gameName, tagLine, region)
            }
            handleHttpException(e, "帳號 $gameName#$tagLine")
        }
    }

    suspend fun getRankedMatchIds(
        puuid: String,
        count: Int = 20,
        region: String = "asia",
    ): List<String> {
        val key = apiKey?.trim()
        if (key.isNullOrBlank()) throw RiotApiException.MissingApiKey()

        val url = "https://$region.api.riotgames.com/lol/match/v5/matches/by-puuid/$puuid/ids?type=ranked&start=0&count=$count"
        return try {
            val response = transport.get(url, headers = mapOf("X-Riot-Token" to key))
            json.decodeFromString<List<String>>(response)
        } catch (e: Exception) {
            handleHttpException(e, "對戰清單")
        }
    }

    suspend fun getMatchDetails(
        matchId: String,
        region: String = "asia",
    ): RiotMatchDto? {
        val key = apiKey?.trim()
        if (key.isNullOrBlank()) throw RiotApiException.MissingApiKey()

        val url = "https://$region.api.riotgames.com/lol/match/v5/matches/$matchId"
        return try {
            val response = transport.get(url, headers = mapOf("X-Riot-Token" to key))
            json.decodeFromString<RiotMatchDto>(response)
        } catch (e: Exception) {
            val status = extractStatusCode(e.message ?: "")
            if (status == 404) return null
            handleHttpException(e, "對戰詳情 $matchId")
        }
    }

    companion object {
        fun parseRole(position: String?): Role {
            if (position.isNullOrBlank()) return Role.MID
            return when (position.trim().uppercase()) {
                "TOP" -> Role.TOP
                "JUNGLE" -> Role.JUNGLE
                "MIDDLE", "MID" -> Role.MID
                "BOTTOM", "BOT" -> Role.BOT
                "UTILITY", "SUPPORT" -> Role.SUPPORT
                else -> Role.MID
            }
        }
    }
}
