package com.loldraft.client.compose.viewmodel

import com.loldraft.analytics.model.AnalyticsSummary
import com.loldraft.analytics.model.AnalyticsTab
import com.loldraft.analytics.model.PlayerAnalyticsRow
import com.loldraft.analytics.model.PlayerChampionStats
import com.loldraft.analytics.model.RosterRoleSlot
import com.loldraft.analytics.model.SortDirection
import com.loldraft.analytics.model.TeamAnalyticsRow
import com.loldraft.analytics.model.TeamRosterMatrix
import com.loldraft.analytics.service.EsportsAnalyticsService
import com.loldraft.data.models.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

data class AnalyticsUiState(
    val currentTab: AnalyticsTab = AnalyticsTab.TEAM_ROSTER_MATRIX,
    val availableTournaments: List<String> = emptyList(),
    val selectedTournament: String? = null,
    val availableSplits: List<String> = emptyList(),
    val selectedSplit: String? = null,
    val availablePatches: List<String> = emptyList(),
    val selectedPatch: String? = null,
    // Tab 1: Team Roster & Pool Matrix
    val availableTeams: List<String> = emptyList(),
    val selectedTeam: String = "T1",
    val substituteOverrides: Map<Role, String> = emptyMap(),
    val rosterMatrix: TeamRosterMatrix? = null,
    val rolePoolSortColumn: Map<Role, String> = emptyMap(),
    val rolePoolSortDirection: Map<Role, SortDirection> = emptyMap(),
    // Tab 2: Players Grid
    val playerRows: List<PlayerAnalyticsRow> = emptyList(),
    val playerLeagueFilter: String? = null,
    val playerSplitFilter: String? = null,
    val playerTeamFilter: String? = null,
    val playerRoleFilter: Role? = null,
    val playerMinGames: Int = 1,
    val playerSearchQuery: String = "",
    val playerSortColumn: String = "games",
    val playerSortDirection: SortDirection = SortDirection.DESCENDING,
    val playerSummary: AnalyticsSummary = AnalyticsSummary(0, 0, 0.0),
    // Tab 3: Teams Grid
    val teamRows: List<TeamAnalyticsRow> = emptyList(),
    val teamLeagueFilter: String? = null,
    val teamSplitFilter: String? = null,
    val teamMinGames: Int = 1,
    val teamSearchQuery: String = "",
    val teamSortColumn: String = "games",
    val teamSortDirection: SortDirection = SortDirection.DESCENDING,
    val teamSummary: AnalyticsSummary = AnalyticsSummary(0, 0, 0.0),
    // Feedback notification
    val notificationMessage: String? = null,
)

