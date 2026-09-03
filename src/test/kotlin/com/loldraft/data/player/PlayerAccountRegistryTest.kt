package com.loldraft.data.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlayerAccountRegistryTest {
    @Test
    fun `should register and retrieve accounts for player`() {
        val registry = PlayerAccountRegistry()
        val accountKr =
            SoloQAccount(
                accountId = "kr_faker_01",
                summonerName = "Hide on bush",
                server = SoloQServer.KR,
                tier = "CHALLENGER",
                lp = 1250,
            )

        registry.registerAccount("Faker", accountKr)
        val accounts = registry.getAccountsForPlayer("Faker")

        assertEquals(1, accounts.size)
        assertEquals("Hide on bush", accounts[0].summonerName)
        assertEquals(SoloQServer.KR, accounts[0].server)
        assertEquals(1250, accounts[0].lp)
    }

    @Test
    fun `should register multiple accounts across different servers`() {
        val registry = PlayerAccountRegistry()
        val krAccount =
            SoloQAccount(
                accountId = "kr_1",
                summonerName = "Chovy KR",
                server = SoloQServer.KR,
                tier = "CHALLENGER",
            )
        val superAccount =
            SoloQAccount(
                accountId = "super_1",
                summonerName = "Chovy Super",
                server = SoloQServer.CN_SUPER,
                tier = "CHALLENGER",
            )

        registry.registerAccounts("Chovy", listOf(krAccount, superAccount))
        val accounts = registry.getAccountsForPlayer("Chovy")

        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.server == SoloQServer.KR })
        assertTrue(accounts.any { it.server == SoloQServer.CN_SUPER })
    }

    @Test
    fun `should update existing account when registering same accountId and server`() {
        val registry = PlayerAccountRegistry()
        val initial =
            SoloQAccount(
                accountId = "acc_1",
                summonerName = "Keria",
                server = SoloQServer.KR,
                lp = 800,
            )
        val updated =
            SoloQAccount(
                accountId = "acc_1",
                summonerName = "Keria T1",
                server = SoloQServer.KR,
                lp = 1100,
            )

        registry.registerAccount("Keria", initial)
        registry.registerAccount("Keria", updated)

        val accounts = registry.getAccountsForPlayer("Keria")
        assertEquals(1, accounts.size)
        assertEquals("Keria T1", accounts[0].summonerName)
        assertEquals(1100, accounts[0].lp)
    }

    @Test
    fun `should unregister account successfully`() {
        val registry = PlayerAccountRegistry()
        val account =
            SoloQAccount(
                accountId = "gumayusi_kr",
                summonerName = "Guma",
                server = SoloQServer.KR,
            )
        registry.registerAccount("Gumayusi", account)

        assertTrue(registry.unregisterAccount("Gumayusi", "gumayusi_kr"))
        assertEquals(0, registry.getAccountsForPlayer("Gumayusi").size)
        assertFalse(registry.unregisterAccount("Gumayusi", "non_existing"))
        assertFalse(registry.unregisterAccount("UnknownPlayer", "any_id"))
    }

    @Test
    fun `should lookup player by account id`() {
        val registry = PlayerAccountRegistry()
        registry.registerAccount(
            "Zeus",
            SoloQAccount("zeus_kr", "ZeusGod", SoloQServer.KR),
        )

        assertEquals("Zeus", registry.findPlayerByAccountId("zeus_kr"))
        assertNull(registry.findPlayerByAccountId("unknown_acc"))
    }

    @Test
    fun `should lookup player by summoner name and server case-insensitively`() {
        val registry = PlayerAccountRegistry()
        registry.registerAccount(
            "Faker",
            SoloQAccount("kr_faker", "Hide on bush", SoloQServer.KR),
        )

        assertEquals("Faker", registry.findPlayerBySummoner(SoloQServer.KR, "hide on bush"))
        assertEquals("Faker", registry.findPlayerBySummoner(SoloQServer.KR, "HIDE ON BUSH"))
        assertNull(registry.findPlayerBySummoner(SoloQServer.CN_SUPER, "Hide on bush"))
        assertNull(registry.findPlayerBySummoner(SoloQServer.KR, "SomeoneElse"))
    }

    @Test
    fun `should handle concurrent registrations safely`() {
        val registry = PlayerAccountRegistry()
        val executor = Executors.newFixedThreadPool(8)

        for (i in 1..100) {
            val playerId = "Player_${i % 10}"
            executor.submit {
                registry.registerAccount(
                    playerId,
                    SoloQAccount("acc_$i", "Summoner_$i", SoloQServer.KR),
                )
            }
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        var totalAccounts = 0
        for (i in 0 until 10) {
            totalAccounts += registry.getAccountsForPlayer("Player_$i").size
        }
        assertEquals(100, totalAccounts)
    }
}
