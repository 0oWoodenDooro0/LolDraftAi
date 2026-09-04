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

interface DraftEvaluator : AutoCloseable {
    fun evaluate(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): DraftEvaluationResult

    fun evaluateBatch(
        draftStates: List<DraftState>,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): List<DraftEvaluationResult> = draftStates.map { evaluate(it, patchMeta, blueTeamProfile, redTeamProfile) }

    override fun close() {}
}

class AnalyticalDraftEvaluator(
    private val featureExtractor: DraftFeatureExtractor = DraftFeatureExtractor(),
    private val flawDetector: CompositionFlawDetector = CompositionFlawDetector(featureExtractor.tagRegistry),
    private val timeCurveCalculator: TimeCurveCalculator = TimeCurveCalculator(featureExtractor.tagRegistry),
) : DraftEvaluator {
    override fun evaluate(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
    ): DraftEvaluationResult {
        val features = featureExtractor.extract(draftState, patchMeta, blueTeamProfile, redTeamProfile)
        return evaluateFromFeatures(features, draftState, patchMeta, blueTeamProfile, redTeamProfile)
    }

    override fun evaluateBatch(
        draftStates: List<DraftState>,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
    ): List<DraftEvaluationResult> = draftStates.map { evaluate(it, patchMeta, blueTeamProfile, redTeamProfile) }

    fun evaluateFromFeatures(
        features: DraftFeatures,
        draftState: DraftState? = null,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
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
        val durabilityImpact = (features.durabilityDelta * 0.04)
        val ccImpact = (features.ccDelta * 0.05)
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
        val metaTierImpact = (features.metaTierDelta * 0.15)
        val metaWinRateImpact = (features.metaWinRateDelta * 1.5)
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
        val flaws = draftState?.let { flawDetector.analyzeDraft(it) }

        val evalBar = EvalBarCalculator.calculate(blueWinRate)
        val timeCurve =
            timeCurveCalculator.calculate(
                draftState = draftState ?: DraftState(),
                features = features,
                baselineBlueWinRate = blueWinRate,
                patchMeta = patchMeta,
                blueTeamProfile = blueTeamProfile,
                redTeamProfile = redTeamProfile,
            )
        val compositionRadar =
            CompositionRadarCalculator.calculate(
                blueRadar = features.blueRadar,
                redRadar = features.redRadar,
                blueDamageProfile = features.blueDamageProfile,
                redDamageProfile = features.redDamageProfile,
            )

        return DraftEvaluationResult(
            blueWinRate = blueWinRate,
            redWinRate = redWinRate,
            evalScore = evalBar.score,
            confidence = confidence,
            dominantFactors = sortedFactors,
            features = features,
            flaws = flaws,
            evalBar = evalBar,
            timeCurve = timeCurve,
            compositionRadar = compositionRadar,
        )
    }
}

