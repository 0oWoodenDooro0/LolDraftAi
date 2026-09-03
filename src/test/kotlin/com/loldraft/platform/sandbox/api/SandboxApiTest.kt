package com.loldraft.platform.sandbox.api

import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import com.loldraft.platform.sandbox.PreMatchSandboxEngine
import com.loldraft.platform.sandbox.models.MatchupSandboxRequest
import com.loldraft.platform.sandbox.models.MatchupSandboxResponse
import com.loldraft.platform.sandbox.models.WhatIfBranchApiRequest
import com.loldraft.platform.sandbox.models.WhatIfBranchRequest
import com.loldraft.platform.sandbox.models.WhatIfBranchResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SandboxApiTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun createTestEngine(): PreMatchSandboxEngine = PreMatchSandboxEngine()

    @Test
    fun testHealthEndpoint() =
        testApplication {
            application {
                sandboxModule(createTestEngine())
            }

            val response = client.get("/api/sandbox/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UP"))
            assertTrue(body.contains("pre-match-sandbox"))
        }

    @Test
    fun testSimulateEndpoint() =
        testApplication {
            val engine = createTestEngine()
            application {
                sandboxModule(engine)
            }

            val request =
                MatchupSandboxRequest(
                    blueTeam = Team("team-t1", "T1", "T1", "LCK"),
                    redTeam = Team("team-gen", "Gen.G", "GEN", "LCK"),
                )

            val response =
                client.post("/api/sandbox/simulate") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(request))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            val parsed = json.decodeFromString<MatchupSandboxResponse>(responseBody)

            assertEquals("T1", parsed.blueTeam.code)
            assertEquals("GEN", parsed.redTeam.code)
            assertEquals(3, parsed.scenarios.size)
            assertNotNull(parsed.rootDraftTree)
        }

    @Test
    fun testWhatIfEndpoint() =
        testApplication {
            val engine = createTestEngine()
            application {
                sandboxModule(engine)
            }

            val baseRequest =
                MatchupSandboxRequest(
                    blueTeam = Team("team-t1", "T1", "T1", "LCK"),
                    redTeam = Team("team-gen", "Gen.G", "GEN", "LCK"),
                )
            val initialSim = engine.generateScenarios(baseRequest)
            val baseDraft = initialSim.scenarios.first().draftState

            val whatIfReq =
                WhatIfBranchApiRequest(
                    baseDraftState = baseDraft,
                    branchRequest =
                        WhatIfBranchRequest(
                            branchTurnNumber = 7,
                            newChampionId = "Ashe",
                            newRole = Role.BOT,
                            rationale = "Test What-If branching via API",
                        ),
                    context = baseRequest,
                )

            val response =
                client.post("/api/sandbox/what-if") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(whatIfReq))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val parsed = json.decodeFromString<WhatIfBranchResult>(response.bodyAsText())
            assertEquals(7, parsed.branchTurnNumber)
            assertEquals(20, parsed.newScenario.draftState.turns.size)
            assertNotNull(parsed.comparativeDelta)
        }

    @Test
    fun testWhatIfEndpointRejectsDuplicateChampion() =
        testApplication {
            val engine = createTestEngine()
            application {
                sandboxModule(engine)
            }

            val baseRequest =
                MatchupSandboxRequest(
                    blueTeam = Team("team-t1", "T1", "T1", "LCK"),
                    redTeam = Team("team-gen", "Gen.G", "GEN", "LCK"),
                )
            val initialSim = engine.generateScenarios(baseRequest)
            val baseDraft = initialSim.scenarios.first().draftState

            // Champion banned at Turn 1
            val turn1Champion = baseDraft.turns.first { it.turnNumber == 1 }.championId

            val whatIfReq =
                WhatIfBranchApiRequest(
                    baseDraftState = baseDraft,
                    branchRequest =
                        WhatIfBranchRequest(
                            branchTurnNumber = 7,
                            newChampionId = turn1Champion,
                        ),
                    context = baseRequest,
                )

            val response =
                client.post("/api/sandbox/what-if") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(whatIfReq))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("error") || body.contains("already been selected") || body.contains("duplicate"))
        }
}
