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
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DraftIntentPredictorTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var predictor: DraftIntentPredictor

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        predictor = DraftIntentPredictor(registry)
    }

    private fun createSamplePatchMeta(): PatchMetaMatrix =
        PatchMetaMatrix(
            patch = "14.15",
            totalGames = 120,
            championStats =
                mapOf(
                    "renekton" to
                        ChampionMetaStats(
                            "Renekton",
                            "14.15",
                            picks = 50,
                            bans = 40,
                            presenceRate = 0.75,
                            winRate = 0.58,
                            tier = MetaTier.T0,
                        ),
                    "aatrox" to
                        ChampionMetaStats(
                            "Aatrox",
                            "14.15",
                            picks = 45,
                            bans = 30,
                            presenceRate = 0.62,
                            winRate = 0.54,
                            tier = MetaTier.T1,
                        ),
                    "orianna" to
                        ChampionMetaStats(
                            "Orianna",
                            "14.15",
                            picks = 55,
                            bans = 35,
                            presenceRate = 0.75,
                            winRate = 0.55,
                            tier = MetaTier.T0,
                        ),
                    "syndra" to
                        ChampionMetaStats(
                            "Syndra",
                            "14.15",
                            picks = 40,
                            bans = 25,
                            presenceRate = 0.54,
                            winRate = 0.52,
                            tier = MetaTier.T1,
                        ),
                    "jinx" to
                        ChampionMetaStats(
                            "Jinx",
                            "14.15",
                            picks = 60,
                            bans = 20,
                            presenceRate = 0.66,
                            winRate = 0.56,
                            tier = MetaTier.T0,
                        ),
                    "lee sin" to
                        ChampionMetaStats(
                            "Lee Sin",
                            "14.15",
                            picks = 30,
                            bans = 15,
                            presenceRate = 0.37,
                            winRate = 0.51,
                            tier = MetaTier.T2,
                        ),
                ),
        )

    private fun createPlayerCareerStats(
        playerId: String,
        signatureChamp: String,
    ): PlayerCareerStats =
        PlayerCareerStats(
            playerId = playerId,
            totalProGames = 80,
            totalWins = 52,
            winRate = 0.65,
            roleDistribution = mapOf(Role.TOP to 80),
            championRecords =
                mapOf(
                    signatureChamp to
                        ChampionCareerRecord(
                            championId = signatureChamp,
                            gamesPlayed = 40,
                            wins = 28,
                            losses = 12,
                            winRate = 0.70,
                            pickRate = 0.50,
                            role = Role.TOP,
                        ),
                ),
            signaturePicks =
                listOf(
                    SignaturePick(
                        championId = signatureChamp,
                        gamesPlayed = 40,
                        wins = 28,
                        winRate = 0.70,
                        pickRate = 0.50,
                        signatureScore = 92.5,
                        tier = SignatureTier.SIGNATURE,
                        role = Role.TOP,
                    ),
                ),
        )

    @Nested
    @DisplayName("1. Next Action Intent Prediction Tests (下一手選角意圖預測)")
    inner class NextActionPredictionTests {
        @Test
        @DisplayName("預測第一輪選角首選：結合版本 T0 優先級與選手招牌絕活")
        fun `test predict next pick favors T0 meta and signature pick`() {
            val patchMeta = createSamplePatchMeta()
            val playerStats =
                mapOf(
                    Role.TOP to createPlayerCareerStats("TopGod", "Renekton"),
                )

            // Turns 1..6 are bans; turn 7 is Blue's first pick
            val turns =
                (1..6).map { turnNum ->
                    val side = if (turnNum % 2 != 0) Side.BLUE else Side.RED
                    DraftTurn(turnNum, side, ActionType.BAN, "DummyBan$turnNum")
                }
            val draft = DraftState.fromTurns(turns)

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    playerStatsByRole = playerStats,
                    topN = 3,
                )

            assertEquals(Side.BLUE, result.actingSide)
            assertEquals(ActionType.PICK, result.actionType)
            assertEquals(3, result.predictions.size)

            val top1 = result.predictions[0]
            assertEquals("Renekton", top1.championId, "T0 且選手招牌絕活的 Renekton 應位居 Top 1")
            assertTrue(top1.probability > result.predictions[1].probability)
            assertTrue(top1.metaScore > 0.0)
            assertTrue(top1.playerMasteryScore > 0.0)
            assertTrue(top1.rationale.isNotBlank())

            // Probabilities reflect true global empirical share (not artificially forced to sum to 1.0)
            assertTrue(top1.probability > 0.0 && top1.probability < 1.0, "機率應為真實全域百分比")
            assertTrue(top1.probability > result.predictions[1].probability, "Top 1 機率應高於 Top 2")
        }

        @Test
        @DisplayName("預測禁用意圖：應鎖定版本 T0 威脅角與對手核心招牌英雄")
        fun `test predict next ban targets op champions and opponent signature picks`() {
            val patchMeta = createSamplePatchMeta()
            val draft = DraftState.empty() // Turn 1: Blue Ban

            val result =
                predictor.predictNextAction(
                    draftState = draft,
                    patchMeta = patchMeta,
                    topN = 3,
                )

            assertEquals(Side.BLUE, result.actingSide)
            assertEquals(ActionType.BAN, result.actionType)
            assertEquals(3, result.predictions.size)

            val predictedChampIds = result.predictions.map { it.championId }
            // Orianna or Renekton (both T0 presence >= 75%) should be among top ban targets
            assertTrue(
                predictedChampIds.contains("Renekton") || predictedChampIds.contains("Orianna"),
                "禁選應鎖定 T0 級別強勢英雄",
            )
        }
    }

    @Nested
    @DisplayName("2. Composition Gap & Role Deficit Tests (陣容缺口填補意圖)")
    inner class CompositionGapTests {
        @Test
        @DisplayName("陣容缺少中路且全為物理傷害時，預測意圖應極力推崇 AP 中路英雄")
        fun `test gap filling boosts AP Mid carry when team lacks magic damage and mid`() {
            val patchMeta = createSamplePatchMeta()

            // Construct draft where Blue already locked: Aatrox (Top), Lee Sin (Jungle), Jinx (Bot), Blitzcrank (Support)
            // All 4 picks are physical/heavy AD; Mid is vacant
            val bluePicks =
                listOf(
                    DraftTurn(7, Side.BLUE, ActionType.PICK, "Aatrox", role = Role.TOP),
                    DraftTurn(10, Side.BLUE, ActionType.PICK, "Lee Sin", role = Role.JUNGLE),
                    DraftTurn(11, Side.BLUE, ActionType.PICK, "Jinx", role = Role.BOT),
                    DraftTurn(18, Side.BLUE, ActionType.PICK, "Blitzcrank", role = Role.SUPPORT),
                )
            val redPicks =
                listOf(
                    DraftTurn(8, Side.RED, ActionType.PICK, "K'Sante", role = Role.TOP),
                    DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", role = Role.JUNGLE),
                    DraftTurn(12, Side.RED, ActionType.PICK, "Ashe", role = Role.BOT),
                    DraftTurn(17, Side.RED, ActionType.PICK, "Nautilus", role = Role.SUPPORT),
                )
            val bans =
                (1..6).map {
                    DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Ban$it")
                } +
                    (13..16).map {
                        DraftTurn(it, if (it % 2 != 0) Side.RED else Side.BLUE, ActionType.BAN, "Ban$it")
                    }

            // Turn 19 is Blue's final pick!
            val draft = DraftState.fromTurns(bans + bluePicks + redPicks)
            assertEquals(19, draft.currentTurnNumber)

            val result = predictor.predictNextAction(draftState = draft, patchMeta = patchMeta, topN = 3)
            assertEquals(Side.BLUE, result.actingSide)
            assertEquals(ActionType.PICK, result.actionType)

            val topCandidate = result.predictions[0]
            // Top candidate must be an AP Mid mage (Orianna or Syndra)
            assertTrue(
                topCandidate.championId == "Orianna" || topCandidate.championId == "Syndra",
                "藍方極度欠缺 AP 中路，預測結果應為 AP 中路 (預測: ${topCandidate.championId})",
            )
            assertTrue(topCandidate.compositionFitScore > 0.6, "補足陣容缺口應獲得高額 compositionFitScore")
        }
    }

    @Nested
    @DisplayName("3. Constraint Filtering Tests (合法性與排除檢查)")
    inner class FilteringTests {
        @Test
        @DisplayName("已被選用或已被禁用的英雄絕不應出現在預測清單中")
        fun `test already picked and banned champions are never predicted`() {
            val patchMeta = createSamplePatchMeta()
            val bans =
                listOf(
                    DraftTurn(1, Side.BLUE, ActionType.BAN, "Renekton"),
                    DraftTurn(2, Side.RED, ActionType.BAN, "Orianna"),
                )
            val draft = DraftState.fromTurns(bans)

            val result = predictor.predictNextAction(draftState = draft, patchMeta = patchMeta, topN = 5)
            val candidateIds = result.predictions.map { it.championId }

            assertFalse(candidateIds.contains("Renekton"), "Renekton 已被禁，不可被預測")
            assertFalse(candidateIds.contains("Orianna"), "Orianna 已被禁，不可被預測")
        }

        @Test
        @DisplayName("預測清單中絕不應出現重複英雄 (包含大小寫或別名)")
        fun `test predictions never contain duplicate champions`() {
            val patchMeta =
                PatchMetaMatrix(
                    patch = "14.15",
                    totalGames = 100,
                    championStats =
                        mapOf(
                            "ahri" to ChampionMetaStats("Ahri", "14.15", picks = 50, bans = 40, presenceRate = 0.9, winRate = 0.6, tier = MetaTier.T0),
                            "Ahri" to ChampionMetaStats("Ahri", "14.15", picks = 50, bans = 40, presenceRate = 0.9, winRate = 0.6, tier = MetaTier.T0),
                            "leblanc" to ChampionMetaStats("Leblanc", "14.15", picks = 50, bans = 40, presenceRate = 0.85, winRate = 0.58, tier = MetaTier.T0),
                            "LeBlanc" to ChampionMetaStats("LeBlanc", "14.15", picks = 50, bans = 40, presenceRate = 0.85, winRate = 0.58, tier = MetaTier.T0),
                        ),
                )
            val draft = DraftState.empty()
            val result = predictor.predictNextAction(draftState = draft, patchMeta = patchMeta, topN = 5)
            val slugs = result.predictions.map { com.loldraft.data.normalization.ChampionNormalizer.toSlug(it.championId) }
            assertEquals(slugs.distinct().size, slugs.size, "預測英雄清單絕不可包含重複英雄: ${result.predictions.map { it.championId }}")
        }
    }

    @Nested
    @DisplayName("4. Strict Role Vacancy & Player Mastery Isolation Tests (嚴格分路空缺與選手熟練度隔離)")
    inner class RoleVacancyAndPlayerMasteryIsolationTests {
        @Test
        @DisplayName("已鎖定中路時，中路專精英雄絕不應出現在選角預測清單中，預測分路必須屬於未鎖定的空缺路")
        fun `test mid only champions never predicted when mid is locked and predicted roles are strictly vacant`() {
            val patchMeta = createSamplePatchMeta()

            // Blue team already locked Mid: Orianna
            val turns =
                (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Ban$it") } +
                    listOf(
                        DraftTurn(7, Side.BLUE, ActionType.PICK, "Orianna", role = Role.MID, player = "Faker"),
                        DraftTurn(8, Side.RED, ActionType.PICK, "K'Sante", role = Role.TOP),
                        DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", role = Role.JUNGLE),
                    )

            val draft = DraftState.fromTurns(turns)
            assertEquals(10, draft.currentTurnNumber) // Turn 10 is Blue's Pick 2

            val result = predictor.predictNextAction(draftState = draft, patchMeta = patchMeta, topN = 5)
            val predictedChampIds = result.predictions.map { it.championId }

            // Mid-only champions like Syndra or Orianna must NOT be predicted
            assertFalse(predictedChampIds.contains("Orianna"), "Orianna 已被選用，絕不可再次被預測")
            assertFalse(predictedChampIds.contains("Syndra"), "Syndra 為中路專精英雄，在中路已鎖定時絕不可預測選用")

            // Every predicted champion's role must be in vacant roles (TOP, JUNGLE, BOT, SUPPORT)
            val vacantRoles = setOf(Role.TOP, Role.JUNGLE, Role.BOT, Role.SUPPORT)
            for (pred in result.predictions) {
                assertNotNull(pred.predictedRole, "選角預測的分路不可為空")
                assertTrue(pred.predictedRole in vacantRoles, "預測分路 ${pred.predictedRole} 必須在空缺分路中")
                assertFalse(pred.predictedRole == Role.MID, "中路已鎖定，預測分路絕不可為 MID")
            }
        }

        @Test
        @DisplayName("隊友之間的招牌絕活熟練度絕不可跨路洩漏，未在職業場玩過的選手絕不可標記為招牌或絕活")
        fun `test player mastery never leaks across teammates and unplayed champions receive unplayed rationale`() {
            val patchMeta = createSamplePatchMeta()

            // Faker has Orianna as SIGNATURE pick (150 games, 68% WR)
            val fakerRecord = ChampionCareerRecord("Orianna", 150, 102, 48, 0.68, 0.40, Role.MID)
            val fakerSig = SignaturePick("Orianna", 150, 102, 0.68, 0.40, 95.0, SignatureTier.SIGNATURE, Role.MID)
            val fakerStats = PlayerCareerStats(
                playerId = "Faker",
                totalProGames = 300,
                totalWins = 200,
                winRate = 0.667,
                roleDistribution = mapOf(Role.MID to 300),
                championRecords = mapOf("Orianna" to fakerRecord),
                signaturePicks = listOf(fakerSig),
            )

            // Zeus has Renekton as SIGNATURE pick (50 games, 65% WR), but 0 games on Orianna
            val zeusRecord = ChampionCareerRecord("Renekton", 50, 33, 17, 0.66, 0.35, Role.TOP)
            val zeusSig = SignaturePick("Renekton", 50, 33, 0.66, 0.35, 90.0, SignatureTier.SIGNATURE, Role.TOP)
            val zeusStats = PlayerCareerStats(
                playerId = "Zeus",
                totalProGames = 140,
                totalWins = 90,
                winRate = 0.643,
                roleDistribution = mapOf(Role.TOP to 140),
                championRecords = mapOf("Renekton" to zeusRecord),
                signaturePicks = listOf(zeusSig),
            )

            val playerStats = mapOf(
                Role.TOP to zeusStats,
                Role.MID to fakerStats,
            )

            // Turn 7: Blue first pick (nothing locked yet)
            val bans = (1..6).map { DraftTurn(it, if (it % 2 != 0) Side.BLUE else Side.RED, ActionType.BAN, "Dummy$it") }
            val draft = DraftState.fromTurns(bans)

            val result = predictor.predictNextAction(
                draftState = draft,
                patchMeta = patchMeta,
                playerStatsByRole = playerStats,
                topN = 10,
            )

            // If any prediction is for Zeus (TOP), it must NEVER claim Orianna or leak Faker's Orianna signature
            for (pred in result.predictions) {
                if (pred.playerName == "Zeus" || pred.predictedRole == Role.TOP) {
                    assertFalse(pred.championId.equals("Orianna", ignoreCase = true), "Zeus (TOP) 絕不應被預測選用 Orianna")
                    assertFalse(pred.rationale.contains("Orianna"), "Zeus 的理由絕不可包含 Orianna")
                    if (pred.championId != "Renekton") {
                        assertFalse(pred.rationale.contains("SIGNATURE"), "Zeus 未玩過或非招牌的英雄理由絕不可標為 SIGNATURE")
                    }
                }
                if (pred.championId.equals("Orianna", ignoreCase = true)) {
                    assertEquals(Role.MID, pred.predictedRole, "Orianna 的預測分路必須為 MID")
                    assertEquals("Faker", pred.playerName, "Orianna 的預測選手必須為 Faker，絕不可是 Zeus")
                    assertTrue(pred.rationale.contains("Faker"), "Orianna 的理由必須歸屬 Faker")
                }
            }
        }
    }
}
