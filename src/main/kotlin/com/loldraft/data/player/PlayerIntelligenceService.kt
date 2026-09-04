package com.loldraft.data.player

import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import kotlinx.serialization.Serializable

@Serializable
data class PlayerRosterIntelligence(
    val role: Role,
    val playerId: String,
    val signaturePicks: List<SignaturePick> = emptyList(),
    val recentSoloQ7Days: List<SoloQChampionStats> = emptyList(),
    val practiceSpikes: List<SpikeAlert> = emptyList(),
    val dossier: PlayerIntelligenceDossier? = null,
)

class PlayerIntelligenceService(
    val tracker: PlayerTracker = PlayerTracker(),
    val accountRegistry: PlayerAccountRegistry = PlayerAccountRegistry(),
) {
    fun getTeamRosterIntelligence(
        teamId: String,
        proGames: List<Game>,
        soloQGames: List<SoloQGame> = emptyList(),
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): Map<Role, PlayerRosterIntelligence> {
        val teamGames = proGames.filter { matchesTeam(it.blueTeam, teamId) || matchesTeam(it.redTeam, teamId) }
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
            val topPlayer = candidatePlayers.maxByOrNull { it.value }?.key?.second ?: continue

            val linkedAccounts = accountRegistry.getAccountsForPlayer(topPlayer)
            val linkedAccountIds = linkedAccounts.map { it.accountId.lowercase() }.toSet()
            val linkedSummonerNames = linkedAccounts.map { it.summonerName.lowercase() }.toSet()

            val playerSoloQ =
                soloQGames.filter { sq ->
                    val accId = sq.accountId.lowercase()
                    linkedAccountIds.contains(accId) ||
                        linkedSummonerNames.contains(accId) ||
                        accId.contains(topPlayer.lowercase()) ||
                        topPlayer.lowercase().contains(accId)
                }

            val dossier =
                tracker.generateDossier(
                    playerId = topPlayer,
                    proGames = teamGames,
                    soloQGames = playerSoloQ,
                    playerRole = role,
                    referenceTimeMs = referenceTimeMs,
                )

            result[role] =
                PlayerRosterIntelligence(
                    role = role,
                    playerId = topPlayer,
                    signaturePicks = dossier.careerStats.signaturePicks,
                    recentSoloQ7Days = dossier.recentSoloQ7Days,
                    practiceSpikes = dossier.activeSpikeAlerts,
                    dossier = dossier,
                )
        }

        return result
    }

    private fun matchesTeam(
        team: Team,
        identifier: String,
    ): Boolean =
        team.id.equals(identifier, ignoreCase = true) ||
            team.name.equals(identifier, ignoreCase = true) ||
            team.code.equals(identifier, ignoreCase = true)
}
