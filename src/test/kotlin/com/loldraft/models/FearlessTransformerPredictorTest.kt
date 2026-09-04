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
            }
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

        val predictor = FearlessTransformerPredictor()
        val response = predictor.predict(request, topK = 5)

        // Verify response
        assertNotNull(response)
        assertEquals("BLG", response.actingTeam)
        assertEquals(5, response.candidates.size)
        assertTrue(response.maskedChampionsCount >= 17) // 10 fearless + 5 bans + 3 current picks

        // Verify Dynamic Action Masking (所有被遮罩英雄絕不出現在候選名單中)
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
        assertTrue(topCandidate.rationale.contains("[Fearless AI]"))
        println("Top-5 Recommendations for BLG (Turn 7):")
        response.candidates.forEach {
            println("  ${it.champion}: ${it.percentage} | ${it.rationale}")
        }
    }

    @Test
    fun `should dynamically adapt to roster substitution without retraining`() {
        // Test Roster Agnostic property: substituting Wei with Xx/another jungler
        val subRoster = FearlessRoster(
            top = "Bin",
            jng = "Xun", // Substituted jungler
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
        // Renekton and Jax must be masked
        val names = response.candidates.map { it.champion.lowercase() }
        assertFalse(names.contains("renekton"))
        assertFalse(names.contains("jax"))
    }
}
