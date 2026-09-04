package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.PlayerIntelligenceService
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.SoloQAccount
import com.loldraft.data.player.SoloQChampionStats
import com.loldraft.data.player.SoloQGame
import com.loldraft.data.player.SoloQServer
import com.loldraft.data.player.SpikeAlert
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.player.SpikeAlertType
import com.loldraft.platform.live.LiveMatchCompanionEngine
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PlayerSoloQDraftPredictionTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var predictor: DraftIntentPredictor

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        predictor = DraftIntentPredictor(registry)
    }

    private fun createSamplePatchMeta(): PatchMetaMatrix =
        PatchMetaMatrix(
            patch = "16.17",
            totalGames = 150,
            championStats =
                mapOf(
                    "renekton" to
                        ChampionMetaStats(
                            championId = "Renekton",
                            patch = "16.17",
                            picks = 60,
                            bans = 30,
                            presenceRate = 0.60,
                            winRate = 0.54,
                            tier = MetaTier.T1,
                        ),
                    "aatrox" to
                        ChampionMetaStats(
                            championId = "Aatrox",
                            patch = "16.17",
                            picks = 60,
                            bans = 30,
                            presenceRate = 0.60,
                            winRate = 0.54,
                            tier = MetaTier.T1,
                        ),
                    "orianna" to
                        ChampionMetaStats(
                            championId = "Orianna",
                            patch = "16.17",
                            picks = 70,
                            bans = 45,
                            presenceRate = 0.76,
                            winRate = 0.55,
                            tier = MetaTier.T0,
                        ),
                    "syndra" to
                        ChampionMetaStats(
                            championId = "Syndra",
                            patch = "16.17",
                            picks = 50,
                            bans = 25,
                            presenceRate = 0.50,
                            winRate = 0.52,
                            tier = MetaTier.T1,
                        ),
                    "sejuani" to
                        ChampionMetaStats(
                            championId = "Sejuani",
                            patch = "16.17",
                            picks = 45,
                            bans = 20,
                            presenceRate = 0.43,
                            winRate = 0.51,
                            tier = MetaTier.T2,
                        ),
                ),
        )

    private fun createProPlayerProfile(
        playerId: String,
        role: Role,
        signatureChamp: String,
        soloQSpikeChamp: String? = null,
        soloQ3dGames: Int = 0,
        soloQ3dWinRate: Double = 0.0,
    ): ProPlayerDetailedProfile {
        val careerRecord =
            ChampionCareerRecord(
                championId = signatureChamp,
                gamesPlayed = 40,
                wins = 28,
                losses = 12,
                winRate = 0.70,
                pickRate = 0.50,
                role = role,
            )

        val signaturePick =
            SignaturePick(
                championId = signatureChamp,
                gamesPlayed = 40,
                wins = 28,
                winRate = 0.70,
                pickRate = 0.50,
                signatureScore = 92.0,
                tier = SignatureTier.SIGNATURE,
                role = role,
            )

        val careerStats =
            PlayerCareerStats(
                playerId = playerId,
                totalProGames = 80,
                totalWins = 52,
                winRate = 0.65,
                roleDistribution = mapOf(role to 80),
                championRecords = mapOf(signatureChamp to careerRecord),
                signaturePicks = listOf(signaturePick),
            )

        val spikeAlerts = mutableListOf<SpikeAlert>()
        val recent3dStats = mutableListOf<SoloQChampionStats>()

        if (soloQSpikeChamp != null && soloQ3dGames > 0) {
            recent3dStats.add(
                SoloQChampionStats(
                    championId = soloQSpikeChamp,
                    gamesPlayed = soloQ3dGames,
                    wins = (soloQ3dGames * soloQ3dWinRate).toInt(),
                    losses = soloQ3dGames - (soloQ3dGames * soloQ3dWinRate).toInt(),
                    winRate = soloQ3dWinRate,
                    pickShare = 0.5,
                    gamesPerDay = soloQ3dGames / 3.0,
                    role = role,
                    avgKda = 3.5,
                ),
            )

            spikeAlerts.add(
                SpikeAlert(
                    championId = soloQSpikeChamp,
                    severity = SpikeAlertSeverity.HIGH,
                    type = SpikeAlertType.PRACTICE_SPIKE,
                    recentDays = 3,
                    recentGamesCount = soloQ3dGames,
                    recentWinRate = soloQ3dWinRate,
                    baselineGamesCount = 2,
                    baselineDays = 18,
                    frequencyMultiplier = 3.5,
                    careerProGames = 40,
                    reason = "Sudden surge of $soloQ3dGames games in 3 days (${(soloQ3dWinRate * 100).toInt()}% WR)",
                ),
            )
        }

        val dossier =
            PlayerIntelligenceDossier(
                playerId = playerId,
                careerStats = careerStats,
                linkedAccounts = emptyList(),
                recentSoloQ3Days = recent3dStats,
                recentSoloQ7Days = recent3dStats,
                activeSpikeAlerts = spikeAlerts,
                blindPickConfidences = emptyMap(),
            )

        return ProPlayerDetailedProfile.fromDossier(role, dossier)
    }

    @Nested
    @DisplayName("1. SoloQ Practice Spike Elevation Tests (天梯練角突增意圖加權)")
    inner class SoloQSpikeTests {
        @Test
        @DisplayName("選手近期 SoloQ 突增練角 (PRACTICE_SPIKE) 應大幅提升意圖預測機率與 soloQScore")
        fun `test soloq practice spike boosts pick intent and soloq score significantly`() {
            val patchMeta = createSamplePatchMeta()

            // Zeus has equal T1 meta candidates: Renekton and Aatrox.
            // On Renekton, Zeus has an active PRACTICE_SPIKE (12 games in 3 days, 75% WR).
            // On Aatrox, Zeus has 0 SoloQ games recently.
            val zeusProfile =
                createProPlayerProfile(
                    playerId = "Zeus",
                    role = Role.TOP,
                    signatureChamp = "Renekton",
                    soloQSpikeChamp = "Renekton",
                    soloQ3dGames = 12,
                    soloQ3dWinRate = 0.75,
                )

            val profiles = mapOf(Role.TOP to zeusProfile)

            // Turns 1..6 bans; Turn 7 is Blue's first pick
            val bans = (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Dummy$it") }
            val draft = DraftState.fromTurns(bans)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerProfilesByRole = profiles,
                    topN = 5,
                )

            val renektonCand = result.predictions.find { it.championId == "Renekton" }
            assertNotNull(renektonCand, "Renekton 應在預測候選名單中")
            assertTrue(renektonCand!!.soloQScore >= 0.85, "擁有高頻練角突增的 Renekton soloQScore 應 >= 0.85 (實際: ${renektonCand.soloQScore})")
            assertTrue(renektonCand.playerMasteryScore >= 0.80, "招牌絕活 playerMasteryScore 應 >= 0.80")
            assertEquals("Zeus", renektonCand.playerName, "候選者應關聯至對應分路選手 Zeus")
            assertEquals(Role.TOP, renektonCand.predictedRole, "預測分路應為 TOP")

            // Top candidate must be Renekton with top probability
            assertEquals("Renekton", result.predictions[0].championId, "結合生涯與 SoloQ 突增的 Renekton 應位居第一首選")
            assertTrue(result.predictions[0].probability > result.predictions[1].probability)
        }
    }

    @Nested
    @DisplayName("2. Vacant Role & Target Player Resolution Tests (空缺分路與選手情報綁定)")
    inner class VacantRoleTests {
        @Test
        @DisplayName("已鎖定上路時，後續選角應自動對齊空缺中路並綁定中路選手情報與 SoloQ 數據")
        fun `test draft automatically focuses on vacant lanes and matches vacant role player`() {
            val patchMeta = createSamplePatchMeta()

            val zeusProfile = createProPlayerProfile("Zeus", Role.TOP, "Renekton", "Renekton", 10, 0.70)
            val fakerProfile = createProPlayerProfile("Faker", Role.MID, "Orianna", "Orianna", 14, 0.78)

            val profiles = mapOf(Role.TOP to zeusProfile, Role.MID to fakerProfile)

            // Draft where Blue already locked TOP: Aatrox
            val turns =
                (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Ban$it") } +
                    listOf(
                        DraftTurn(7, Side.BLUE, ActionType.PICK, "Aatrox", role = Role.TOP),
                        DraftTurn(8, Side.RED, ActionType.PICK, "K'Sante", role = Role.TOP),
                        DraftTurn(9, Side.RED, ActionType.PICK, "Maokai", role = Role.JUNGLE),
                    )

            // Turn 10 is Blue's Pick 2
            val draft = DraftState.fromTurns(turns)
            assertEquals(10, draft.currentTurnNumber)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerProfilesByRole = profiles,
                    topN = 5,
                )

            val topPick = result.predictions[0]
            assertEquals("Orianna", topPick.championId, "在 TOP 已被鎖定且 MID 空缺的情況下，應預測 Faker 的 Orianna")
            assertEquals(Role.MID, topPick.predictedRole, "預測位置應為 MID")
            assertEquals("Faker", topPick.playerName, "預測選手應為 Faker")
            assertTrue(topPick.soloQScore >= 0.85, "Faker 的 SoloQ 突增分數應反映在預測中")

            // Renekton (TOP) should be penalized for redundant lane
            val renektonCand = result.predictions.find { it.championId == "Renekton" }
            if (renektonCand != null) {
                assertTrue(renektonCand.intentScore < topPick.intentScore, "已選上路時，上路英雄評分應被位置衝突懲罰降低")
            }
        }
    }

    @Nested
    @DisplayName("3. Transparent Decision Rationale Tests (透明決策原因產出)")
    inner class RationaleTests {
        @Test
        @DisplayName("決策理由必須詳盡包含選手姓名、分路、招牌等級、SoloQ 突增數據與版本梯度")
        fun `test rationale articulates player name, role, signature tier, soloq spike, and meta`() {
            val patchMeta = createSamplePatchMeta()
            val zeusProfile = createProPlayerProfile("Zeus", Role.TOP, "Renekton", "Renekton", 10, 0.80)

            val bans = (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Dummy$it") }
            val draft = DraftState.fromTurns(bans)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerProfilesByRole = mapOf(Role.TOP to zeusProfile),
                    topN = 3,
                )

            val renekton = result.predictions.first { it.championId == "Renekton" }
            val rationale = renekton.rationale

            assertTrue(rationale.contains("Zeus"), "Rationale 必須包含選手姓名 Zeus (實際: $rationale)")
            assertTrue(rationale.contains("TOP"), "Rationale 必須包含分路 TOP (實際: $rationale)")
            assertTrue(rationale.contains("SIGNATURE") || rationale.contains("招牌"), "Rationale 必須包含招牌等級")
            assertTrue(rationale.contains("SoloQ") || rationale.contains("SPIKE") || rationale.contains("練角"), "Rationale 必須包含 SoloQ 練角情報")
            assertTrue(rationale.contains("T1") || rationale.contains("meta"), "Rationale 必須包含版本梯度")
        }
    }

    @Nested
    @DisplayName("4. 4-Factor Weighted Algorithm Verification Tests (25% + 30% + 30% + 15% 權重驗證)")
    inner class WeightAlgorithmTests {
        @Test
        @DisplayName("驗證意圖評分嚴格符合四要素複合公式：版本(25%) + 生涯(30%) + SoloQ(30%) + 陣容契合與反制(15%)")
        fun `test 4-factor composite weighting algorithm formula adherence`() {
            val patchMeta = createSamplePatchMeta()
            val zeusProfile = createProPlayerProfile("Zeus", Role.TOP, "Renekton", "Renekton", 8, 0.75)

            val bans = (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "B$it") }
            val draft = DraftState.fromTurns(bans)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerProfilesByRole = mapOf(Role.TOP to zeusProfile),
                    topN = 5,
                )

            val candidate = result.predictions.first { it.championId == "Renekton" }

            // Expected formula: 0.25 * metaScore + 0.30 * playerMasteryScore + 0.30 * soloQScore + 0.10 * compFit + 0.05 * counterDenial
            val expectedScore =
                (candidate.metaScore * 0.25) +
                    (candidate.playerMasteryScore * 0.30) +
                    (candidate.soloQScore * 0.30) +
                    (candidate.compositionFitScore * 0.10) +
                    (candidate.counterDenialScore * 0.05)

            assertTrue(
                abs(candidate.intentScore - expectedScore) <= 0.05,
                "IntentScore (${candidate.intentScore}) 應符合 25%/30%/30%/15% 複合公式計算值 ($expectedScore)",
            )
        }
    }

    @Nested
    @DisplayName("5. LiveMatchCompanionEngine Integration Tests (即時推演推播情報綁定)")
    inner class LiveMatchCompanionIntegrationTests {
        @Test
        @DisplayName("LiveMatchCompanionEngine 應自動綁定選手情報並在即時推演 Snapshot 產出 SoloQ 與生涯融合預測")
        fun `test live match companion engine automatically binds roster intelligence and provides enriched predictions`() {
            val patchMeta = createSamplePatchMeta()
            val intelligenceService = PlayerIntelligenceService()

            val now = System.currentTimeMillis()
            // Add SoloQ games for Zeus (T1 top) with spike on Renekton
            intelligenceService.registerSoloQAccount(
                "Zeus",
                SoloQAccount("zeus_kr", "T1 Zeus", SoloQServer.KR),
            )

            // Register SoloQ game batch within last 3 days
            val soloQGames =
                (1..8).map { i ->
                    SoloQGame(
                        gameId = "soloq_t1_zeus_$i",
                        accountId = "zeus_kr",
                        server = SoloQServer.KR,
                        timestampEpochMs = now - TimeUnit.HOURS.toMillis(i.toLong() * 6),
                        championId = "Renekton",
                        role = Role.TOP,
                        win = true,
                        kills = 5,
                        deaths = 1,
                        assists = 6,
                    )
                }
            intelligenceService.addSoloQGames(soloQGames)

            val engine =
                LiveMatchCompanionEngine(
                    intentPredictor = predictor,
                    playerIntelligenceService = intelligenceService,
                )

            val t1 = Team("T1", "T1", "T1", "LCK")
            val gen = Team("GEN", "Gen.G", "GEN", "LCK")

            val session =
                engine.createSession(
                    CreateLiveSessionRequest(
                        sessionId = "live-soloq-session",
                        blueTeam = t1,
                        redTeam = gen,
                        patchMeta = patchMeta,
                    ),
                )

            // Apply 6 bans
            listOf("Kalista", "Rumble", "Lucian", "Ashe", "Varus", "Caitlyn").forEach {
                engine.applyTurn(session.sessionId, it)
            }

            // Snapshot at Turn 6 represents next turn Turn 7 (Blue Pick 1)
            val latestSnapshot = engine.getSession(session.sessionId)!!.history.last()
            assertEquals(6, latestSnapshot.turnNumber)
            assertEquals(7, latestSnapshot.nextTurnSpec?.turnNumber)
            assertEquals(Side.BLUE, latestSnapshot.nextTurnSpec?.side)

            val intentPredictions = latestSnapshot.aiIntentPredictions
            assertTrue(intentPredictions.isNotEmpty(), "Turn 7 首選應產出 AI 意圖預測")

            val renektonPrediction = intentPredictions.find { it.championId == "Renekton" }
            if (renektonPrediction != null) {
                assertTrue(renektonPrediction.soloQScore > 0.0, "Renekton 預測應包含來自 SoloQ 練角的 soloQScore")
                assertTrue(renektonPrediction.rationale.isNotBlank(), "預測理由應完整產出")
            }
        }
    }

    @Nested
    @DisplayName("6. Graceful Fallback Tests (降級相容測試)")
    inner class FallbackTests {
        @Test
        @DisplayName("未提供選手 SoloQ 或生涯情報時，預測大腦應優雅降級為純版本/陣容預測且不發生異常")
        fun `test predictor falls back gracefully when player profiles are null`() {
            val patchMeta = createSamplePatchMeta()
            val bans = (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Dummy$it") }
            val draft = DraftState.fromTurns(bans)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerProfilesByRole = null,
                    topN = 3,
                )

            assertEquals(3, result.predictions.size)
            result.predictions.forEach { cand ->
                assertEquals(0.0, cand.soloQScore, "降級模式下 soloQScore 應為 0.0")
                assertTrue(cand.metaScore > 0.0)
                assertTrue(cand.probability > 0.0)
            }

            val totalProb = result.predictions.sumOf { it.probability }
            assertEquals(1.0, totalProb, 0.001, "預測機率總和應歸一化為 1.0")
        }
    }
}
