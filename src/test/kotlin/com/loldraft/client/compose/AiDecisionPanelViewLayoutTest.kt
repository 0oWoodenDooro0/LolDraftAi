package com.loldraft.client.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.loldraft.client.compose.ui.DraftApp
import com.loldraft.client.compose.ui.components.AiDecisionPanelView
import com.loldraft.client.compose.ui.components.FearlessDraftDialog
import com.loldraft.client.compose.ui.components.NextBpPredictionView
import com.loldraft.client.compose.viewmodel.DraftClientViewModel
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.models.ChampionIntentCandidate
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.FlawCategory
import com.loldraft.models.FlawSeverity
import com.loldraft.models.PickRecommendation
import com.loldraft.server.ProMatchRepository
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AiDecisionPanelViewLayoutTest {
    @Test
    fun `test AiDecisionPanelView renders inside verticalScroll container without crash`() =
        runDesktopComposeUiTest {
            val intentPredictions =
                listOf(
                    ChampionIntentCandidate(
                        championId = "Ahri",
                        probability = 0.42,
                        intentScore = 0.42,
                        predictedRole = Role.MID,
                        rationale = "High meta priority & comfort pick",
                    ),
                )
            val recommendations =
                listOf(
                    PickRecommendation(
                        championId = "Syndra",
                        recommendedRole = Role.MID,
                        winRateGain = 0.035,
                        predictedWinRate = 0.535,
                        baseWinRate = 0.50,
                        reasons = listOf("Strong lane matchup vs Ahri"),
                    ),
                )
            val compositionFlaws =
                listOf(
                    CompositionFlaw(
                        id = "flaw-1",
                        category = FlawCategory.ENGAGE_FRONTLINE,
                        severity = FlawSeverity.WARNING,
                        title = "Lack of Engage",
                        description = "Team lacks reliable hard engage",
                        affectedSide = Side.BLUE,
                        currentPicksCount = 3,
                        suggestion = "Pick a vanguard support or top laner",
                    ),
                )

            setContent {
                val parentScrollState = rememberScrollState()
                Column(
                    modifier =
                        Modifier
                            .width(340.dp)
                            .verticalScroll(parentScrollState),
                ) {
                    AiDecisionPanelView(
                        currentTurnNumber = 1,
                        currentTurnSpec = com.loldraft.data.models.DraftTurnSpec.forTurn(1, Side.BLUE),
                        intentPredictions = intentPredictions,
                        recommendations = recommendations,
                        compositionFlaws = compositionFlaws,
                    )
                }
            }

            // Wait for measure and layout pass
            waitForIdle()
        }

    @Test
    fun `test DraftApp root composition mounts and measures successfully`() =
        runDesktopComposeUiTest {
            val repository = ProMatchRepository().apply { initialize() }
            val viewModel = DraftClientViewModel(repository = repository)

            setContent {
                DraftApp(viewModel = viewModel)
            }

            // Wait for measure and layout pass
            waitForIdle()
        }

    @Test
    fun `test NextBpPredictionView renders candidate intent predictions cleanly`() =
        runDesktopComposeUiTest {
            val intentPredictions =
                listOf(
                    ChampionIntentCandidate(
                        championId = "Ahri",
                        probability = 0.55,
                        intentScore = 0.55,
                        predictedRole = Role.MID,
                        rationale = "Core priority mid pick",
                    ),
                    ChampionIntentCandidate(
                        championId = "Orianna",
                        probability = 0.25,
                        intentScore = 0.25,
                        predictedRole = Role.MID,
                        rationale = "Control mage meta anchor",
                    ),
                )

            setContent {
                NextBpPredictionView(
                    currentTurnNumber = 1,
                    currentTurnSpec = com.loldraft.data.models.DraftTurnSpec.forTurn(1, Side.BLUE),
                    intentPredictions = intentPredictions,
                )
            }
            waitForIdle()
        }

    @Test
    fun `test FearlessDraftDialog renders cleanly with exclusions`() =
        runDesktopComposeUiTest {
            setContent {
                FearlessDraftDialog(
                    allChampions = emptyList(),
                    excludedChampionIds = setOf("Ahri", "Vi"),
                    currentPicksCount = 2,
                    onAddChampion = {},
                    onRemoveChampion = {},
                    onClearAll = {},
                    onImportCurrentPicks = {},
                    onDismiss = {},
                )
            }
            waitForIdle()
        }
}
