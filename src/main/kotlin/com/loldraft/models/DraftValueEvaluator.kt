package com.loldraft.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.style.TeamTacticalProfile
import java.io.File
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

interface DraftEvaluator {
    fun evaluate(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): DraftEvaluationResult
}

class AnalyticalDraftEvaluator(
    private val featureExtractor: DraftFeatureExtractor = DraftFeatureExtractor(),
) : DraftEvaluator {
    override fun evaluate(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
    ): DraftEvaluationResult {
        val features = featureExtractor.extract(draftState, patchMeta, blueTeamProfile, redTeamProfile)
        return evaluateFromFeatures(features, draftState)
    }

    fun evaluateFromFeatures(
        features: DraftFeatures,
        draftState: DraftState? = null,
    ): DraftEvaluationResult {
        val totalPicks = (draftState?.bluePicks?.size ?: 5) + (draftState?.redPicks?.size ?: 5)
        val confidence = (totalPicks / 10.0).coerceIn(0.1, 1.0)

        val factorContributions = mutableListOf<EvaluationFactor>()

        // 1. Radar contribution
        val radarLaningImpact = features.radarDelta.laningStrength * 0.08
        val radarLateImpact = features.radarDelta.lateGameScaling * 0.07
        val radarEngageImpact = features.radarDelta.engage * 0.05
        val totalRadarImpact = radarLaningImpact + radarLateImpact + radarEngageImpact
        if (abs(totalRadarImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Radar Dimension Advantage",
                    category = "RADAR",
                    impact = totalRadarImpact,
                    description =
                        if (totalRadarImpact > 0) {
                            "Blue has superior lane control & scaling (+${(totalRadarImpact * 100).roundToInt() / 100.0})"
                        } else {
                            "Red has superior lane control & scaling (${(totalRadarImpact * 100).roundToInt() / 100.0})"
                        },
                ),
            )
        }

        // 2. Durability & CC contribution
        val durabilityImpact = (features.values[23] * 0.04).toDouble() // delta_durability
        val ccImpact = (features.values[26] * 0.05).toDouble() // delta_cc_score
        val defenseCcImpact = durabilityImpact + ccImpact
        if (abs(defenseCcImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Frontline & Crowd Control",
                    category = "DURABILITY_CC",
                    impact = defenseCcImpact,
                    description = "Frontline durability and hard CC disparity",
                ),
            )
        }

        // 3. Patch Meta contribution
        val metaTierImpact = (features.values[29] * 0.15).toDouble() // delta_meta_tier
        val metaWinRateImpact = (features.values[32] * 1.5).toDouble() // delta_meta_winrate
        val totalMetaImpact = metaTierImpact + metaWinRateImpact
        if (abs(totalMetaImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Patch Meta Tier & Win Rate",
                    category = "META",
                    impact = totalMetaImpact,
                    description =
                        if (totalMetaImpact > 0) {
                            "Blue drafted higher tier meta champions"
                        } else {
                            "Red drafted higher tier meta champions"
                        },
                ),
            )
        }

        // 4. Synergy contribution
        val synergyImpact = (features.synergyDelta * 0.12)
        if (abs(synergyImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Teammate Synergy",
                    category = "SYNERGY",
                    impact = synergyImpact,
                    description = "Champion kit synergy and combo effectiveness",
                ),
            )
        }

        // 5. Matchup counter contribution
        val matchupImpact = (features.matchupDelta * 0.10)
        if (abs(matchupImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Lane Matchup Advantage",
                    category = "MATCHUP",
                    impact = matchupImpact,
                    description = "Direct lane counter and matchup win rate advantage",
                ),
            )
        }

        // 6. Team rating & dominance
        val teamRatingImpact = (features.teamRatingDelta * 1.6)
        val dominanceImpact = (features.earlyDominanceDelta * 0.05)
        val totalTeamImpact = teamRatingImpact + dominanceImpact
        if (abs(totalTeamImpact) > 0.01) {
            factorContributions.add(
                EvaluationFactor(
                    name = "Team Historical Power",
                    category = "TEAM",
                    impact = totalTeamImpact,
                    description = "Overall team strength and early dominance track record",
                ),
            )
        }

        // 7. Side advantage
        val sideAdvantageImpact = features.sideAdvantage * 3.5
        factorContributions.add(
            EvaluationFactor(
                name = "Blue Side Advantage",
                category = "SIDE",
                impact = sideAdvantageImpact,
                description = "Base blue side first-pick advantage",
            ),
        )

        val logit =
            totalRadarImpact +
                defenseCcImpact +
                totalMetaImpact +
                synergyImpact +
                matchupImpact +
                totalTeamImpact +
                sideAdvantageImpact

        val rawWinRate = 1.0 / (1.0 + exp(-logit))
        val blueWinRate = rawWinRate.coerceIn(0.01, 0.99)
        val redWinRate = 1.0 - blueWinRate

        val sortedFactors = factorContributions.sortedByDescending { abs(it.impact) }

        return DraftEvaluationResult(
            blueWinRate = blueWinRate,
            redWinRate = redWinRate,
            evalScore = logit,
            confidence = confidence,
            dominantFactors = sortedFactors,
            features = features,
        )
    }
}

