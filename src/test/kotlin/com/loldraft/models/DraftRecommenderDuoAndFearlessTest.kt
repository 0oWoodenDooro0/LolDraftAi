package com.loldraft.models

import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DraftRecommenderDuoAndFearlessTest {

    private val recommender = DraftRecommender()
    private val patchMeta = PatchMetaAnalyzer().analyzeGames(emptyList(), "14.10")

    @Test
    fun `DraftRecommender should never recommend spent champions in Fearless mode`() {
        val spentChampions = setOf("Aatrox", "Renekton", "Orianna", "Varus", "Nautilus")
        val state = DraftState.empty().withFearlessSpent(spentChampions)

        val recommendations = recommender.recommendBestPicks(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            limit = 10,
        )

        for (rec in recommendations) {
            assertFalse(
                spentChampions.contains(rec.championId),
                "Spent champion ${rec.championId} must not be recommended in fearless mode"
            )
        }

        val banRecommendations = recommender.recommendBestBans(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            limit = 5,
        )

        for (rec in banRecommendations) {
            assertFalse(
                spentChampions.contains(rec.championId),
                "Spent champion ${rec.championId} must not be recommended as ban in fearless mode"
            )
        }
    }

    @Test
    fun `DraftRecommender should strongly recommend Nami or Milio when Lucian is already picked`() {
        val state = DraftState(
            bluePicks = listOf(PickSelection("Lucian", Role.BOT)),
            redPicks = listOf(PickSelection("Aatrox", Role.TOP)),
        )

        val recommendations = recommender.recommend(
            draftState = state,
            targetSide = Side.BLUE,
            targetRole = Role.SUPPORT,
            patchMeta = patchMeta,
            limit = 5,
        )

        assertTrue(recommendations.recommendations.isNotEmpty())
        val topSupport = recommendations.recommendations.first()
        assertTrue(
            topSupport.championId.equals("Nami", ignoreCase = true) || topSupport.championId.equals("Milio", ignoreCase = true),
            "Top recommended support for Lucian should be Nami or Milio, but was ${topSupport.championId}"
        )
        assertTrue(
            topSupport.reasons.any { it.contains("Elite 2v2 Bot Duo Synergy with Lucian") },
            "Recommendation reasons should include 2v2 Bot Duo Synergy"
        )
    }

    @Test
    fun `DraftRecommender should evaluate 2v2 counter against enemy bot duo`() {
        val state = DraftState(
            bluePicks = listOf(PickSelection("Lucian", Role.BOT)),
            redPicks = listOf(
                PickSelection("Zeri", Role.BOT),
                PickSelection("Lulu", Role.SUPPORT),
            ),
        )

        val recommendations = recommender.recommend(
            draftState = state,
            targetSide = Side.BLUE,
            targetRole = Role.SUPPORT,
            patchMeta = patchMeta,
            limit = 5,
        )

        val namiRec = recommendations.recommendations.find { it.championId.equals("Nami", ignoreCase = true) }
        assertNotNull(namiRec)
        assertTrue(
            namiRec.reasons.any { it.contains("2v2 Bot Duo Counter vs Zeri+Lulu") || it.contains("Elite 2v2 Bot Duo Synergy with Lucian") },
            "Reasons must contain 2v2 Duo Counter or Synergy"
        )
    }
}
