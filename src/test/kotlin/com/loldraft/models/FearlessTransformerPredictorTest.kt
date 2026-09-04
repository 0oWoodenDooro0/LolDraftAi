package com.loldraft.models

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FearlessTransformerPredictorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should parse specification JSON request correctly and execute prediction`() {
        val sampleJson = """
        {
          "context": {
            "patch": "14.15",
            "region": "LPL",
            "series_type": "Bo5",
            "game_number": 3,
            "current_turn": {
              "side": "blue",
              "phase": "pick",
              "step_index": 7
            }
          },
          "teams": {
            "blue": {
              "team_id": "BLG",
              "roster": {
                "top": "Bin",
                "jng": "Wei",
                "mid": "knight",
                "bot": "Elk",
                "sup": "ON"
              }
            },
            "red": {
              "team_id": "WBG",
              "roster": {
                "top": "Breathe",
                "jng": "Tarzan",
                "mid": "Xiaohu",
                "bot": "Light",
                "sup": "Crisp"
              }
            }
          },
          "constraints": {
            "fearless_locked": [
              "Rumble", "Maokai", "Tristana", "Ashe", "Braum",
              "KSante", "Sejuani", "Yone", "Kaisa", "Nautilus"
            ],
            "current_bans": ["Lucian", "Kalista", "Caitlyn", "Corki", "Jayce"],
            "current_picks": {
              "blue": ["Renekton"],
              "red": ["Jax", "Leona"]
            }
          },
          "history": {
            "player_pick_counts": {
              "Bin": {"Renekton": 12, "Jax": 10, "Camille": 8, "Gnar": 5},
              "Wei": {"Maokai": 9, "Sejuani": 8, "Viego": 7}
            },
            "player_decayed_frequencies": {
              "Bin": {"Camille": 6.8, "Gnar": 4.5}
            },
            "decay_lambda": 0.95
          }
        }
        """.trimIndent()

        val request = json.decodeFromString<FearlessPredictionRequest>(sampleJson)
        assertEquals("14.15", request.context.patch)
        assertEquals("LPL", request.context.region)
        assertEquals(3, request.context.gameNumber)
        assertEquals("BLG", request.teams.blue.teamId)
        assertEquals("Bin", request.teams.blue.roster.top)
        assertEquals(10, request.constraints.fearlessLocked.size)
        assertEquals(0.95, request.history.decayLambda)

        val predictor = FearlessTransformerPredictor()
        val response = predictor.predict(request, topK = 5)

        // Verify response
        assertNotNull(response)
        assertEquals("BLG", response.actingTeam)
        assertEquals(5, response.candidates.size)
        assertTrue(response.maskedChampionsCount >= 17) // 10 fearless + 5 bans + 3 current picks

        // Verify Dynamic Action Masking
        val candidateNames = response.candidates.map { it.champion.lowercase() }
        val fearlessLockedNormalized = request.constraints.fearlessLocked.map { it.lowercase().replace("'", "").replace(" ", "") }
        val currentBansNormalized = request.constraints.currentBans.map { it.lowercase() }
        val currentPicksNormalized = (request.constraints.currentPicks.blue + request.constraints.currentPicks.red).map { it.lowercase() }

        for (cand in candidateNames) {
            val cleanCand = cand.replace("'", "").replace(" ", "")
            assertFalse(fearlessLockedNormalized.contains(cleanCand), "候選角色 $cand 不應包含全局已鎖定英雄")
            assertFalse(currentBansNormalized.contains(cleanCand), "候選角色 $cand 不應包含本局已禁用英雄")
            assertFalse(currentPicksNormalized.contains(cleanCand), "候選角色 $cand 不應包含本局已選取英雄")
        }

        // Verify probability distribution sum and rationale
        val topCandidate = response.candidates.first()
        assertTrue(topCandidate.probability > 0.0)
        assertTrue(topCandidate.rationale.contains("[Fearless Behavioral]"))
    }

    @Test
    fun `should strictly enforce CSP Hard Action Masking with mathematical zero probability`() {
        val request = FearlessPredictionRequest(
            context = FearlessDraftContext(
                patch = "14.15",
                region = "LCK",
                gameNumber = 4,
                currentTurn = FearlessTurnInfo(side = "blue", phase = "pick", stepIndex = 7),
            ),
            teams = FearlessTeams(
                blue = FearlessTeamInfo("T1", FearlessRoster(top = "Zeus")),
                red = FearlessTeamInfo("GEN", FearlessRoster(top = "Kiin")),
            ),
            constraints = FearlessConstraints(
                fearlessLocked = listOf("Aatrox", "Gnar", "Renekton", "Jax", "KSante"),
                currentBans = listOf("Rumble", "Jayce"),
                currentPicks = FearlessCurrentPicks(
                    blue = emptyList(),
                    red = listOf("Camille"),
                ),
            ),
            history = FearlessHistory(
                playerPickCounts = mapOf("Zeus" to mapOf("Aatrox" to 20, "Gnar" to 15, "Yone" to 10)),
            ),
        )

        val predictor = FearlessTransformerPredictor()
        val response = predictor.predict(request, topK = 10)

        val candidateNames = response.candidates.map { it.champion.lowercase() }

        // Aatrox, Gnar, Renekton, Jax, KSante must be 100% masked
        assertFalse(candidateNames.contains("aatrox"))
        assertFalse(candidateNames.contains("gnar"))
        assertFalse(candidateNames.contains("renekton"))
        assertFalse(candidateNames.contains("jax"))
        assertFalse(candidateNames.contains("k'sante"))
        assertFalse(candidateNames.contains("ksante"))

        // Current bans: Rumble, Jayce must be masked
        assertFalse(candidateNames.contains("rumble"))
        assertFalse(candidateNames.contains("jayce"))

        // Current pick: Camille must be masked
        assertFalse(candidateNames.contains("camille"))

        // All recommended candidates must have probability > 0
        response.candidates.forEach {
            assertTrue(it.probability > 0.0)
            assertTrue(it.logit > -1e8)
        }
    }

    @Test
    fun `should boost player decayed pick frequency when available`() {
        val requestWithDecay = FearlessPredictionRequest(
            context = FearlessDraftContext(
                patch = "14.15",
                region = "LPL",
                gameNumber = 1,
                currentTurn = FearlessTurnInfo(side = "blue", phase = "pick", stepIndex = 7),
            ),
            teams = FearlessTeams(
                blue = FearlessTeamInfo("BLG", FearlessRoster(top = "Bin")),
                red = FearlessTeamInfo("WBG", FearlessRoster(top = "Breathe")),
            ),
            constraints = FearlessConstraints(),
            history = FearlessHistory(
                playerDecayedFrequencies = mapOf(
                    "Bin" to mapOf("Camille" to 9.5),
                ),
            ),
        )

        val predictor = FearlessTransformerPredictor()
        val response = predictor.predict(requestWithDecay, topK = 3)

        val topCand = response.candidates.first()
        assertEquals("Camille", topCand.champion)
        assertTrue(topCand.rationale.contains("衰減加權: 9.5"))
    }

    @Test
    fun `should dynamically adapt to roster substitution without retraining`() {
        val subRoster = FearlessRoster(
            top = "Bin",
            jng = "Xun",
            mid = "knight",
            bot = "Elk",
            sup = "ON",
        )
        val request = FearlessPredictionRequest(
            context = FearlessDraftContext(
                patch = "14.15",
                region = "LPL",
                gameNumber = 2,
                currentTurn = FearlessTurnInfo(side = "blue", phase = "pick", stepIndex = 7),
            ),
            teams = FearlessTeams(
                blue = FearlessTeamInfo("BLG", subRoster),
                red = FearlessTeamInfo("WBG", FearlessRoster(top = "Breathe")),
            ),
            constraints = FearlessConstraints(
                fearlessLocked = listOf("Renekton", "Jax"),
                currentBans = listOf("Lucian", "Kalista"),
            ),
            history = FearlessHistory(
                playerPickCounts = mapOf(
                    "Xun" to mapOf("Nidalee" to 15, "Xin Zhao" to 10),
                ),
            ),
        )

        val predictor = FearlessTransformerPredictor()
        val response = predictor.predict(request, topK = 5)

        assertEquals("BLG", response.actingTeam)
        assertEquals(5, response.candidates.size)
        val names = response.candidates.map { it.champion.lowercase() }
        assertFalse(names.contains("renekton"))
        assertFalse(names.contains("jax"))
    }
}
