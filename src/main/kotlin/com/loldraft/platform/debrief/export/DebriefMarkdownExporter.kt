package com.loldraft.platform.debrief.export

import com.loldraft.data.models.Side
import com.loldraft.platform.debrief.models.DebriefReport
import com.loldraft.platform.debrief.models.MatchDebriefReport
import java.util.Locale

object DebriefMarkdownExporter {
    fun exportGameDebrief(report: DebriefReport): String =
        buildString {
            val blueTeamName = report.blueTeam.name
            val redTeamName = report.redTeam.name
            val winnerName = if (report.actualWinner == Side.BLUE) blueTeamName else redTeamName
            val durationStr =
                if (report.durationSeconds != null) {
                    "${report.durationSeconds / 60}m ${report.durationSeconds % 60}s"
                } else {
                    "N/A"
                }

            appendLine("# Post-Match BP Debrief Report: $blueTeamName vs $redTeamName")
            appendLine()
            appendLine(
                "> **Match Metadata**: Game `${report.gameId}` | Patch `${report.patch}` | " +
                    "Tournament: `${report.tournament ?: "Custom"}` | **Winner**: **$winnerName** (${report.actualWinner}) | Duration: `$durationStr`",
            )
            appendLine()

            // 1. Attribution Section
            appendLine("## Attribution Verdict & Influence Split")
            appendLine()
            val attr = report.attribution
            val draftPct = (attr.draftInfluencePct * 100.0).toInt()
            val execPct = (attr.executionInfluencePct * 100.0).toInt()

            appendLine("### Verdict: `${attr.category.name}`")
            appendLine("**${attr.title}**")
            appendLine()
            appendLine(attr.explanation)
            appendLine()
            appendLine("| Influence Factor | Weight | Visual Bar |")
            appendLine("| :--- | :--- | :--- |")
            appendLine("| **BP Draft Advantage** | $draftPct% | ${renderBar(draftPct)} |")
            appendLine("| **In-Game Execution** | $execPct% | ${renderBar(execPct)} |")
            appendLine()

            // 2. Coach BP Scorecard
            appendLine("## Coach BP Performance Scorecard")
            appendLine()
            val bCoach = report.blueCoachSummary
            val rCoach = report.redCoachSummary
            appendLine("| Metric | Blue Side: $blueTeamName | Red Side: $redTeamName |")
            appendLine("| :--- | :--- | :--- |")
            appendLine("| **Coach BP Grade** | **${bCoach.coachBpGrade}** | **${rCoach.coachBpGrade}** |")
            appendLine("| **Coach BP Score (0-100)** | `${bCoach.coachBpScore}` | `${rCoach.coachBpScore}` |")
            appendLine(
                "| **Net Draft Delta WR** | `${formatDelta(
                    bCoach.netDraftDeltaWinRate,
                )}` | `${formatDelta(rCoach.netDraftDeltaWinRate)}` |",
            )
            appendLine(
                "| **Phase 1 Delta WR (Turns 1-12)** | `${formatDelta(
                    bCoach.phase1DeltaWinRate,
                )}` | `${formatDelta(rCoach.phase1DeltaWinRate)}` |",
            )
            appendLine(
                "| **Phase 2 Delta WR (Turns 13-20)** | `${formatDelta(
                    bCoach.phase2DeltaWinRate,
                )}` | `${formatDelta(rCoach.phase2DeltaWinRate)}` |",
            )
            appendLine("| **Optimal Choices (S/A)** | ${bCoach.optimalPicksCount} | ${rCoach.optimalPicksCount} |")
            appendLine("| **Blunders (D)** | ${bCoach.blundersCount} | ${rCoach.blundersCount} |")

            val bMvp = bCoach.mvpTurn?.let { "T${it.turnNumber} ${it.championId} (${formatDelta(it.deltaWinRate)})" } ?: "N/A"
            val rMvp = rCoach.mvpTurn?.let { "T${it.turnNumber} ${it.championId} (${formatDelta(it.deltaWinRate)})" } ?: "N/A"
            appendLine("| **MVP Turn** | $bMvp | $rMvp |")

            val bWorst = bCoach.worstTurn?.let { "T${it.turnNumber} ${it.championId} (${formatDelta(it.deltaWinRate)})" } ?: "N/A"
            val rWorst = rCoach.worstTurn?.let { "T${it.turnNumber} ${it.championId} (${formatDelta(it.deltaWinRate)})" } ?: "N/A"
            appendLine("| **Costliest Choice** | $bWorst | $rWorst |")
            appendLine()

            // 3. Turn-by-Turn Draft Timeline
            appendLine("## Turn-by-Turn Draft Timeline")
            appendLine()
            appendLine("| Turn | Side | Action | Champion | Role | Player | Δ WR | Grade | Critique |")
            appendLine("| :---: | :---: | :---: | :--- | :---: | :--- | :---: | :---: | :--- |")
            for (turn in report.turns) {
                val tag =
                    if (turn.isMvpTurn) {
                        " ⭐ MVP"
                    } else if (turn.isBlunderTurn) {
                        " ⚠️ BLUNDER"
                    } else {
                        ""
                    }
                val roleStr = turn.role?.name ?: "-"
                val playerStr = turn.player ?: "-"
                val champWithTag = "${turn.championId}$tag"
                val deltaStr = formatDelta(turn.deltaWinRate)
                appendLine(
                    "| ${turn.turnNumber} | ${turn.side} | ${turn.actionType} | $champWithTag | $roleStr | $playerStr | $deltaStr | ${turn.grade} | ${turn.critique} |",
                )
            }
            appendLine()

            // 4. Composition Radar
            appendLine("## Composition 5-Dimension Radar")
            appendLine()
            appendLine("| Dimension | $blueTeamName (Blue) | $redTeamName (Red) | Delta | Advantage |")
            appendLine("| :--- | :---: | :---: | :---: | :---: |")
            for (dim in report.charts.radarComparison) {
                val advStr = dim.advantage?.let { if (it == Side.BLUE) blueTeamName else redTeamName } ?: "Even"
                appendLine("| ${dim.dimension} | ${dim.blueScore} | ${dim.redScore} | ${formatDelta(dim.delta)} | $advStr |")
            }
            appendLine()

            if (bCoach.unresolvedFlaws.isNotEmpty() || rCoach.unresolvedFlaws.isNotEmpty()) {
                appendLine("### Composition Flaws Identified")
                if (bCoach.unresolvedFlaws.isNotEmpty()) {
                    appendLine(
                        "- **$blueTeamName (Blue)**: " + bCoach.unresolvedFlaws.joinToString("; ") { "${it.title} (${it.severity})" },
                    )
                }
                if (rCoach.unresolvedFlaws.isNotEmpty()) {
                    appendLine("- **$redTeamName (Red)**: " + rCoach.unresolvedFlaws.joinToString("; ") { "${it.title} (${it.severity})" })
                }
                appendLine()
            }

            // 5. Time-Horizon Win Probability Curve
            appendLine("## Time-Horizon Win Probability Curve")
            appendLine()
            appendLine("> **Trajectory Summary**: ${report.timeCurve.trajectorySummary}")
            appendLine()
            appendLine("| Minute | $blueTeamName Projected WR | $redTeamName Projected WR | Dominant Phase |")
            appendLine("| :---: | :---: | :---: | :--- |")
            for (pt in report.timeCurve.points) {
                val bPct = String.format(Locale.US, "%.1f%%", pt.blueWinRate * 100.0)
                val rPct = String.format(Locale.US, "%.1f%%", pt.redWinRate * 100.0)
                appendLine("| ${pt.minute}m | $bPct | $rPct | ${pt.dominantPhase} |")
            }
        }

    fun exportMatchDebrief(report: MatchDebriefReport): String =
        buildString {
            appendLine("# Series Match Debrief Report: ${report.blueTeam.name} vs ${report.redTeam.name}")
            appendLine()
            appendLine(
                "> **Series Metadata**: Match `${report.matchId}` | Tournament: `${report.tournament}` | " +
                    "Patch: `${report.patch}` | Best Of: `${report.bestOf}` | Games Played: `${report.gamesPlayed}`",
            )
            appendLine()
            appendLine("## Series Coaching Performance")
            appendLine("- **${report.blueTeam.name} Series Coaching Score**: `${report.blueSeriesCoachScore}`")
            appendLine("- **${report.redTeam.name} Series Coaching Score**: `${report.redSeriesCoachScore}`")
            appendLine(
                "- **Side Win Rates**: Blue Side: ${(report.sideWinRateStats[Side.BLUE]?.times(100))?.toInt() ?: 50}% | " +
                    "Red Side: ${(report.sideWinRateStats[Side.RED]?.times(100))?.toInt() ?: 50}%",
            )
            appendLine()
            appendLine("### Series Meta Trends")
            appendLine("- **Top Banned Champions**: ${report.frequentBans.joinToString(", ")}")
            appendLine("- **Top Picked Champions**: ${report.frequentPicks.joinToString(", ")}")
            appendLine()
            appendLine("## Games Overview")
            appendLine("| Game # | Blue Team | Red Team | Winner | Duration | Attribution | Blue Coach Score | Red Coach Score |")
            appendLine("| :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |")
            for ((idx, g) in report.gameReports.withIndex()) {
                val winnerTeam = if (g.actualWinner == Side.BLUE) g.blueTeam.name else g.redTeam.name
                val durStr = g.durationSeconds?.let { "${it / 60}m ${it % 60}s" } ?: "N/A"
                appendLine(
                    "| Game ${idx + 1} | ${g.blueTeam.name} | ${g.redTeam.name} | $winnerTeam | $durStr | `${g.attribution.category}` | ${g.blueCoachSummary.coachBpScore} | ${g.redCoachSummary.coachBpScore} |",
                )
            }
            appendLine()
            appendLine("### Strategic Series Takeaway")
            appendLine(report.overallAttributionSummary)
        }

    private fun formatDelta(delta: Double): String = String.format(Locale.US, "%+.1f%%", delta * 100.0)

    private fun renderBar(pct: Int): String {
        val filled = (pct / 10).coerceIn(0, 10)
        val empty = 10 - filled
        return "█".repeat(filled) + "░".repeat(empty)
    }
}
