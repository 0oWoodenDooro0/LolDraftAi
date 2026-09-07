package com.loldraft.client.compose.image

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DdragonVersionServiceTest {
    @Test
    fun `should have valid non-blank fallback version`() {
        val version = DdragonVersionService.getVersion()
        assertTrue(version.isNotBlank())
        assertTrue(version.matches(Regex("""\d+\.\d+(\.\d+)?""")))
    }

    @Test
    fun `should successfully query or fallback to valid version from versions API`() = runBlocking {
        val version = DdragonVersionService.refreshLatestVersion()
        assertNotNull(version)
        assertTrue(version.isNotBlank())
        assertTrue(version.matches(Regex("""\d+\.\d+(\.\d+)?""")))
    }
}
