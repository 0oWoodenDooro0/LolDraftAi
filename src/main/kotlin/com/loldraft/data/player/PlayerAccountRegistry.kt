package com.loldraft.data.player

import java.util.concurrent.ConcurrentHashMap

class PlayerAccountRegistry(
    initialMappings: Map<String, List<SoloQAccount>> = emptyMap(),
) {
    private val playerToAccounts = ConcurrentHashMap<String, MutableList<SoloQAccount>>()

    init {
        for ((playerId, accounts) in initialMappings) {
            playerToAccounts[playerId] = accounts.toMutableList()
        }
    }

    fun registerAccount(
        playerId: String,
        account: SoloQAccount,
    ) {
        val list = playerToAccounts.computeIfAbsent(playerId) { mutableListOf() }
        synchronized(list) {
            val idx = list.indexOfFirst { it.accountId == account.accountId && it.server == account.server }
            if (idx >= 0) {
                list[idx] = account
            } else {
                list.add(account)
            }
        }
    }

    fun registerAccounts(
        playerId: String,
        accounts: List<SoloQAccount>,
    ) {
        accounts.forEach { registerAccount(playerId, it) }
    }

    fun unregisterAccount(
        playerId: String,
        accountId: String,
    ): Boolean {
        val list = playerToAccounts[playerId] ?: return false
        return synchronized(list) {
            list.removeIf { it.accountId == accountId }
        }
    }

    fun getAccountsForPlayer(playerId: String): List<SoloQAccount> {
        val list = playerToAccounts[playerId] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun findPlayerByAccountId(accountId: String): String? {
        for ((playerId, accounts) in playerToAccounts) {
            val found = synchronized(accounts) { accounts.any { it.accountId == accountId } }
            if (found) return playerId
        }
        return null
    }

    fun findPlayerBySummoner(
        server: SoloQServer,
        summonerName: String,
    ): String? {
        for ((playerId, accounts) in playerToAccounts) {
            val found =
                synchronized(accounts) {
                    accounts.any {
                        it.server == server && it.summonerName.equals(summonerName, ignoreCase = true)
                    }
                }
            if (found) return playerId
        }
        return null
    }

    fun getAllMappings(): Map<String, List<SoloQAccount>> = playerToAccounts.mapValues { (_, v) -> synchronized(v) { v.toList() } }
}
