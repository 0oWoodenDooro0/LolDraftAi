package com.loldraft.models

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.SoloQChampionStats
import com.loldraft.data.player.SpikeAlert
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.player.SpikeAlertType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSoloQDraftPredictionTest {
    private val tagRegistry = ChampionTagRegistry.createDefault()
    private val predictor = DraftIntentPredictor(tagRegistry = tagRegistry)

    private fun createDossier(
        playerId: String,
        role: Role,
        signatureChamp: String,
        spikeChamp: String? = null,
    ): PlayerIntelligenceDossier {
        val careerRecord =
            ChampionCareerRecord(
                championId = signatureChamp,
                gamesPlayed = 24,
                wins = 17,
                losses = 7,
                winRate = 0.708,
                pickRate = 0.35,
                role = role,
            )
        val sigPick =
            SignaturePick(
                championId = signatureChamp,
                gamesPlayed = 24,
                wins = 17,
                winRate = 0.708,
                pickRate = 0.35,
                signatureScore = 85.0,
                tier = SignatureTier.SIGNATURE,
                role = role,
            )
        val careerStats =
            PlayerCareerStats(
                playerId = playerId,
                totalProGames = 68,
                totalWins = 48,
                winRate = 0.705,
                roleDistribution = mapOf(role to 68),
                championRecords = mapOf(signatureChamp to careerRecord),
                signaturePicks = listOf(sigPick),
            )

        val soloQStats = mutableListOf<SoloQChampionStats>()
        val alerts = mutableListOf<SpikeAlert>()

        if (spikeChamp != null) {
            soloQStats.add(
                SoloQChampionStats(
                    championId = spikeChamp,
                    gamesPlayed = 12,
                    wins = 8,
                    losses = 4,
                    winRate = 0.667,
                    pickShare = 0.35,
                    gamesPerDay = 3.0,
                    role = role,
                    avgKda = 6.75,
                ),
            )
            alerts.add(
                SpikeAlert(
                    championId = spikeChamp,
                    severity = SpikeAlertSeverity.HIGH,
                    type = SpikeAlertType.PRACTICE_SPIKE,
                    recentDays = 3,
                    recentGamesCount = 12,
                    recentWinRate = 0.667,
                    baselineGamesCount = 1,
                    baselineDays = 30,
                    frequencyMultiplier = 6.0,
                    careerProGames = 2,
                    reason = "Sudden high-volume practice in SoloQ (12 games, 66.7% WR)",
                ),
            )
        }

        return PlayerIntelligenceDossier(
            playerId = playerId,
            careerStats = careerStats,
            linkedAccounts = emptyList(),
            recentSoloQ3Days = soloQStats,
            recentSoloQ7Days = soloQStats,
            activeSpikeAlerts = alerts,
            blindPickConfidences = emptyMap(),
        )
    }

    @Test
    fun `test vacant role identification matches player and incorporates SoloQ spike`() {
        // Draft State at Turn 7: Blue Pick 1 (All roles vacant for Blue)
        // Mid laner is Faker who has a PRACTICE_SPIKE on Ahri in SoloQ
        val sixBans =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Lucian"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Caitlyn"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Jinx"),
            )
        val draftState =
            DraftState(
                blueBans = listOf("Kalista", "Varus", "Caitlyn"),
                redBans = listOf("Lucian", "Ashe", "Jinx"),
                turns = sixBans,
            )

        val fakerDossier = createDossier("Faker", Role.MID, signatureChamp = "Ahri", spikeChamp = "Ahri")
        val zeusDossier = createDossier("Zeus", Role.TOP, signatureChamp = "Aatrox")
        val dossiersByRole =
            mapOf(
                Role.MID to fakerDossier,
                Role.TOP to zeusDossier,
            )

        val result =
            predictor.predictNextAction(
                draftState = draftState,
                playerDossiersByRole = dossiersByRole,
                topN = 5,
            )

        val ahriCandidate = result.predictions.find { it.championId.equals("Ahri", ignoreCase = true) }
        assertNotNull(ahriCandidate, "Ahri should be in top intent predictions")

        // Probability should be high due to combined signature + SoloQ spike
        assertTrue(ahriCandidate!!.probability > 0.15, "Ahri probability should be boosted: ${ahriCandidate.probability}")
        assertTrue(
            ahriCandidate.rationale.contains("Faker") && ahriCandidate.rationale.contains("MID"),
            "Rationale should contain player name and role: ${ahriCandidate.rationale}",
        )
        assertTrue(
            ahriCandidate.rationale.contains("SoloQ") || ahriCandidate.rationale.contains("SPIKE"),
            "Rationale should mention SoloQ spike: ${ahriCandidate.rationale}",
        )
    }

    @Test
    fun `test SoloQ spike significantly boosts candidate over non-practiced champion`() {
        // Turn 7 Blue Pick
        val draftState =
            DraftState(
                blueBans = listOf("Kalista", "Varus", "Caitlyn"),
                redBans = listOf("Lucian", "Ashe", "Jinx"),
                turns =
                    listOf(
                        DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                        DraftTurn(2, Side.RED, ActionType.BAN, "Lucian"),
                        DraftTurn(3, Side.BLUE, ActionType.BAN, "Varus"),
                        DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                        DraftTurn(5, Side.BLUE, ActionType.BAN, "Caitlyn"),
                        DraftTurn(6, Side.RED, ActionType.BAN, "Jinx"),
                    ),
            )

        // Dossier with spike on LeBlanc (12 games in SoloQ)
        val withSpikeDossier =
            mapOf(
                Role.MID to createDossier("Faker", Role.MID, signatureChamp = "Orianna", spikeChamp = "LeBlanc"),
            )
        val withoutSpikeDossier =
            mapOf(
                Role.MID to createDossier("Faker", Role.MID, signatureChamp = "Orianna", spikeChamp = null),
            )

        val predWithSpike = predictor.predictNextAction(draftState, playerDossiersByRole = withSpikeDossier, topN = 10)
        val predWithoutSpike = predictor.predictNextAction(draftState, playerDossiersByRole = withoutSpikeDossier, topN = 10)

        val scoreWithSpike = predWithSpike.predictions.find { it.championId.equals("LeBlanc", ignoreCase = true) }?.intentScore ?: 0.0
        val scoreWithoutSpike = predWithoutSpike.predictions.find { it.championId.equals("LeBlanc", ignoreCase = true) }?.intentScore ?: 0.0

        assertTrue(
            scoreWithSpike > scoreWithoutSpike,
            "Intent score with SoloQ spike ($scoreWithSpike) must exceed without spike ($scoreWithoutSpike)",
        )
    }
}
