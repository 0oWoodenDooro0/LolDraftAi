package com.loldraft.data.normalization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChampionNormalizerTest {
    @Test
    fun `should normalize apostrophe champions`() {
        assertEquals("K'Sante", ChampionNormalizer.normalize("K'Sante"))
        assertEquals("K'Sante", ChampionNormalizer.normalize("KSante"))
        assertEquals("K'Sante", ChampionNormalizer.normalize("k'sante"))
        assertEquals("K'Sante", ChampionNormalizer.normalize("ksante"))

        assertEquals("Kog'Maw", ChampionNormalizer.normalize("Kog'Maw"))
        assertEquals("Kog'Maw", ChampionNormalizer.normalize("KogMaw"))
        assertEquals("Kog'Maw", ChampionNormalizer.normalize("kog'maw"))

        assertEquals("Kai'Sa", ChampionNormalizer.normalize("Kai'Sa"))
        assertEquals("Kai'Sa", ChampionNormalizer.normalize("KaiSa"))

        assertEquals("Cho'Gath", ChampionNormalizer.normalize("Cho'Gath"))
        assertEquals("Cho'Gath", ChampionNormalizer.normalize("ChoGath"))

        assertEquals("Rek'Sai", ChampionNormalizer.normalize("Rek'Sai"))
        assertEquals("Rek'Sai", ChampionNormalizer.normalize("RekSai"))

        assertEquals("Vel'Koz", ChampionNormalizer.normalize("Vel'Koz"))
        assertEquals("Vel'Koz", ChampionNormalizer.normalize("VelKoz"))
    }

    @Test
    fun `should normalize special names titles and multi-word champions`() {
        assertEquals("Dr. Mundo", ChampionNormalizer.normalize("Dr. Mundo"))
        assertEquals("Dr. Mundo", ChampionNormalizer.normalize("DrMundo"))
        assertEquals("Dr. Mundo", ChampionNormalizer.normalize("dr mundo"))

        assertEquals("Nunu & Willump", ChampionNormalizer.normalize("Nunu & Willump"))
        assertEquals("Nunu & Willump", ChampionNormalizer.normalize("Nunu"))

        assertEquals("Renata Glasc", ChampionNormalizer.normalize("Renata Glasc"))
        assertEquals("Renata Glasc", ChampionNormalizer.normalize("Renata"))

        assertEquals("Twisted Fate", ChampionNormalizer.normalize("Twisted Fate"))
        assertEquals("Twisted Fate", ChampionNormalizer.normalize("TwistedFate"))

        assertEquals("Miss Fortune", ChampionNormalizer.normalize("Miss Fortune"))
        assertEquals("Miss Fortune", ChampionNormalizer.normalize("MissFortune"))

        assertEquals("Jarvan IV", ChampionNormalizer.normalize("Jarvan IV"))
        assertEquals("Jarvan IV", ChampionNormalizer.normalize("JarvanIV"))

        assertEquals("Xin Zhao", ChampionNormalizer.normalize("Xin Zhao"))
        assertEquals("Xin Zhao", ChampionNormalizer.normalize("XinZhao"))

        assertEquals("Tahm Kench", ChampionNormalizer.normalize("Tahm Kench"))
        assertEquals("Tahm Kench", ChampionNormalizer.normalize("TahmKench"))

        assertEquals("Aurelion Sol", ChampionNormalizer.normalize("Aurelion Sol"))
        assertEquals("Aurelion Sol", ChampionNormalizer.normalize("AurelionSol"))

        assertEquals("Master Yi", ChampionNormalizer.normalize("Master Yi"))
        assertEquals("Master Yi", ChampionNormalizer.normalize("MasterYi"))
    }

    @Test
    fun `should map internal Riot keys and aliases`() {
        assertEquals("Wukong", ChampionNormalizer.normalize("MonkeyKing"))
        assertEquals("Wukong", ChampionNormalizer.normalize("monkeyking"))
        assertEquals("Wukong", ChampionNormalizer.normalize("Wukong"))

        assertEquals("LeBlanc", ChampionNormalizer.normalize("Leblanc"))
        assertEquals("LeBlanc", ChampionNormalizer.normalize("LeBlanc"))
    }

    @Test
    fun `should generate clean normalized slugs for IDs`() {
        assertEquals("ksante", ChampionNormalizer.toSlug("K'Sante"))
        assertEquals("drmundo", ChampionNormalizer.toSlug("Dr. Mundo"))
        assertEquals("twistedfate", ChampionNormalizer.toSlug("Twisted Fate"))
        assertEquals("wukong", ChampionNormalizer.toSlug("MonkeyKing"))
    }

    @Test
    fun `should convert champion names to correct Data Dragon image keys`() {
        assertEquals("MonkeyKing", ChampionNormalizer.toDdragonKey("Wukong"))
        assertEquals("MonkeyKing", ChampionNormalizer.toDdragonKey("MonkeyKing"))
        assertEquals("Leblanc", ChampionNormalizer.toDdragonKey("LeBlanc"))
        assertEquals("Leblanc", ChampionNormalizer.toDdragonKey("leblanc"))
        assertEquals("Nunu", ChampionNormalizer.toDdragonKey("Nunu & Willump"))
        assertEquals("Nunu", ChampionNormalizer.toDdragonKey("nunu"))
        assertEquals("Renata", ChampionNormalizer.toDdragonKey("Renata Glasc"))
        assertEquals("Renata", ChampionNormalizer.toDdragonKey("renata"))
        assertEquals("Belveth", ChampionNormalizer.toDdragonKey("Bel'Veth"))
        assertEquals("Chogath", ChampionNormalizer.toDdragonKey("Cho'Gath"))
        assertEquals("Kaisa", ChampionNormalizer.toDdragonKey("Kai'Sa"))
        assertEquals("Khazix", ChampionNormalizer.toDdragonKey("Kha'Zix"))
        assertEquals("Velkoz", ChampionNormalizer.toDdragonKey("Vel'Koz"))
        assertEquals("KSante", ChampionNormalizer.toDdragonKey("K'Sante"))
        assertEquals("DrMundo", ChampionNormalizer.toDdragonKey("Dr. Mundo"))
        assertEquals("JarvanIV", ChampionNormalizer.toDdragonKey("Jarvan IV"))
        assertEquals("LeeSin", ChampionNormalizer.toDdragonKey("Lee Sin"))
        assertEquals("TwistedFate", ChampionNormalizer.toDdragonKey("Twisted Fate"))
        assertEquals("AurelionSol", ChampionNormalizer.toDdragonKey("Aurelion Sol"))
        assertEquals("Ambessa", ChampionNormalizer.toDdragonKey("Ambessa"))
        assertEquals("Mel", ChampionNormalizer.toDdragonKey("Mel"))
        assertEquals("", ChampionNormalizer.toDdragonKey(null))
        assertEquals("", ChampionNormalizer.toDdragonKey("None"))
    }

    @Test
    fun `should identify empty or none bans`() {
        assertTrue(ChampionNormalizer.isNoneOrEmpty(null))
        assertTrue(ChampionNormalizer.isNoneOrEmpty(""))
        assertTrue(ChampionNormalizer.isNoneOrEmpty("   "))
        assertTrue(ChampionNormalizer.isNoneOrEmpty("None"))
        assertTrue(ChampionNormalizer.isNoneOrEmpty("none"))
        assertTrue(ChampionNormalizer.isNoneOrEmpty("null"))

        assertFalse(ChampionNormalizer.isNoneOrEmpty("Ahri"))
        assertEquals("", ChampionNormalizer.normalize("None"))
        assertEquals("", ChampionNormalizer.normalize(""))
    }
}
