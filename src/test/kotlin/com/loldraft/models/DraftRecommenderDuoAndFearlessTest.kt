package com.loldraft.models

import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.ProPlayerDetailedProfile
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

    @Test
    fun `recommendBestBans should not exceed 3 bans for any single role`() {
        val state = DraftState()
        val bestBans = recommender.recommendBestBans(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            limit = 10,
        )

        val roleCounts = bestBans.groupingBy { it.recommendedRole }.eachCount()
        for ((role, count) in roleCounts) {
            assertTrue(
                count <= 3,
                "Role $role should not exceed 3 recommended bans, but found $count: ${bestBans.map { "${it.championId}(${it.recommendedRole})" }}"
            )
        }
    }

    @Test
    fun `recommendBestBans in Turn 14 must never recommend more than 3 bans for TOP lane even with high opponent signatures`() {
        val turns: List<DraftTurn> = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Nami", role = Role.SUPPORT),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "Ban7"),
        )
        val state = DraftState.fromTurns(turns)

        val kiinSigPicks = listOf(
            SignaturePick("Aatrox", 50, 35, 0.70, 0.35, 95.0, SignatureTier.SIGNATURE, Role.TOP),
            SignaturePick("K'Sante", 45, 30, 0.67, 0.30, 92.0, SignatureTier.SIGNATURE, Role.TOP),
            SignaturePick("Rumble", 40, 26, 0.65, 0.28, 90.0, SignatureTier.SIGNATURE, Role.TOP),
            SignaturePick("Renekton", 35, 22, 0.63, 0.25, 88.0, SignatureTier.SIGNATURE, Role.TOP),
            SignaturePick("Jax", 30, 18, 0.60, 0.20, 85.0, SignatureTier.SIGNATURE, Role.TOP),
        )
        val careerStats = PlayerCareerStats(
            playerId = "Kiin",
            totalProGames = 200,
            totalWins = 130,
            winRate = 0.65,
            signaturePicks = kiinSigPicks,
            roleDistribution = mapOf(Role.TOP to 200),
            championRecords = emptyMap(),
        )
        val oppTopProfile = ProPlayerDetailedProfile(
            playerId = "Kiin",
            role = Role.TOP,
            totalProGames = 200,
            proWinRate = 0.65,
            careerStats = careerStats,
            signaturePicks = kiinSigPicks,
            dossier = null,
        )

        val bestBans = recommender.recommendBestBans(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            opponentPlayerProfilesByRole = mapOf(Role.TOP to oppTopProfile),
            limit = 5,
        )

        val topCount = bestBans.count { it.recommendedRole == Role.TOP }
        assertTrue(
            topCount <= 3,
            "Turn 14 should NEVER exceed 3 TOP bans even with 5 top signatures, but found $topCount: ${bestBans.map { "${it.championId}(${it.recommendedRole})" }}"
        )
        assertEquals(5, bestBans.size, "Should still return 5 recommendations by filling from other roles")
    }

    @Test
    fun `Vayne tagged as BOT but played TOP by opponent should be excluded if enemy already locked TOP in Turn 14`() {
        // Enemy RED locked TOP (Gnar) in Turn 8!
        val turns: List<DraftTurn> = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Gnar", role = Role.TOP), // Enemy locked TOP!
            DraftTurn(9, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "Ban7"),
        )
        val state = DraftState.fromTurns(turns)

        // Opponent TOP player has Vayne as signature
        val topPlayerProfile = ProPlayerDetailedProfile(
            playerId = "TheShy",
            role = Role.TOP,
            totalProGames = 200,
            proWinRate = 0.65,
            careerStats = PlayerCareerStats(
                playerId = "TheShy",
                totalProGames = 200,
                totalWins = 130,
                winRate = 0.65,
                signaturePicks = listOf(
                    SignaturePick("Vayne", 30, 22, 0.73, 0.20, 95.0, SignatureTier.SIGNATURE, Role.TOP),
                ),
                roleDistribution = mapOf(Role.TOP to 200),
                championRecords = emptyMap(),
            ),
            signaturePicks = listOf(
                SignaturePick("Vayne", 30, 22, 0.73, 0.20, 95.0, SignatureTier.SIGNATURE, Role.TOP),
            ),
            dossier = null,
        )

        val bestBans = recommender.recommendBestBans(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            opponentPlayerProfilesByRole = mapOf(Role.TOP to topPlayerProfile),
            limit = 5,
        )

        // Vayne must NOT be recommended to deny TOP player because enemy already locked TOP!
        val deniesTopVayne = bestBans.any { it.championId == "Vayne" && it.reasons.any { r -> r.contains("TOP") } }
        assertFalse(deniesTopVayne, "Vayne should not be recommended with TOP player denial reason when enemy already picked TOP")
    }

    @Test
    fun `Vayne tagged as BOT but played TOP by opponent must count towards TOP lane quota when enemy has not locked TOP`() {
        // Enemy RED has NOT locked TOP yet!
        val turns: List<DraftTurn> = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Nami", role = Role.SUPPORT),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "Ban7"),
        )
        val state = DraftState.fromTurns(turns)

        // Opponent TOP player has Vayne + 4 other top signatures
        val topPlayerProfile = ProPlayerDetailedProfile(
            playerId = "TheShy",
            role = Role.TOP,
            totalProGames = 200,
            proWinRate = 0.65,
            careerStats = PlayerCareerStats(
                playerId = "TheShy",
                totalProGames = 200,
                totalWins = 130,
                winRate = 0.65,
                signaturePicks = listOf(
                    SignaturePick("Vayne", 40, 30, 0.75, 0.25, 96.0, SignatureTier.SIGNATURE, Role.TOP),
                    SignaturePick("Aatrox", 50, 35, 0.70, 0.35, 95.0, SignatureTier.SIGNATURE, Role.TOP),
                    SignaturePick("K'Sante", 45, 30, 0.67, 0.30, 92.0, SignatureTier.SIGNATURE, Role.TOP),
                    SignaturePick("Rumble", 40, 26, 0.65, 0.28, 90.0, SignatureTier.SIGNATURE, Role.TOP),
                    SignaturePick("Renekton", 35, 22, 0.63, 0.25, 88.0, SignatureTier.SIGNATURE, Role.TOP),
                ),
                roleDistribution = mapOf(Role.TOP to 200),
                championRecords = emptyMap(),
            ),
            signaturePicks = listOf(
                SignaturePick("Vayne", 40, 30, 0.75, 0.25, 96.0, SignatureTier.SIGNATURE, Role.TOP),
                SignaturePick("Aatrox", 50, 35, 0.70, 0.35, 95.0, SignatureTier.SIGNATURE, Role.TOP),
                SignaturePick("K'Sante", 45, 30, 0.67, 0.30, 92.0, SignatureTier.SIGNATURE, Role.TOP),
                SignaturePick("Rumble", 40, 26, 0.65, 0.28, 90.0, SignatureTier.SIGNATURE, Role.TOP),
                SignaturePick("Renekton", 35, 22, 0.63, 0.25, 88.0, SignatureTier.SIGNATURE, Role.TOP),
            ),
            dossier = null,
        )

        val bestBans = recommender.recommendBestBans(
            draftState = state,
            targetSide = Side.BLUE,
            patchMeta = patchMeta,
            opponentPlayerProfilesByRole = mapOf(Role.TOP to topPlayerProfile),
            limit = 5,
        )

        // When Vayne is recommended targeting opponent TOP player, its recommendedRole must be TOP
        val vayneRec = bestBans.firstOrNull { it.championId == "Vayne" }
        if (vayneRec != null) {
            assertEquals(Role.TOP, vayneRec.recommendedRole, "Vayne targeting TOP player must have recommendedRole = TOP")
        }

        val topBansCount = bestBans.count { it.recommendedRole == Role.TOP }
        assertTrue(
            topBansCount <= 3,
            "Total TOP bans (including Vayne) must never exceed 3, but got $topBansCount: ${bestBans.map { "${it.championId}(${it.recommendedRole})" }}"
        )
    }

    @Test
    fun `champion with static secondary role should not be recommended for that role if pro match roleDistribution has zero games`() {
        // Vayne has static secondaryRole = [TOP] in ChampionDatabaseBuilder.
        // But in pro match meta (14.15), Vayne has 50 games in BOT, 0 games in TOP.
        val proMeta = PatchMetaMatrix(
            patch = "14.15",
            totalGames = 100,
            championStats = mapOf(
                "vayne" to ChampionMetaStats(
                    championId = "Vayne",
                    patch = "14.15",
                    picks = 50,
                    roleDistribution = mapOf(Role.BOT to 50), // 0 in TOP!
                    tier = MetaTier.T1,
                ),
            ),
        )

        // Recommend for TOP lane specifically
        val recs = recommender.recommend(
            draftState = DraftState.empty(),
            targetSide = Side.BLUE,
            targetRole = Role.TOP,
            patchMeta = proMeta,
            limit = 10,
        )

        val vayneInTop = recs.recommendations.any { it.championId == "Vayne" }
        assertFalse(
            vayneInTop,
            "Vayne should NEVER be recommended for TOP when pro match roleDistribution has 0 games in TOP!"
        )
    }
}
