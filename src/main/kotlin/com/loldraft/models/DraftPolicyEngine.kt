package com.loldraft.models

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.style.TeamTacticalProfile

class DraftPolicyEngine(
    val evaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(tagRegistry, flexAnalyzer, flawDetector),
    val recommender: DraftRecommender = DraftRecommender(evaluator, tagRegistry, flawDetector, flexAnalyzer),
) : AutoCloseable {
    fun predictNextAction(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        topN: Int = 3,
    ): IntentPredictionResult =
        intentPredictor.predictNextAction(
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            playerProfilesByRole = playerProfilesByRole,
            topN = topN,
        )

    fun recommendPicks(
        draftState: DraftState,
        targetSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        targetRole: Role? = null,
        limit: Int = 5,
    ): RecommendationReport =
        recommender.recommend(
            draftState = draftState,
            targetSide = targetSide,
            patchMeta = patchMeta,
            blueTeamProfile = blueTeamProfile,
            redTeamProfile = redTeamProfile,
            playerStatsByRole = playerStatsByRole,
            targetRole = targetRole,
            limit = limit,
        )

    fun analyzeFlex(
        championId: String,
        patchMeta: PatchMetaMatrix? = null,
        teamExistingRoles: Set<Role> = emptySet(),
    ): FlexAnalysisResult =
        flexAnalyzer.analyzeChampion(
            championId = championId,
            patchMeta = patchMeta,
            teamExistingRoles = teamExistingRoles,
        )

    fun defendFlex(
        draftState: DraftState,
        opponentSide: Side,
        patchMeta: PatchMetaMatrix? = null,
    ): List<FlexDefenseAdvice> =
        flexAnalyzer.generateDefenseAdvice(
            draftState = draftState,
            opponentSide = opponentSide,
            patchMeta = patchMeta,
        )

    override fun close() {
        evaluator.close()
    }
}
