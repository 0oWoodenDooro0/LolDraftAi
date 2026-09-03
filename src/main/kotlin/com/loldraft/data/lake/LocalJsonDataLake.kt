package com.loldraft.data.lake

import com.loldraft.data.models.Game
import kotlinx.serialization.json.Json
import java.io.File

class LocalJsonDataLake(
    val baseDir: File,
) : DataLakeStorage {
    constructor(baseDirPath: String) : this(File(baseDirPath))

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    private val matchesDir: File
        get() = File(baseDir, "matches").apply { mkdirs() }

    private fun sanitizeFileName(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    override fun saveGame(game: Game) {
        val patchDir = File(matchesDir, sanitizeFileName(game.patch)).apply { mkdirs() }
        val targetFile = File(patchDir, "${sanitizeFileName(game.id)}.json")
        val content = json.encodeToString(Game.serializer(), game)
        targetFile.writeText(content)
    }

    override fun getGame(gameId: String): Game? {
        val sanitizedId = sanitizeFileName(gameId)
        val file =
            matchesDir
                .walkTopDown()
                .filter { it.isFile && it.name == "$sanitizedId.json" }
                .firstOrNull() ?: return null

        return try {
            json.decodeFromString(Game.serializer(), file.readText())
        } catch (_: Exception) {
            null
        }
    }

    override fun getGamesByPatch(patch: String): List<Game> {
        val patchDir = File(matchesDir, sanitizeFileName(patch))
        if (!patchDir.exists() || !patchDir.isDirectory) return emptyList()

        return patchDir
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(Game.serializer(), file.readText())
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
    }

    override fun getAllGames(): List<Game> =
        matchesDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file ->
                try {
                    json.decodeFromString(Game.serializer(), file.readText())
                } catch (_: Exception) {
                    null
                }
            }.toList()

    override fun count(): Int =
        matchesDir
            .walkTopDown()
            .count { it.isFile && it.extension == "json" }
}
