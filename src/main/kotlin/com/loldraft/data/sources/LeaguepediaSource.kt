package com.loldraft.data.sources

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer

class LeaguepediaSource(
    val client: LeaguepediaClient,
) {
    suspend fun fetchGames(
        tournament: String? = null,
        limit: Int = 50,
    ): List<Game> {
        val pbRows = client.queryPicksAndBans(tournament, limit)
        if (pbRows.isEmpty()) return emptyList()

        val scoreboardRows = client.queryScoreboardGames(tournament, limit)
        val scoreboardByGameId =
            scoreboardRows
                .mapNotNull { row ->
                    row.gameId?.let { it to row }
                }.toMap()

        val scoreboardByMatchId =
            scoreboardRows
                .mapNotNull { row ->
                    row.matchId?.let { it to row }
                }.toMap()

        return pbRows.mapIndexed { index, pb ->
            val gameId = pb.gameId ?: pb.matchId ?: "game_$index"
            val sb = scoreboardByGameId[pb.gameId] ?: scoreboardByMatchId[pb.matchId]

            val rawPatch = pb.patch ?: sb?.patch
            val patch = PatchNormalizer.normalize(rawPatch)

            val blueTeamName = pb.team1 ?: sb?.team1 ?: "BlueTeam"
            val redTeamName = pb.team2 ?: sb?.team2 ?: "RedTeam"

            val blueTeam =
                Team(
                    id = ChampionNormalizer.toSlug(blueTeamName),
                    name = blueTeamName,
                    code = blueTeamName,
                )
            val redTeam =
                Team(
                    id = ChampionNormalizer.toSlug(redTeamName),
                    name = redTeamName,
                    code = redTeamName,
                )

            val winner =
                when (pb.winTeam ?: sb?.winTeam) {
                    blueTeamName -> Side.BLUE
                    redTeamName -> Side.RED
                    else -> null
                }

            val durationSeconds = sb?.gamelengthNumber?.let { (it * 60).toInt() }
            val date = pb.dateTimeUtc ?: sb?.dateTimeUtc

            val rawTurns =
                listOf(
                    DraftTurn(1, Side.BLUE, ActionType.BAN, ChampionNormalizer.normalize(pb.team1Ban1)),
                    DraftTurn(2, Side.RED, ActionType.BAN, ChampionNormalizer.normalize(pb.team2Ban1)),
                    DraftTurn(3, Side.BLUE, ActionType.BAN, ChampionNormalizer.normalize(pb.team1Ban2)),
                    DraftTurn(4, Side.RED, ActionType.BAN, ChampionNormalizer.normalize(pb.team2Ban2)),
                    DraftTurn(5, Side.BLUE, ActionType.BAN, ChampionNormalizer.normalize(pb.team1Ban3)),
                    DraftTurn(6, Side.RED, ActionType.BAN, ChampionNormalizer.normalize(pb.team2Ban3)),
                    DraftTurn(7, Side.BLUE, ActionType.PICK, ChampionNormalizer.normalize(pb.team1Pick1)),
                    DraftTurn(8, Side.RED, ActionType.PICK, ChampionNormalizer.normalize(pb.team2Pick1)),
                    DraftTurn(9, Side.RED, ActionType.PICK, ChampionNormalizer.normalize(pb.team2Pick2)),
                    DraftTurn(10, Side.BLUE, ActionType.PICK, ChampionNormalizer.normalize(pb.team1Pick2)),
                    DraftTurn(11, Side.BLUE, ActionType.PICK, ChampionNormalizer.normalize(pb.team1Pick3)),
                    DraftTurn(12, Side.RED, ActionType.PICK, ChampionNormalizer.normalize(pb.team2Pick3)),
                    DraftTurn(13, Side.RED, ActionType.BAN, ChampionNormalizer.normalize(pb.team2Ban4)),
                    DraftTurn(14, Side.BLUE, ActionType.BAN, ChampionNormalizer.normalize(pb.team1Ban4)),
                    DraftTurn(15, Side.RED, ActionType.BAN, ChampionNormalizer.normalize(pb.team2Ban5)),
                    DraftTurn(16, Side.BLUE, ActionType.BAN, ChampionNormalizer.normalize(pb.team1Ban5)),
                    DraftTurn(17, Side.RED, ActionType.PICK, ChampionNormalizer.normalize(pb.team2Pick4)),
                    DraftTurn(18, Side.BLUE, ActionType.PICK, ChampionNormalizer.normalize(pb.team1Pick4)),
                    DraftTurn(19, Side.BLUE, ActionType.PICK, ChampionNormalizer.normalize(pb.team1Pick5)),
                    DraftTurn(20, Side.RED, ActionType.PICK, ChampionNormalizer.normalize(pb.team2Pick5)),
                )

            val blueBans = rawTurns.filter { it.side == Side.BLUE && it.actionType == ActionType.BAN }.map { it.championId }
            val redBans = rawTurns.filter { it.side == Side.RED && it.actionType == ActionType.BAN }.map { it.championId }
            val bluePicks =
                rawTurns.filter { it.side == Side.BLUE && it.actionType == ActionType.PICK }.map {
                    PickSelection(
                        it.championId,
                    )
                }
            val redPicks = rawTurns.filter { it.side == Side.RED && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }

            val draftState =
                DraftState(
                    blueBans = blueBans,
                    redBans = redBans,
                    bluePicks = bluePicks,
                    redPicks = redPicks,
                    turns = rawTurns,
                )

            Game(
                id = gameId,
                gameNumber = index + 1,
                patch = patch,
                blueTeam = blueTeam,
                redTeam = redTeam,
                draftState = draftState,
                winner = winner,
                durationSeconds = durationSeconds,
                date = date,
            )
        }
    }
}
