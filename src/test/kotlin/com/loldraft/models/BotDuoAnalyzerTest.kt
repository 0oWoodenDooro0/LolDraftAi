package com.loldraft.models

import com.loldraft.data.meta.BotDuoStyleTag
import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BotDuoAnalyzerTest {

    @Test
    fun `PatchMetaMatrix should provide classic bot duos and synergy lookups`() {
        val analyzer = PatchMetaAnalyzer()
        val matrix = analyzer.analyzeGames(emptyList(), patchLabel = "14.10")

        assertTrue(matrix.botDuoSynergies.isNotEmpty())
        assertTrue(matrix.botDuoMatchups.isNotEmpty())

        val lucianNami = matrix.getDuoSynergy("Lucian", "Nami")
        assertNotNull(lucianNami)
        assertEquals("Lucian", lucianNami.botChampion)
        assertEquals("Nami", lucianNami.supportChampion)
        assertTrue(lucianNami.synergyScore > 70.0)
        assertTrue(lucianNami.styleTags.contains(BotDuoStyleTag.ALL_IN_KILL))

        val topSupports = matrix.getTopSupportersFor("Lucian")
        assertTrue(topSupports.any { it.supportChampion.equals("Nami", ignoreCase = true) })

        val matchup = matrix.getDuoMatchup("Draven", "Nautilus", "Varus", "Nami")
        assertNotNull(matchup)
        assertTrue(matchup.blueWinRate >= 0.65)
        assertTrue(matchup.counterScore > 70.0)
    }

    @Test
    fun `PatchMetaAnalyzer should extract empirical 2v2 bot duo statistics from games`() {
        val game = Game(
            id = "game_test_01",
            gameNumber = 1,
            patch = "14.10",
            blueTeam = Team("t1", "T1", "T1", "LCK"),
            redTeam = Team("gen", "Gen.G", "GEN", "LCK"),
            draftState = DraftState(
                bluePicks = listOf(
                    PickSelection("Lucian", Role.BOT),
                    PickSelection("Nami", Role.SUPPORT),
                    PickSelection("Aatrox", Role.TOP),
                    PickSelection("Sejuani", Role.JUNGLE),
                    PickSelection("Orianna", Role.MID),
                ),
                redPicks = listOf(
                    PickSelection("Varus", Role.BOT),
                    PickSelection("Ashe", Role.SUPPORT),
                    PickSelection("Renekton", Role.TOP),
                    PickSelection("Vi", Role.JUNGLE),
                    PickSelection("Azir", Role.MID),
                )
            ),
            winner = Side.BLUE
        )

        val analyzer = PatchMetaAnalyzer()
        val matrix = analyzer.analyzeGames(listOf(game), patchLabel = "14.10")

        val lucianNami = matrix.getDuoSynergy("Lucian", "Nami")
        assertNotNull(lucianNami)
        assertTrue(lucianNami.gamesTogether >= 1)
    }
}
