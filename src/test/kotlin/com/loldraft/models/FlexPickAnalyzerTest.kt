package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
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

class FlexPickAnalyzerTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var analyzer: FlexPickAnalyzer

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        analyzer = FlexPickAnalyzer(registry)
    }

    @Nested
    @DisplayName("1. Unconditional Flex Detection Tests (多路搖擺位基礎識別)")
    inner class UnconditionalFlexTests {
        @Test
        @DisplayName("藍寶 (Rumble) 應被識別為多路搖擺位 (Top/Mid/Jungle)")
        fun `test rumble is identified as flex pick`() {
            val result = analyzer.analyzeChampion("Rumble")

            assertTrue(result.isFlex, "Rumble 應判定為搖擺位")
            assertEquals("Rumble", result.championId)
            assertTrue(result.roleProbabilities.containsKey(Role.TOP))
            assertTrue(result.roleProbabilities.containsKey(Role.MID))
            assertTrue(result.roleProbabilities[Role.TOP]!! >= 0.20)
            assertTrue(result.roleProbabilities[Role.MID]!! >= 0.20)

            // Probability sum must be 1.0 (with small floating precision tolerance)
            val totalProb = result.roleProbabilities.values.sum()
            assertEquals(1.0, totalProb, 0.001)
            assertTrue(result.flexEntropy > 0.5, "搖擺位應有顯著的熵值")
        }

        @Test
        @DisplayName("波比 (Poppy) 應被識別為多路搖擺位 (Top/Jungle/Support)")
        fun `test poppy is identified as flex pick`() {
            val result = analyzer.analyzeChampion("Poppy")

            assertTrue(result.isFlex, "Poppy 應判定為搖擺位")
            val roles = result.roleProbabilities.filter { it.value >= 0.15 }.keys
            assertTrue(roles.contains(Role.JUNGLE) || roles.contains(Role.TOP) || roles.contains(Role.SUPPORT))
            assertEquals(1.0, result.roleProbabilities.values.sum(), 0.001)
        }

        @Test
        @DisplayName("庫奇 (Corki) 應被識別為 Mid/Bot 雙路搖擺位")
        fun `test corki is identified as flex pick`() {
            val result = analyzer.analyzeChampion("Corki")

            assertTrue(result.isFlex, "Corki 應判定為搖擺位")
            assertTrue(result.roleProbabilities.containsKey(Role.MID))
            assertTrue(result.roleProbabilities.containsKey(Role.BOT))
        }

        @Test
        @DisplayName("單一路線專精英雄 (如 Jinx, Blitzcrank, Darius) 不應被標記為搖擺位")
        fun `test single role champions are not flex`() {
            val jinxResult = analyzer.analyzeChampion("Jinx")
            assertFalse(jinxResult.isFlex, "Jinx 不應是搖擺位")
            assertEquals(Role.BOT, jinxResult.primaryRole)
            assertTrue(jinxResult.roleProbabilities[Role.BOT]!! >= 0.85)

            val blitzResult = analyzer.analyzeChampion("Blitzcrank")
            assertFalse(blitzResult.isFlex, "Blitzcrank 不應是搖擺位")
            assertEquals(Role.SUPPORT, blitzResult.primaryRole)
            assertTrue(blitzResult.roleProbabilities[Role.SUPPORT]!! >= 0.85)
        }

        @Test
        @DisplayName("查詢不存在或未知英雄時應優雅回退而不拋出異常")
        fun `test unknown champion returns graceful fallback`() {
            val result = analyzer.analyzeChampion("UnknownChampionX")
            assertNotNull(result)
            assertFalse(result.isFlex)
            assertEquals(1.0, result.roleProbabilities.values.sum(), 0.001)
        }
    }

    @Nested
    @DisplayName("2. Contextual Bayesian Role Updating Tests (條件機率動態更新)")
    inner class ContextualUpdatingTests {
        @Test
        @DisplayName("當己方已有鎖定的上路 (Aatrox) 時，藍寶走向 Top 的機率應歸零並彈向 Mid/Jungle")
        fun `test rumble flexes to mid when top is already locked`() {
            val initial = analyzer.analyzeChampion("Rumble")
            assertTrue(initial.roleProbabilities[Role.TOP]!! > 0.0)

            // Conditional update with TOP already claimed
            val updated =
                analyzer.analyzeChampion(
                    championId = "Rumble",
                    teamExistingRoles = setOf(Role.TOP),
                )

            assertEquals(0.0, updated.roleProbabilities[Role.TOP] ?: 0.0, 0.0001, "Top 已被鎖定，藍寶走 Top 機率應為 0")
            assertTrue(updated.roleProbabilities[Role.MID]!! > initial.roleProbabilities[Role.MID]!!, "Mid 機率應提升")
            assertEquals(1.0, updated.roleProbabilities.values.sum(), 0.001, "歸一化後總機率應為 1.0")
        }

        @Test
        @DisplayName("當 Top 與 Mid 皆已鎖定時，藍寶應強制流向 Jungle")
        fun `test rumble forced to jungle when top and mid are locked`() {
            val updated =
                analyzer.analyzeChampion(
                    championId = "Rumble",
                    teamExistingRoles = setOf(Role.TOP, Role.MID),
                )

            assertEquals(0.0, updated.roleProbabilities[Role.TOP] ?: 0.0)
            assertEquals(0.0, updated.roleProbabilities[Role.MID] ?: 0.0)
            assertEquals(1.0, updated.roleProbabilities[Role.JUNGLE] ?: 0.0, 0.001)
        }

        @Test
        @DisplayName("從完整 DraftState 提取戰隊陣容進行全隊搖擺位分析")
        fun `test analyze team draft from draft state`() {
            val draft =
                DraftState
                    .empty()
                    .copy(
                        bluePicks =
                            listOf(
                                PickSelection(championId = "Rumble"),
                                PickSelection(championId = "Poppy"),
                            ),
                    )

            val flexResults = analyzer.analyzeTeamDraft(draft, Side.BLUE)
            assertEquals(2, flexResults.size)
            assertTrue(flexResults.any { it.championId == "Rumble" && it.isFlex })
            assertTrue(flexResults.any { it.championId == "Poppy" && it.isFlex })
        }
    }

    @Nested
    @DisplayName("3. Patch Meta Empirical Distribution Integration (版本數據融合)")
    inner class PatchMetaIntegrationTests {
        @Test
        @DisplayName("版本矩陣中具備實際登場分佈時，應動態校準機率權重")
        fun `test empirical patch meta role distribution updates flex probabilities`() {
            val customMeta =
                PatchMetaMatrix(
                    patch = "14.15",
                    totalGames = 100,
                    championStats =
                        mapOf(
                            "corki" to
                                ChampionMetaStats(
                                    championId = "Corki",
                                    patch = "14.15",
                                    picks = 80,
                                    roleDistribution =
                                        mapOf(
                                            Role.MID to 70,
                                            Role.BOT to 10,
                                        ),
                                    tier = MetaTier.T1,
                                ),
                        ),
                )

            val result = analyzer.analyzeChampion("Corki", patchMeta = customMeta)
            assertTrue(result.roleProbabilities[Role.MID]!! > result.roleProbabilities[Role.BOT]!!)
            assertEquals(1.0, result.roleProbabilities.values.sum(), 0.001)
        }
    }

    @Nested
    @DisplayName("4. Flex Defense Advice Generation (搖擺位防守戰術預警)")
    inner class FlexDefenseAdviceTests {
        @Test
        @DisplayName("敵方鎖定藍寶時，應產生防守戰術提示並推薦雙路覆蓋反制角")
        fun `test generate defense advice when opponent locks rumble`() {
            val draft =
                DraftState.empty().copy(
                    bluePicks = listOf(PickSelection("Rumble")),
                )

            // Red side receives defense advice against Blue's Rumble
            val adviceList = analyzer.generateDefenseAdvice(draft, opponentSide = Side.BLUE)
            assertFalse(adviceList.isEmpty(), "應產出搖擺位防守建議")

            val rumbleAdvice = adviceList.firstOrNull { it.targetChampion == "Rumble" }
            assertNotNull(rumbleAdvice, "應包含針對藍寶的戰術警告")
            assertTrue(
                rumbleAdvice!!.threatLevel == FlexThreatLevel.HIGH ||
                    rumbleAdvice.threatLevel == FlexThreatLevel.CRITICAL,
            )
            assertTrue(rumbleAdvice.tacticalWarnings.isNotEmpty(), "應包含戰術警告訊息")
            assertTrue(rumbleAdvice.counterStrategies.isNotEmpty(), "應包含防守策略")
            assertTrue(rumbleAdvice.recommendedDualCounters.isNotEmpty(), "應推薦可雙路覆蓋的反制選角")
        }

        @Test
        @DisplayName("敵方僅有單一路線英雄 (如 Jinx) 時不應產生高威脅搖擺位防守警告")
        fun `test no flex advice for non flex picks`() {
            val draft =
                DraftState.empty().copy(
                    redPicks = listOf(PickSelection("Jinx")),
                )

            val adviceList = analyzer.generateDefenseAdvice(draft, opponentSide = Side.RED)
            assertTrue(adviceList.isEmpty(), "單一路線英雄不應觸發搖擺位警告")
        }
    }
}
