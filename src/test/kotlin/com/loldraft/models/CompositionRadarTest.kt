package com.loldraft.models

import com.loldraft.data.meta.DamageProfile
import com.loldraft.data.meta.DamageType
import com.loldraft.data.meta.FiveDimensionRadar
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositionRadarTest {
    @Test
    fun `should normalize all 5 dimensions within 0 to 10 scale`() {
        val blueRadar = FiveDimensionRadar(8.5, 7.0, 6.5, 7.8, 8.2)
        val redRadar = FiveDimensionRadar(6.0, 8.8, 5.0, 6.2, 7.5)
        val blueDamage = DamageProfile(0.55, 0.40, 0.05, DamageType.MIXED)
        val redDamage = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL)

        val score = CompositionRadarCalculator.calculate(blueRadar, redRadar, blueDamage, redDamage)

        // Check bounds on Blue
        assertTrue(score.blueRadar.laning in 0.0..10.0)
        assertTrue(score.blueRadar.engage in 0.0..10.0)
        assertTrue(score.blueRadar.waveclear in 0.0..10.0)
        assertTrue(score.blueRadar.damageBalance in 0.0..10.0)
        assertTrue(score.blueRadar.lateScaling in 0.0..10.0)

        // Check bounds on Red
        assertTrue(score.redRadar.laning in 0.0..10.0)
        assertTrue(score.redRadar.engage in 0.0..10.0)
        assertTrue(score.redRadar.waveclear in 0.0..10.0)
        assertTrue(score.redRadar.damageBalance in 0.0..10.0)
        assertTrue(score.redRadar.lateScaling in 0.0..10.0)
    }

    @Test
    fun `should reward balanced mixed damage compositions with high damage balance score`() {
        val idealMixed = DamageProfile(0.50, 0.45, 0.05, DamageType.MIXED)
        val score = CompositionRadarCalculator.calculateDamageBalance(idealMixed)

        assertTrue(score >= 9.0, "Ideal mixed damage should have damage balance >= 9.0, got: $score")
    }

    @Test
    fun `should penalize pure physical or pure magic compositions in damage balance`() {
        val purePhysical = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL)
        val pureMagic = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC)

        val physScore = CompositionRadarCalculator.calculateDamageBalance(purePhysical)
        val magicScore = CompositionRadarCalculator.calculateDamageBalance(pureMagic)

        assertTrue(physScore <= 5.0, "Pure physical comp should have damage balance <= 5.0, got: $physScore")
        assertTrue(magicScore <= 5.0, "Pure magic comp should have damage balance <= 5.0, got: $magicScore")
    }

    @Test
    fun `should correctly compute delta radar and attribute dimension advantages`() {
        // Blue has superior laning, waveclear, late scaling, damage balance
        // Red has superior engage
        val blueRadar = FiveDimensionRadar(laningStrength = 8.5, engage = 6.0, disengage = 7.0, waveclear = 8.0, lateGameScaling = 9.0)
        val redRadar = FiveDimensionRadar(laningStrength = 6.0, engage = 9.5, disengage = 5.0, waveclear = 6.0, lateGameScaling = 6.5)
        val blueDamage = DamageProfile(0.50, 0.50, 0.0, DamageType.MIXED)
        val redDamage = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL)

        val score = CompositionRadarCalculator.calculate(blueRadar, redRadar, blueDamage, redDamage)

        assertEquals(Side.BLUE, score.dimensionAdvantages[RadarDimension.LANING])
        assertEquals(Side.RED, score.dimensionAdvantages[RadarDimension.ENGAGE])
        assertEquals(Side.BLUE, score.dimensionAdvantages[RadarDimension.WAVECLEAR])
        assertEquals(Side.BLUE, score.dimensionAdvantages[RadarDimension.DAMAGE_BALANCE])
        assertEquals(Side.BLUE, score.dimensionAdvantages[RadarDimension.LATE_SCALING])

        // Verify deltas
        assertEquals(2.5, score.deltaRadar.laning, 0.01)
        assertEquals(-3.5, score.deltaRadar.engage, 0.01)
        assertEquals(2.0, score.deltaRadar.waveclear, 0.01)
        assertTrue(score.deltaRadar.damageBalance > 0.0)
        assertEquals(2.5, score.deltaRadar.lateScaling, 0.01)
    }

    @Test
    fun `should report null advantage when dimensions are perfectly tied`() {
        val tiedRadar = FiveDimensionRadar(7.0, 7.0, 7.0, 7.0, 7.0)
        val tiedDamage = DamageProfile(0.50, 0.50, 0.0, DamageType.MIXED)

        val score = CompositionRadarCalculator.calculate(tiedRadar, tiedRadar, tiedDamage, tiedDamage)

        assertNull(score.dimensionAdvantages[RadarDimension.LANING])
        assertNull(score.dimensionAdvantages[RadarDimension.ENGAGE])
        assertNull(score.dimensionAdvantages[RadarDimension.WAVECLEAR])
        assertNull(score.dimensionAdvantages[RadarDimension.DAMAGE_BALANCE])
        assertNull(score.dimensionAdvantages[RadarDimension.LATE_SCALING])
    }
}