class DraftValueEvaluator(
    val featureExtractor: DraftFeatureExtractor = DraftFeatureExtractor(),
    val modelPath: String? = null,
    val fallbackEvaluator: DraftEvaluator = AnalyticalDraftEvaluator(featureExtractor),
) : DraftEvaluator {
    private val session: OrtSession?
    private val environment: OrtEnvironment?

    init {
        var env: OrtEnvironment? = null
        var sess: OrtSession? = null

        try {
            val modelBytes = loadModelBytes(modelPath)
            if (modelBytes != null) {
                env = OrtEnvironment.getEnvironment()
                sess = env.createSession(modelBytes)
            }
        } catch (_: Throwable) {
            sess = null
            env = null
        }

        this.environment = env
        this.session = sess
    }

    override fun evaluate(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
    ): DraftEvaluationResult {
        val features = featureExtractor.extract(draftState, patchMeta, blueTeamProfile, redTeamProfile)

        val currentSession = session
        val currentEnv = environment

        if (currentSession != null && currentEnv != null) {
            try {
                val inputBuffer = FloatBuffer.wrap(features.values)
                val shape = longArrayOf(1, DraftFeatures.FEATURE_COUNT.toLong())
                val tensor = OnnxTensor.createTensor(currentEnv, inputBuffer, shape)

                tensor.use { onnxTensor ->
                    val inputName = currentSession.inputNames.iterator().next()
                    val results = currentSession.run(mapOf(inputName to onnxTensor))

                    results.use { ortResults ->
                        val outputValue = ortResults.get(0).value
                        val prob = extractProbability(outputValue)
                        if (prob != null) {
                            val blueWinRate = prob.coerceIn(0.01, 0.99)
                            val redWinRate = 1.0 - blueWinRate
                            val evalScore = ln(blueWinRate / redWinRate)
                            val totalPicks = draftState.bluePicks.size + draftState.redPicks.size
                            val confidence = (totalPicks / 10.0).coerceIn(0.1, 1.0)
                            val analytical = (fallbackEvaluator as? AnalyticalDraftEvaluator)?.evaluateFromFeatures(features, draftState)

                            return DraftEvaluationResult(
                                blueWinRate = blueWinRate,
                                redWinRate = redWinRate,
                                evalScore = evalScore,
                                confidence = confidence,
                                dominantFactors = analytical?.dominantFactors ?: emptyList(),
                                features = features,
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fall back gracefully if inference fails
            }
        }

        return fallbackEvaluator.evaluate(draftState, patchMeta, blueTeamProfile, redTeamProfile)
    }

    private fun extractProbability(outputValue: Any?): Double? =
        when (outputValue) {
            is Array<*> -> {
                when (val first = outputValue.firstOrNull()) {
                    is FloatArray -> first.getOrNull(0)?.toDouble()
                    is DoubleArray -> first.getOrNull(0)
                    is Array<*> -> (first.firstOrNull() as? Number)?.toDouble()
                    is Number -> first.toDouble()
                    else -> null
                }
            }
            is FloatArray -> outputValue.getOrNull(0)?.toDouble()
            is DoubleArray -> outputValue.getOrNull(0)
            is Number -> outputValue.toDouble()
            else -> null
        }

    private fun loadModelBytes(path: String?): ByteArray? {
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                return file.readBytes()
            }
            return null
        }

        // Try classpath resource
        val resourceStream: InputStream? = javaClass.getResourceAsStream("/models/draft_value_model.onnx")
        if (resourceStream != null) {
            return resourceStream.use { it.readBytes() }
        }

        // Try default relative path
        val defaultFile = File("src/main/resources/models/draft_value_model.onnx")
        if (defaultFile.exists() && defaultFile.isFile) {
            return defaultFile.readBytes()
        }

        return null
    }
}