class AnalyticsViewModel(
    private val analyticsService: EsportsAnalyticsService,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        refreshInitialData()
    }

    fun refreshInitialData() {
        coroutineScope.launch {
            val tournaments = analyticsService.getAvailableTournaments()
            val splits = analyticsService.getAvailableSplits()
            val patches = analyticsService.getAvailablePatches()
            val teams = analyticsService.getAvailableTeams()

            val defaultTeam = teams.find { it.equals("T1", ignoreCase = true) } ?: teams.firstOrNull() ?: "T1"

            _uiState.update {
                it.copy(
                    availableTournaments = tournaments,
                    availableSplits = splits,
                    availablePatches = patches,
                    availableTeams = teams,
                    selectedTeam = defaultTeam,
                )
            }
            refreshRosterMatrix()
            refreshPlayerGrid()
            refreshTeamGrid()
        }
    }

    fun selectTab(tab: AnalyticsTab) {
        _uiState.update { it.copy(currentTab = tab, notificationMessage = null) }
    }

    // ----------------------------------------------------
    // Tab 1: Team Roster & Pool Matrix
    // ----------------------------------------------------

    fun selectTournament(tournament: String?) {
        _uiState.update {
            val updatedSplits = analyticsService.getAvailableSplits(tournament)
            val newSelectedSplit = if (it.selectedSplit != null && updatedSplits.contains(it.selectedSplit)) it.selectedSplit else null
            val updatedTeams = analyticsService.getAvailableTeams(tournament, newSelectedSplit)
            val newSelectedTeam =
                if (updatedTeams.contains(it.selectedTeam)) {
                    it.selectedTeam
                } else {
                    updatedTeams.firstOrNull() ?: it.selectedTeam
                }
            it.copy(
                selectedTournament = tournament,
                availableSplits = updatedSplits,
                selectedSplit = newSelectedSplit,
                availableTeams = updatedTeams,
                selectedTeam = newSelectedTeam,
                substituteOverrides = emptyMap(), // reset overrides when tournament changes
            )
        }
        refreshRosterMatrix()
    }

    fun selectSplit(split: String?) {
        _uiState.update {
            val updatedTeams = analyticsService.getAvailableTeams(it.selectedTournament, split)
            val newSelectedTeam =
                if (updatedTeams.contains(it.selectedTeam)) {
                    it.selectedTeam
                } else {
                    updatedTeams.firstOrNull() ?: it.selectedTeam
                }
            it.copy(
                selectedSplit = split,
                availableTeams = updatedTeams,
                selectedTeam = newSelectedTeam,
                substituteOverrides = emptyMap(),
            )
        }
        refreshRosterMatrix()
    }

    fun selectPatch(patch: String?) {
        _uiState.update { it.copy(selectedPatch = patch) }
        refreshRosterMatrix()
        refreshPlayerGrid()
        refreshTeamGrid()
    }

    fun selectTeam(team: String) {
        _uiState.update {
            it.copy(
                selectedTeam = team,
                substituteOverrides = emptyMap(),
            )
        }
        refreshRosterMatrix()
    }

    fun selectSubstituteForRole(
        role: Role,
        playerName: String,
    ) {
        _uiState.update {
            it.copy(substituteOverrides = it.substituteOverrides + (role to playerName))
        }
        refreshRosterMatrix()
    }

    fun sortRoleChampionPool(
        role: Role,
        column: String,
    ) {
        val currentCol = _uiState.value.rolePoolSortColumn[role]
        val currentDir = _uiState.value.rolePoolSortDirection[role] ?: SortDirection.DESCENDING
        val nextDir = if (currentCol == column) currentDir.toggle() else SortDirection.DESCENDING

        _uiState.update {
            it.copy(
                rolePoolSortColumn = it.rolePoolSortColumn + (role to column),
                rolePoolSortDirection = it.rolePoolSortDirection + (role to nextDir),
            )
        }
    }

    fun getSortedChampionPool(role: Role): List<PlayerChampionStats> {
        val slot = _uiState.value.rosterMatrix?.roles?.get(role) ?: return emptyList()
        val column = _uiState.value.rolePoolSortColumn[role] ?: "games"
        val dir = _uiState.value.rolePoolSortDirection[role] ?: SortDirection.DESCENDING

        // Default / games sort: gamesPlayed DESC, then winRate DESC, then opponentBanRate DESC
        val comparator =
            when (column.lowercase()) {
                "champion", "championname" -> compareBy<PlayerChampionStats> { it.championName }
                "winrate" -> compareBy<PlayerChampionStats> { it.winRate }.thenBy { it.gamesPlayed }
                "bans", "opponentbans", "opponentbanrate" -> compareBy<PlayerChampionStats> { it.opponentBanRate }.thenBy { it.gamesPlayed }
                "kda" -> compareBy<PlayerChampionStats> { it.kda }
                "dpm" -> compareBy<PlayerChampionStats> { it.dpm }
                else -> compareBy<PlayerChampionStats> { it.gamesPlayed }.thenBy { it.winRate }.thenBy { it.opponentBanRate }
            }

        return if (dir == SortDirection.DESCENDING) {
            slot.championPool.sortedWith(comparator.reversed())
        } else {
            slot.championPool.sortedWith(comparator)
        }
    }

    private fun refreshRosterMatrix() {
        val s = _uiState.value
        val matrix =
            analyticsService.getTeamRosterMatrix(
                teamNameOrId = s.selectedTeam,
                tournament = s.selectedTournament,
                split = s.selectedSplit,
                patch = s.selectedPatch,
                playerOverrides = s.substituteOverrides,
            )
        _uiState.update { it.copy(rosterMatrix = matrix) }
    }

    // ----------------------------------------------------
    // Tab 2: Players Grid
    // ----------------------------------------------------

    fun setPlayerSearchQuery(query: String) {
        _uiState.update { it.copy(playerSearchQuery = query) }
        refreshPlayerGrid()
    }

    fun setPlayerLeagueFilter(league: String?) {
        _uiState.update { it.copy(playerLeagueFilter = league) }
        refreshPlayerGrid()
    }

    fun setPlayerSplitFilter(split: String?) {
        _uiState.update { it.copy(playerSplitFilter = split) }
        refreshPlayerGrid()
    }

    fun setPlayerTeamFilter(team: String?) {
        _uiState.update { it.copy(playerTeamFilter = team) }
        refreshPlayerGrid()
    }

    fun setPlayerRoleFilter(role: Role?) {
        _uiState.update { it.copy(playerRoleFilter = role) }
        refreshPlayerGrid()
    }

    fun setPlayerMinGames(minGames: Int) {
        _uiState.update { it.copy(playerMinGames = minGames) }
        refreshPlayerGrid()
    }

    fun sortPlayerGrid(column: String) {
        val current = _uiState.value
        val nextDir =
            if (current.playerSortColumn == column) {
                current.playerSortDirection.toggle()
            } else {
                SortDirection.DESCENDING
            }
        _uiState.update { it.copy(playerSortColumn = column, playerSortDirection = nextDir) }
        refreshPlayerGrid()
    }

    private fun refreshPlayerGrid() {
        val s = _uiState.value
        val rows =
            analyticsService.queryPlayerGrid(
                tournament = s.playerLeagueFilter ?: s.selectedTournament,
                split = s.playerSplitFilter ?: s.selectedSplit,
                team = s.playerTeamFilter,
                role = s.playerRoleFilter,
                patch = s.selectedPatch,
                minGames = s.playerMinGames,
                searchQuery = s.playerSearchQuery,
                sortColumn = s.playerSortColumn,
                sortDirection = s.playerSortDirection,
            )

        val totalGames = rows.sumOf { it.games }
        val avgWinRate = if (rows.isNotEmpty()) rows.map { it.winRate }.average() else 0.0
        val avgKda = if (rows.isNotEmpty()) rows.map { it.kda }.average() else 0.0
        val avgDpm = if (rows.isNotEmpty()) rows.map { it.avgDpm }.average() else 0.0

        val summary =
            AnalyticsSummary(
                totalCount = rows.size,
                totalGames = totalGames,
                avgWinRate = avgWinRate,
                avgKda = avgKda,
                avgDpm = avgDpm,
            )

        _uiState.update { it.copy(playerRows = rows, playerSummary = summary) }
    }

    // ----------------------------------------------------
    // Tab 3: Teams Grid
    // ----------------------------------------------------

    fun setTeamSearchQuery(query: String) {
        _uiState.update { it.copy(teamSearchQuery = query) }
        refreshTeamGrid()
    }

    fun setTeamLeagueFilter(league: String?) {
        _uiState.update { it.copy(teamLeagueFilter = league) }
        refreshTeamGrid()
    }

    fun setTeamSplitFilter(split: String?) {
        _uiState.update { it.copy(teamSplitFilter = split) }
        refreshTeamGrid()
    }

    fun setTeamMinGames(minGames: Int) {
        _uiState.update { it.copy(teamMinGames = minGames) }
        refreshTeamGrid()
    }

    fun sortTeamGrid(column: String) {
        val current = _uiState.value
        val nextDir =
            if (current.teamSortColumn == column) {
                current.teamSortDirection.toggle()
            } else {
                SortDirection.DESCENDING
            }
        _uiState.update { it.copy(teamSortColumn = column, teamSortDirection = nextDir) }
        refreshTeamGrid()
    }

    private fun refreshTeamGrid() {
        val s = _uiState.value
        val rows =
            analyticsService.queryTeamGrid(
                tournament = s.teamLeagueFilter ?: s.selectedTournament,
                split = s.teamSplitFilter ?: s.selectedSplit,
                patch = s.selectedPatch,
                minGames = s.teamMinGames,
                searchQuery = s.teamSearchQuery,
                sortColumn = s.teamSortColumn,
                sortDirection = s.teamSortDirection,
            )

        val totalGames = rows.sumOf { it.games }
        val avgWinRate = if (rows.isNotEmpty()) rows.map { it.winRate }.average() else 0.0

        val summary =
            AnalyticsSummary(
                totalCount = rows.size,
                totalGames = totalGames,
                avgWinRate = avgWinRate,
            )

        _uiState.update { it.copy(teamRows = rows, teamSummary = summary) }
    }

    // ----------------------------------------------------
    // Export & Clipboard
    // ----------------------------------------------------

    fun copyToClipboard() {
        val s = _uiState.value
        val text =
            when (s.currentTab) {
                AnalyticsTab.PLAYERS_GRID -> analyticsService.exportPlayersToTsv(s.playerRows)
                AnalyticsTab.TEAMS_GRID -> analyticsService.exportTeamsToTsv(s.teamRows)
                AnalyticsTab.TEAM_ROSTER_MATRIX -> exportRosterMatrixToTsv()
            }
        try {
            val sel = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            _uiState.update { it.copy(notificationMessage = "已複製表格至剪貼簿 (可在 Excel / Sheets 中直接按 Ctrl+V 貼上)") }
        } catch (e: Exception) {
            _uiState.update { it.copy(notificationMessage = "複製失敗: ${e.message}") }
        }
    }

    fun exportToCsv() {
        val s = _uiState.value
        try {
            val exportDir = File("exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file: File
            val content: String

            when (s.currentTab) {
                AnalyticsTab.PLAYERS_GRID -> {
                    file = File(exportDir, "players_analytics_${System.currentTimeMillis()}.csv")
                    content = analyticsService.exportPlayersToCsv(s.playerRows)
                }
                AnalyticsTab.TEAMS_GRID -> {
                    file = File(exportDir, "teams_analytics_${System.currentTimeMillis()}.csv")
                    content = analyticsService.exportTeamsToCsv(s.teamRows)
                }
                AnalyticsTab.TEAM_ROSTER_MATRIX -> {
                    val teamName = s.selectedTeam.replace(" ", "_")
                    file = File(exportDir, "roster_pool_${teamName}_${System.currentTimeMillis()}.csv")
                    content = exportRosterMatrixToCsv()
                }
            }

            file.writeText(content)
            _uiState.update { it.copy(notificationMessage = "已匯出 CSV 至: ${file.absolutePath}") }
        } catch (e: Exception) {
            _uiState.update { it.copy(notificationMessage = "匯出失敗: ${e.message}") }
        }
    }

    fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    private fun exportRosterMatrixToTsv(): String {
        val matrix = _uiState.value.rosterMatrix ?: return ""
        val sb = StringBuilder()
        sb.append("位置\t選手\t英雄池數量\t英雄\t場次\t勝場\t敗場\t勝率%\t敵方Ban數\t敵方Ban率%\tKDA\tDPM\tCSPM\n")
        for (role in listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)) {
            val slot = matrix.roles[role] ?: continue
            val poolCount = slot.championPoolCount
            for (c in slot.championPool) {
                val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
                val obr = String.format(java.util.Locale.US, "%.1f%%", c.opponentBanRate * 100)
                val kda = String.format(java.util.Locale.US, "%.2f", c.kda)
                val dpm = String.format(java.util.Locale.US, "%.1f", c.dpm)
                val cspm = String.format(java.util.Locale.US, "%.1f", c.cspm)
                sb.append("${role.name}\t${slot.currentPlayer}\t$poolCount\t${c.championName}\t${c.gamesPlayed}\t${c.wins}\t${c.losses}\t$wr\t${c.opponentBans}\t$obr\t$kda\t$dpm\t$cspm\n")
            }
        }
        return sb.toString()
    }

    private fun exportRosterMatrixToCsv(): String {
        val matrix = _uiState.value.rosterMatrix ?: return ""
        val sb = StringBuilder()
        sb.append("位置,選手,英雄池數量,英雄,場次,勝場,敗場,勝率%,敵方Ban數,敵方Ban率%,KDA,DPM,CSPM\n")
        for (role in listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)) {
            val slot = matrix.roles[role] ?: continue
            val poolCount = slot.championPoolCount
            for (c in slot.championPool) {
                val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
                val obr = String.format(java.util.Locale.US, "%.1f%%", c.opponentBanRate * 100)
                val kda = String.format(java.util.Locale.US, "%.2f", c.kda)
                val dpm = String.format(java.util.Locale.US, "%.1f", c.dpm)
                val cspm = String.format(java.util.Locale.US, "%.1f", c.cspm)
                sb.append("${role.name},${slot.currentPlayer},$poolCount,${c.championName},${c.gamesPlayed},${c.wins},${c.losses},$wr,${c.opponentBans},$obr,$kda,$dpm,$cspm\n")
            }
        }
        return sb.toString()
    }
}
