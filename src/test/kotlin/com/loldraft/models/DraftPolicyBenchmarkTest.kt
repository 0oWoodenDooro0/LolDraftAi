package com.loldraft.models

import com.loldraft.data.meta.ChampionTagRegistry
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
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class DraftPolicyBenchmarkTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var engine: DraftPolicyEngine

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        engine = DraftPolicyEngine(tagRegistry = registry)
    }

    @Test
    @DisplayName("決策速度基準測試：全英雄池候選評估平均延遲應低於 50ms")
    fun `test recommendation latency is under 50ms on average`() {
        val draft =
            DraftState.empty().copy(
                bluePicks =
                    listOf(
                        PickSelection("Renekton", Role.TOP),
                        PickSelection("Lee Sin", Role.JUNGLE),
                    ),
                redPicks =
                    listOf(
                        PickSelection("Aatrox", Role.TOP),
                        PickSelection("Sejuani", Role.JUNGLE),
                    ),
            )

        // Warmup JIT
        repeat(5) {
            engine.recommendPicks(draft, Side.BLUE, limit = 5)
        }

        val iterations = 30
        val totalMs =
            measureTimeMillis {
                repeat(iterations) {
                    val report = engine.recommendPicks(draft, Side.BLUE, limit = 5)
                    assertEquals(5, report.recommendations.size)
                }
            }

        val avgMs = totalMs.toDouble() / iterations
        println("=== Recommendation Speed Benchmark: Average Latency: ${avgMs}ms per call ===")
        assertTrue(
            avgMs < 50.0,
            "平均推薦延遲必須低於 50ms (實際平均: ${avgMs}ms)",
        )
    }

    @Test
    @DisplayName("戰術合理性基準測試：面對全物理脆皮陣容，應推薦反甲坦克 (如 Malphite, K'Sante) 且增益為正")
    fun `test tactical sanity anti ad tank prioritized against full AD squishy team`() {
        // Red team is full AD
        val draft =
            DraftState.empty().copy(
                bluePicks =
                    listOf(
                        PickSelection("Syndra", Role.MID),
                        PickSelection("Jinx", Role.BOT),
                    ),
                redPicks =
                    listOf(
                        PickSelection("Renekton", Role.TOP),
                        PickSelection("Lee Sin", Role.JUNGLE),
                        PickSelection("Jayce", Role.MID),
                        PickSelection("Lucian", Role.BOT),
                    ),
            )

        val report =
            engine.recommendPicks(
                draftState = draft,
                targetSide = Side.BLUE,
                targetRole = Role.TOP,
                limit = 5,
            )

        val topPicks = report.recommendations.map { it.championId }
        // Malphite or K'Sante (armor frontline tanks) should be in the top recommendations
        assertTrue(
            topPicks.contains("Malphite") || topPicks.contains("K'Sante"),
            "面對全物理隊伍時應推薦重裝防禦坦克 (推薦列表: $topPicks)",
        )

        val armorTankRec = report.recommendations.first { it.championId == "Malphite" || it.championId == "K'Sante" }
        assertTrue(
            armorTankRec.winRateGain > 0.0,
            "克制全 AD 的重裝坦克應具備正向勝率增益 (gain: ${armorTankRec.winRateGain})",
        )
    }

    @Test
    @DisplayName("整合引擎測試：DraftPolicyEngine 各功能模組協同運作")
    fun `test draft policy engine end to end coordination`() {
        val draft =
            DraftState.empty().copy(
                bluePicks = listOf(PickSelection("Rumble")),
            )

        // 1. Flex Defense
        val defenseAdvice = engine.defendFlex(draft, opponentSide = Side.BLUE)
        assertFalse(defenseAdvice.isEmpty())
        assertEquals("Rumble", defenseAdvice[0].targetChampion)

        // 2. Intent Prediction
        val intent = engine.predictNextAction(draft, topN = 3)
        assertNotNull(intent)
        assertEquals(3, intent.predictions.size)

        // 3. Flex Analysis
        val flex = engine.analyzeFlex("Poppy")
        assertTrue(flex.isFlex)

        // 4. Recommendation
        val recs = engine.recommendPicks(draft, Side.RED, limit = 3)
        assertEquals(3, recs.recommendations.size)
    }
}
