package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionSynergy
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.MatchupCounter
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DraftRecommenderTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var recommender: DraftRecommender

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        recommender =
            DraftRecommender(
                evaluator = AnalyticalDraftEvaluator(),
                tagRegistry = registry,
            )
    }

    private fun createSamplePatchMeta(): PatchMetaMatrix =
        PatchMetaMatrix(
            patch = "14.15",
            totalGames = 100,
            championStats =
                mapOf(
                    "syndra" to
                        ChampionMetaStats(
                            "Syndra",
                            "14.15",
                            picks = 40,
                            presenceRate = 0.50,
                            winRate = 0.54,
                            tier = MetaTier.T1,
                        ),
                    "orianna" to
                        ChampionMetaStats(
                            "Orianna",
                            "14.15",
                            picks = 50,
                            presenceRate = 0.60,
                            winRate = 0.53,
                            tier = MetaTier.T1,
                        ),
                    "malphite" to
                        ChampionMetaStats(
                            "Malphite",
                            "14.15",
                            picks = 30,
                            presenceRate = 0.35,
                            winRate = 0.56,
                            tier = MetaTier.T2,
                        ),
                    "k'sante" to
                        ChampionMetaStats(
                            "K'Sante",
                            "14.15",
                            picks = 55,
                            presenceRate = 0.70,
                            winRate = 0.52,
                            tier = MetaTier.T1,
                        ),
                    "renekton" to
                        ChampionMetaStats(
                            "Renekton",
                            "14.15",
                            picks = 60,
                            presenceRate = 0.75,
                            winRate = 0.55,
                            tier = MetaTier.T0,
                        ),
                ),
            matchupCounters =
                listOf(
                    MatchupCounter(
                        champion = "Malphite",
                        opponent = "Renekton",
                        role = Role.TOP,
                        gamesFaced = 20,
                        wins = 13,
                        losses = 7,
                        winRate = 0.65,
                        winRateDelta = 0.12,
                        counterScore = 85.0,
                    ),
                ),
            synergies =
                listOf(
                    ChampionSynergy(
                        championA = "Jarvan IV",
                        championB = "Orianna",
                        gamesTogether = 25,
                        winsTogether = 17,
                        synergyWinRate = 0.68,
                        expectedWinRate = 0.53,
                        winRateDelta = 0.15,
                        synergyScore = 88.0,
                    ),
                ),
        )

    @Nested
    @DisplayName("1. Max-WinRate Gain Recommendation Tests (最大期望勝率增益推薦)")
    inner class MaxWinRateGainTests {
        @Test
        @DisplayName("候選英雄應按勝率增益 (winRateGain) 嚴格降序排列，且精確計算預測勝率")
        fun `test candidates are sorted by win rate gain descending`() {
            val draft =
                DraftState.empty().copy(
                    bluePicks =
                        listOf(
                            com.loldraft.data.models
                                .PickSelection("Renekton", Role.TOP),
                            com.loldraft.data.models
                                .PickSelection("Lee Sin", Role.JUNGLE),
                        ),
                    redPicks =
                        listOf(
                            com.loldraft.data.models
                                .PickSelection("Aatrox", Role.TOP),
                            com.loldraft.data.models
                                .PickSelection("Sejuani", Role.JUNGLE),
                        ),
                )

            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.BLUE,
                    limit = 5,
                )

            assertEquals(Side.BLUE, report.targetSide)
            assertFalse(report.recommendations.isEmpty(), "應產出推薦清單")
            assertEquals(5, report.recommendations.size)

            for (i in 0 until report.recommendations.size - 1) {
                val current = report.recommendations[i]
                val next = report.recommendations[i + 1]
                assertTrue(
                    current.winRateGain >= next.winRateGain - 0.0001,
                    "第 $i 位增益 (${current.winRateGain}) 應大於等於第 ${i + 1} 位增益 (${next.winRateGain})",
                )
            }

            val top1 = report.recommendations[0]
            assertEquals(report.baseWinRate + top1.winRateGain, top1.predictedWinRate, 0.001)
            assertTrue(top1.reasons.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("2. Counter & Synergy Synergy Tests (剋制反制與陣容聯動)")
    inner class CounterAndSynergyTests {
        @Test
        @DisplayName("敵方鎖定 Renekton 時，對位硬克制英雄 (如 Malphite) 應獲得反制加成並標註原因")
        fun `test counter pick gets counter score and rationale against locked opponent`() {
            val patchMeta = createSamplePatchMeta()
            val draft =
                DraftState.empty().copy(
                    redPicks =
                        listOf(
                            com.loldraft.data.models
                                .PickSelection("Renekton", Role.TOP),
                        ),
                )

            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.BLUE,
                    patchMeta = patchMeta,
                    targetRole = Role.TOP,
                    limit = 3,
                )

            val malphiteRec = report.recommendations.firstOrNull { it.championId == "Malphite" }
            assertNotNull(malphiteRec, "Top 路線推薦中應包含 Malphite")
            assertTrue(malphiteRec!!.counterScore > 0.0, "應有正向 counterScore")
            assertTrue(malphiteRec.reasons.any { it.contains("Renekton", ignoreCase = true) || it.contains("Counter", ignoreCase = true) })
        }

        @Test
        @DisplayName("己方已有 Jarvan IV 時，高搭配度英雄 (如 Orianna) 應享有連動加成")
        fun `test synergy champion gets synergy bonus when paired with teammates`() {
            val patchMeta = createSamplePatchMeta()
            val draft =
                DraftState.empty().copy(
                    bluePicks =
                        listOf(
                            com.loldraft.data.models
                                .PickSelection("Jarvan IV", Role.JUNGLE),
                        ),
                )

            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.BLUE,
                    patchMeta = patchMeta,
                    targetRole = Role.MID,
                    limit = 3,
                )

            val oriannaRec = report.recommendations.firstOrNull { it.championId == "Orianna" }
            assertNotNull(oriannaRec, "Mid 路線推薦中應包含 Orianna")
            assertTrue(oriannaRec!!.synergyScore > 0.0, "應有正向 synergyScore")
        }
    }

    @Nested
    @DisplayName("3. Composition Flaw Resolution Tests (陣容缺陷修復能力)")
    inner class FlawResolutionTests {
        @Test
        @DisplayName("己方 3 手純物理且缺乏魔法傷害時，選入 AP 法師應標記修復 FLAW_LACK_AP 缺陷")
        fun `test picking AP mage resolves lack AP flaw and lists it in reasons`() {
            val draft =
                DraftState.empty().copy(
                    bluePicks =
                        listOf(
                            com.loldraft.data.models
                                .PickSelection("Renekton", Role.TOP),
                            com.loldraft.data.models
                                .PickSelection("Lee Sin", Role.JUNGLE),
                            com.loldraft.data.models
                                .PickSelection("Jinx", Role.BOT),
                        ),
                )

            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.BLUE,
                    targetRole = Role.MID,
                    limit = 3,
                )

            val topMage =
                report.recommendations.firstOrNull {
                    it.championId == "Syndra" ||
                        it.championId == "Orianna" ||
                        it.championId == "Ahri"
                }
            assertNotNull(topMage, "應推薦 AP 中路法師")
            assertTrue(topMage!!.flawsResolved.isNotEmpty(), "應記錄被成功修復的缺陷")
            assertTrue(
                topMage.reasons.any {
                    it.contains("AP", ignoreCase = true) ||
                        it.contains("Magic", ignoreCase = true) ||
                        it.contains("Flaw", ignoreCase = true)
                },
                "推薦理由應包含修復魔法傷害缺陷",
            )
        }
    }

    @Nested
    @DisplayName("4. Constraints & Filter Tests (篩選與約束)")
    inner class FilterTests {
        @Test
        @DisplayName("指定 targetRole 時，所有推薦候選英雄必須匹配該路線")
        fun `test target role strictly filters recommended candidates`() {
            val draft = DraftState.empty()
            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.BLUE,
                    targetRole = Role.SUPPORT,
                    limit = 5,
                )

            assertTrue(report.recommendations.isNotEmpty())
            for (rec in report.recommendations) {
                assertEquals(Role.SUPPORT, rec.recommendedRole)
            }
        }

        @Test
        @DisplayName("已被選或被禁的英雄絕不應出現在推薦清單中")
        fun `test selected or banned champions are never recommended`() {
            val turns =
                listOf(
                    DraftTurn(1, Side.BLUE, ActionType.BAN, "Thresh"),
                    DraftTurn(7, Side.BLUE, ActionType.PICK, "Nautilus"),
                )
            val draft = DraftState.fromTurns(turns)

            val report =
                recommender.recommend(
                    draftState = draft,
                    targetSide = Side.RED,
                    targetRole = Role.SUPPORT,
                    limit = 10,
                )

            val ids = report.recommendations.map { it.championId }
            assertFalse(ids.contains("Thresh"), "被禁的 Thresh 不可被推薦")
            assertFalse(ids.contains("Nautilus"), "被選的 Nautilus 不可被推薦")
        }
    }
}
