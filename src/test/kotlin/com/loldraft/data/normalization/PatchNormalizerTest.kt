package com.loldraft.data.normalization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchNormalizerTest {
    @Test
    fun `should parse standard patch format`() {
        val patch = PatchNormalizer.parse("14.1")
        assertNotNull(patch)
        assertEquals(14, patch.major)
        assertEquals(1, patch.minor)
        assertEquals(0, patch.subPatch)
        assertNull(patch.hotfix)
        assertEquals("14.1", patch.canonicalString)
    }

    @Test
    fun `should parse patch with leading zero in minor version`() {
        // "14.01" -> 14.1
        val patch = PatchNormalizer.parse("14.01")
        assertNotNull(patch)
        assertEquals(14, patch.major)
        assertEquals(1, patch.minor)
        assertEquals("14.1", patch.canonicalString)
    }

    @Test
    fun `should parse two-digit minor patch version`() {
        // "14.10" -> 14.10
        val patch = PatchNormalizer.parse("14.10")
        assertNotNull(patch)
        assertEquals(14, patch.major)
        assertEquals(10, patch.minor)
        assertEquals("14.10", patch.canonicalString)
    }

    @Test
    fun `should parse hotfix letter or sub-patch`() {
        val patchWithLetter = PatchNormalizer.parse("14.1b")
        assertNotNull(patchWithLetter)
        assertEquals(14, patchWithLetter.major)
        assertEquals(1, patchWithLetter.minor)
        assertEquals("b", patchWithLetter.hotfix)
        assertEquals("14.1b", patchWithLetter.canonicalString)

        val patchWithSub = PatchNormalizer.parse("14.1.1")
        assertNotNull(patchWithSub)
        assertEquals(14, patchWithSub.major)
        assertEquals(1, patchWithSub.minor)
        assertEquals(1, patchWithSub.subPatch)
        assertEquals("14.1.1", patchWithSub.canonicalString)
    }

    @Test
    fun `should parse prefix forms like Patch and v`() {
        assertEquals("14.1", PatchNormalizer.normalize("Patch 14.1"))
        assertEquals("13.24", PatchNormalizer.normalize("v13.24"))
        assertEquals("14.4", PatchNormalizer.normalize("  14.4  "))
    }

    @Test
    fun `should correctly compare patches in chronological order`() {
        val p14_1 = PatchNormalizer.parse("14.1")!!
        val p14_2 = PatchNormalizer.parse("14.2")!!
        val p14_9 = PatchNormalizer.parse("14.9")!!
        val p14_10 = PatchNormalizer.parse("14.10")!!
        val p13_24 = PatchNormalizer.parse("13.24")!!

        assertTrue(p14_2 > p14_1)
        assertTrue(p14_10 > p14_9)
        assertTrue(p14_1 > p13_24)
        assertEquals(0, p14_1.compareTo(PatchNormalizer.parse("14.01")!!))
    }

    @Test
    fun `should handle invalid or blank patch gracefully`() {
        assertNull(PatchNormalizer.parse(null))
        assertNull(PatchNormalizer.parse(""))
        assertNull(PatchNormalizer.parse("   "))
        assertNull(PatchNormalizer.parse("invalid_patch"))
        assertFalse(PatchNormalizer.isValid("not_a_patch"))
        assertEquals("unknown", PatchNormalizer.normalize("invalid"))
        assertEquals("default_val", PatchNormalizer.normalize(null, default = "default_val"))
    }
}
