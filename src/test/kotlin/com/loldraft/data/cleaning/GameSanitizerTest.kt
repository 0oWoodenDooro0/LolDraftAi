package com.loldraft.data.cleaning

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameSanitizerTest {
    private val sanitizer = GameSanitizer()

    private fun createValidGame(
        id: String = "game_1",
        patch: String = "14.1",
        durationSeconds: Int = 1800,
        duplicateChamp: Boolean = false,
        shortDuration: Boolean = false,
        emptyPick: Boolean = false,
    ): Game {
        val champs =
            if (duplicateChamp) {
                listOf(
                    "Aatrox",
                    "Aatrox",
                    "C3",
                    "C4",
                    "C5",
                    "C6",
                    "C7",
                    "C8",
                    "C9",
                    "C10",
                    "C11",
                    "C12",
                    "C13",
                    "C14",
                    "C15",
                    "C16",
                    "C17",
                    "C18",
                    "C19",
                    "C20",
                )
            } else {
                (1..20).map { "Champion$it" }
            }

        val turns =
            if (emptyPick) {
                DraftTurnSpec.SPECS.take(15).mapIndexed { idx, spec ->
                    DraftTurn(spec.turnNumber, spec.side, spec.actionType, champs[idx])
                }
            } else {
                DraftTurnSpec.SPECS.mapIndexed { idx, spec ->
                    DraftTurn(spec.turnNumber, spec.side, spec.actionType, champs[idx])
                }
            }

        val blueBans = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.BAN }.map { it.championId }
        val redBans = turns.filter { it.side == Side.RED && it.actionType == ActionType.BAN }.map { it.championId }
        val bluePicks = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }
        val redPicks = turns.filter { it.side == Side.RED && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }

        val draftState =
            DraftState(
                blueBans = blueBans,
                redBans = redBans,
                bluePicks = bluePicks,
                redPicks = redPicks,
                turns = turns,
            )

        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = Team("t1", "T1", "T1"),
            redTeam = Team("t2", "Gen.G", "GEN"),
            draftState = draftState,
            winner = Side.BLUE,
            durationSeconds = if (shortDuration) 180 else durationSeconds,
        )
    }

    @Test
    fun `should accept valid standard 20-round game`() {
        val game = createValidGame()
        val result = sanitizer.sanitize(game)

        assertTrue(result.isValid)
        assertIs<SanitizationResult.Valid>(result)
        assertEquals("game_1", result.game.id)
    }

    @Test
    fun `should reject remake games with duration under threshold`() {
        val game = createValidGame(shortDuration = true) // 180 seconds < 300s
        val result = sanitizer.sanitize(game)

        assertFalse(result.isValid)
        assertIs<SanitizationResult.Rejected>(result)
        assertTrue(result.reasons.contains(AnomalyReason.REMAKE))
    }

    @Test
    fun `should reject game with insufficient picks`() {
        val game = createValidGame(emptyPick = true) // Only 15 turns
        val result = sanitizer.sanitize(game)

        assertFalse(result.isValid)
        assertIs<SanitizationResult.Rejected>(result)
        assertTrue(result.reasons.contains(AnomalyReason.INSUFFICIENT_PICKS))
    }

    @Test
    fun `should reject game with duplicate champions`() {
        val game = createValidGame(duplicateChamp = true)
        val result = sanitizer.sanitize(game)

        assertFalse(result.isValid)
        assertIs<SanitizationResult.Rejected>(result)
        assertTrue(result.reasons.contains(AnomalyReason.DUPLICATE_CHAMPION))
    }

    @Test
    fun `should reject game with invalid patch string`() {
        val game = createValidGame(patch = "invalid_patch_str")
        val result = sanitizer.sanitize(game)

        assertFalse(result.isValid)
        assertIs<SanitizationResult.Rejected>(result)
        assertTrue(result.reasons.contains(AnomalyReason.INVALID_PATCH))
    }

    @Test
    fun `should reject game with alias duplicate champions like monkeyking and Wukong`() {
        val champs =
            listOf(
                "Wukong",
                "monkeyking",
                "C3",
                "C4",
                "C5",
                "C6",
                "C7",
                "C8",
                "C9",
                "C10",
                "C11",
                "C12",
                "C13",
                "C14",
                "C15",
                "C16",
                "C17",
                "C18",
                "C19",
                "C20",
            )
        val turns =
            DraftTurnSpec.SPECS.mapIndexed { idx, spec ->
                DraftTurn(spec.turnNumber, spec.side, spec.actionType, champs[idx])
            }
        val blueBans = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.BAN }.map { it.championId }
        val redBans = turns.filter { it.side == Side.RED && it.actionType == ActionType.BAN }.map { it.championId }
        val bluePicks = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }
        val redPicks = turns.filter { it.side == Side.RED && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }

        val game =
            Game(
                id = "alias_dup_game",
                gameNumber = 1,
                patch = "14.1",
                blueTeam = Team("t1", "T1", "T1"),
                redTeam = Team("t2", "Gen.G", "GEN"),
                draftState = DraftState(blueBans, redBans, bluePicks, redPicks, turns),
                winner = Side.BLUE,
                durationSeconds = 1800,
            )

        val result = sanitizer.sanitize(game)
        assertFalse(result.isValid)
        assertIs<SanitizationResult.Rejected>(result)
        assertTrue(result.reasons.contains(AnomalyReason.DUPLICATE_CHAMPION))
    }
}
