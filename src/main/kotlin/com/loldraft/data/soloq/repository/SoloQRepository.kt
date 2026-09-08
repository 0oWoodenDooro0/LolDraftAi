package com.loldraft.data.soloq.repository

import com.loldraft.data.models.Role
import com.loldraft.data.soloq.models.PlayerSummonerAccount
import com.loldraft.data.soloq.models.SoloQMatchRecord
import com.loldraft.data.soloq.riot.RiotApiClient
import com.loldraft.data.soloq.riot.RiotApiException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SoloQRepository(
    val riotApiClient: RiotApiClient = RiotApiClient(),
    val accountsStorageFile: File = File("config/soloq_accounts.json"),
    val matchesStorageFile: File = File("config/soloq_matches_cache.json"),
) {
    private val accounts = ConcurrentHashMap<String, PlayerSummonerAccount>()
    private val puuidCache = ConcurrentHashMap<String, String>()
    private val matchesCache = ConcurrentHashMap<String, List<SoloQMatchRecord>>()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    init {
        loadPersistedAccounts()
        loadPersistedMatches()
    }

    private fun loadPersistedAccounts() {
        try {
            if (accountsStorageFile.exists()) {
                val text = accountsStorageFile.readText()
                if (text.isNotBlank()) {
                    val list = json.decodeFromString<List<PlayerSummonerAccount>>(text)
                    for (acc in list) {
                        accounts[acc.playerId.lowercase()] = acc
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("載入選手帳號失敗：${e.message}")
        }
    }

    private fun persistAccounts() {
        try {
            accountsStorageFile.parentFile?.mkdirs()
            val list = accounts.values.toList().sortedWith(compareBy({ it.league }, { it.teamId }, { it.playerId }))
            accountsStorageFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            System.err.println("儲存選手帳號失敗：${e.message}")
        }
    }

    private fun loadPersistedMatches() {
        try {
            if (matchesStorageFile.exists()) {
                val text = matchesStorageFile.readText()
                if (text.isNotBlank()) {
                    val map = json.decodeFromString<Map<String, List<SoloQMatchRecord>>>(text)
                    for ((k, v) in map) {
                        matchesCache[k.lowercase()] = v
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("載入天梯快取失敗：${e.message}")
        }
    }

    private fun persistMatches() {
        try {
            matchesStorageFile.parentFile?.mkdirs()
            matchesStorageFile.writeText(json.encodeToString(matchesCache.toMap()))
        } catch (e: Exception) {
            System.err.println("儲存天梯快取失敗：${e.message}")
        }
    }

    fun registerAccount(account: PlayerSummonerAccount) {
        val normalized = account.playerId.lowercase()
        accounts[normalized] = account
        puuidCache.remove(normalized)
        // Keep existing cached matches; do not delete them when registering or editing account
        persistAccounts()
    }

    fun deleteAccount(playerId: String) {
        val normalized = playerId.lowercase()
        accounts.remove(normalized)
        puuidCache.remove(normalized)
        matchesCache.remove(normalized)
        persistAccounts()
        persistMatches()
    }

    fun getAccount(playerId: String): PlayerSummonerAccount? =
        accounts[playerId.lowercase()]

    fun getAllRegisteredPlayers(): List<PlayerSummonerAccount> =
        accounts.values.sortedWith(compareBy({ it.league }, { it.teamId }, { it.primaryRole.ordinal }))

    fun getPlayersForTeam(teamId: String): List<PlayerSummonerAccount> =
        accounts.values
            .filter { it.teamId.equals(teamId, ignoreCase = true) }
            .sortedBy { it.primaryRole.ordinal }

    fun getPlayersForLeague(league: String): List<PlayerSummonerAccount> =
        accounts.values
            .filter { it.league.equals(league, ignoreCase = true) }
            .sortedBy { it.primaryRole.ordinal }

    fun saveMatchesToCache(playerId: String, matches: List<SoloQMatchRecord>) {
        matchesCache[playerId.lowercase()] = matches
        persistMatches()
    }

    fun clearAllAccounts() {
        accounts.clear()
        puuidCache.clear()
        matchesCache.clear()
        persistAccounts()
        persistMatches()
    }

    suspend fun getRecentMatches(
        playerId: String,
        count: Int = 30,
        forceSync: Boolean = false,
    ): List<SoloQMatchRecord> {
        val normalizedId = playerId.lowercase()
        val cached = matchesCache[normalizedId]

        // Strictly read from local cache if forceSync is false (never auto-trigger Riot API calls)
        if (!forceSync) {
            return cached ?: emptyList()
        }

        val account = accounts[normalizedId] ?: return emptyList()
        val apiKey = riotApiClient.apiKey
        if (apiKey.isNullOrBlank()) {
            throw RiotApiException.MissingApiKey()
        }

        puuidCache.remove(normalizedId)

        val puuid =
            puuidCache[normalizedId] ?: run {
                val acc = riotApiClient.getAccountByRiotId(account.gameName, account.tagLine, account.region)
                if (acc.puuid.isNotBlank()) {
                    puuidCache[normalizedId] = acc.puuid
                }
                acc.puuid
            }

        if (puuid.isBlank()) {
            return emptyList()
        }

        val matchIds = riotApiClient.getRankedMatchIds(puuid, count = count, region = account.region)
        val fetchedRecords = mutableListOf<SoloQMatchRecord>()

        for (mid in matchIds) {
            val detail = riotApiClient.getMatchDetails(mid, region = account.region) ?: continue
            val p = detail.info.participants.find { it.puuid == puuid } ?: continue
            val playedRole = RiotApiClient.parseRole(p.teamPosition)
            val totalCs = p.totalMinionsKilled + p.neutralMinionsKilled

            val record =
                SoloQMatchRecord(
                    matchId = mid,
                    playerId = account.playerId,
                    championId = p.championName,
                    championName = p.championName,
                    playedRole = playedRole,
                    isPrimaryRole = (playedRole == account.primaryRole),
                    win = p.win,
                    kills = killsClamp(p.kills),
                    deaths = deathsClamp(p.deaths),
                    assists = assistsClamp(p.assists),
                    cs = totalCs,
                    durationSeconds = detail.info.gameDuration.toInt(),
                    timestamp = detail.info.gameEndTimestamp,
                )
            fetchedRecords.add(record)
        }

        matchesCache[normalizedId] = fetchedRecords
        persistMatches()
        return fetchedRecords
    }

    private fun killsClamp(k: Int): Int = if (k < 0) 0 else k
    private fun deathsClamp(d: Int): Int = if (d < 0) 0 else d
    private fun assistsClamp(a: Int): Int = if (a < 0) 0 else a
}
