package com.loldraft.client.compose.image

import com.loldraft.data.normalization.ChampionNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChampionImageLoaderTest {
    @Test
    fun `should return null for empty or none champion inputs`() = runBlocking {
        assertNull(ChampionImageLoader.loadBitmap(null))
        assertNull(ChampionImageLoader.loadBitmap(""))
        assertNull(ChampionImageLoader.loadBitmap("None"))
        assertNull(ChampionImageLoader.loadBitmap("noban"))
    }

    @Test
    fun `should load or attempt download for canonical champion without throwing`() = runBlocking {
        // Should safely return an ImageBitmap if online, or null if offline without throwing an unhandled exception
        val bitmap = ChampionImageLoader.loadBitmap("Ahri")
        if (bitmap != null) {
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
        }
    }

    @Test
    fun `should properly map special names to ddragon keys`() {
        org.junit.jupiter.api.Assertions.assertEquals("MonkeyKing", ChampionNormalizer.toDdragonKey("Wukong"))
        org.junit.jupiter.api.Assertions.assertEquals("Leblanc", ChampionNormalizer.toDdragonKey("LeBlanc"))
        org.junit.jupiter.api.Assertions.assertEquals("Nunu", ChampionNormalizer.toDdragonKey("Nunu & Willump"))
        org.junit.jupiter.api.Assertions.assertEquals("Renata", ChampionNormalizer.toDdragonKey("Renata Glasc"))
        org.junit.jupiter.api.Assertions.assertEquals("KSante", ChampionNormalizer.toDdragonKey("K'Sante"))
    }
}