class DraftValueEvaluator(
    val featureExtractor: DraftFeatureExtractor = DraftFeatureExtractor(),
    val modelPath: String? = null,
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(featureExtractor.tagRegistry),
    val timeCurveCalculator: TimeCurveCalculator = TimeCurveCalculator(featureExtractor.tagRegistry),
    val fallbackEvaluator: DraftEvaluator =
        AnalyticalDraftEvaluator(featureExtractor, flawDetector, timeCurveCalculator),
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

    val isOnnxLoaded: Boolean
        get() = session != null

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
                val inputFloats = if (features.empiricalValues.isNotEmpty()) features.empiricalValues else features.values
                val inputBuffer = FloatBuffer.wrap(inputFloats)
                val shape = longArrayOf(1, inputFloats.size.toLong())
                val tensor = OnnxTensor.createTensor(currentEnv, inputBuffer, shape)

                tensor.use { onnxTensor ->
                    val inputName = currentSession.inputNames.iterator().next()
                    val results = currentSession.run(mapOf(inputName to onnxTensor))

                    results.use { ortResults ->
                        val outputValue = if (ortResults.size() > 1) ortResults.get(1).value else ortResults.get(0).value
                        val prob = extractProbability(outputValue)
                        if (prob != null) {
                            val boundedProb = prob.coerceIn(0.01, 0.99)
                            val baseLogit = ln(boundedProb / (1.0 - boundedProb))

                            val metaTierImpact = if (patchMeta != null) (features.metaTierDelta * 0.15) else 0.0
                            val metaWinRateImpact = if (patchMeta != null) (features.metaWinRateDelta * 1.5) else 0.0
                            val teamRatingImpact = if (blueTeamProfile != null || redTeamProfile != null) (features.teamRatingDelta * 0.25) else 0.0
                            val earlyDominanceImpact = if (blueTeamProfile != null || redTeamProfile != null) (features.earlyDominanceDelta * 0.03) else 0.0

                            val totalLogit = baseLogit + metaTierImpact + metaWinRateImpact + teamRatingImpact + earlyDominanceImpact
                            val blueWinRate = (1.0 / (1.0 + exp(-totalLogit))).coerceIn(0.01, 0.99)
                            val redWinRate = 1.0 - blueWinRate
                            val evalBar = EvalBarCalculator.calculate(blueWinRate)
                            val evalScore = evalBar.score
                            val totalPicks = draftState.bluePicks.size + draftState.redPicks.size
                            val confidence = (totalPicks / 10.0).coerceIn(0.1, 1.0)
                            val analytical =
                                (fallbackEvaluator as? AnalyticalDraftEvaluator)
                                    ?.evaluateFromFeatures(features, draftState, patchMeta, blueTeamProfile, redTeamProfile)
                            val flaws = flawDetector.analyzeDraft(draftState)

                            val timeCurve =
                                timeCurveCalculator.calculate(
                                    draftState = draftState,
                                    features = features,
                                    baselineBlueWinRate = blueWinRate,
                                    patchMeta = patchMeta,
                                    blueTeamProfile = blueTeamProfile,
                                    redTeamProfile = redTeamProfile,
                                )
                            val compositionRadar =
                                CompositionRadarCalculator.calculate(
                                    blueRadar = features.blueRadar,
                                    redRadar = features.redRadar,
                                    blueDamageProfile = features.blueDamageProfile,
                                    redDamageProfile = features.redDamageProfile,
                                )

                            return DraftEvaluationResult(
                                blueWinRate = blueWinRate,
                                redWinRate = redWinRate,
                                evalScore = evalScore,
                                confidence = confidence,
                                dominantFactors = analytical?.dominantFactors ?: emptyList(),
                                features = features,
                                flaws = flaws,
                                evalBar = evalBar,
                                timeCurve = timeCurve,
                                compositionRadar = compositionRadar,
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

    override fun evaluateBatch(
        draftStates: List<DraftState>,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
    ): List<DraftEvaluationResult> {
        if (draftStates.isEmpty()) return emptyList()

        val currentSession = session
        val currentEnv = environment

        if (currentSession != null && currentEnv != null) {
            try {
                val batchSize = draftStates.size
                val allFeatures =
                    draftStates.map {
                        featureExtractor.extract(it, patchMeta, blueTeamProfile, redTeamProfile)
                    }
                val sampleFloats = allFeatures.first().let { if (it.empiricalValues.isNotEmpty()) it.empiricalValues else it.values }
                val featureDim = sampleFloats.size
                val totalFloats = batchSize * featureDim
                val inputBuffer = FloatBuffer.allocate(totalFloats)
                for (feat in allFeatures) {
                    val arr = if (feat.empiricalValues.isNotEmpty()) feat.empiricalValues else feat.values
                    inputBuffer.put(arr)
                }
                inputBuffer.flip()

                val shape = longArrayOf(batchSize.toLong(), featureDim.toLong())
                val tensor = OnnxTensor.createTensor(currentEnv, inputBuffer, shape)

                tensor.use { onnxTensor ->
                    val inputName = currentSession.inputNames.iterator().next()
                    val results = currentSession.run(mapOf(inputName to onnxTensor))

                    results.use { ortResults ->
                        val outputValue = if (ortResults.size() > 1) ortResults.get(1).value else ortResults.get(0).value
                        val probs = extractBatchProbabilities(outputValue, batchSize)
                        if (probs != null && probs.size == batchSize) {
                            return draftStates.indices.map { i ->
                                val draft = draftStates[i]
                                val features = allFeatures[i]
                                val boundedProb = probs[i].coerceIn(0.01, 0.99)
                                val baseLogit = ln(boundedProb / (1.0 - boundedProb))

                                val metaTierImpact = if (patchMeta != null) (features.metaTierDelta * 0.15) else 0.0
                                val metaWinRateImpact = if (patchMeta != null) (features.metaWinRateDelta * 1.5) else 0.0
                                val teamRatingImpact = if (blueTeamProfile != null || redTeamProfile != null) (features.teamRatingDelta * 0.25) else 0.0
                                val earlyDominanceImpact = if (blueTeamProfile != null || redTeamProfile != null) (features.earlyDominanceDelta * 0.03) else 0.0

                                val totalLogit = baseLogit + metaTierImpact + metaWinRateImpact + teamRatingImpact + earlyDominanceImpact
                                val blueWinRate = (1.0 / (1.0 + exp(-totalLogit))).coerceIn(0.01, 0.99)
                                val redWinRate = 1.0 - blueWinRate
                                val evalBar = EvalBarCalculator.calculate(blueWinRate)
                                val evalScore = evalBar.score
                                val totalPicks = draft.bluePicks.size + draft.redPicks.size
                                val confidence = (totalPicks / 10.0).coerceIn(0.1, 1.0)
                                val analytical =
                                    (fallbackEvaluator as? AnalyticalDraftEvaluator)
                                        ?.evaluateFromFeatures(features, draft, patchMeta, blueTeamProfile, redTeamProfile)
                                val flaws = flawDetector.analyzeDraft(draft)

                                val timeCurve =
                                    timeCurveCalculator.calculate(
                                        draftState = draft,
                                        features = features,
                                        baselineBlueWinRate = blueWinRate,
                                        patchMeta = patchMeta,
                                        blueTeamProfile = blueTeamProfile,
                                        redTeamProfile = redTeamProfile,
                                    )
                                val compositionRadar =
                                    CompositionRadarCalculator.calculate(
                                        blueRadar = features.blueRadar,
                                        redRadar = features.redRadar,
                                        blueDamageProfile = features.blueDamageProfile,
                                        redDamageProfile = features.redDamageProfile,
                                    )

                                DraftEvaluationResult(
                                    blueWinRate = blueWinRate,
                                    redWinRate = redWinRate,
                                    evalScore = evalScore,
                                    confidence = confidence,
                                    dominantFactors = analytical?.dominantFactors ?: emptyList(),
                                    features = features,
                                    flaws = flaws,
                                    evalBar = evalBar,
                                    timeCurve = timeCurve,
                                    compositionRadar = compositionRadar,
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fall back to sequential evaluation if batch tensor fails
            }
        }

        return draftStates.map { evaluate(it, patchMeta, blueTeamProfile, redTeamProfile) }
    }

    override fun close() {
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        try {
            environment?.close()
        } catch (_: Throwable) {
        }
    }

    private fun extractProbability(outputValue: Any?): Double? =
        when (outputValue) {
            is Array<*> -> {
                when (val first = outputValue.firstOrNull()) {
                    is FloatArray -> if (first.size >= 2) first[1].toDouble() else first.getOrNull(0)?.toDouble()
                    is DoubleArray -> if (first.size >= 2) first[1] else first.getOrNull(0)
                    is Array<*> -> {
                        if (first.size >= 2) (first[1] as? Number)?.toDouble() else (first.firstOrNull() as? Number)?.toDouble()
                    }
                    is Number -> first.toDouble()
                    else -> null
                }
            }
            is FloatArray -> if (outputValue.size >= 2) outputValue[1].toDouble() else outputValue.getOrNull(0)?.toDouble()
            is DoubleArray -> if (outputValue.size >= 2) outputValue[1] else outputValue.getOrNull(0)
            is Number -> outputValue.toDouble()
            else -> null
        }

    private fun extractBatchProbabilities(
        outputValue: Any?,
        batchSize: Int,
    ): List<Double>? {
        if (outputValue is Array<*>) {
            val list = mutableListOf<Double>()
            for (row in outputValue) {
                val prob =
                    when (row) {
                        is FloatArray -> if (row.size >= 2) row[1].toDouble() else row.getOrNull(0)?.toDouble()
                        is DoubleArray -> if (row.size >= 2) row[1] else row.getOrNull(0)
                        is Number -> row.toDouble()
                        else -> null
                    } ?: return null
                list.add(prob)
            }
            return if (list.size == batchSize) list else null
        }
        if (outputValue is FloatArray && outputValue.size == batchSize) {
            return outputValue.map { it.toDouble() }
        }
        if (outputValue is DoubleArray && outputValue.size == batchSize) {
            return outputValue.toList()
        }
        return null
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
