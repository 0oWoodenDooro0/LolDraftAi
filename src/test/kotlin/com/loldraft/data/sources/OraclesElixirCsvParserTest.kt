package com.loldraft.data.sources

import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OraclesElixirCsvParserTest {
    private val sampleCsv =
        """
        gameid,league,year,split,date,game,patch,side,position,playername,playerid,teamname,teamid,champion,ban1,ban2,ban3,ban4,ban5,gamelength,result
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,top,Zeus,p1,T1,t1,Aatrox,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,jng,Oner,p2,T1,t1,Nocturne,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,mid,Faker,p3,T1,t1,Orianna,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,bot,Gumayusi,p4,T1,t1,Varus,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,sup,Keria,p5,T1,t1,Nautilus,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Blue,team,,,T1,t1,,Lucian,Kalista,Ashe,Poppy,Vi,2000,1
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,top,Kiin,p6,Gen.G,t2,K'Sante,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,jng,Canyon,p7,Gen.G,t2,Rell,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,mid,Chovy,p8,Gen.G,t2,Corki,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,bot,Peyz,p9,Gen.G,t2,Aphelios,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,sup,Lehends,p10,Gen.G,t2,Milio,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        OE_2024_001,LCK,2024,Spring,2024-01-17 17:00:00,1,14.01,Red,team,,,Gen.G,t2,,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,2000,0
        """.trimIndent()

    @Test
    fun `should parse standard Oracle's Elixir match CSV with roles and bans`() {
        val parser = OraclesElixirCsvParser()
        val games = parser.parseCsv(sampleCsv)

        assertEquals(1, games.size)
        val game = games.first()

        assertEquals("OE_2024_001", game.id)
        assertEquals(1, game.gameNumber)
        assertEquals("14.1", game.patch) // 14.01 normalized to 14.1
        assertEquals("T1", game.blueTeam.name)
        assertEquals("Gen.G", game.redTeam.name)
        assertEquals(Side.BLUE, game.winner)
        assertEquals(2000, game.durationSeconds)

        val draft = game.draftState
        assertEquals(5, draft.blueBans.size)
        assertEquals(listOf("Lucian", "Kalista", "Ashe", "Poppy", "Vi"), draft.blueBans)

        assertEquals(5, draft.redBans.size)
        assertEquals(listOf("Sejuani", "Azir", "Rell", "Lee Sin", "Jarvan IV"), draft.redBans)

        assertEquals(5, draft.bluePicks.size)
        val fakerPick = draft.bluePicks.find { it.role == Role.MID }
        assertNotNull(fakerPick)
        assertEquals("Orianna", fakerPick.championId)
        assertEquals("Faker", fakerPick.playerId)

        assertEquals(5, draft.redPicks.size)
        val kiinPick = draft.redPicks.find { it.role == Role.TOP }
        assertNotNull(kiinPick)
        assertEquals("K'Sante", kiinPick.championId)
        assertEquals("Kiin", kiinPick.playerId)
    }

    @Test
    fun `should parse multiple games and ignore blank lines`() {
        val parser = OraclesElixirCsvParser()
        val multiGameCsv = sampleCsv + "\n\n" + sampleCsv.replace("OE_2024_001", "OE_2024_002").replace("14.01", "14.2")
        val games = parser.parseCsv(multiGameCsv)

        assertEquals(2, games.size)
        assertEquals("OE_2024_001", games[0].id)
        assertEquals("14.1", games[0].patch)
        assertEquals("OE_2024_002", games[1].id)
        assertEquals("14.2", games[1].patch)
    }
}
