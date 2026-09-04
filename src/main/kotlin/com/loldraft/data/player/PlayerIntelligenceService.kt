package com.loldraft.data.player

import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import com.loldraft.server.ProMatchRepository
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class PlayerRosterIntelligence(
    val role: Role,
    val playerId: String,
    val signaturePicks: List<SignaturePick> = emptyList(),
    val dossier: PlayerIntelligenceDossier? = null,
)

class PlayerIntelligenceService(
    val proMatchRepository: ProMatchRepository? = null,
    val proGames: List<Game>? = null,
    val careerAnalyzer: PlayerCareerAnalyzer = PlayerCareerAnalyzer(),
    val confidenceCalculator: BlindPickConfidenceCalculator = BlindPickConfidenceCalculator(),
    val playerTracker: PlayerTracker =
        PlayerTracker(
            careerAnalyzer = careerAnalyzer,
            confidenceCalculator = confidenceCalculator,
        ),
) {
    val tracker: PlayerTracker get() = playerTracker

    private val extraProGames = CopyOnWriteArrayList<Game>(proGames ?: emptyList())

    fun getAllProGames(): List<Game> {
        val repoGames = proMatchRepository?.getAllGames() ?: emptyList()
        return if (extraProGames.isEmpty()) repoGames else repoGames + extraProGames
    }

    fun addProGames(games: Collection<Game>) {
        extraProGames.addAll(games)
    }

    fun getPlayerDossier(
        playerId: String,
        role: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): PlayerIntelligenceDossier {
        val allGames = getAllProGames()
        return playerTracker.generateDossier(
            playerId = playerId,
            proGames = allGames,
            playerRole = role,
            referenceTimeMs = referenceTimeMs,
        )
    }

    fun getPlayerProfile(
        playerId: String,
        role: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): ProPlayerDetailedProfile {
        val dossier = getPlayerDossier(playerId, role, referenceTimeMs)
        val effectiveRole =
            role
                ?: dossier
                    .careerStats
                    .roleDistribution
                    .maxByOrNull { it.value }
                    ?.key
                ?: Role.MID
        return ProPlayerDetailedProfile.fromDossier(effectiveRole, dossier)
    }

    fun getTeamPlayerProfiles(
        teamId: String,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): List<ProPlayerDetailedProfile> {
        val standardRoles = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)

        // 1. Try resolving roster from repository
        val roster = proMatchRepository?.getTeamRoster(teamId)
        if (roster != null && roster.isNotEmpty()) {
            return roster.map { entry ->
                getPlayerProfile(
                    playerId = entry.playerName,
                    role = entry.role,
                    referenceTimeMs = referenceTimeMs,
                )
            }
        }

        // 2. Otherwise resolve from all available pro games
        val teamGames = getAllProGames().filter { matchesTeam(it.blueTeam, teamId) || matchesTeam(it.redTeam, teamId) }
        if (teamGames.isNotEmpty()) {
            val statsByRolePlayer = mutableMapOf<Pair<Role, String>, Int>()
            for (game in teamGames) {
                val isBlue = matchesTeam(game.blueTeam, teamId)
                val picks = if (isBlue) game.draftState.bluePicks else game.draftState.redPicks
                for (pick in picks) {
                    val r = pick.role ?: continue
                    val p = pick.playerId?.takeIf { it.isNotBlank() } ?: continue
                    val key = r to p
                    statsByRolePlayer[key] = (statsByRolePlayer[key] ?: 0) + 1
                }
            }

            val profiles = mutableListOf<ProPlayerDetailedProfile>()
            for (role in standardRoles) {
                val playersForRole = statsByRolePlayer.filterKeys { it.first == role }
                val topPlayer = playersForRole.maxByOrNull { it.value }
                if (topPlayer != null) {
                    val playerName = topPlayer.key.second
                    profiles.add(
                        getPlayerProfile(
                            playerId = playerName,
                            role = role,
                            referenceTimeMs = referenceTimeMs,
                        ),
                    )
                }
            }
            return profiles
        }

        return emptyList()
    }

    fun getTeamRosterIntelligence(
        teamId: String,
        proGames: List<Game> = getAllProGames(),
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): Map<Role, PlayerRosterIntelligence> {
        val allGames = if (proGames.isNotEmpty()) proGames else getAllProGames()
        val teamGames = allGames.filter { matchesTeam(it.blueTeam, teamId) || matchesTeam(it.redTeam, teamId) }
        val playerGamesByRole = mutableMapOf<Pair<Role, String>, Int>()

        for (game in teamGames) {
            val isBlue = matchesTeam(game.blueTeam, teamId)
            val picks = if (isBlue) game.draftState.bluePicks else game.draftState.redPicks

            for (pick in picks) {
                val role = pick.role ?: continue
                val player = pick.playerId?.takeIf { it.isNotBlank() } ?: continue
                playerGamesByRole.merge(role to player, 1, Int::plus)
            }
        }

        val result = mutableMapOf<Role, PlayerRosterIntelligence>()
        val standardRoles = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)

        for (role in standardRoles) {
            val candidatePlayers = playerGamesByRole.filterKeys { it.first == role }
            val topPlayer =
                candidatePlayers.maxByOrNull { it.value }?.key?.second
                    ?: proMatchRepository?.getTeamRoster(teamId)?.find { it.role == role }?.playerName
                    ?: continue

            val dossier =
                playerTracker.generateDossier(
                    playerId = topPlayer,
                    proGames = teamGames,
                    playerRole = role,
                    referenceTimeMs = referenceTimeMs,
                )

            result[role] =
                PlayerRosterIntelligence(
                    role = role,
                    playerId = topPlayer,
                    signaturePicks = dossier.careerStats.signaturePicks,
                    dossier = dossier,
                )
        }

        return result
    }

    private fun isKnownTeamPlayer(
        teamId: String,
        playerId: String,
    ): Boolean {
        val t = teamId.uppercase()
        val p = playerId.uppercase()
        return when {
            t.contains("T1") -> p in setOf("ZEUS", "ONER", "FAKER", "GUMAYUSI", "KERIA", "DORAN")
            t.contains("GEN") -> p in setOf("KIIN", "CANYON", "CHOVY", "PEYZ", "LEHENDS", "DURO", "RULER")
            t.contains("BLG") -> p in setOf("BIN", "WEI", "XUN", "KNIGHT", "ELK", "ON")
            t.contains("HLE") -> p in setOf("DORAN", "PEANUT", "ZEKA", "VIPER", "DELIGHT", "ZEUS")
            t.contains("DK") -> p in setOf("KINGEN", "LUCID", "SHOWMAKER", "AIMING", "MOHAM", "KELLIN")
            else -> false
        }
    }

    private fun getKnownPlayerRole(playerId: String): Role? {
        val p = playerId.uppercase()
        return when (p) {
            "ZEUS", "BIN", "KIIN", "KINGEN", "DORAN", "THESHY", "369", "BREATHE" -> Role.TOP
            "ONER", "CANYON", "WEI", "XUN", "PEANUT", "LUCID", "TARZAN", "KANAVI" -> Role.JUNGLE
            "FAKER", "CHOVY", "KNIGHT", "ZEKA", "SHOWMAKER", "ROOKIE", "SCOUT", "YAGAO", "BBD" -> Role.MID
            "GUMAYUSI", "PEYZ", "ELK", "VIPER", "AIMING", "RULER", "JACKEYLOVE", "GALA", "DEFT" -> Role.BOT
            "KERIA", "LEHENDS", "ON", "DELIGHT", "MOHAM", "KELLIN", "MEIKO", "CRISP", "MISSING" -> Role.SUPPORT
            else -> null
        }
    }

    private fun matchesTeam(
        team: Team,
        identifier: String,
    ): Boolean =
        team.id.equals(identifier, ignoreCase = true) ||
            team.name.equals(identifier, ignoreCase = true) ||
            team.code.equals(identifier, ignoreCase = true)
}
