package com.loldraft.models

import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTag
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PowerSpikeCurve
import com.loldraft.data.meta.TankinessTier
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Side
import kotlin.math.round
import kotlin.math.roundToInt

class CompositionFlawDetector(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val config: FlawDetectionConfig = FlawDetectionConfig(),
) {
    fun detect(
        picks: List<String>,
        side: Side,
    ): CompositionFlawReport {
        if (picks.isEmpty()) {
            return CompositionFlawReport(
                side = side,
                picks = picks,
                flaws = emptyList(),
                hasCriticalFlaws = false,
                flawCountByCategory = emptyMap(),
                overallHealthScore = 100.0,
            )
        }

        // Avoid premature false alerts during early turns (picks 1 and 2)
        if (picks.size < config.minPicksForDamageImbalance) {
            return CompositionFlawReport(
                side = side,
                picks = picks,
                flaws = emptyList(),
                hasCriticalFlaws = false,
                flawCountByCategory = emptyMap(),
                overallHealthScore = 100.0,
            )
        }

        val profiles = picks.mapNotNull { tagRegistry.getProfile(it) }
        val flaws = mutableListOf<CompositionFlaw>()

        checkDamageFlaws(picks, profiles, side, flaws)
        checkEngageAndFrontlineFlaws(picks, profiles, side, flaws)
        checkWaveclearFlaws(picks, profiles, side, flaws)
        checkTempoFlaws(picks, profiles, side, flaws)

        val hasCritical = flaws.any { it.severity == FlawSeverity.CRITICAL }
        val categoryCounts = flaws.groupBy { it.category }.mapValues { it.value.size }

        val criticalCount = flaws.count { it.severity == FlawSeverity.CRITICAL }
        val warningCount = flaws.count { it.severity == FlawSeverity.WARNING }
        val infoCount = flaws.count { it.severity == FlawSeverity.INFO }

        val rawScore = 100.0 - (criticalCount * 25.0) - (warningCount * 10.0) - (infoCount * 3.0)
        val healthScore = roundToSingleDecimal(rawScore.coerceIn(0.0, 100.0))

        return CompositionFlawReport(
            side = side,
            picks = picks,
            flaws = flaws,
            hasCriticalFlaws = hasCritical,
            flawCountByCategory = categoryCounts,
            overallHealthScore = healthScore,
        )
    }

    fun detectSelections(
        selections: List<PickSelection>,
        side: Side,
    ): CompositionFlawReport = detect(selections.map { it.championId }, side)

    fun analyzeDraft(draftState: DraftState): DraftFlawAnalysisResult {
        val blueReport = detectSelections(draftState.bluePicks, Side.BLUE)
        val redReport = detectSelections(draftState.redPicks, Side.RED)
        return DraftFlawAnalysisResult(
            blueReport = blueReport,
            redReport = redReport,
            allFlaws = blueReport.flaws + redReport.flaws,
        )
    }

    private fun checkDamageFlaws(
        picks: List<String>,
        profiles: List<ChampionProfile>,
        side: Side,
        flaws: MutableList<CompositionFlaw>,
    ) {
        val damageSplit = tagRegistry.calculateTeamDamageSplit(picks)
        val pickCount = picks.size

        // 1. All physical / 菜刀隊
        if (damageSplit.physicalRatio >= config.physicalRatioWarningThreshold ||
            damageSplit.magicRatio <= config.magicRatioDeficitThreshold
        ) {
            val severity =
                if (pickCount >= 5 && damageSplit.physicalRatio >= config.physicalRatioCriticalThreshold) {
                    FlawSeverity.CRITICAL
                } else {
                    FlawSeverity.WARNING
                }

            val physPercent = (damageSplit.physicalRatio * 100).roundToInt()
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_ALL_PHYSICAL",
                    category = FlawCategory.DAMAGE_PROFILE,
                    severity = severity,
                    title = "全物理傷害陣容 (菜刀隊)",
                    description = "隊伍物理傷害佔比高達 $physPercent%，缺乏法術傷害威脅，敵方容易堆疊物理防禦裝備進行全面針對。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議在後續選角補充 AP 核心輸出點位（如法師中單或 AP 打野）以平衡傷害屬性。",
                    metrics =
                        mapOf(
                            "physicalRatio" to damageSplit.physicalRatio,
                            "magicRatio" to damageSplit.magicRatio,
                        ),
                ),
            )
        } else if (damageSplit.magicRatio < 0.20 && damageSplit.physicalRatio >= 0.75) {
            // Lack magic damage
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_LACK_MAGIC_DAMAGE",
                    category = FlawCategory.DAMAGE_PROFILE,
                    severity = FlawSeverity.WARNING,
                    title = "缺乏法術傷害 (AP 匱乏)",
                    description = "隊伍法術傷害佔比過低 (${(damageSplit.magicRatio * 100).roundToInt()}%)，容易面臨破甲無力與後期傷害不足。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議優先考量補齊具備高 AP 爆發或混傷輸出的英雄。",
                    metrics = mapOf("magicRatio" to damageSplit.magicRatio),
                ),
            )
        }

        // 2. All magic / 全法傷
        if (damageSplit.magicRatio >= config.magicRatioWarningThreshold ||
            damageSplit.physicalRatio <= 0.15
        ) {
            val severity =
                if (pickCount >= 5 && damageSplit.magicRatio >= config.magicRatioCriticalThreshold) {
                    FlawSeverity.CRITICAL
                } else {
                    FlawSeverity.WARNING
                }

            val magicPercent = (damageSplit.magicRatio * 100).roundToInt()
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_ALL_MAGIC",
                    category = FlawCategory.DAMAGE_PROFILE,
                    severity = severity,
                    title = "全法術傷害陣容",
                    description = "隊伍法術傷害佔比高達 $magicPercent%，缺乏物理輸出與持續推塔能力，易遭魔防道具克制。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議補充物理持續輸出核心（如射手或 AD 戰士/刺客）。",
                    metrics =
                        mapOf(
                            "magicRatio" to damageSplit.magicRatio,
                            "physicalRatio" to damageSplit.physicalRatio,
                        ),
                ),
            )
        } else if (damageSplit.physicalRatio < 0.20 && damageSplit.magicRatio >= 0.75) {
            // Lack physical damage
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_LACK_PHYSICAL_DAMAGE",
                    category = FlawCategory.DAMAGE_PROFILE,
                    severity = FlawSeverity.WARNING,
                    title = "缺乏物理傷害 (AD 匱乏)",
                    description = "隊伍物理傷害佔比過低 (${(damageSplit.physicalRatio * 100).roundToInt()}%)，推塔與吃龍節奏容易受限。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議補充具備穩定物理輸出的射手或物理單帶英雄。",
                    metrics = mapOf("physicalRatio" to damageSplit.physicalRatio),
                ),
            )
        }
    }

    private fun checkEngageAndFrontlineFlaws(
        picks: List<String>,
        profiles: List<ChampionProfile>,
        side: Side,
        flaws: MutableList<CompositionFlaw>,
    ) {
        val pickCount = picks.size
        val avgDurability =
            if (profiles.isEmpty()) 5.0 else profiles.sumOf { it.durability.durabilityScore } / profiles.size
        val frontlineCount = profiles.count { it.durability.tankinessTier == TankinessTier.FRONTLINE_TANK }
        val bruiserCount = profiles.count { it.durability.tankinessTier == TankinessTier.BRUISER }
        val hardCcSeconds = profiles.sumOf { it.ccRating.hardCcDurationSeconds }
        val reliableHardCcCount = profiles.count { it.ccRating.hasReliableHardCc }
        val radar = tagRegistry.calculateTeamRadar(picks)

        // 1. Frontline deficit
        if (frontlineCount == 0 && (bruiserCount == 0 || avgDurability < config.minDurabilityScore)) {
            val severity = if (pickCount >= 5) FlawSeverity.CRITICAL else FlawSeverity.WARNING
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_NO_FRONTLINE",
                    category = FlawCategory.ENGAGE_FRONTLINE,
                    severity = severity,
                    title = "缺乏前排坦度 (全脆皮陣容)",
                    description = "隊伍缺乏重裝前排坦克，平均坦度評分僅 ${roundToTwoDecimals(avgDurability)}，團戰容錯率低且難以承受正面傷害衝擊。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議補強前排坦克或重裝戰士，以承擔陣型第一線吸收傷害與視野防守。",
                    metrics =
                        mapOf(
                            "durabilityScore" to roundToTwoDecimals(avgDurability),
                            "frontlineCount" to frontlineCount.toDouble(),
                        ),
                ),
            )
        }

        // 2. Hard CC deficit
        if (reliableHardCcCount == 0 || (hardCcSeconds < 1.0 && pickCount >= 3)) {
            val severity =
                if (pickCount >= 5 && hardCcSeconds < config.minHardCcDurationSeconds) {
                    FlawSeverity.CRITICAL
                } else {
                    FlawSeverity.WARNING
                }

            flaws.add(
                CompositionFlaw(
                    id = "FLAW_NO_HARD_CC",
                    category = FlawCategory.ENGAGE_FRONTLINE,
                    severity = severity,
                    title = "缺乏先手硬控 (缺乏穩定 CC)",
                    description = "隊伍缺乏穩定先手硬控制技能，累積硬控時長僅 ${roundToTwoDecimals(hardCcSeconds)} 秒，難以中斷敵方突進或鎖定擊殺敵方關鍵目標。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議選擇具備穩定控制或先手暈眩/擊飛技能的英雄（如開團型打野或硬控輔助）。",
                    metrics =
                        mapOf(
                            "hardCcSeconds" to roundToTwoDecimals(hardCcSeconds),
                            "reliableHardCcCount" to reliableHardCcCount.toDouble(),
                        ),
                ),
            )
        }

        // 3. Engage deficit
        val hasHardEngageTag =
            profiles.any {
                it.tags.contains(ChampionTag.HARD_ENGAGE) || it.tags.contains(ChampionTag.VANGUARD_TANK)
            }
        if (radar.engage < config.minEngageScore && !hasHardEngageTag) {
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_LACK_ENGAGE",
                    category = FlawCategory.ENGAGE_FRONTLINE,
                    severity = FlawSeverity.WARNING,
                    title = "缺乏主動開團手段",
                    description = "隊伍主動開團評分僅 ${roundToTwoDecimals(radar.engage)}，缺乏強開手段，對局陷入僵局時難以主動開啟團戰。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議補充具備遠程或強力突進開團能力的英雄，避免陷入被動挨打局面。",
                    metrics = mapOf("engage" to roundToTwoDecimals(radar.engage)),
                ),
            )
        }
    }

    private fun checkWaveclearFlaws(
        picks: List<String>,
        profiles: List<ChampionProfile>,
        side: Side,
        flaws: MutableList<CompositionFlaw>,
    ) {
        val pickCount = picks.size
        val radar = tagRegistry.calculateTeamRadar(picks)
        val hasWaveclearTag =
            profiles.any {
                it.tags.contains(ChampionTag.WAVECLEAR_STALL) || it.tags.contains(ChampionTag.ARTILLERY_MAGE)
            }

        if (radar.waveclear < config.minWaveclearScore && !hasWaveclearTag) {
            val severity = if (pickCount >= 5 && radar.waveclear < 4.5) FlawSeverity.CRITICAL else FlawSeverity.WARNING
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_WAVECLEAR_DEFICIT",
                    category = FlawCategory.WAVECLEAR,
                    severity = severity,
                    title = "清線防守劣勢 (Waveclear Deficit)",
                    description = "隊伍清線能力評分僅 ${roundToTwoDecimals(radar.waveclear)}，缺乏長手或高爆發 AOE 清線防守技能，一旦落後極易被兵線壓制甚至遭推進逼塔。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議補齊長手 AOE 清線英雄（如傳統控制法師或推線型射手）以穩固防線。",
                    metrics = mapOf("waveclear" to roundToTwoDecimals(radar.waveclear)),
                ),
            )
        }
    }

    private fun checkTempoFlaws(
        picks: List<String>,
        profiles: List<ChampionProfile>,
        side: Side,
        flaws: MutableList<CompositionFlaw>,
    ) {
        val pickCount = picks.size
        if (pickCount < 3) return

        val radar = tagRegistry.calculateTeamRadar(picks)
        val earlySpikeCount = profiles.count { it.powerSpike == PowerSpikeCurve.EARLY_SPIKE }
        val lateSpikeCount =
            profiles.count {
                it.powerSpike == PowerSpikeCurve.LATE_GAME_SPIKE || it.powerSpike == PowerSpikeCurve.HYPER_SCALING
            }

        // 1. Extreme early dependent
        if (earlySpikeCount >= 3 && radar.lateGameScaling < config.minLateScalingScoreForEarlyComp && lateSpikeCount == 0) {
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_EXTREME_EARLY_DEPENDENT",
                    category = FlawCategory.TEMPO_DISCONNECT,
                    severity = FlawSeverity.WARNING,
                    title = "前期節奏容錯率低 (依賴早期滾雪球)",
                    description = "陣容發力期高度集中於前期，後期成長評分僅 ${roundToTwoDecimals(radar.lateGameScaling)}。若在 25 分鐘前未能建立巨大領先，進入後期勝率將急劇下跌。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "需要前中期積極爭奪小龍與先鋒加快推塔節奏，或補充至少一名具備後期保障的終結點。",
                    metrics =
                        mapOf(
                            "earlySpikeCount" to earlySpikeCount.toDouble(),
                            "lateGameScaling" to roundToTwoDecimals(radar.lateGameScaling),
                        ),
                ),
            )
        }

        // 2. Extreme late scaling collapse risk
        if (lateSpikeCount >= 3 && radar.laningStrength < config.minLaningScoreForLateComp) {
            flaws.add(
                CompositionFlaw(
                    id = "FLAW_EXTREME_LATE_SCALING_COLLAPSE",
                    category = FlawCategory.TEMPO_DISCONNECT,
                    severity = FlawSeverity.WARNING,
                    title = "陣容發育期過長 (前期對線崩盤風險)",
                    description = "隊伍過多選角依賴裝備成型，前期對線強度僅 ${roundToTwoDecimals(radar.laningStrength)}，在 15 分鐘前極易失去線權、野區遭入侵與資源受制。",
                    affectedSide = side,
                    currentPicksCount = pickCount,
                    suggestion = "建議前三手至少鎖定一至二條強勢線路英雄以保障野區節奏與防禦塔血量。",
                    metrics =
                        mapOf(
                            "lateSpikeCount" to lateSpikeCount.toDouble(),
                            "laningStrength" to roundToTwoDecimals(radar.laningStrength),
                        ),
                ),
            )
        }
    }

    private fun roundToTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0

    private fun roundToSingleDecimal(value: Double): Double = round(value * 10.0) / 10.0
}
