package com.loldraft.client.compose.viewmodel

import com.loldraft.analytics.model.AnalyticsSummary
import com.loldraft.analytics.model.AnalyticsTab
import com.loldraft.analytics.model.PlayerAnalyticsRow
import com.loldraft.analytics.model.PlayerChampionStats
import com.loldraft.analytics.model.SortDirection
import com.loldraft.analytics.model.TeamAnalyticsRow
import com.loldraft.analytics.model.TeamRosterMatrix
import com.loldraft.analytics.service.EsportsAnalyticsService
import com.loldraft.analytics.service.SoloQIntelligenceService
import com.loldraft.data.models.Role
import com.loldraft.data.soloq.models.PlayerSummonerAccount
import com.loldraft.data.soloq.models.SoloQPlayerIntelligence
import com.loldraft.data.soloq.models.SoloQRoleFilter
import com.loldraft.data.soloq.riot.RiotApiException
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

data class SoloQPlayerItem(
    val playerName: String,
    val role: Role,
    val isBound: Boolean,
    val riotId: String? = null,
)

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
    // Tab 4: SoloQ Intelligence Tracker
    val soloQAvailableLeagues: List<String> = emptyList(),
    val selectedSoloQLeague: String? = null,
    val soloQAvailableTeams: List<String> = emptyList(),
    val selectedSoloQTeam: String? = null,
    val availableSoloQPlayers: List<SoloQPlayerItem> = emptyList(),
    val selectedSoloQPlayer: String? = null,
    val selectedSoloQAccount: PlayerSummonerAccount? = null,
    val soloQTimeRangeDays: Int = 14,
    val soloQRoleFilter: SoloQRoleFilter = SoloQRoleFilter.ALL,
    val soloQIntelligence: SoloQPlayerIntelligence? = null,
    val isSoloQSyncing: Boolean = false,
    val soloQSyncError: String? = null,
    val riotApiKeyInput: String = "",
    val isApiKeyDialogOpen: Boolean = false,
    // Add/Edit Player Account Dialog
    val isAddPlayerDialogOpen: Boolean = false,
    val addPlayerLeague: String = "",
    val addPlayerTeam: String = "",
    val addPlayerName: String = "",
    val addPlayerRole: Role = Role.MID,
    val addPlayerGameName: String = "",
    val addPlayerTagLine: String = "",
    val addPlayerPlatform: String = "KR",
    val addPlayerRegion: String = "asia",
    // Feedback notification
    val notificationMessage: String? = null,
)

