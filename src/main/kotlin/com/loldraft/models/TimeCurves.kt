package com.loldraft.models

import com.loldraft.data.meta.ChampionTag
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.meta.PowerSpikeCurve
import com.loldraft.data.models.DraftState
import com.loldraft.data.style.TeamTacticalProfile
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

@Serializable
enum class MatchPhase {
    EARLY_GAME,
    MID_GAME,
    LATE_GAME,
    ULTRA_LATE,
}

@Serializable
data class TimePointWinRate(
    val minute: Int,
    val blueWinRate: Double,
    val redWinRate: Double,
    val evalBar: EvalBarScore,
    val dominantPhase: MatchPhase,
    val keyFactors: List<String> = emptyList(),
)

@Serializable
data class TimeCurve(
    val points: List<TimePointWinRate>,
    val earlyGameWinRate: Double,
    val midGameWinRate: Double,
    val lateGameWinRate: Double,
    val ultraLateWinRate: Double,
    val trajectorySummary: String,
)

class TimeCurveCalculator(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val evalBarScale: Double = EvalBarCalculator.DEFAULT_SCALE,
) {
    companion object {
        val TIME_INTERVALS: List<Int> = listOf(10, 15, 20, 25, 30, 35, 40)
        private const val EPSILON: Double = 1e-6
    }

    fun calculate(
        draftState: DraftState,
        features: DraftFeatures,
        baselineBlueWinRate: Double,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): TimeCurve {
        val safeBaseP = baselineBlueWinRate.coerceIn(0.01, 0.99)
        val baseLogit = ln(safeBaseP / (1.0 - safeBaseP))

        val blueProfiles = draftState.bluePicks.mapNotNull { tagRegistry.getProfile(it.championId) }
        val redProfiles = draftState.redPicks.mapNotNull { tagRegistry.getProfile(it.championId) }

        val blueEarlyCount =
            blueProfiles.count {
                it.powerSpike == PowerSpikeCurve.EARLY_SPIKE || it.tags.contains(ChampionTag.EARLY_BULLY)
            }
        val redEarlyCount =
            redProfiles.count {
                it.powerSpike == PowerSpikeCurve.EARLY_SPIKE || it.tags.contains(ChampionTag.EARLY_BULLY)
            }
        val deltaEarlyBully = blueEarlyCount - redEarlyCount

        val blueLateCount =
            blueProfiles.count {
                it.powerSpike == PowerSpikeCurve.HYPER_SCALING ||
                    it.powerSpike == PowerSpikeCurve.LATE_GAME_SPIKE ||
                    it.tags.contains(ChampionTag.HYPER_CARRY)
            }
        val redLateCount =
            redProfiles.count {
                it.powerSpike == PowerSpikeCurve.HYPER_SCALING ||
                    it.powerSpike == PowerSpikeCurve.LATE_GAME_SPIKE ||
                    it.tags.contains(ChampionTag.HYPER_CARRY)
            }
        val deltaHyperCarry = blueLateCount - redLateCount

        val deltaLaning = features.radarDelta.laningStrength
        val deltaLateScaling = features.radarDelta.lateGameScaling
        val deltaDominance = features.earlyDominanceDelta
        val deltaMatchup = features.matchupDelta
        val deltaDurability = features.values[23].toDouble()
        val deltaCc = features.values[26].toDouble()

        val earlySignal =
            deltaLaning * 0.12 +
                deltaEarlyBully * 0.10 +
                deltaDominance * 0.04 +
                deltaMatchup * 0.08

        val lateSignal =
            deltaLateScaling * 0.12 +
                deltaHyperCarry * 0.10 +
                deltaDurability * 0.03 +
                deltaCc * 0.03

        val points = mutableListOf<TimePointWinRate>()

        for (minute in TIME_INTERVALS) {
            val wEarly = 1.0 / (1.0 + exp((minute - 16.0) / 4.0))
            val wLate = 1.0 / (1.0 + exp(-(minute - 27.0) / 4.5))

            val shift = earlySignal * (wEarly - 0.35) + lateSignal * (wLate - 0.35)
            val logit = baseLogit + shift
            val rawBlue = 1.0 / (1.0 + exp(-logit))
            val blueWinRate = roundToFourDecimals(rawBlue.coerceIn(0.01, 0.99))
            val redWinRate = roundToFourDecimals(1.0 - blueWinRate)

            val evalBar = EvalBarCalculator.calculate(blueWinRate, scale = evalBarScale)
            val phase = resolveMatchPhase(minute)
            val factors = extractKeyFactors(minute, phase, deltaLaning, deltaEarlyBully, deltaLateScaling, deltaHyperCarry)

            points.add(
                TimePointWinRate(
                    minute = minute,
                    blueWinRate = blueWinRate,
                    redWinRate = redWinRate,
                    evalBar = evalBar,
                    dominantPhase = phase,
                    keyFactors = factors,
                ),
            )
        }

        val min10 = points.find { it.minute == 10 }?.blueWinRate ?: safeBaseP
        val min15 = points.find { it.minute == 15 }?.blueWinRate ?: safeBaseP
        val min20 = points.find { it.minute == 20 }?.blueWinRate ?: safeBaseP
        val min25 = points.find { it.minute == 25 }?.blueWinRate ?: safeBaseP
        val min30 = points.find { it.minute == 30 }?.blueWinRate ?: safeBaseP
        val min35 = points.find { it.minute == 35 }?.blueWinRate ?: safeBaseP
        val min40 = points.find { it.minute == 40 }?.blueWinRate ?: safeBaseP

        val earlyGameWinRate = roundToFourDecimals((min10 + min15) / 2.0)
        val midGameWinRate = roundToFourDecimals((min20 + min25) / 2.0)
        val lateGameWinRate = roundToFourDecimals((min30 + min35) / 2.0)
        val ultraLateWinRate = roundToFourDecimals(min40)

        val summary = generateTrajectorySummary(earlyGameWinRate, lateGameWinRate)

        return TimeCurve(
            points = points,
            earlyGameWinRate = earlyGameWinRate,
            midGameWinRate = midGameWinRate,
            lateGameWinRate = lateGameWinRate,
            ultraLateWinRate = ultraLateWinRate,
            trajectorySummary = summary,
        )
    }

    private fun resolveMatchPhase(minute: Int): MatchPhase =
        when {
            minute <= 15 -> MatchPhase.EARLY_GAME
            minute <= 25 -> MatchPhase.MID_GAME
            minute <= 35 -> MatchPhase.LATE_GAME
            else -> MatchPhase.ULTRA_LATE
        }

    private fun extractKeyFactors(
        minute: Int,
        phase: MatchPhase,
        laningDelta: Double,
        earlyBullyDelta: Int,
        lateScalingDelta: Double,
        hyperCarryDelta: Int,
    ): List<String> {
        val factors = mutableListOf<String>()
        when (phase) {
            MatchPhase.EARLY_GAME -> {
                if (laningDelta > 0.5) factors.add("Blue laning strength priority")
                if (laningDelta < -0.5) factors.add("Red laning strength priority")
                if (earlyBullyDelta > 0) factors.add("Blue early bully spike aggression")
                if (earlyBullyDelta < 0) factors.add("Red early bully spike aggression")
                if (factors.isEmpty()) factors.add("Evenly matched laning phase")
            }
            MatchPhase.MID_GAME -> {
                factors.add("Objective contest and tower transition")
            }
            MatchPhase.LATE_GAME, MatchPhase.ULTRA_LATE -> {
                if (lateScalingDelta > 0.5) factors.add("Blue hyper-scaling teamfight advantage")
                if (lateScalingDelta < -0.5) factors.add("Red hyper-scaling teamfight advantage")
                if (hyperCarryDelta > 0) factors.add("Blue carries item spikes reached")
                if (hyperCarryDelta < 0) factors.add("Red carries item spikes reached")
                if (factors.isEmpty()) factors.add("Late game 5v5 teamfight execution")
            }
        }
        return factors
    }

    private fun generateTrajectorySummary(
        earlyWinRate: Double,
        lateWinRate: Double,
    ): String =
        when {
            earlyWinRate >= 0.52 && lateWinRate <= 0.48 -> "Early Bully -> Late Falloff"
            earlyWinRate <= 0.48 && lateWinRate >= 0.52 -> "Early Deficit -> Late Scaling Inversion"
            earlyWinRate >= 0.52 && lateWinRate >= 0.52 -> "Sustained Blue Composition Dominance"
            earlyWinRate <= 0.48 && lateWinRate <= 0.48 -> "Sustained Red Composition Dominance"
            else -> "Evenly Contested Trajectory"
        }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}
