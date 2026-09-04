package com.loldraft.data.meta

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChampionEmpiricalRegistryTest {

    @Test
    fun `test createDefault loads champions and empirical stats`() {
        val registry = ChampionEmpiricalRegistry.createDefault()
        assertTrue(registry.numChampions >= 168, "Expected at least 168 champions, got ${registry.numChampions}")
        
        val aatroxId = registry.getChampId("Aatrox")
        assertTrue(aatroxId > 0, "Aatrox ID should be > 0, got $aatroxId")
        
        val unpickedId = registry.getChampId("")
        assertEquals(0, unpickedId, "Empty champ name should map to ID 0 (padding)")
    }

    @Test
    fun `test getStats retrieves empirical metrics`() {
        val registry = ChampionEmpiricalRegistry.createDefault()
        val stats = registry.getStats("Aatrox")
        assertNotNull(stats)
        assertTrue(stats!!.picks >= 0)
        assertTrue(stats.smoothedWinRate in 0.0..1.0)
        assertTrue(stats.smoothedDpm > 0.0)
    }

    @Test
    fun `test getSynergy retrieves pairwise team synergy`() {
        val registry = ChampionEmpiricalRegistry.createDefault()
        val syn = registry.getSynergy("Aatrox", "Sejuani")
        // Returns smoothed win rate or 0.50 default
        assertTrue(syn in 0.0..1.0)
    }

    @Test
    fun `test getCounter retrieves lane matchup stats`() {
        val registry = ChampionEmpiricalRegistry.createDefault()
        val counter = registry.getCounter("Aatrox", "Renekton")
        assertNotNull(counter)
        assertTrue(counter.winRateAdvantage in -0.5..0.5)
    }
}
