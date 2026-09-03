package com.loldraft.server

import com.loldraft.data.models.Role
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProMatchRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private val sampleCsv =
        """
        gameid,league,year,split,date,game,patch,side,position,playername,playerid,teamname,teamid,champion,ban1,ban2,ban3,ban4,ban5,gamelength,result,firstblood,firstdragon,golddiffat15,teamkills,teamdeaths
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,top,Zeus,p1,T1,t1,Aatrox,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,jng,Oner,p2,T1,t1,Nocturne,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,mid,Faker,p3,T1,t1,Orianna,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,bot,Gumayusi,p4,T1,t1,Varus,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,sup,Keria,p5,T1,t1,Nautilus,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Blue,team,,,T1,t1,,Lucian,Kalista,Ashe,Poppy,Vi,1900,1,1,1,1200,16,6
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,top,Kiin,p6,Gen.G,gen,K'Sante,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,jng,Canyon,p7,Gen.G,gen,Rell,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,mid,Chovy,p8,Gen.G,gen,Corki,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,bot,Peyz,p9,Gen.G,gen,Aphelios,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,sup,Lehends,p10,Gen.G,gen,Milio,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LCK_01,LCK,2026,Spring,2026-01-15 17:00:00,1,16.01,Red,team,,,Gen.G,gen,,Sejuani,Azir,Rell,Lee Sin,Jarvan IV,1900,0,0,0,-1200,6,16
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,top,Bin,p11,Bilibili Gaming,blg,Jax,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,jng,Xun,p12,Bilibili Gaming,blg,Kindred,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,mid,knight,p13,Bilibili Gaming,blg,Ahri,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,bot,Elk,p14,Bilibili Gaming,blg,Kalista,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,sup,ON,p15,Bilibili Gaming,blg,Blitzcrank,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Blue,team,,,Bilibili Gaming,blg,,Rumble,Ashe,Varus,Viego,Xin Zhao,1800,1,1,1,1500,20,10
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,top,369,p16,Top Esports,tes,Renekton,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,jng,Tian,p17,Top Esports,tes,Jarvan IV,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,mid,Creme,p18,Top Esports,tes,Tristana,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,bot,JackeyLove,p19,Top Esports,tes,Draven,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,sup,Meiko,p20,Top Esports,tes,Thresh,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        OE_2026_LPL_01,LPL,2026,Spring,2026-01-16 19:00:00,1,16.01,Red,team,,,Top Esports,tes,,Kalista,Varus,Ashe,Nautilus,Poppy,1800,0,0,0,-1500,10,20
        """.trimIndent()

    private lateinit var testCsvFile: File

    @BeforeEach
    fun setUp() {
        testCsvFile = File(tempDir, "test_matches.csv")
        testCsvFile.writeText(sampleCsv)
    }

    @Test
    fun `should parse matches from file and return games count`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        assertEquals(2, repository.totalGamesCount)
    }

    @Test
    fun `should list distinct sorted leagues`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        val leagues = repository.getLeagues()
        assertEquals(listOf("LCK", "LPL"), leagues)
    }

    @Test
    fun `should filter teams by league and query text`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        val allTeams = repository.getTeams()
        assertEquals(4, allTeams.size)

        val lckTeams = repository.getTeams(league = "LCK")
        assertEquals(2, lckTeams.size)
        assertTrue(lckTeams.any { it.name == "T1" })
        assertTrue(lckTeams.any { it.name == "Gen.G" })

        val t1Query = repository.getTeams(query = "T1")
        assertEquals(1, t1Query.size)
        assertEquals("T1", t1Query.first().name)
        assertEquals(1, t1Query.first().totalGames)
        assertEquals(1.0, t1Query.first().winRate)

        val blgQuery = repository.getTeams(query = "bilibili")
        assertEquals(1, blgQuery.size)
        assertEquals("Bilibili Gaming", blgQuery.first().name)
    }

    @Test
    fun `should compute comprehensive tactical profile for team`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        val profile = repository.getTeamProfile("t1")
        assertNotNull(profile)
        assertEquals("T1", profile.team.name)
        assertEquals(1, profile.totalGamesAnalyzed)

        // Side preference
        assertEquals(1, profile.sidePreference.blueRecord.games)
        assertEquals(1.0, profile.sidePreference.blueRecord.winRate)

        // Early game metrics
        assertNotNull(profile.earlyGameMetrics)
        assertEquals(1.0, profile.earlyGameMetrics.firstBloodRate)
        assertEquals(1.0, profile.earlyGameMetrics.firstDragonRate)
        assertEquals(1200.0, profile.earlyGameMetrics.avgGoldDiffAt15)

        // Tactical tags
        assertTrue(profile.tags.isNotEmpty())
    }

    @Test
    fun `should extract player roster with signature champions`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        val roster = repository.getTeamRoster("t1")
        assertEquals(5, roster.size)

        val faker = roster.find { it.role == Role.MID }
        assertNotNull(faker)
        assertEquals("Faker", faker.playerName)
        assertEquals(1, faker.gamesPlayed)
        assertEquals(listOf("Orianna"), faker.topChampions)

        val zeus = roster.find { it.role == Role.TOP }
        assertNotNull(zeus)
        assertEquals("Zeus", zeus.playerName)
        assertEquals(listOf("Aatrox"), zeus.topChampions)
    }

    @Test
    fun `should retrieve complete list of distinct champions`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        val champions = repository.getChampions()
        assertTrue(champions.isNotEmpty())
        assertTrue(champions.any { it.name.equals("Orianna", ignoreCase = true) })
        assertTrue(champions.any { it.name.equals("Aatrox", ignoreCase = true) })
        assertTrue(champions.any { it.name.equals("Ahri", ignoreCase = true) })
    }

    @Test
    fun `should return null when team is not found`() {
        val repository = ProMatchRepository(dataFilePath = testCsvFile.absolutePath)
        repository.initialize()

        assertNull(repository.getTeamProfile("non_existent_team_123"))
        val emptyRoster = repository.getTeamRoster("non_existent_team_123")
        assertTrue(emptyRoster.isEmpty())
    }

    @Test
    fun `should handle non-existent file gracefully`() {
        val repository = ProMatchRepository(dataFilePath = "/path/does/not/exist.csv")
        repository.initialize()

        assertEquals(0, repository.totalGamesCount)
        assertTrue(repository.getLeagues().isEmpty())
        assertTrue(repository.getTeams().isEmpty())
    }
}
