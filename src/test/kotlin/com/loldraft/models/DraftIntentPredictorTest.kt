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

            // Sum of candidate probabilities should be normalized to 1.0
            val totalProb = result.predictions.sumOf { it.probability }
            assertEquals(1.0, totalProb, 0.001)
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
    }
}
