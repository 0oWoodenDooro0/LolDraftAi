package com.loldraft.data.soloq

import com.loldraft.analytics.service.SoloQIntelligenceService
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.soloq.models.PlayerSummonerAccount
import com.loldraft.data.soloq.models.SoloQMatchRecord
import com.loldraft.data.soloq.models.SoloQRoleFilter
import com.loldraft.data.soloq.repository.SoloQRepository
import com.loldraft.data.soloq.riot.RiotApiClient
import com.loldraft.data.sources.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import com.loldraft.data.soloq.riot.RiotApiException
import org.junit.jupiter.api.Test
import java.io.File

class SoloQIntelligenceTest {
    class MockHttpTransport(private val responses: Map<String, String>) : HttpTransport {
        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): String {
            for ((key, value) in responses) {
                if (url.contains(key)) return value
            }
            error("No mock response for $url")
        }
    }

    @Test
    fun testRiotApiClientParsesAccountAndMatches() =
        runTest {
            val accountJson =
                """
                {
                    "puuid": "puuid-faker-12345",
                    "gameName": "Hide on bush",
                    "tagLine": "KR1"
                }
                """.trimIndent()

            val matchIdsJson = """["KR_70001", "KR_70002"]"""

            val matchDetailJson =
                """
                {
                    "metadata": {
                        "matchId": "KR_70001",
                        "participants": ["puuid-faker-12345"]
                    },
                    "info": {
                        "gameDuration": 1800,
                        "gameEndTimestamp": 1700000000000,
                        "participants": [
                            {
                                "puuid": "puuid-faker-12345",
                                "championId": 103,
                                "championName": "Ahri",
                                "teamPosition": "MIDDLE",
                                "win": true,
                                "kills": 8,
                                "deaths": 2,
                                "assists": 9,
                                "totalMinionsKilled": 210,
                                "neutralMinionsKilled": 12
                            }
                        ]
                    }
                }
                """.trimIndent()

            val transport =
                MockHttpTransport(
                    mapOf(
                        "by-riot-id" to accountJson,
                        "matches/by-puuid" to matchIdsJson,
                        "matches/KR_70001" to matchDetailJson,
                    ),
                )

            val client = RiotApiClient(transport = transport, apiKey = "RGAPI-TEST-TOKEN")

            val acc = client.getAccountByRiotId("Hide on bush", "KR1")
            assertNotNull(acc)
            assertEquals("puuid-faker-12345", acc?.puuid)

            val ids = client.getRankedMatchIds(acc!!.puuid)
            assertEquals(2, ids.size)
            assertEquals("KR_70001", ids[0])

            val details = client.getMatchDetails("KR_70001")
            assertNotNull(details)
            assertEquals(1800L, details?.info?.gameDuration)
            val p = details!!.info.participants.first()
            assertEquals("Ahri", p.championName)
            assertEquals(Role.MID, RiotApiClient.parseRole(p.teamPosition))
            assertTrue(p.win)
        }

    @Test
    fun testSoloQRepositoryCustomRegistrationAndNoFakeData() =
        runTest {
            val tempConfigFile = File.createTempFile("test_soloq_accounts", ".json")
            val tempMatchesFile = File.createTempFile("test_soloq_matches", ".json")
            tempConfigFile.deleteOnExit()
            tempMatchesFile.deleteOnExit()

            val repo = SoloQRepository(accountsStorageFile = tempConfigFile, matchesStorageFile = tempMatchesFile)

            // Initially empty - no default accounts and no fake data
            val initialFaker = repo.getAccount("Faker")
            assertNull(initialFaker)

            val emptyMatches = repo.getRecentMatches("Faker", count = 20)
            assertTrue(emptyMatches.isEmpty(), "Must not return fake matches")

            // User registers Faker manually
            val fakerAcc =
                PlayerSummonerAccount(
                    playerId = "Faker",
                    teamId = "T1",
                    primaryRole = Role.MID,
                    gameName = "Hide on bush",
                    tagLine = "KR1",
                    league = "LCK",
                )
            repo.registerAccount(fakerAcc)

            val retrieved = repo.getAccount("Faker")
            assertNotNull(retrieved)
            assertEquals("Hide on bush", retrieved?.gameName)
            assertEquals("KR1", retrieved?.tagLine)
            assertEquals("LCK", retrieved?.league)

            // Verify persistence by loading another repository instance from same file
            val repo2 = SoloQRepository(accountsStorageFile = tempConfigFile, matchesStorageFile = tempMatchesFile)
            val retrieved2 = repo2.getAccount("Faker")
            assertNotNull(retrieved2)
            assertEquals("Hide on bush", retrieved2?.gameName)

            // Delete account
            repo.deleteAccount("Faker")
            assertNull(repo.getAccount("Faker"))
        }

    @Test
    fun testSecretPickDetectionWithProGamesCrossAnalysis() =
        runTest {
            val t1 = Team("t1", "T1", "T1")
            val geng = Team("gen-g", "Gen.G", "GEN")
            val sampleProGame =
                Game(
                    id = "pro-1",
                    gameNumber = 1,
                    patch = "14.1",
                    season = "Spring",
                    blueTeam = t1,
                    redTeam = geng,
                    winner = Side.BLUE,
                    tournament = "LCK",
                    draftState =
                        DraftState(
                            blueBans = emptyList(),
                            redBans = emptyList(),
                            bluePicks =
                                listOf(
                                    PickSelection(championId = "Azir", role = Role.MID, playerId = "Faker"),
                                ),
                            redPicks = emptyList(),
                        ),
                )

            val tempConfigFile = File.createTempFile("test_soloq_accounts2", ".json")
            tempConfigFile.deleteOnExit()

            val repo = SoloQRepository(accountsStorageFile = tempConfigFile)
            val fakerAcc =
                PlayerSummonerAccount(
                    playerId = "Faker",
                    teamId = "T1",
                    primaryRole = Role.MID,
                    gameName = "Hide on bush",
                    tagLine = "KR1",
                    league = "LCK",
                )
            repo.registerAccount(fakerAcc)

            val now = System.currentTimeMillis()
            // Provide real matches to matchCache:
            // 3 games of Zac in MID (primary role, 3W-0L, 100% winrate) -> Secret pick (0 pro games)
            // 2 games of Azir in MID (primary role) -> NOT secret pick (played in pro game 1)
            val matches =
                listOf(
                    SoloQMatchRecord("m1", "Faker", "Zac", "Zac", Role.MID, true, true, 5, 1, 8, 180, 1800, now - 1000),
                    SoloQMatchRecord("m2", "Faker", "Zac", "Zac", Role.MID, true, true, 4, 2, 9, 190, 1850, now - 2000),
                    SoloQMatchRecord("m3", "Faker", "Zac", "Zac", Role.MID, true, true, 6, 0, 7, 200, 1900, now - 3000),
                    SoloQMatchRecord("m4", "Faker", "Azir", "Azir", Role.MID, true, true, 7, 2, 5, 210, 2000, now - 4000),
                    SoloQMatchRecord("m5", "Faker", "Azir", "Azir", Role.MID, true, false, 2, 4, 3, 170, 1700, now - 5000),
                    SoloQMatchRecord("m6", "Faker", "Sylas", "Sylas", Role.TOP, false, true, 5, 3, 4, 160, 1600, now - 6000), // off-role
                )
            repo.saveMatchesToCache("Faker", matches)

            val service =
                SoloQIntelligenceService(
                    soloQRepository = repo,
                    gamesSupplier = { listOf(sampleProGame) },
                )

            val intel =
                service.analyzePlayer(
                    playerId = "Faker",
                    timeRangeDays = 30,
                    roleFilter = SoloQRoleFilter.ALL,
                    referenceTimeMs = now,
                )

            assertNotNull(intel)
            assertEquals("Faker", intel?.playerId)
            assertEquals(6, intel?.totalMatches)

            // Azir was played in pro game, so not secret pick
            val azirSummary = intel!!.championSummaries.find { it.championName.equals("Azir", ignoreCase = true) }
            assertNotNull(azirSummary)
            assertEquals(1, azirSummary!!.playerProGames)
            assertFalse(azirSummary.isSecretPick)

            // Zac has 0 pro games, primary role MID, 3 games >= 2, winrate 100% >= 50% -> secret pick!
            val zacSummary = intel.championSummaries.find { it.championName.equals("Zac", ignoreCase = true) }
            assertNotNull(zacSummary)
            assertEquals(0, zacSummary!!.playerProGames)
            assertTrue(zacSummary.isPrimaryRole)
            assertTrue(zacSummary.isSecretPick)

            assertEquals(1, intel.secretPicks.size)
            assertEquals("Zac", intel.secretPicks.first().championName)
        }

    @Test
    fun testRoleFilterFiltersSummariesCorrectly() =
        runTest {
            val tempConfigFile = File.createTempFile("test_soloq_accounts3", ".json")
            val tempMatchesFile = File.createTempFile("test_matches3_rf", ".json")
            tempConfigFile.deleteOnExit()
            tempMatchesFile.deleteOnExit()

            val repo = SoloQRepository(accountsStorageFile = tempConfigFile, matchesStorageFile = tempMatchesFile)
            val fakerAcc =
                PlayerSummonerAccount(
                    playerId = "Faker",
                    teamId = "T1",
                    primaryRole = Role.MID,
                    gameName = "Hide on bush",
                    tagLine = "KR1",
                )
            repo.registerAccount(fakerAcc)

            val now = System.currentTimeMillis()
            val matches =
                listOf(
                    SoloQMatchRecord("m1", "Faker", "Azir", "Azir", Role.MID, true, true, 5, 1, 8, 180, 1800, now - 1000),
                    SoloQMatchRecord("m2", "Faker", "Sylas", "Sylas", Role.TOP, false, true, 5, 3, 4, 160, 1600, now - 2000),
                )
            repo.saveMatchesToCache("Faker", matches)

            val service =
                SoloQIntelligenceService(
                    soloQRepository = repo,
                    gamesSupplier = { emptyList() },
                )

            val primaryOnly =
                service.analyzePlayer(
                    playerId = "Faker",
                    timeRangeDays = 30,
                    roleFilter = SoloQRoleFilter.PRIMARY_ONLY,
                    referenceTimeMs = now,
                )
            assertNotNull(primaryOnly)
            assertEquals(1, primaryOnly!!.championSummaries.size)
            assertTrue(primaryOnly.championSummaries.all { it.isPrimaryRole })

            val offRoleOnly =
                service.analyzePlayer(
                    playerId = "Faker",
                    timeRangeDays = 30,
                    roleFilter = SoloQRoleFilter.OFF_ROLE_ONLY,
                    referenceTimeMs = now,
                )
            assertNotNull(offRoleOnly)
            assertEquals(1, offRoleOnly!!.championSummaries.size)
            assertFalse(offRoleOnly.championSummaries.first().isPrimaryRole)
        }

    
    @Test
    fun testRiotApiClientThrowsInvalidApiKeyOn401() =
        runTest {
            val mockTransport =
                object : HttpTransport {
                    override suspend fun get(url: String, headers: Map<String, String>): String {
                        throw RuntimeException("HTTP 401: Unauthorized")
                    }
                                    }
            val tempKeyFile = File.createTempFile("test_key", ".txt")
            tempKeyFile.deleteOnExit()
            val client = RiotApiClient(transport = mockTransport, apiKey = "expired-token", keyConfigFile = tempKeyFile)

            try {
                client.getAccountByRiotId("Faker", "KR1")
                fail("Expected RiotApiException.InvalidApiKey")
            } catch (ex: RiotApiException.InvalidApiKey) {
                assertTrue(ex.message!!.contains("金鑰無效或已過期") || ex.message!!.contains("24 小時"))
            }
        }

    @Test
    fun testRiotApiClientThrowsAccountNotFoundOn404() =
        runTest {
            val mockTransport =
                object : HttpTransport {
                    override suspend fun get(url: String, headers: Map<String, String>): String {
                        throw RuntimeException("HTTP 404: Not Found")
                    }
                                    }
            val tempKeyFile = File.createTempFile("test_key404", ".txt")
            tempKeyFile.deleteOnExit()
            val client = RiotApiClient(transport = mockTransport, apiKey = "valid-token", keyConfigFile = tempKeyFile)

            try {
                client.getAccountByRiotId("NonExistentPlayer", "999")
                fail("Expected RiotApiException.AccountNotFound")
            } catch (ex: RiotApiException.AccountNotFound) {
                assertTrue(ex.message!!.contains("找不到帳號 NonExistentPlayer#999"))
            }
        }

    @Test
    fun testRiotApiClientThrowsRateLimitOn429() =
        runTest {
            val mockTransport =
                object : HttpTransport {
                    override suspend fun get(url: String, headers: Map<String, String>): String {
                        throw RuntimeException("HTTP 429: Rate limit exceeded")
                    }
                                    }
            val tempKeyFile = File.createTempFile("test_key429", ".txt")
            tempKeyFile.deleteOnExit()
            val client = RiotApiClient(transport = mockTransport, apiKey = "valid-token", keyConfigFile = tempKeyFile)

            try {
                client.getAccountByRiotId("Faker", "KR1")
                fail("Expected RiotApiException.RateLimitExceeded")
            } catch (ex: RiotApiException.RateLimitExceeded) {
                assertTrue(ex.message!!.contains("請求頻率上限"))
            }
        }

    @Test
    fun testSoloQRepositoryThrowsMissingApiKeyOnForceSync() =
        runTest {
            val tempConfigFile = File.createTempFile("test_soloq_accounts_err", ".json")
            val tempMatchesFile = File.createTempFile("test_matches_err", ".json")
            val tempKeyFile = File.createTempFile("test_key_blank", ".txt")
            tempConfigFile.deleteOnExit()
            tempMatchesFile.deleteOnExit()
            tempKeyFile.deleteOnExit()

            val client = RiotApiClient(apiKey = null, keyConfigFile = tempKeyFile)
            val repo = SoloQRepository(riotApiClient = client, accountsStorageFile = tempConfigFile, matchesStorageFile = tempMatchesFile)
            val acc = PlayerSummonerAccount("Faker", "T1", Role.MID, "Faker", "KR1")
            repo.registerAccount(acc)

            try {
                repo.getRecentMatches("Faker", forceSync = true)
                fail("Expected RiotApiException.MissingApiKey")
            } catch (ex: RiotApiException.MissingApiKey) {
                assertTrue(ex.message!!.contains("金鑰"))
            }
        }

    @Test
    fun testSoloQMatchesPersistedToDiskCache() =
        runTest {
            val accountsFile = File.createTempFile("test_accs", ".json")
            val matchesFile = File.createTempFile("test_matches", ".json")
            accountsFile.deleteOnExit()
            matchesFile.deleteOnExit()

            val repo1 = SoloQRepository(accountsStorageFile = accountsFile, matchesStorageFile = matchesFile)
            val acc = PlayerSummonerAccount("Chovy", "GEN", Role.MID, "Chovy", "KR1")
            repo1.registerAccount(acc)

            val matches = listOf(
                SoloQMatchRecord("m101", "Chovy", "Ahri", "Ahri", Role.MID, true, true, 8, 1, 10, 240, 1800, System.currentTimeMillis())
            )
            repo1.saveMatchesToCache("Chovy", matches)

            // Verify file written to disk
            assertTrue(matchesFile.exists())
            assertTrue(matchesFile.length() > 0)

            // Re-instantiate repository pointing to same file
            val repo2 = SoloQRepository(accountsStorageFile = accountsFile, matchesStorageFile = matchesFile)
            val loadedMatches = repo2.getRecentMatches("Chovy", forceSync = false)
            assertEquals(1, loadedMatches.size)
            assertEquals("Ahri", loadedMatches.first().championName)
        }

    @Test
    fun testSoloQRepositoryDoesNotCallApiWhenNotForceSync() =
        runTest {
            val accountsFile = File.createTempFile("test_accs2", ".json")
            val matchesFile = File.createTempFile("test_matches2", ".json")
            accountsFile.deleteOnExit()
            matchesFile.deleteOnExit()

            // RiotApiClient has no apiKey and throwing transport
            val mockTransport =
                object : HttpTransport {
                    override suspend fun get(url: String, headers: Map<String, String>): String {
                        throw RuntimeException("API should NOT be called!")
                    }
                }
            val tempKeyFile = File.createTempFile("test_key_none", ".txt")
            tempKeyFile.deleteOnExit()
            val client = RiotApiClient(transport = mockTransport, apiKey = null, keyConfigFile = tempKeyFile)
            val repo = SoloQRepository(riotApiClient = client, accountsStorageFile = accountsFile, matchesStorageFile = matchesFile)

            val acc = PlayerSummonerAccount("ShowMaker", "DK", Role.MID, "ShowMaker", "KR1")
            repo.registerAccount(acc)

            // When forceSync = false and cache is empty, it must return emptyList without throwing or calling API
            val result = repo.getRecentMatches("ShowMaker", forceSync = false)
            assertTrue(result.isEmpty())
        }
}