class AnalyticsViewModel(
    val analyticsService: EsportsAnalyticsService,
    val soloQService: SoloQIntelligenceService = SoloQIntelligenceService(),
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
                    selectedSoloQLeague = tournaments.firstOrNull(),
                )
            }
            refreshRosterMatrix()
            refreshPlayerGrid()
            refreshTeamGrid()
            refreshSoloQTeamsAndPlayers(keepSelection = true)
        }
    }

    fun selectTab(tab: AnalyticsTab) {
        _uiState.update { it.copy(currentTab = tab, notificationMessage = null) }
        if (tab == AnalyticsTab.SOLOQ_TRACKER) {
            refreshSoloQTeamsAndPlayers(keepSelection = true)
            refreshSoloQData()
        }
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
                substituteOverrides = emptyMap(),
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

        val comparator =
            when (column.lowercase()) {
                "champion", "championname" -> compareBy<PlayerChampionStats> { it.championName }
                "winrate" -> compareBy<PlayerChampionStats> { it.winRate }.thenBy { it.gamesPlayed }
                "bans", "opponentbans", "opponentbanrate" -> compareBy<PlayerChampionStats> { it.opponentBanRate }.thenBy { it.gamesPlayed }
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
    // Tab 4: SoloQ Intelligence Tracker
    // ----------------------------------------------------

    fun selectSoloQLeague(league: String?) {
        _uiState.update { it.copy(selectedSoloQLeague = league) }
        refreshSoloQTeamsAndPlayers(keepSelection = false)
    }

    fun selectSoloQTeam(teamId: String?) {
        _uiState.update { it.copy(selectedSoloQTeam = teamId) }
        refreshSoloQPlayers(keepSelection = false)
    }

    fun selectSoloQPlayer(playerName: String?) {
        val account = playerName?.let { soloQService.soloQRepository.getAccount(it) }
        _uiState.update {
            it.copy(
                selectedSoloQPlayer = playerName,
                selectedSoloQAccount = account,
            )
        }
        refreshSoloQData()
    }

    fun selectSoloQTimeRange(days: Int) {
        _uiState.update { it.copy(soloQTimeRangeDays = days) }
        refreshSoloQData()
    }

    fun selectSoloQRoleFilter(filter: SoloQRoleFilter) {
        _uiState.update { it.copy(soloQRoleFilter = filter) }
        refreshSoloQData()
    }

    fun refreshSoloQTeamsAndPlayers(keepSelection: Boolean = true) {
        val s = _uiState.value
        val tournaments = analyticsService.getAvailableTournaments()
        val customLeagues =
            soloQService.soloQRepository.getAllRegisteredPlayers()
                .map { it.league }
                .filter { it.isNotBlank() }
        val allLeagues = (tournaments + customLeagues).distinct().sorted()

        val activeLeague = s.selectedSoloQLeague ?: allLeagues.firstOrNull()
        val dbTeams = analyticsService.getAvailableTeams(activeLeague)
        val registeredAccounts = soloQService.soloQRepository.getAllRegisteredPlayers()
        val customTeams =
            registeredAccounts
                .filter { activeLeague == null || it.league.equals(activeLeague, ignoreCase = true) }
                .map { it.teamId }
        val allTeams = (dbTeams + customTeams).distinct().sorted()

        val activeTeam =
            if (keepSelection && s.selectedSoloQTeam != null && allTeams.contains(s.selectedSoloQTeam)) {
                s.selectedSoloQTeam
            } else if (allTeams.contains("T1")) {
                "T1"
            } else {
                allTeams.firstOrNull()
            }

        _uiState.update {
            it.copy(
                soloQAvailableLeagues = allLeagues,
                selectedSoloQLeague = activeLeague,
                soloQAvailableTeams = allTeams,
                selectedSoloQTeam = activeTeam,
            )
        }

        refreshSoloQPlayers(keepSelection = keepSelection)
    }

    private fun refreshSoloQPlayers(keepSelection: Boolean = true) {
        val s = _uiState.value
        val targetTeam = s.selectedSoloQTeam ?: return
        val targetLeague = s.selectedSoloQLeague

        // 1. Get pro players from dataset
        val proPlayers = analyticsService.getPlayersForTeam(targetTeam, targetLeague)

        // 2. Get registered accounts from repository
        val registered = soloQService.soloQRepository.getPlayersForTeam(targetTeam)
        val registeredMap = registered.associateBy { it.playerId.lowercase() }

        val playerItems = mutableListOf<SoloQPlayerItem>()

        for ((pName, pRole) in proPlayers) {
            val bound = registeredMap[pName.lowercase()]
            playerItems.add(
                SoloQPlayerItem(
                    playerName = pName,
                    role = bound?.primaryRole ?: pRole,
                    isBound = (bound != null),
                    riotId = bound?.riotId,
                ),
            )
        }

        // Add any manually registered player not in dataset
        val existingNames = playerItems.map { it.playerName.lowercase() }.toSet()
        for (reg in registered) {
            if (!existingNames.contains(reg.playerId.lowercase())) {
                playerItems.add(
                    SoloQPlayerItem(
                        playerName = reg.playerId,
                        role = reg.primaryRole,
                        isBound = true,
                        riotId = reg.riotId,
                    ),
                )
            }
        }

        playerItems.sortWith(compareBy({ it.role.ordinal }, { it.playerName }))

        val activePlayer =
            if (keepSelection && s.selectedSoloQPlayer != null && playerItems.any { it.playerName == s.selectedSoloQPlayer }) {
                s.selectedSoloQPlayer
            } else {
                playerItems.find { it.role == Role.MID }?.playerName
                    ?: playerItems.firstOrNull()?.playerName
            }

        val account = activePlayer?.let { soloQService.soloQRepository.getAccount(it) }

        _uiState.update {
            it.copy(
                availableSoloQPlayers = playerItems,
                selectedSoloQPlayer = activePlayer,
                selectedSoloQAccount = account,
            )
        }
        refreshSoloQData()
    }

    fun syncRiotApiData() {
        val pName = _uiState.value.selectedSoloQPlayer ?: return
        val account = soloQService.soloQRepository.getAccount(pName)
        if (account == null) {
            val msg = "請先為選手 $pName 綁定 Riot ID 才能同步"
            _uiState.update { it.copy(soloQSyncError = msg, notificationMessage = msg) }
            return
        }
        if (soloQService.soloQRepository.riotApiClient.apiKey.isNullOrBlank()) {
            val msg = "尚未設定 Riot API Key，請點擊右上角金鑰按鈕進行設定"
            _uiState.update { it.copy(soloQSyncError = msg, notificationMessage = msg) }
            return
        }

        coroutineScope.launch {
            _uiState.update { it.copy(isSoloQSyncing = true, soloQSyncError = null) }
            try {
                val state = _uiState.value
                val intel =
                    soloQService.analyzePlayer(
                        playerId = pName,
                        timeRangeDays = state.soloQTimeRangeDays,
                        roleFilter = state.soloQRoleFilter,
                        forceSync = true,
                    )
                val matchCount = intel?.recentMatches?.size ?: 0
                if (matchCount == 0) {
                    val noMatchMsg = "已連線至 Riot 伺服器，但在伺服器 ${account.region} / ${account.platformId} 查無該帳號 (${account.riotId}) 近期單雙排對局紀錄"
                    _uiState.update {
                        it.copy(
                            soloQIntelligence = intel,
                            isSoloQSyncing = false,
                            soloQSyncError = noMatchMsg,
                            notificationMessage = noMatchMsg,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            soloQIntelligence = intel,
                            isSoloQSyncing = false,
                            soloQSyncError = null,
                            notificationMessage = "成功同步 $pName 最新天梯紀錄：共抓取 $matchCount 場",
                        )
                    }
                }
            } catch (e: RiotApiException) {
                val errorMsg = e.message ?: "連線錯誤"
                _uiState.update {
                    it.copy(
                        isSoloQSyncing = false,
                        soloQSyncError = errorMsg,
                        notificationMessage = "同步失敗：$errorMsg",
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知錯誤"
                _uiState.update {
                    it.copy(
                        isSoloQSyncing = false,
                        soloQSyncError = "連線失敗：$errorMsg",
                        notificationMessage = "同步失敗：$errorMsg",
                    )
                }
            }
        }
    }

    fun refreshSoloQData(forceSync: Boolean = false) {
        coroutineScope.launch {
            val state = _uiState.value
            val pName = state.selectedSoloQPlayer
            if (pName.isNullOrBlank()) {
                _uiState.update { it.copy(soloQIntelligence = null) }
                return@launch
            }
            val account = soloQService.soloQRepository.getAccount(pName)
            if (account == null) {
                _uiState.update { it.copy(selectedSoloQAccount = null, soloQIntelligence = null) }
                return@launch
            }
            val intel =
                soloQService.analyzePlayer(
                    playerId = pName,
                    timeRangeDays = state.soloQTimeRangeDays,
                    roleFilter = state.soloQRoleFilter,
                    forceSync = forceSync,
                )
            _uiState.update {
                it.copy(
                    selectedSoloQAccount = account,
                    soloQIntelligence = intel,
                )
            }
        }
    }

    fun navigateToSoloQ(
        playerId: String,
        teamId: String? = null,
    ) {
        val s = _uiState.value
        val targetTeam = teamId ?: s.selectedTeam
        val targetLeague = s.selectedTournament
        val account = soloQService.soloQRepository.getAccount(playerId)

        _uiState.update {
            it.copy(
                currentTab = AnalyticsTab.SOLOQ_TRACKER,
                selectedSoloQLeague = account?.league?.takeIf { it.isNotBlank() } ?: targetLeague ?: it.selectedSoloQLeague,
                selectedSoloQTeam = targetTeam,
                selectedSoloQPlayer = playerId,
                selectedSoloQAccount = account,
            )
        }
        refreshSoloQTeamsAndPlayers(keepSelection = true)
        refreshSoloQData()
    }

    fun openAddPlayerDialog(
        open: Boolean,
        prefillPlayerName: String? = null,
    ) {
        val s = _uiState.value
        val pName = prefillPlayerName ?: s.selectedSoloQPlayer ?: ""
        val existing = if (pName.isNotBlank()) soloQService.soloQRepository.getAccount(pName) else null

        val pRole =
            existing?.primaryRole
                ?: s.availableSoloQPlayers.find { it.playerName.equals(pName, ignoreCase = true) }?.role
                ?: Role.MID

        _uiState.update {
            it.copy(
                isAddPlayerDialogOpen = open,
                addPlayerLeague = existing?.league ?: it.selectedSoloQLeague ?: it.selectedTournament ?: "LCK",
                addPlayerTeam = existing?.teamId ?: it.selectedSoloQTeam ?: it.selectedTeam,
                addPlayerName = pName,
                addPlayerRole = pRole,
                addPlayerGameName = existing?.gameName ?: "",
                addPlayerTagLine = existing?.tagLine ?: "KR1",
                addPlayerPlatform = existing?.platformId ?: "KR",
                addPlayerRegion = existing?.region ?: "asia",
            )
        }
    }

    fun setAddPlayerFields(
        league: String? = null,
        team: String? = null,
        name: String? = null,
        role: Role? = null,
        gameName: String? = null,
        tagLine: String? = null,
        platform: String? = null,
        region: String? = null,
    ) {
        _uiState.update {
            it.copy(
                addPlayerLeague = league ?: it.addPlayerLeague,
                addPlayerTeam = team ?: it.addPlayerTeam,
                addPlayerName = name ?: it.addPlayerName,
                addPlayerRole = role ?: it.addPlayerRole,
                addPlayerGameName = gameName ?: it.addPlayerGameName,
                addPlayerTagLine = tagLine ?: it.addPlayerTagLine,
                addPlayerPlatform = platform ?: it.addPlayerPlatform,
                addPlayerRegion = region ?: it.addPlayerRegion,
            )
        }
    }

    fun savePlayerAccount() {
        val s = _uiState.value
        val name = s.addPlayerName.trim()
        val team = s.addPlayerTeam.trim()
        val gameName = s.addPlayerGameName.trim()
        val tagLine = s.addPlayerTagLine.trim()

        if (name.isBlank() || team.isBlank() || gameName.isBlank() || tagLine.isBlank()) {
            _uiState.update { it.copy(notificationMessage = "請填寫完整選手名稱、隊伍與 Riot ID (名稱與 Tag)") }
            return
        }

        val account =
            PlayerSummonerAccount(
                playerId = name,
                teamId = team,
                primaryRole = s.addPlayerRole,
                gameName = gameName,
                tagLine = tagLine,
                league = s.addPlayerLeague.trim(),
                region = s.addPlayerRegion.trim(),
                platformId = s.addPlayerPlatform.trim(),
            )

        soloQService.soloQRepository.registerAccount(account)
        _uiState.update {
            it.copy(
                isAddPlayerDialogOpen = false,
                selectedSoloQLeague = if (it.selectedSoloQLeague == null) null else account.league,
                selectedSoloQTeam = account.teamId,
                selectedSoloQPlayer = account.playerId,
                selectedSoloQAccount = account,
                notificationMessage = "已儲存選手 ${account.playerId} (${account.riotId})",
            )
        }
        refreshSoloQTeamsAndPlayers(keepSelection = true)
        syncRiotApiData()
    }

    fun deletePlayerAccount(playerId: String) {
        soloQService.soloQRepository.deleteAccount(playerId)
        _uiState.update {
            val isCurrent = it.selectedSoloQPlayer.equals(playerId, ignoreCase = true)
            it.copy(
                selectedSoloQAccount = if (isCurrent) null else it.selectedSoloQAccount,
                notificationMessage = "已移除選手 $playerId 的天梯綁定帳號",
            )
        }
        refreshSoloQPlayers(keepSelection = true)
        refreshSoloQData()
    }

    fun openApiKeyDialog(open: Boolean) {
        _uiState.update {
            it.copy(
                isApiKeyDialogOpen = open,
                riotApiKeyInput = soloQService.soloQRepository.riotApiClient.apiKey ?: "",
            )
        }
    }

    fun setRiotApiKeyInput(input: String) {
        _uiState.update { it.copy(riotApiKeyInput = input) }
    }

    fun saveRiotApiKey(key: String) {
        soloQService.soloQRepository.riotApiClient.apiKey = key.trim()
        _uiState.update {
            it.copy(
                isApiKeyDialogOpen = false,
                notificationMessage = if (key.isBlank()) "已清除 Riot API Key" else "已設定 Riot API Key 並更新",
            )
        }
        syncRiotApiData()
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
                AnalyticsTab.SOLOQ_TRACKER -> exportSoloQToTsv()
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
                AnalyticsTab.SOLOQ_TRACKER -> {
                    val pName = (s.selectedSoloQPlayer ?: "player").replace(" ", "_")
                    file = File(exportDir, "soloq_${pName}_${System.currentTimeMillis()}.csv")
                    content = exportSoloQToCsv()
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

    private fun exportSoloQToTsv(): String {
        val intel = _uiState.value.soloQIntelligence ?: return ""
        val sb = StringBuilder()
        sb.append("選手\t路線\t英雄\t是否本職\tSoloQ場次\t勝場\t敗場\t勝率%\tKDA\t職業賽出場數\t特殊秘密武器\t標籤\n")
        for (c in intel.championSummaries) {
            val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
            val isPrimary = if (c.isPrimaryRole) "是" else "否"
            val isSecret = if (c.isSecretPick) "是" else "否"
            sb.append("${intel.playerId}\t${c.playedRole}\t${c.championName}\t$isPrimary\t${c.gamesPlayed}\t${c.wins}\t${c.losses}\t$wr\t${c.kda}\t${c.playerProGames}\t$isSecret\t${c.secretBadgeText ?: ""}\n")
        }
        return sb.toString()
    }

    private fun exportSoloQToCsv(): String {
        val intel = _uiState.value.soloQIntelligence ?: return ""
        val sb = StringBuilder()
        sb.append("選手,路線,英雄,是否本職,SoloQ場次,勝場,敗場,勝率%,KDA,職業賽出場數,特殊秘密武器,標籤\n")
        for (c in intel.championSummaries) {
            val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
            val isPrimary = if (c.isPrimaryRole) "是" else "否"
            val isSecret = if (c.isSecretPick) "是" else "否"
            sb.append("${intel.playerId},${c.playedRole},${c.championName},$isPrimary,${c.gamesPlayed},${c.wins},${c.losses},$wr,${c.kda},${c.playerProGames},$isSecret,\"${c.secretBadgeText ?: ""}\"\n")
        }
        return sb.toString()
    }

    private fun exportRosterMatrixToTsv(): String {
        val matrix = _uiState.value.rosterMatrix ?: return ""
        val sb = StringBuilder()
        sb.append("位置\t選手\t英雄池數量\t英雄\t場次\t勝場\t敗場\t勝率%\t敵方Ban數\t敵方Ban率%\n")
        for (role in listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)) {
            val slot = matrix.roles[role] ?: continue
            val poolCount = slot.championPoolCount
            for (c in slot.championPool) {
                val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
                val obr = String.format(java.util.Locale.US, "%.1f%%", c.opponentBanRate * 100)
                sb.append("${role.name}\t${slot.currentPlayer}\t$poolCount\t${c.championName}\t${c.gamesPlayed}\t${c.wins}\t${c.losses}\t$wr\t${c.opponentBans}\t$obr\n")
            }
        }
        return sb.toString()
    }

    private fun exportRosterMatrixToCsv(): String {
        val matrix = _uiState.value.rosterMatrix ?: return ""
        val sb = StringBuilder()
        sb.append("位置,選手,英雄池數量,英雄,場次,勝場,敗場,勝率%,敵方Ban數,敵方Ban率%\n")
        for (role in listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)) {
            val slot = matrix.roles[role] ?: continue
            val poolCount = slot.championPoolCount
            for (c in slot.championPool) {
                val wr = String.format(java.util.Locale.US, "%.1f%%", c.winRate * 100)
                val obr = String.format(java.util.Locale.US, "%.1f%%", c.opponentBanRate * 100)
                sb.append("${role.name},${slot.currentPlayer},$poolCount,${c.championName},${c.gamesPlayed},${c.wins},${c.losses},$wr,${c.opponentBans},$obr\n")
            }
        }
        return sb.toString()
    }
}
