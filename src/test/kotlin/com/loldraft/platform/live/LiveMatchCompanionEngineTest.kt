package com.loldraft.platform.live

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.live.models.LiveSessionStatus
import com.loldraft.platform.live.models.LiveWsServerMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveMatchCompanionEngineTest {
    private val engine = LiveMatchCompanionEngine()

    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")

    @Test
    fun testCreateSessionInitialState() {
        val request =
            CreateLiveSessionRequest(
                sessionId = "test-live-session-1",
                blueTeam = t1,
                redTeam = gen,
            )
        val session = engine.createSession(request)

        assertEquals("test-live-session-1", session.sessionId)
        assertEquals("T1", session.blueTeam.code)
        assertEquals("GEN", session.redTeam.code)
        assertEquals(LiveSessionStatus.IN_PROGRESS, session.status)
        assertEquals(0, session.currentState.turns.size)
        assertEquals(1, session.history.size) // Turn 0 initial snapshot

        val snapshot0 = session.history.first()
        assertEquals(0, snapshot0.turnNumber)
        assertNull(snapshot0.turn)
        assertNotNull(snapshot0.evalBar)
        assertEquals(50.0, snapshot0.evalBar.blueBarPercentage, 5.0)
        assertNotNull(snapshot0.timeCurve)
        assertEquals(7, snapshot0.timeCurve.points.size)
        assertNotNull(snapshot0.blueRadar)
        assertNotNull(snapshot0.redRadar)
        assertNotNull(snapshot0.radarDelta)
        assertEquals(DraftTurnSpec.forTurn(1), snapshot0.nextTurnSpec)
        assertTrue(snapshot0.aiRecommendations.isNotEmpty(), "AI recommendations for Turn 1 should be pre-computed")
    }

    @Test
    fun testFull20TurnDraftProgression() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "progression-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        // 20 Standard Draft Actions:
        // Turn 1..6 Bans (B-R-B-R-B-R)
        val turn1 = engine.applyTurn(session.sessionId, "Kalista")
        assertEquals(1, turn1.turnNumber)
        assertEquals(Side.BLUE, turn1.turn?.side)
        assertEquals(ActionType.BAN, turn1.turn?.actionType)
        assertNotNull(turn1.coachPickFeedback)

        val turn2 = engine.applyTurn(session.sessionId, "Rumble")
        assertEquals(2, turn2.turnNumber)
        assertEquals(Side.RED, turn2.turn?.side)

        engine.applyTurn(session.sessionId, "Lucian") // 3
        engine.applyTurn(session.sessionId, "Ashe") // 4
        engine.applyTurn(session.sessionId, "Varus") // 5
        engine.applyTurn(session.sessionId, "Caitlyn") // 6

        // Turn 7..12 Picks (B-R-R-B-B-R)
        val turn7 = engine.applyTurn(session.sessionId, "Azir", Role.MID)
        assertEquals(7, turn7.turnNumber)
        assertEquals(Side.BLUE, turn7.turn?.side)
        assertEquals(ActionType.PICK, turn7.turn?.actionType)
        assertEquals(Role.MID, turn7.turn?.role)
        assertNotNull(turn7.coachPickFeedback)

        val turn8 = engine.applyTurn(session.sessionId, "Corki", Role.MID)
        val turn9 = engine.applyTurn(session.sessionId, "Sejuani", Role.JUNGLE)
        val turn10 = engine.applyTurn(session.sessionId, "Maokai", Role.JUNGLE)
        val turn11 = engine.applyTurn(session.sessionId, "K'Sante", Role.TOP)
        val turn12 = engine.applyTurn(session.sessionId, "Renekton", Role.TOP)

        // Turn 13..16 Bans (R-B-R-B)
        val turn13 = engine.applyTurn(session.sessionId, "Nautilus")
        assertEquals(Side.RED, turn13.turn?.side)
        val turn14 = engine.applyTurn(session.sessionId, "Leona")
        assertEquals(Side.BLUE, turn14.turn?.side)
        val turn15 = engine.applyTurn(session.sessionId, "Braum")
        val turn16 = engine.applyTurn(session.sessionId, "Rell")

        // Turn 17..20 Picks (R-B-B-R)
        val turn17 = engine.applyTurn(session.sessionId, "Jinx", Role.BOT)
        assertEquals(Side.RED, turn17.turn?.side)
        val turn18 = engine.applyTurn(session.sessionId, "Aphelios", Role.BOT)
        assertEquals(Side.BLUE, turn18.turn?.side)
        val turn19 = engine.applyTurn(session.sessionId, "Thresh", Role.SUPPORT)
        assertEquals(Side.BLUE, turn19.turn?.side)

        val turn20 = engine.applyTurn(session.sessionId, "Lulu", Role.SUPPORT)
        assertEquals(20, turn20.turnNumber)
        assertEquals(Side.RED, turn20.turn?.side)
        assertNull(turn20.nextTurnSpec, "After turn 20, nextTurnSpec should be null")

        val completedSession = engine.getSession(session.sessionId)
        assertNotNull(completedSession)
        assertEquals(LiveSessionStatus.COMPLETED, completedSession.status)
        assertEquals(20, completedSession.currentState.turns.size)
        assertTrue(completedSession.currentState.isComplete)
        assertEquals(21, completedSession.history.size) // 1 initial + 20 turns
    }

    @Test
    fun testRejectDuplicateChampionSelection() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "duplicate-test-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        engine.applyTurn(session.sessionId, "Kalista") // Turn 1 ban

        val ex =
            assertThrows<IllegalArgumentException> {
                engine.applyTurn(session.sessionId, "Kalista") // Turn 2 duplicate ban attempt
            }
        assertTrue(ex.message?.contains("already") == true || ex.message?.contains("duplicate") == true)
    }

    @Test
    fun testRejectTurnAfterDraftComplete() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "complete-test-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        val champs =
            listOf(
                "Kalista",
                "Rumble",
                "Lucian",
                "Ashe",
                "Varus",
                "Caitlyn",
                "Azir",
                "Corki",
                "Sejuani",
                "Maokai",
                "K'Sante",
                "Renekton",
                "Nautilus",
                "Leona",
                "Braum",
                "Rell",
                "Jinx",
                "Aphelios",
                "Thresh",
                "Lulu",
            )
        champs.forEach { engine.applyTurn(session.sessionId, it) }

        assertThrows<IllegalStateException> {
            engine.applyTurn(session.sessionId, "Aatrox")
        }
    }

    @Test
    fun testAutomaticRoleDeduction() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "role-deduction-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        // Turns 1..6 bans
        listOf("Kalista", "Rumble", "Lucian", "Ashe", "Varus", "Caitlyn").forEach {
            engine.applyTurn(session.sessionId, it)
        }

        // Turn 7 Blue pick "Ahri" without providing role explicitly
        val turn7 = engine.applyTurn(session.sessionId, "Ahri", role = null)
        assertEquals(Role.MID, turn7.turn?.role, "Ahri should be automatically assigned primary role MID")
    }

    @Test
    fun testUndoLastTurn() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "undo-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        engine.applyTurn(session.sessionId, "Kalista")
        engine.applyTurn(session.sessionId, "Rumble")
        engine.applyTurn(session.sessionId, "Lucian")

        val stateBeforeUndo = engine.getSession(session.sessionId)
        assertEquals(3, stateBeforeUndo?.currentState?.turns?.size)
        assertEquals(4, stateBeforeUndo?.history?.size) // Turn 0, 1, 2, 3

        val snapshotAfterUndo = engine.undoTurn(session.sessionId)
        assertEquals(2, snapshotAfterUndo.turnNumber)

        val stateAfterUndo = engine.getSession(session.sessionId)
        assertEquals(2, stateAfterUndo?.currentState?.turns?.size)
        assertEquals(3, stateAfterUndo?.history?.size)
        assertEquals(
            "Rumble",
            stateAfterUndo
                ?.currentState
                ?.turns
                ?.last()
                ?.championId,
        )
    }

    @Test
    fun testResetSession() {
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "reset-session",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

        engine.applyTurn(session.sessionId, "Kalista")
        engine.applyTurn(session.sessionId, "Rumble")

        val snapshotAfterReset = engine.resetSession(session.sessionId)
        assertEquals(0, snapshotAfterReset.turnNumber)

        val sessionAfterReset = engine.getSession(session.sessionId)
        assertEquals(0, sessionAfterReset?.currentState?.turns?.size)
        assertEquals(1, sessionAfterReset?.history?.size)
        assertEquals(LiveSessionStatus.IN_PROGRESS, sessionAfterReset?.status)
    }

    @Test
    fun testRealtimeEventFlow() =
        runTest {
            val session =
                engine.createSession(
                    CreateLiveSessionRequest(
                        sessionId = "event-flow-session",
                        blueTeam = t1,
                        redTeam = gen,
                    ),
                )

            val flow = engine.getSessionEventFlow(session.sessionId)

            // Test TurnApplied event emission
            var emittedMessage: LiveWsServerMessage? = null
            val job =
                launch {
                    emittedMessage = flow.first()
                }

            engine.applyTurn(session.sessionId, "Kalista")
            job.join()

            assertTrue(emittedMessage is LiveWsServerMessage.TurnApplied)
            val turnApplied = emittedMessage as LiveWsServerMessage.TurnApplied
            assertEquals("Kalista", turnApplied.turn.championId)
            assertEquals(1, turnApplied.snapshot.turnNumber)
        }
}
