package com.loldraft.data.normalization

object ChampionNormalizer {
    private val CANONICAL_NAMES: List<String> =
        listOf(
            "Aatrox",
            "Ahri",
            "Akali",
            "Akshan",
            "Alistar",
            "Ambessa",
            "Amumu",
            "Anivia",
            "Annie",
            "Aphelios",
            "Ashe",
            "Aurelion Sol",
            "Aurora",
            "Azir",
            "Bard",
            "Bel'Veth",
            "Blitzcrank",
            "Brand",
            "Braum",
            "Briar",
            "Caitlyn",
            "Camille",
            "Cassiopeia",
            "Cho'Gath",
            "Corki",
            "Darius",
            "Diana",
            "Dr. Mundo",
            "Draven",
            "Ekko",
            "Elise",
            "Evelynn",
            "Ezreal",
            "Fiddlesticks",
            "Fiora",
            "Fizz",
            "Galio",
            "Gangplank",
            "Garen",
            "Gnar",
            "Gragas",
            "Graves",
            "Gwen",
            "Hecarim",
            "Heimerdinger",
            "Hwei",
            "Illaoi",
            "Irelia",
            "Ivern",
            "Janna",
            "Jarvan IV",
            "Jax",
            "Jayce",
            "Jhin",
            "Jinx",
            "K'Sante",
            "Kai'Sa",
            "Kalista",
            "Karma",
            "Karthus",
            "Kassadin",
            "Katarina",
            "Kayle",
            "Kayn",
            "Kennen",
            "Kha'Zix",
            "Kindred",
            "Kled",
            "Kog'Maw",
            "LeBlanc",
            "Lee Sin",
            "Leona",
            "Lillia",
            "Lissandra",
            "Lucian",
            "Lulu",
            "Lux",
            "Malphite",
            "Malzahar",
            "Maokai",
            "Master Yi",
            "Mel",
            "Milio",
            "Miss Fortune",
            "Mordekaiser",
            "Morgana",
            "Naafiri",
            "Nami",
            "Nasus",
            "Nautilus",
            "Neeko",
            "Nidalee",
            "Nilah",
            "Nocturne",
            "Nunu & Willump",
            "Olaf",
            "Orianna",
            "Ornn",
            "Pantheon",
            "Poppy",
            "Pyke",
            "Qiyana",
            "Quinn",
            "Rakan",
            "Rammus",
            "Rek'Sai",
            "Rell",
            "Renata Glasc",
            "Renekton",
            "Rengar",
            "Riven",
            "Rumble",
            "Ryze",
            "Samira",
            "Sejuani",
            "Senna",
            "Seraphine",
            "Sett",
            "Shaco",
            "Shen",
            "Shyvana",
            "Singed",
            "Sion",
            "Sivir",
            "Skarner",
            "Smolder",
            "Sona",
            "Soraka",
            "Swain",
            "Sylas",
            "Syndra",
            "Tahm Kench",
            "Taliyah",
            "Talon",
            "Taric",
            "Teemo",
            "Thresh",
            "Tristana",
            "Trundle",
            "Tryndamere",
            "Twisted Fate",
            "Twitch",
            "Udyr",
            "Urgot",
            "Varus",
            "Vayne",
            "Veigar",
            "Vel'Koz",
            "Vex",
            "Vi",
            "Viego",
            "Viktor",
            "Vladimir",
            "Volibear",
            "Warwick",
            "Wukong",
            "Xayah",
            "Xerath",
            "Xin Zhao",
            "Yasuo",
            "Yone",
            "Yorick",
            "Yuumi",
            "Zac",
            "Zed",
            "Zeri",
            "Ziggs",
            "Zilean",
            "Zoe",
            "Zyra",
        )

    private val CLEAN_KEY_REGEX = Regex("[^a-z0-9]")
    private fun cleanKey(name: String): String = name.lowercase().replace(CLEAN_KEY_REGEX, "")

    private val MAPPINGS: Map<String, String> =
        run {
            val map = mutableMapOf<String, String>()
            for (canonical in CANONICAL_NAMES) {
                map[cleanKey(canonical)] = canonical
            }
            // Aliases & historical names
            map["monkeyking"] = "Wukong"
            map["nunu"] = "Nunu & Willump"
            map["renata"] = "Renata Glasc"
            map["leblanc"] = "LeBlanc"
            map
        }

    fun isNoneOrEmpty(rawName: String?): Boolean {
        if (rawName.isNullOrBlank()) return true
        val lower = rawName.trim().lowercase()
        return lower == "none" || lower == "null" || lower == "noban" || lower == "no ban"
    }

    fun normalize(rawName: String?): String {
        if (isNoneOrEmpty(rawName)) return ""
        val trimmed = rawName!!.trim()
        val key = cleanKey(trimmed)
        return MAPPINGS[key] ?: trimmed
    }

    fun toSlug(rawName: String?): String {
        if (isNoneOrEmpty(rawName)) return ""
        val normalized = normalize(rawName)
        return cleanKey(normalized)
    }

    fun getCanonicalNames(): List<String> = CANONICAL_NAMES
}
