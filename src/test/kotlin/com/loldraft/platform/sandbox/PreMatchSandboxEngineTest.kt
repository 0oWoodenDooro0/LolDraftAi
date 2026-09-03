package com.loldraft.platform.sandbox

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.SoloQAccount
import com.loldraft.data.player.SoloQServer
import com.loldraft.data.player.SpikeAlert
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.player.SpikeAlertType
import com.loldraft.data.style.AggressionLevel
import com.loldraft.data.style.EarlyGameMetrics
import com.loldraft.data.style.FirstPickAnalysis
import com.loldraft.data.style.GamePace
import com.loldraft.data.style.SidePreference
import com.loldraft.data.style.SideRecord
import com.loldraft.data.style.SideTendency
import com.loldraft.data.style.TacticalStyleMetrics
import com.loldraft.data.style.TacticalTag
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.data.validation.DraftValidator
import com.loldraft.platform.sandbox.models.MatchupSandboxRequest
import com.loldraft.platform.sandbox.models.ScenarioPreset
import com.loldraft.platform.sandbox.models.WhatIfBranchRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreMatchSandboxEngineTest {
    private lateinit var engine: PreMatchSandboxEngine
    private lateinit var sampleRequest: MatchupSandboxRequest
    private val validator = DraftValidator()

    @BeforeEach
    fun setUp() {
        val registry = ChampionTagRegistry.createDefault()
        engine = PreMatchSandboxEngine(tagRegistry = registry)
        sampleRequest = createSampleMatchupRequest()
    }

    @AfterEach
    fun tearDown() {
        engine.close()
    }

    @Test
    fun testGenerateThreeDistinctScenarios() {
        val response = engine.generateScenarios(sampleRequest)

        assertEquals("T1", response.blueTeam.code)
        assertEquals("GEN", response.redTeam.code)
        assertTrue(response.matchupSummary.contains("T1 vs GEN"))
        assertEquals(3, response.scenarios.size, "Should generate exactly 3 BP scenarios")

        val presets = response.scenarios.map { it.preset }.toSet()
        assertEquals(
            setOf(ScenarioPreset.META_OPTIMAL, ScenarioPreset.TARGETED_COUNTER, ScenarioPreset.STYLE_CLASH),
            presets,
            "All 3 presets should be represented",
        )

        val totalLikelihood = response.scenarios.sumOf { it.likelihood }
        assertTrue(
            abs(totalLikelihood - 1.0) < 0.05,
            "Total likelihoods should sum to ~1.0, was $totalLikelihood",
        )

        response.scenarios.forEach { scenario ->
            assertTrue(scenario.title.isNotBlank())
            assertTrue(scenario.description.isNotBlank())
            assertNotNull(scenario.draftState)
            assertNotNull(scenario.evaluation)
        }

        assertNotNull(response.rootDraftTree)
        assertEquals(0, response.rootDraftTree.turnNumber)
    }

    @Test
    fun testScenarioValidity() {
        val response = engine.generateScenarios(sampleRequest)

        response.scenarios.forEach { scenario ->
            val draft = scenario.draftState
            assertEquals(20, draft.turns.size, "Scenario ${scenario.scenarioId} must have 20 turns")
            assertEquals(5, draft.blueBans.size, "Blue side must have 5 bans")
            assertEquals(5, draft.redBans.size, "Red side must have 5 bans")
            assertEquals(5, draft.bluePicks.size, "Blue side must have 5 picks")
            assertEquals(5, draft.redPicks.size, "Red side must have 5 picks")

            // Strict DraftValidator check
            val completeValidation = validator.validateCompleteDraft(draft)
            assertTrue(
                completeValidation.isValid,
                "Complete draft validation failed for ${scenario.scenarioId}: ${completeValidation.errors}",
            )

            // No duplicate champions across any ban or pick
            assertEquals(20, draft.allSelectedChampions.size, "All 20 selected champions must be unique")
        }
    }

    @Test
    fun testEvalBarTrajectory() {
        val response = engine.generateScenarios(sampleRequest)

        response.scenarios.forEach { scenario ->
            assertEquals(20, scenario.turnTrajectories.size)

            scenario.turnTrajectories.forEachIndexed { index, point ->
                assertEquals(index + 1, point.turnNumber)
                assertTrue(point.championId.isNotBlank())
                assertTrue(point.rationale.isNotBlank())
                assertTrue(point.blueWinRate in 0.01..0.99)
                assertTrue(point.evalBarScore.formattedScore.isNotBlank())
            }

            val lastTrajectory = scenario.turnTrajectories.last()
            assertTrue(
                abs(lastTrajectory.blueWinRate - scenario.evaluation.blueWinRate) < 0.05,
                "Final trajectory win rate should align with draft evaluation win rate",
            )
        }
    }

    @Test
    fun testPivotPointsDetection() {
        val response = engine.generateScenarios(sampleRequest)

        response.scenarios.forEach { scenario ->
            assertTrue(
                scenario.pivotPoints.isNotEmpty(),
                "Scenario ${scenario.scenarioId} should identify at least one pivot point",
            )

            scenario.pivotPoints.forEach { pivot ->
                assertTrue(pivot.turnNumber in 1..20)
                assertTrue(pivot.impactDescription.isNotBlank())
                assertTrue(pivot.championId.isNotBlank())
                assertNotNull(pivot.pivotType)
            }
        }
    }

    @Test
    fun testSoloQSpikesAndSignaturesIncorporated() {
        val response = engine.generateScenarios(sampleRequest)
        val targetedScenario = response.scenarios.first { it.preset == ScenarioPreset.TARGETED_COUNTER }

        // In sampleRequest, Faker has an OFF_META_SURGE spike on "Ahri" and Chovy has a signature on "Yone"
        // Under TARGETED_COUNTER, either Ahri or Yone should be targeted (banned or picked early)
        val selectedChampions = targetedScenario.draftState.allSelectedChampions
        val hasTargetedPickOrBan =
            selectedChampions.contains("Ahri") ||
                selectedChampions.contains("Yone") ||
                selectedChampions.contains("Azir")
        assertTrue(
            hasTargetedPickOrBan,
            "Targeted Counter scenario should draft or ban signature / SoloQ spike champions (Ahri/Yone/Azir)",
        )
    }

    @Test
    fun testWhatIfBranchingValid() {
        val response = engine.generateScenarios(sampleRequest)
        val baseScenario = response.scenarios.first { it.preset == ScenarioPreset.META_OPTIMAL }

        // Branch at Turn 7 (Blue first pick) by picking a different valid champion
        val turn7Original = baseScenario.draftState.turns.first { it.turnNumber == 7 }
        val alternativeChampion = if (turn7Original.championId == "Ashe") "Varus" else "Ashe"

        val branchRequest =
            WhatIfBranchRequest(
                branchTurnNumber = 7,
                newChampionId = alternativeChampion,
                newRole = Role.BOT,
                scenarioPreset = ScenarioPreset.META_OPTIMAL,
                rationale = "Coach prefers Ashe arrow engage over original pick",
            )

        val branchResult = engine.simulateWhatIfBranch(baseScenario.draftState, branchRequest, sampleRequest)

        assertEquals(7, branchResult.branchTurnNumber)
        assertEquals(turn7Original.championId, branchResult.originalTurn.championId)
        assertEquals(alternativeChampion, branchResult.replacementTurn.championId)
        assertEquals(20, branchResult.newScenario.draftState.turns.size)

        // Turn 7 in new scenario should be the replacement champion
        val newTurn7 =
            branchResult.newScenario.draftState.turns
                .first { it.turnNumber == 7 }
        assertEquals(alternativeChampion, newTurn7.championId)

        // Verify validity of the new branched draft
        val validation = validator.validateCompleteDraft(branchResult.newScenario.draftState)
        assertTrue(validation.isValid, "Branched draft must be valid: ${validation.errors}")

        // Comparative delta
        assertNotNull(branchResult.comparativeDelta)
        assertTrue(branchResult.comparativeDelta.strategicSummary.isNotBlank())
    }

    @Test
    fun testWhatIfBranchingRejectsDuplicatePick() {
        val response = engine.generateScenarios(sampleRequest)
        val baseScenario = response.scenarios.first()

        // Turn 1 champion was banned. Attempting to pick Turn 1 banned champion at Turn 7 should fail
        val turn1BannedChampion =
            baseScenario.draftState.turns
                .first { it.turnNumber == 1 }
                .championId

        val branchRequest =
            WhatIfBranchRequest(
                branchTurnNumber = 7,
                newChampionId = turn1BannedChampion,
            )

        assertThrows<IllegalArgumentException> {
            engine.simulateWhatIfBranch(baseScenario.draftState, branchRequest, sampleRequest)
        }
    }

    @Test
    fun testWhatIfBranchingRejectsOutOfBoundsTurn() {
        val response = engine.generateScenarios(sampleRequest)
        val baseScenario = response.scenarios.first()

        assertThrows<IllegalArgumentException> {
            engine.simulateWhatIfBranch(
                baseScenario.draftState,
                WhatIfBranchRequest(branchTurnNumber = 0, newChampionId = "Ashe"),
                sampleRequest,
            )
        }

        assertThrows<IllegalArgumentException> {
            engine.simulateWhatIfBranch(
                baseScenario.draftState,
                WhatIfBranchRequest(branchTurnNumber = 21, newChampionId = "Ashe"),
                sampleRequest,
            )
        }
    }

    @Test
    fun testDraftTreeBuilding() {
        val response = engine.generateScenarios(sampleRequest)
        val tree = engine.buildDraftTree(response.scenarios)

        assertNotNull(tree)
        assertEquals(0, tree.turnNumber)
        assertTrue(tree.children.isNotEmpty(), "Root node should have child nodes")
    }

    @Test
    fun testPartialDraftResumption() {
        // Prepare initial Phase 1 bans (Turns 1..6)
        val initialBans =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Lucian"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Rumble"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Sejuani"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Nautilus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Leona"),
            )

        val requestWithInitial = sampleRequest.copy(initialTurns = initialBans)
        val response = engine.generateScenarios(requestWithInitial)

        assertEquals(3, response.scenarios.size)
        response.scenarios.forEach { scenario ->
            assertEquals(20, scenario.draftState.turns.size)
            // Verify initial turns were preserved
            for (i in 0..5) {
                assertEquals(initialBans[i].championId, scenario.draftState.turns[i].championId)
            }
            val validation = validator.validateCompleteDraft(scenario.draftState)
            assertTrue(validation.isValid, "Draft resumed from partial turns must be valid")
        }
    }

    private fun createSampleMatchupRequest(): MatchupSandboxRequest {
        val blueTeam = Team(id = "team-t1", name = "T1", code = "T1", region = "LCK")
        val redTeam = Team(id = "team-gen", name = "Gen.G", code = "GEN", region = "LCK")

        val blueProfile =
            TeamTacticalProfile(
                team = blueTeam,
                totalGamesAnalyzed = 40,
                sidePreference =
                    SidePreference(
                        blueRecord = SideRecord(20, 16, 4, 0.80),
                        redRecord = SideRecord(20, 12, 8, 0.60),
                        overallRecord = SideRecord(40, 28, 12, 0.70),
                        winRateDelta = 0.20,
                        blueRate = 0.80,
                        redRate = 0.60,
                        tendency = SideTendency.BLUE_FAVORED,
                    ),
                earlyGameMetrics =
                    EarlyGameMetrics(
                        firstBloodRate = 0.65,
                        firstDragonRate = 0.60,
                        avgGoldDiffAt15 = 1250.0,
                        gamesSampled = 40,
                        dominanceScore = 0.75,
                    ),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        teamKillsPerMinute = 0.60,
                        combinedKillsPerMinute = 1.10,
                        avgDurationSeconds = 1800.0,
                        avgDurationFormatted = "30:00",
                        pace = GamePace.FAST_PACED,
                        aggression = AggressionLevel.VERY_AGGRESSIVE,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = setOf(TacticalTag.EARLY_AGGRESSOR, TacticalTag.FAST_TEMPO, TacticalTag.BLUE_SIDE_SPECIALIST),
            )

        val redProfile =
            TeamTacticalProfile(
                team = redTeam,
                totalGamesAnalyzed = 40,
                sidePreference =
                    SidePreference(
                        blueRecord = SideRecord(20, 14, 6, 0.70),
                        redRecord = SideRecord(20, 15, 5, 0.75),
                        overallRecord = SideRecord(40, 29, 11, 0.725),
                        winRateDelta = -0.05,
                        blueRate = 0.70,
                        redRate = 0.75,
                        tendency = SideTendency.BALANCED,
                    ),
                earlyGameMetrics =
                    EarlyGameMetrics(
                        firstBloodRate = 0.50,
                        firstDragonRate = 0.70,
                        avgGoldDiffAt15 = 450.0,
                        gamesSampled = 40,
                        dominanceScore = 0.60,
                    ),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        teamKillsPerMinute = 0.45,
                        combinedKillsPerMinute = 0.85,
                        avgDurationSeconds = 2100.0,
                        avgDurationFormatted = "35:00",
                        pace = GamePace.SLOW_CONTROLLED,
                        aggression = AggressionLevel.CONTROL_ORIENTED,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = setOf(TacticalTag.LATE_GAME_MACRO, TacticalTag.DRAGON_CONTROL, TacticalTag.SLOW_TEMPO),
            )

        val fakerMidStats =
            PlayerCareerStats(
                playerId = "Faker",
                totalProGames = 800,
                totalWins = 550,
                winRate = 0.687,
                roleDistribution = mapOf(Role.MID to 800),
                championRecords =
                    mapOf(
                        "Azir" to ChampionCareerRecord("Azir", 120, 85, 35, 0.708, 0.15, Role.MID),
                        "Orianna" to ChampionCareerRecord("Orianna", 90, 65, 25, 0.722, 0.11, Role.MID),
                        "Ahri" to ChampionCareerRecord("Ahri", 50, 38, 12, 0.760, 0.06, Role.MID),
                    ),
                signaturePicks =
                    listOf(
                        SignaturePick("Azir", 120, 85, 0.708, 0.15, 0.95, SignatureTier.SIGNATURE, Role.MID),
                        SignaturePick("Orianna", 90, 65, 0.722, 0.11, 0.90, SignatureTier.SIGNATURE, Role.MID),
                    ),
            )

        val chovyMidStats =
            PlayerCareerStats(
                playerId = "Chovy",
                totalProGames = 600,
                totalWins = 430,
                winRate = 0.716,
                roleDistribution = mapOf(Role.MID to 600),
                championRecords =
                    mapOf(
                        "Yone" to ChampionCareerRecord("Yone", 45, 36, 9, 0.800, 0.075, Role.MID),
                        "Azir" to ChampionCareerRecord("Azir", 70, 52, 18, 0.742, 0.116, Role.MID),
                        "Corki" to ChampionCareerRecord("Corki", 40, 30, 10, 0.750, 0.066, Role.MID),
                    ),
                signaturePicks =
                    listOf(
                        SignaturePick("Yone", 45, 36, 0.800, 0.075, 0.96, SignatureTier.SIGNATURE, Role.MID),
                        SignaturePick("Azir", 70, 52, 0.742, 0.116, 0.92, SignatureTier.SIGNATURE, Role.MID),
                    ),
            )

        val blueDossiers =
            listOf(
                PlayerIntelligenceDossier(
                    playerId = "Faker",
                    careerStats = fakerMidStats,
                    linkedAccounts =
                        listOf(
                            SoloQAccount("faker_kr", "Hide on bush", SoloQServer.KR, "Challenger", "I", 1150),
                        ),
                    recentSoloQ3Days = emptyList(),
                    recentSoloQ7Days = emptyList(),
                    activeSpikeAlerts =
                        listOf(
                            SpikeAlert(
                                championId = "Ahri",
                                severity = SpikeAlertSeverity.HIGH,
                                type = SpikeAlertType.OFF_META_SURGE,
                                recentDays = 7,
                                recentGamesCount = 28,
                                recentWinRate = 0.785,
                                baselineGamesCount = 4,
                                baselineDays = 30,
                                frequencyMultiplier = 7.0,
                                careerProGames = 50,
                                reason = "Sudden high frequency soloQ practice with 78.5% win rate",
                            ),
                        ),
                    blindPickConfidences = emptyMap(),
                ),
            )

        val patchMeta =
            PatchMetaMatrix(
                patch = "14.15",
                totalGames = 120,
                championStats =
                    mapOf(
                        "Ashe" to ChampionMetaStats("Ashe", "14.15", 40, 50, 90, 0.75, 0.33, 0.41, 24, 16, 0.60, tier = MetaTier.T0),
                        "Rumble" to ChampionMetaStats("Rumble", "14.15", 35, 60, 95, 0.79, 0.29, 0.50, 20, 15, 0.57, tier = MetaTier.T0),
                        "Sejuani" to ChampionMetaStats("Sejuani", "14.15", 45, 30, 75, 0.62, 0.37, 0.25, 25, 20, 0.55, tier = MetaTier.T1),
                        "Azir" to ChampionMetaStats("Azir", "14.15", 30, 25, 55, 0.45, 0.25, 0.20, 16, 14, 0.53, tier = MetaTier.T1),
                        "Yone" to ChampionMetaStats("Yone", "14.15", 25, 35, 60, 0.50, 0.20, 0.29, 14, 11, 0.56, tier = MetaTier.T1),
                        "KSante" to ChampionMetaStats("KSante", "14.15", 35, 20, 55, 0.45, 0.29, 0.16, 18, 17, 0.51, tier = MetaTier.T1),
                        "Kalista" to ChampionMetaStats("Kalista", "14.15", 20, 60, 80, 0.66, 0.16, 0.50, 11, 9, 0.55, tier = MetaTier.T0),
                    ),
            )

        return MatchupSandboxRequest(
            blueTeam = blueTeam,
            redTeam = redTeam,
            blueTeamProfile = blueProfile,
            redTeamProfile = redProfile,
            bluePlayerStats = mapOf(Role.MID to fakerMidStats),
            redPlayerStats = mapOf(Role.MID to chovyMidStats),
            blueSoloQDossiers = blueDossiers,
            patchMeta = patchMeta,
        )
    }
}
