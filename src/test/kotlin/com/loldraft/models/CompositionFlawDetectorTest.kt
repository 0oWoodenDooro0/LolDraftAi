package com.loldraft.models

import com.loldraft.data.meta.CcTier
import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.CrowdControlRating
import com.loldraft.data.meta.DamageProfile
import com.loldraft.data.meta.DamageType
import com.loldraft.data.meta.DurabilityProfile
import com.loldraft.data.meta.FiveDimensionRadar
import com.loldraft.data.meta.PowerSpikeCurve
import com.loldraft.data.meta.TankinessTier
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

class CompositionFlawDetectorTest {
    private lateinit var registry: ChampionTagRegistry
    private lateinit var detector: CompositionFlawDetector

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
        detector = CompositionFlawDetector(registry)
    }

    @Nested
    @DisplayName("1. Damage Profile Flaw Tests (傷害單一預警)")
    inner class DamageProfileTests {
        @Test
        @DisplayName("全物理菜刀隊在 5 手時應觸發 CRITICAL 傷害失衡預警")
        fun `test full AD comp triggers critical all physical flaw`() {
            // Renekton, Lee Sin, Jayce, Jinx, Jarvan IV (全員高物理輸出)
            val fullAdComp = listOf("Renekton", "Lee Sin", "Jayce", "Jinx", "Jarvan IV")
            val report = detector.detect(fullAdComp, Side.BLUE)

            val damageFlaws = report.getFlawsByCategory(FlawCategory.DAMAGE_PROFILE)
            assertFalse(damageFlaws.isEmpty(), "應該檢測出傷害類型缺陷")

            val allPhysFlaw = damageFlaws.firstOrNull { it.id == "FLAW_ALL_PHYSICAL" }
            assertNotNull(allPhysFlaw, "應包含 FLAW_ALL_PHYSICAL 預警")
            assertEquals(FlawSeverity.CRITICAL, allPhysFlaw!!.severity)
            assertTrue(allPhysFlaw.metrics["physicalRatio"]!! >= 0.85)
            assertTrue(report.hasCriticalFlaws)
            assertTrue(allPhysFlaw.suggestion.isNotBlank())
        }

        @Test
        @DisplayName("3 手純物理陣容應觸發 WARNING 級別預警以提醒後續補 AP")
        fun `test 3 picks full AD triggers warning flaw`() {
            val threePicksAd = listOf("Renekton", "Lee Sin", "Jayce")
            val report = detector.detect(threePicksAd, Side.BLUE)

            val damageFlaws = report.getFlawsByCategory(FlawCategory.DAMAGE_PROFILE)
            assertFalse(damageFlaws.isEmpty())

            val warningFlaw = damageFlaws.firstOrNull { it.id == "FLAW_ALL_PHYSICAL" || it.id == "FLAW_LACK_MAGIC_DAMAGE" }
            assertNotNull(warningFlaw)
            assertEquals(FlawSeverity.WARNING, warningFlaw!!.severity)
            assertEquals(3, warningFlaw.currentPicksCount)
        }

        @Test
        @DisplayName("全法術 AP 陣容應觸發全法傷預警與缺乏物理輸出預警")
        fun `test full AP comp triggers lack physical or all magic flaw`() {
            val fullApComp = listOf("Malphite", "Maokai", "Orianna", "Syndra", "Ahri")
            val report = detector.detect(fullApComp, Side.RED)

            val damageFlaws = report.getFlawsByCategory(FlawCategory.DAMAGE_PROFILE)
            assertFalse(damageFlaws.isEmpty())

            val apFlaw = damageFlaws.firstOrNull { it.id == "FLAW_ALL_MAGIC" || it.id == "FLAW_LACK_PHYSICAL_DAMAGE" }
            assertNotNull(apFlaw)
            assertTrue(apFlaw!!.metrics["magicRatio"]!! >= 0.80)
        }

        @Test
        @DisplayName("雙修混傷均衡陣容不應觸發任何傷害缺陷")
        fun `test balanced damage comp has no damage flaws`() {
            // Aatrox (AD), Sejuani (AP/Tank), Orianna (AP), Jinx (AD), Nautilus (Tank/AP)
            val balancedComp = listOf("Aatrox", "Sejuani", "Orianna", "Jinx", "Nautilus")
            val report = detector.detect(balancedComp, Side.BLUE)

            val damageFlaws = report.getFlawsByCategory(FlawCategory.DAMAGE_PROFILE)
            assertTrue(damageFlaws.isEmpty(), "混傷均衡陣容不應有傷害缺陷: $damageFlaws")
        }
    }

    @Nested
    @DisplayName("2. Engage, Hard CC & Frontline Flaw Tests (開團/硬控/前排缺失)")
    inner class EngageFrontlineTests {
        @Test
        @DisplayName("全脆皮陣容在 5 手時應觸發缺乏前排坦度預警 (CRITICAL)")
        fun `test all squishy comp triggers lack of frontline flaw`() {
            // Jayce (Squishy), LeBlanc (Squishy), Jinx (Squishy), Ahri (Squishy), Syndra (Squishy)
            val squishyComp = listOf("Jayce", "LeBlanc", "Jinx", "Ahri", "Syndra")
            val report = detector.detect(squishyComp, Side.BLUE)

            val flaws = report.getFlawsByCategory(FlawCategory.ENGAGE_FRONTLINE)
            val noFrontline = flaws.firstOrNull { it.id == "FLAW_NO_FRONTLINE" }

            assertNotNull(noFrontline, "應檢測出 FLAW_NO_FRONTLINE")
            assertEquals(FlawSeverity.CRITICAL, noFrontline!!.severity)
            assertTrue(noFrontline.metrics["durabilityScore"]!! < 4.5)
        }

        @Test
        @DisplayName("缺乏可靠硬控陣容應觸發缺乏硬控預警")
        fun `test lack of hard cc triggers no hard cc flaw`() {
            // 註冊無硬控的自訂英雄以精準測試零 CC 情境
            val customRegistry =
                ChampionTagRegistry(
                    listOf(
                        ChampionProfile(
                            championId = "NoCcChamp1",
                            displayName = "NoCcChamp1",
                            primaryRole = Role.TOP,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(0.0, false, CcTier.NONE),
                            durability = DurabilityProfile(5.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(6.0, 3.0, 5.0, 6.0, 6.0),
                        ),
                        ChampionProfile(
                            championId = "NoCcChamp2",
                            displayName = "NoCcChamp2",
                            primaryRole = Role.JUNGLE,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(0.0, false, CcTier.NONE),
                            durability = DurabilityProfile(5.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(6.0, 3.0, 5.0, 6.0, 6.0),
                        ),
                        ChampionProfile(
                            championId = "NoCcChamp3",
                            displayName = "NoCcChamp3",
                            primaryRole = Role.MID,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(0.0, false, CcTier.NONE),
                            durability = DurabilityProfile(5.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(6.0, 3.0, 5.0, 6.0, 6.0),
                        ),
                    ),
                )
            val customDetector = CompositionFlawDetector(customRegistry)
            val report = customDetector.detect(listOf("NoCcChamp1", "NoCcChamp2", "NoCcChamp3"), Side.RED)

            val ccFlaw = report.flaws.firstOrNull { it.id == "FLAW_NO_HARD_CC" }
            assertNotNull(ccFlaw, "應檢測出缺乏硬控 FLAW_NO_HARD_CC")
        }

        @Test
        @DisplayName("重裝開團前排陣容不應觸發前排或開團缺失")
        fun `test robust frontline and engage comp has no frontline flaws`() {
            // K'Sante, Sejuani, Orianna, Jinx, Nautilus (雙坦 + 多重硬控 + 強開團)
            val robustComp = listOf("K'Sante", "Sejuani", "Orianna", "Jinx", "Nautilus")
            val report = detector.detect(robustComp, Side.BLUE)

            val flaws = report.getFlawsByCategory(FlawCategory.ENGAGE_FRONTLINE)
            assertTrue(flaws.isEmpty(), "雙坦強開陣容不應存在開團坦度缺陷: $flaws")
        }
    }

    @Nested
    @DisplayName("3. Waveclear Deficit Flaw Tests (清線防守劣勢)")
    inner class WaveclearTests {
        @Test
        @DisplayName("低清線防禦陣容應觸發清線赤字預警 (WAVECLEAR_DEFICIT)")
        fun `test low waveclear comp triggers waveclear deficit flaw`() {
            val customRegistry =
                ChampionTagRegistry(
                    listOf(
                        ChampionProfile(
                            championId = "LowWave1",
                            displayName = "LowWave1",
                            primaryRole = Role.TOP,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(6.0, 6.0, 5.0, 3.0, 6.0),
                        ),
                        ChampionProfile(
                            championId = "LowWave2",
                            displayName = "LowWave2",
                            primaryRole = Role.JUNGLE,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(6.0, 6.0, 5.0, 3.5, 6.0),
                        ),
                        ChampionProfile(
                            championId = "LowWave3",
                            displayName = "LowWave3",
                            primaryRole = Role.MID,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(5.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(6.0, 6.0, 5.0, 4.0, 6.0),
                        ),
                        ChampionProfile(
                            championId = "LowWave4",
                            displayName = "LowWave4",
                            primaryRole = Role.BOT,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.0, true, CcTier.LIGHT),
                            durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(6.0, 6.0, 5.0, 3.8, 6.0),
                        ),
                        ChampionProfile(
                            championId = "LowWave5",
                            displayName = "LowWave5",
                            primaryRole = Role.SUPPORT,
                            damageProfile = DamageProfile(0.2, 0.8, 0.0, DamageType.MAGIC),
                            ccRating = CrowdControlRating(2.0, true, CcTier.HEAVY),
                            durability = DurabilityProfile(8.0, TankinessTier.FRONTLINE_TANK),
                            radar = FiveDimensionRadar(5.0, 7.0, 6.0, 2.5, 5.0),
                        ),
                    ),
                )
            val customDetector = CompositionFlawDetector(customRegistry)
            val report =
                customDetector.detect(
                    listOf("LowWave1", "LowWave2", "LowWave3", "LowWave4", "LowWave5"),
                    Side.BLUE,
                )

            val waveFlaw = report.getFlawsByCategory(FlawCategory.WAVECLEAR).firstOrNull { it.id == "FLAW_WAVECLEAR_DEFICIT" }
            assertNotNull(waveFlaw, "應檢測出清線赤字 FLAW_WAVECLEAR_DEFICIT")
            assertTrue(waveFlaw!!.metrics["waveclear"]!! < 5.0)
        }

        @Test
        @DisplayName("高清線長手法師與射手不應觸發清線劣勢預警")
        fun `test high waveclear comp has no waveclear flaws`() {
            // Orianna (Waveclear 8.8), Jinx (Waveclear 8.5)
            val goodWaveComp = listOf("Aatrox", "Sejuani", "Orianna", "Jinx", "Nautilus")
            val report = detector.detect(goodWaveComp, Side.BLUE)

            val waveFlaws = report.getFlawsByCategory(FlawCategory.WAVECLEAR)
            assertTrue(waveFlaws.isEmpty())
        }
    }

    @Nested
    @DisplayName("4. Tempo Disconnect & Power Spike Flaw Tests (發力期脫節)")
    inner class TempoDisconnectTests {
        @Test
        @DisplayName("全員前期且後期雷達極低時應觸發前期滾雪球依賴預警")
        fun `test extreme early spike comp triggers extreme early dependency flaw`() {
            val customRegistry =
                ChampionTagRegistry(
                    listOf(
                        ChampionProfile(
                            championId = "Early1",
                            displayName = "Early1",
                            primaryRole = Role.TOP,
                            damageProfile = DamageProfile(0.9, 0.1, 0.0, DamageType.PHYSICAL),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(8.5, 7.0, 5.0, 6.0, 3.5),
                            powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        ),
                        ChampionProfile(
                            championId = "Early2",
                            displayName = "Early2",
                            primaryRole = Role.JUNGLE,
                            damageProfile = DamageProfile(0.8, 0.2, 0.0, DamageType.PHYSICAL),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(6.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(8.5, 7.0, 5.0, 6.0, 3.8),
                            powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        ),
                        ChampionProfile(
                            championId = "Early3",
                            displayName = "Early3",
                            primaryRole = Role.MID,
                            damageProfile = DamageProfile(0.1, 0.9, 0.0, DamageType.MAGIC),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(8.5, 7.0, 5.0, 6.0, 3.5),
                            powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        ),
                        ChampionProfile(
                            championId = "Early4",
                            displayName = "Early4",
                            primaryRole = Role.BOT,
                            damageProfile = DamageProfile(0.9, 0.1, 0.0, DamageType.PHYSICAL),
                            ccRating = CrowdControlRating(1.0, true, CcTier.LIGHT),
                            durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(8.5, 5.0, 5.0, 6.0, 3.8),
                            powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        ),
                        ChampionProfile(
                            championId = "Early5",
                            displayName = "Early5",
                            primaryRole = Role.SUPPORT,
                            damageProfile = DamageProfile(0.3, 0.7, 0.0, DamageType.MAGIC),
                            ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                            durability = DurabilityProfile(8.0, TankinessTier.FRONTLINE_TANK),
                            radar = FiveDimensionRadar(8.0, 8.0, 5.0, 5.0, 4.0),
                            powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        ),
                    ),
                )
            val customDetector = CompositionFlawDetector(customRegistry)
            val report = customDetector.detect(listOf("Early1", "Early2", "Early3", "Early4", "Early5"), Side.RED)

            val tempoFlaw =
                report.getFlawsByCategory(FlawCategory.TEMPO_DISCONNECT).firstOrNull {
                    it.id == "FLAW_EXTREME_EARLY_DEPENDENT"
                }
            assertNotNull(tempoFlaw, "應檢測出 FLAW_EXTREME_EARLY_DEPENDENT")
        }

        @Test
        @DisplayName("全員大後期且前期線路極弱時應觸發前期崩盤風險預警")
        fun `test extreme late scaling comp triggers early laning collapse risk`() {
            val customRegistry =
                ChampionTagRegistry(
                    listOf(
                        ChampionProfile(
                            championId = "Late1",
                            displayName = "Late1",
                            primaryRole = Role.TOP,
                            damageProfile = DamageProfile(0.7, 0.3, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(3.5, 5.0, 5.0, 5.0, 9.5),
                            powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        ),
                        ChampionProfile(
                            championId = "Late2",
                            displayName = "Late2",
                            primaryRole = Role.JUNGLE,
                            damageProfile = DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(6.0, TankinessTier.BRUISER),
                            radar = FiveDimensionRadar(3.5, 5.0, 5.0, 5.0, 9.0),
                            powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        ),
                        ChampionProfile(
                            championId = "Late3",
                            displayName = "Late3",
                            primaryRole = Role.MID,
                            damageProfile = DamageProfile(0.1, 0.9, 0.0, DamageType.MAGIC),
                            ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                            durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(4.0, 5.0, 5.0, 5.0, 9.8),
                            powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        ),
                        ChampionProfile(
                            championId = "Late4",
                            displayName = "Late4",
                            primaryRole = Role.BOT,
                            damageProfile = DamageProfile(0.9, 0.1, 0.0, DamageType.PHYSICAL),
                            ccRating = CrowdControlRating(1.0, true, CcTier.LIGHT),
                            durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(4.0, 4.0, 5.0, 5.0, 9.5),
                            powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        ),
                        ChampionProfile(
                            championId = "Late5",
                            displayName = "Late5",
                            primaryRole = Role.SUPPORT,
                            damageProfile = DamageProfile(0.2, 0.8, 0.0, DamageType.MAGIC),
                            ccRating = CrowdControlRating(2.0, true, CcTier.HEAVY),
                            durability = DurabilityProfile(5.0, TankinessTier.SQUISHY),
                            radar = FiveDimensionRadar(3.8, 6.0, 6.0, 4.5, 9.0),
                            powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        ),
                    ),
                )
            val customDetector = CompositionFlawDetector(customRegistry)
            val report = customDetector.detect(listOf("Late1", "Late2", "Late3", "Late4", "Late5"), Side.BLUE)

            val tempoFlaw =
                report.getFlawsByCategory(FlawCategory.TEMPO_DISCONNECT).firstOrNull {
                    it.id == "FLAW_EXTREME_LATE_SCALING_COLLAPSE"
                }
            assertNotNull(tempoFlaw, "應檢測出 FLAW_EXTREME_LATE_SCALING_COLLAPSE")
        }
    }

    @Nested
    @DisplayName("5. Dynamic Pick Progression Gating Tests (逐手動態階梯式預警)")
    inner class DynamicGatingTests {
        @Test
        @DisplayName("第 1 手不應誤報全物理或無坦等重大缺陷")
        fun `test first pick does not trigger premature flaw warnings`() {
            val onePick = listOf("Renekton") // 雖然純物理，但才第 1 手
            val report = detector.detect(onePick, Side.BLUE)

            assertEquals(0, report.flaws.size, "第 1 手不應觸發結構性缺陷: ${report.flaws}")
            assertFalse(report.hasCriticalFlaws)
            assertEquals(100.0, report.overallHealthScore)
        }

        @Test
        @DisplayName("第 2 手亦不應觸發結構性嚴重缺陷")
        fun `test two picks does not trigger critical flaws`() {
            val twoPicks = listOf("Renekton", "Lee Sin")
            val report = detector.detect(twoPicks, Side.BLUE)

            val criticalFlaws = report.getFlawsBySeverity(FlawSeverity.CRITICAL)
            assertTrue(criticalFlaws.isEmpty(), "前 2 手不應產生 CRITICAL 缺陷")
        }

        @Test
        @DisplayName("3 手純物理陣容觸發 WARNING，至 5 手確認為 CRITICAL")
        fun `test escalation from warning at 3 picks to critical at 5 picks`() {
            val threePicks = listOf("Renekton", "Lee Sin", "Jayce")
            val report3 = detector.detect(threePicks, Side.BLUE)

            val flaw3 = report3.flaws.firstOrNull { it.id == "FLAW_ALL_PHYSICAL" }
            assertNotNull(flaw3, "第 3 手應有 WARNING 菜刀隊提示")
            assertEquals(FlawSeverity.WARNING, flaw3!!.severity)
            assertFalse(report3.hasCriticalFlaws)

            val fivePicks = listOf("Renekton", "Lee Sin", "Jayce", "Jinx", "Jarvan IV")
            val report5 = detector.detect(fivePicks, Side.BLUE)

            val flaw5 = report5.flaws.firstOrNull { it.id == "FLAW_ALL_PHYSICAL" }
            assertNotNull(flaw5, "第 5 手應為 CRITICAL 菜刀隊缺陷")
            assertEquals(FlawSeverity.CRITICAL, flaw5!!.severity)
            assertTrue(report5.hasCriticalFlaws)
        }
    }

    @Nested
    @DisplayName("6. Health Score & Report Aggregation Tests (健康評分與完整報告)")
    inner class HealthScoreTests {
        @Test
        @DisplayName("健康分數隨 CRITICAL 與 WARNING 數量相應扣減且介於 0 與 100 之間")
        fun `test overall health score deduction formula`() {
            val fullAdComp = listOf("Renekton", "Lee Sin", "Jayce", "Jinx", "Jarvan IV")
            val report = detector.detect(fullAdComp, Side.BLUE)

            assertTrue(report.overallHealthScore < 80.0, "嚴重失衡陣容健康分應顯著扣減: ${report.overallHealthScore}")
            assertTrue(report.overallHealthScore in 0.0..100.0)
            assertTrue(report.flawCountByCategory.isNotEmpty())
        }

        @Test
        @DisplayName("空名單應安全回傳 100 分無缺陷報告")
        fun `test empty picks returns clean 100 score report`() {
            val report = detector.detect(emptyList(), Side.BLUE)
            assertEquals(100.0, report.overallHealthScore)
            assertTrue(report.flaws.isEmpty())
            assertFalse(report.hasCriticalFlaws)
        }
    }

    @Nested
    @DisplayName("7. Full Draft State Analysis Tests (全局雙邊對局審計)")
    inner class DraftStateAnalysisTests {
        @Test
        @DisplayName("analyzeDraft 應同時正確診斷藍紅雙方陣容並輸出全局報告")
        fun `test analyzeDraft processes both blue and red teams`() {
            val draftState =
                DraftState(
                    bluePicks =
                        listOf(
                            PickSelection("Renekton"),
                            PickSelection("Lee Sin"),
                            PickSelection("Jayce"),
                            PickSelection("Jinx"),
                            PickSelection("Jarvan IV"),
                        ),
                    redPicks =
                        listOf(
                            PickSelection("Aatrox"),
                            PickSelection("Sejuani"),
                            PickSelection("Orianna"),
                            PickSelection("Varus"),
                            PickSelection("Nautilus"),
                        ),
                )

            val analysis = detector.analyzeDraft(draftState)

            assertEquals(Side.BLUE, analysis.blueReport.side)
            assertEquals(Side.RED, analysis.redReport.side)
            assertTrue(analysis.blueReport.hasCriticalFlaws, "藍方純菜刀隊應有 Critical 缺陷")
            assertFalse(analysis.redReport.hasCriticalFlaws, "紅方均衡陣容不應有 Critical 缺陷")
            assertTrue(analysis.blueReport.overallHealthScore < analysis.redReport.overallHealthScore)
            assertEquals(analysis.blueReport.flaws + analysis.redReport.flaws, analysis.allFlaws)
        }
    }
}
