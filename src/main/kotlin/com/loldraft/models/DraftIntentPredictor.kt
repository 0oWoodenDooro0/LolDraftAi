package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.DamageType
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.player.BlindPickConfidenceCalculator
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.ConfidenceRating
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.style.TeamTacticalProfile
import java.util.Locale
import kotlin.math.round

class DraftIntentPredictor(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val blindConfidenceCalc: BlindPickConfidenceCalculator = BlindPickConfidenceCalculator(),
) {
    private fun findChampionRecord(
        careerStats: PlayerCareerStats?,
        champ: String,
    ): ChampionCareerRecord? {
        if (careerStats == null) return null
        val targetSlug = ChampionNormalizer.toSlug(champ)
        return careerStats.championRecords.values.firstOrNull {
            ChampionNormalizer.toSlug(it.championId) == targetSlug
        } ?: careerStats.championRecords[champ]
    }

    private fun findSignaturePick(
        careerStats: PlayerCareerStats?,
        champ: String,
    ): SignaturePick? {
        if (careerStats == null) return null
        val targetSlug = ChampionNormalizer.toSlug(champ)
        return careerStats.signaturePicks.firstOrNull {
            ChampionNormalizer.toSlug(it.championId) == targetSlug
        }
    }

    private fun isViableForRole(
        champ: String,
        role: Role,
        profile: ChampionProfile?,
        flexAnalysis: FlexAnalysisResult,
        targetCareerStats: PlayerCareerStats?,
        metaStats: ChampionMetaStats? = null,
    ): Boolean {
        // 1. 若有版本/賽事職業賽數據，以職業賽實際有出現過的路線為準
        if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
            val proCount = metaStats.roleDistribution[role] ?: 0
            if (proCount > 0) return true
            // 檢查該選手生涯是否在職業賽中打過該英雄的該路線
            val rec = findChampionRecord(targetCareerStats, champ)
            if (rec != null && rec.gamesPlayed > 0 && (rec.role == null || rec.role == role)) return true
            val sig = findSignaturePick(targetCareerStats, champ)
            if (sig != null && (sig.role == null || sig.role == role)) return true
            // 若職業賽該路線從未有出場紀錄，且選手也未曾在該路線打過，則不採用系統預設
            return false
        }

        // 2. 檢查選手個人職業生涯是否有在該路線使用過
        val rec = findChampionRecord(targetCareerStats, champ)
        if (rec != null && rec.gamesPlayed >= 2 && (rec.role == null || rec.role == role)) return true
        val sig = findSignaturePick(targetCareerStats, champ)
        if (sig != null && (sig.role == null || sig.role == role)) return true

        // 3. 若無任何職業賽數據，才 fallback 到系統預設
        if (profile?.primaryRole == role) return true
        if (profile?.secondaryRoles?.contains(role) == true) return true
        if ((flexAnalysis.roleProbabilities[role] ?: 0.0) >= 0.15) return true
        return false
    }

    fun predictNextAction(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        playerDossiersByRole: Map<Role, PlayerIntelligenceDossier>? = null,
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        firstPickSide: Side = Side.BLUE,
        topN: Int = 5,
    ): IntentPredictionResult {
        val effectiveProfiles =
            playerProfilesByRole
                ?: playerDossiersByRole?.mapValues { ProPlayerDetailedProfile.fromDossier(it.key, it.value) }
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val turnSpec = DraftTurnSpec.forTurn(turnNumber, firstPickSide)
        return predictForTurnSpec(
            turnSpec = turnSpec,
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            playerProfilesByRole = effectiveProfiles,
            opponentPlayerProfilesByRole = opponentPlayerProfilesByRole,
            opponentBansAgainstTargetTeam = opponentBansAgainstTargetTeam,
            targetTeamName = targetTeamName,
            topN = topN,
        )
    }

    fun predictIntentForSide(
        draftState: DraftState,
        actingSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        playerDossiersByRole: Map<Role, PlayerIntelligenceDossier>? = null,
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        firstPickSide: Side = Side.BLUE,
        topN: Int = 5,
    ): IntentPredictionResult {
        val effectiveProfiles =
            playerProfilesByRole
                ?: playerDossiersByRole?.mapValues { ProPlayerDetailedProfile.fromDossier(it.key, it.value) }
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val defaultSpec = DraftTurnSpec.forTurn(turnNumber, firstPickSide)
        val turnSpec =
            if (defaultSpec.side == actingSide) {
                defaultSpec
            } else {
                DraftTurnSpec(
                    turnNumber = turnNumber,
                    phase = defaultSpec.phase,
                    side = actingSide,
                    actionType = defaultSpec.actionType,
                )
            }
        return predictForTurnSpec(
            turnSpec = turnSpec,
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            playerProfilesByRole = effectiveProfiles,
            opponentPlayerProfilesByRole = opponentPlayerProfilesByRole,
            opponentBansAgainstTargetTeam = opponentBansAgainstTargetTeam,
            targetTeamName = targetTeamName,
            topN = topN,
        )
    }

    private fun predictForTurnSpec(
        turnSpec: DraftTurnSpec,
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        teamProfile: TeamTacticalProfile?,
        playerStatsByRole: Map<Role, PlayerCareerStats>?,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>?,
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        topN: Int,
    ): IntentPredictionResult {
        val unavailable = draftState.allSelectedChampions.map { ChampionNormalizer.toSlug(it) }.toSet()

        // Candidate pool (deduplicated by canonical champion slug)
        val candidateMap = mutableMapOf<String, String>()
        tagRegistry.getAllProfiles().forEach {
            val slug = ChampionNormalizer.toSlug(it.displayName)
            if (slug.isNotBlank()) {
                candidateMap[slug] = it.displayName
            }
        }
        patchMeta?.championStats?.values?.forEach {
            val norm = ChampionNormalizer.normalize(it.championId)
            val slug = ChampionNormalizer.toSlug(norm)
            if (slug.isNotBlank() && !candidateMap.containsKey(slug)) {
                candidateMap[slug] = norm
            }
        }

        val candidates = candidateMap.filterKeys { it !in unavailable }.values.toList()

        val actingSide = turnSpec.side
        val isBan = turnSpec.actionType == ActionType.BAN
        val teamPicks = if (actingSide == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val enemyPicks = if (actingSide == Side.BLUE) draftState.redPicks else draftState.bluePicks

        val explicitlyLocked = teamPicks.mapNotNull { it.role }.toSet()
        val lockedRoles = explicitlyLocked.toMutableSet()
        for (p in teamPicks) {
            if (p.role == null) {
                val pRole = tagRegistry.getProfile(p.championId)?.primaryRole
                if (pRole != null) {
                    lockedRoles.add(pRole)
                }
            }
        }
        val vacantRoles = Role.entries.filterNot { it in lockedRoles }.toSet()

        val enemyExplicitlyLocked = enemyPicks.mapNotNull { it.role }.toSet()
        val enemyLockedRoles = enemyExplicitlyLocked.toMutableSet()
        for (p in enemyPicks) {
            if (p.role == null) {
                val pRole = tagRegistry.getProfile(p.championId)?.primaryRole
                if (pRole != null) {
                    enemyLockedRoles.add(pRole)
                }
            }
        }

        fun findPickForRole(picks: List<PickSelection>, role: Role): PickSelection? {
            return picks.find { it.role == role }
                ?: picks.find { it.role == null && tagRegistry.getProfile(it.championId)?.primaryRole == role }
        }

        val enemyBot = findPickForRole(enemyPicks, Role.BOT)
        val enemySup = findPickForRole(enemyPicks, Role.SUPPORT)
        val enemyTop = findPickForRole(enemyPicks, Role.TOP)
        val enemyMid = findPickForRole(enemyPicks, Role.MID)
        val enemyJungle = findPickForRole(enemyPicks, Role.JUNGLE)

        val allyBot = findPickForRole(teamPicks, Role.BOT)
        val allySup = findPickForRole(teamPicks, Role.SUPPORT)
        val allyTop = findPickForRole(teamPicks, Role.TOP)
        val allyMid = findPickForRole(teamPicks, Role.MID)
        val allyJungle = findPickForRole(teamPicks, Role.JUNGLE)

        val enemyDuoComplete = enemyBot != null && enemySup != null
        val allyDuoComplete = allyBot != null && allySup != null

        val damageSplit = tagRegistry.calculateTeamDamageSplit(teamPicks.map { it.championId })
        val needsAp = teamPicks.size >= 2 && damageSplit.physicalRatio >= 0.75
        val needsAd = teamPicks.size >= 2 && damageSplit.magicRatio >= 0.75

        val effectiveCareerStats = playerStatsByRole ?: playerProfilesByRole?.mapValues { it.value.careerStats }
        val hasDetailedProfiles = playerProfilesByRole != null && playerProfilesByRole.isNotEmpty()

        val scoredList = mutableListOf<ChampionIntentCandidate>()

        for (champ in candidates) {
            val profile = tagRegistry.getProfile(champ)
            val metaStats = patchMeta?.getStats(champ)

            val flexAnalysis = flexAnalyzer.analyzeChampion(champ, patchMeta, lockedRoles)
            val primaryRole =
                if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
                    metaStats.roleDistribution.maxByOrNull { it.value }?.key ?: flexAnalysis.primaryRole
                } else {
                    profile?.primaryRole ?: flexAnalysis.primaryRole
                }

            // 1. For pick turns, filter candidates that can play an available vacant role
            val viableVacantRoles =
                if (!isBan && vacantRoles.isNotEmpty()) {
                    vacantRoles.filter { r ->
                        val rStats = playerProfilesByRole?.get(r)?.careerStats ?: effectiveCareerStats?.get(r)
                        isViableForRole(champ, r, profile, flexAnalysis, rStats, metaStats)
                    }
                } else {
                    emptyList()
                }

            // In pick turns, if the champion cannot play any available vacant role, skip immediately!
            if (!isBan && vacantRoles.isNotEmpty() && viableVacantRoles.isEmpty()) {
                continue
            }

            // Target role resolution:
            val targetRole =
                if (!isBan) {
                    when {
                        primaryRole in viableVacantRoles -> primaryRole
                        viableVacantRoles.isNotEmpty() ->
                            viableVacantRoles.maxByOrNull { flexAnalysis.roleProbabilities[it] ?: 0.0 } ?: viableVacantRoles.first()
                        else -> primaryRole
                    }
                } else {
                    primaryRole
                }

            val targetProfile = playerProfilesByRole?.get(targetRole)
            val targetCareerStats = targetProfile?.careerStats ?: effectiveCareerStats?.get(targetRole)
            val targetPlayerName = targetProfile?.playerId ?: targetCareerStats?.playerId

            // 1. Meta score with robust fallback to prevent identical scores
            var metaScore = 0.3
            if (metaStats != null) {
                val tierScore =
                    when (metaStats.tier) {
                        MetaTier.T0 -> 1.0
                        MetaTier.T1 -> 0.8
                        MetaTier.T2 -> 0.6
                        MetaTier.T3 -> 0.4
                        MetaTier.T4 -> 0.2
                    }
                val presenceScore = metaStats.presenceRate.coerceIn(0.0, 1.0)
                val winRateScore = ((metaStats.winRate - 0.50) * 2.0).coerceIn(-0.5, 0.5)
                metaScore = (tierScore * 0.5) + (presenceScore * 0.4) + (winRateScore * 0.1)
            } else {
                metaScore = 0.30
            }

            // 2. Opponent respect ban score (what other teams ban against target team)
            val oppBanRecord =
                opponentBansAgainstTargetTeam?.firstOrNull {
                    it.championId.equals(champ, ignoreCase = true) ||
                        ChampionNormalizer.normalize(it.championId).equals(ChampionNormalizer.normalize(champ), ignoreCase = true)
                }
            val opponentRespectBanScore = oppBanRecord?.banRate ?: 0.0

            // 3. Player career mastery score (for pick: acting team; for ban: target opponent)
            var playerMasteryScore = 0.0
            var matchedSignature: String? = null
            var matchedOpponentPlayer: String? = null
            var matchedOppRole: Role? = null

            if (isBan) {
                // In BAN turns, check if champion is in opponent's player mastery pool
                // Only consider roles the opponent has NOT already locked in draft!
                if (opponentPlayerProfilesByRole != null && opponentPlayerProfilesByRole.isNotEmpty()) {
                    for ((r, oProfile) in opponentPlayerProfilesByRole) {
                        if (r in enemyLockedRoles) continue
                        val sig = findSignaturePick(oProfile.careerStats, champ)
                        if (sig != null) {
                            val bonus =
                                when (sig.tier) {
                                    SignatureTier.SIGNATURE -> 0.95
                                    SignatureTier.COMFORT -> 0.80
                                    SignatureTier.POCKET -> 0.70
                                }
                            if (bonus > playerMasteryScore) {
                                playerMasteryScore = bonus
                                matchedSignature = sig.tier.name
                                matchedOpponentPlayer = "${oProfile.playerId} ($r)"
                                matchedOppRole = r
                            }
                        } else {
                            val rec = findChampionRecord(oProfile.careerStats, champ)
                            if (rec != null && rec.gamesPlayed > 0) {
                                val score = (rec.winRate * 0.40 + (rec.gamesPlayed / 20.0).coerceAtMost(1.0) * 0.30).coerceIn(0.20, 0.65)
                                if (score > playerMasteryScore) {
                                    playerMasteryScore = score
                                    matchedOpponentPlayer = "${oProfile.playerId} ($r)"
                                    matchedOppRole = r
                                }
                            }
                        }
                    }
                }
            } else {
                // In PICK turns, check ONLY acting team's player mastery for targetRole!
                if (targetCareerStats != null) {
                    val sig = findSignaturePick(targetCareerStats, champ)
                    if (sig != null) {
                        val tierBonus =
                            when (sig.tier) {
                                SignatureTier.SIGNATURE -> 0.95
                                SignatureTier.COMFORT -> 0.80
                                SignatureTier.POCKET -> 0.70
                            }
                        playerMasteryScore = tierBonus
                        matchedSignature = sig.tier.name
                    } else {
                        val record = findChampionRecord(targetCareerStats, champ)
                        if (record != null && record.gamesPlayed > 0) {
                            playerMasteryScore =
                                (record.winRate * 0.40 + (record.gamesPlayed / 20.0).coerceAtMost(1.0) * 0.30).coerceIn(0.20, 0.65)
                        }
                    }
                }
            }

            // 4. Composition fit & role gap score
            var compositionFitScore = 0.0
            if (!isBan) {
                val canFillVacant = flexAnalysis.roleProbabilities.any { it.key in vacantRoles && it.value >= 0.20 }
                if (canFillVacant) {
                    compositionFitScore += 0.45
                } else if (lockedRoles.isNotEmpty() &&
                    flexAnalysis.roleProbabilities[targetRole] ?: 0.0 >= 0.70 &&
                    targetRole in lockedRoles
                ) {
                    compositionFitScore -= 0.40
                }

                val isAp =
                    profile?.damageProfile?.primaryType == DamageType.MAGIC ||
                        (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65
                val isAd =
                    profile?.damageProfile?.primaryType == DamageType.PHYSICAL ||
                        (profile?.damageProfile?.physicalRatio ?: 0.0) >= 0.65

                if (needsAp && isAp) {
                    compositionFitScore += 0.45
                }
                if (needsAd && isAd) {
                    compositionFitScore += 0.35
                }
            } else {
                if (metaStats?.tier == MetaTier.T0 || (metaStats?.presenceRate ?: 0.0) >= 0.70) {
                    compositionFitScore += 0.40
                }
            }
            compositionFitScore = compositionFitScore.coerceIn(-1.0, 1.0)

            // 5. Bot Duo Synergy & 2v2 Matchup Counter (Only when acting side's bot duo is NOT complete)
            var botDuoScore = 0.0
            var matchedDuoPartner: String? = null
            var matchedDuoStats: com.loldraft.data.meta.BotDuoSynergy? = null
            var duoCounterBonus = 0.0
            var matchedDuoMatchup: com.loldraft.data.meta.BotDuoMatchup? = null

            if (!isBan && !allyDuoComplete) {
                if (targetRole == Role.SUPPORT && allyBot != null && allySup == null) {
                    val duo =
                        patchMeta?.getDuoSynergy(allyBot.championId, champ)
                            ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(allyBot.championId) &&
                                    ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(champ)
                            }
                    if (duo != null) {
                        val winRateBonus = ((duo.synergyWinRate - 0.50) * 0.4).coerceAtLeast(0.0)
                        botDuoScore = ((duo.synergyScore / 80.0) + winRateBonus).coerceIn(0.3, 1.0)
                        matchedDuoPartner = allyBot.championId
                        matchedDuoStats = duo

                        if (enemyBot != null && enemySup != null) {
                            val dMatch =
                                patchMeta?.getDuoMatchup(allyBot.championId, champ, enemyBot.championId, enemySup.championId)
                                    ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_DUO_MATCHUPS.find {
                                        ChampionNormalizer.toSlug(it.blueDuo.first) == ChampionNormalizer.toSlug(allyBot.championId) &&
                                            ChampionNormalizer.toSlug(it.blueDuo.second) == ChampionNormalizer.toSlug(champ) &&
                                            ChampionNormalizer.toSlug(it.redDuo.first) == ChampionNormalizer.toSlug(enemyBot.championId) &&
                                            ChampionNormalizer.toSlug(it.redDuo.second) == ChampionNormalizer.toSlug(enemySup.championId)
                                    }
                            if (dMatch != null && dMatch.blueWinRate >= 0.52) {
                                duoCounterBonus = ((dMatch.blueWinRate - 0.50) * 0.4).coerceIn(0.05, 0.20)
                                matchedDuoMatchup = dMatch
                            }
                        }
                    }
                } else if (targetRole == Role.BOT && allySup != null && allyBot == null) {
                    val duo =
                        patchMeta?.getDuoSynergy(champ, allySup.championId)
                            ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(champ) &&
                                    ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(allySup.championId)
                            }
                    if (duo != null) {
                        val winRateBonus = ((duo.synergyWinRate - 0.50) * 0.4).coerceAtLeast(0.0)
                        botDuoScore = ((duo.synergyScore / 80.0) + winRateBonus).coerceIn(0.3, 1.0)
                        matchedDuoPartner = allySup.championId
                        matchedDuoStats = duo

                        if (enemyBot != null && enemySup != null) {
                            val dMatch =
                                patchMeta?.getDuoMatchup(champ, allySup.championId, enemyBot.championId, enemySup.championId)
                                    ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_DUO_MATCHUPS.find {
                                        ChampionNormalizer.toSlug(it.blueDuo.first) == ChampionNormalizer.toSlug(champ) &&
                                            ChampionNormalizer.toSlug(it.blueDuo.second) == ChampionNormalizer.toSlug(allySup.championId) &&
                                            ChampionNormalizer.toSlug(it.redDuo.first) == ChampionNormalizer.toSlug(enemyBot.championId) &&
                                            ChampionNormalizer.toSlug(it.redDuo.second) == ChampionNormalizer.toSlug(enemySup.championId)
                                    }
                            if (dMatch != null && dMatch.blueWinRate >= 0.52) {
                                duoCounterBonus = ((dMatch.blueWinRate - 0.50) * 0.4).coerceIn(0.05, 0.20)
                                matchedDuoMatchup = dMatch
                            }
                        }
                    }
                }
            }

            // 5.6 Enemy Bot Duo Denial in Ban turns (Only when enemy has picked EXACTLY ONE of Bot/Support)
            var enemyDuoDenialScore = 0.0
            var matchedEnemyDuoPartner: String? = null
            var matchedEnemyDuoStats: com.loldraft.data.meta.BotDuoSynergy? = null
            if (isBan && !enemyDuoComplete) {
                if (enemyBot != null && enemySup == null && (profile?.primaryRole == Role.SUPPORT || (flexAnalysis.roleProbabilities[Role.SUPPORT] ?: 0.0) >= 0.20)) {
                    val d =
                        patchMeta?.getDuoSynergy(enemyBot.championId, champ)
                            ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(enemyBot.championId) &&
                                    ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(champ)
                            }
                    if (d != null && d.synergyScore >= 65.0) {
                        enemyDuoDenialScore = (d.synergyScore / 85.0).coerceIn(0.5, 1.0)
                        matchedEnemyDuoPartner = enemyBot.championId
                        matchedEnemyDuoStats = d
                    }
                } else if (enemySup != null && enemyBot == null && (profile?.primaryRole == Role.BOT || (flexAnalysis.roleProbabilities[Role.BOT] ?: 0.0) >= 0.20)) {
                    val d =
                        patchMeta?.getDuoSynergy(champ, enemySup.championId)
                            ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(champ) &&
                                    ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(enemySup.championId)
                            }
                    if (d != null && d.synergyScore >= 65.0) {
                        enemyDuoDenialScore = (d.synergyScore / 85.0).coerceIn(0.5, 1.0)
                        matchedEnemyDuoPartner = enemySup.championId
                        matchedEnemyDuoStats = d
                    }
                }
            }

            // 5.7 Solo Lane Protective Ban in Ban turns (Neutralizing enemy counter pick against ally's locked solo laner)
            var protectiveBanScore = 0.0
            var matchedProtectiveLane: Role? = null
            var matchedProtectedAlly: String? = null
            var matchedCounterMatchup: com.loldraft.data.meta.MatchupCounter? = null
            if (isBan && patchMeta != null) {
                val vulnerableAllies = mutableListOf<Pair<Role, PickSelection>>()
                if (allyTop != null && enemyTop == null && (profile?.primaryRole == Role.TOP || (flexAnalysis.roleProbabilities[Role.TOP] ?: 0.0) >= 0.20)) {
                    vulnerableAllies.add(Role.TOP to allyTop)
                }
                if (allyMid != null && enemyMid == null && (profile?.primaryRole == Role.MID || (flexAnalysis.roleProbabilities[Role.MID] ?: 0.0) >= 0.20)) {
                    vulnerableAllies.add(Role.MID to allyMid)
                }
                for ((vRole, vAlly) in vulnerableAllies) {
                    val m =
                        patchMeta.getMatchup(champ, vAlly.championId, vRole)
                            ?: patchMeta.getMatchup(champ, vAlly.championId)
                    if (m != null && m.winRate >= 0.52 && m.counterScore >= 52.0) {
                        val score = (((m.winRate - 0.50) * 1.5) + ((m.counterScore - 50.0) / 50.0) * 0.4).coerceIn(0.3, 0.85)
                        if (score > protectiveBanScore) {
                            protectiveBanScore = score
                            matchedProtectiveLane = vRole
                            matchedProtectedAlly = vAlly.championId
                            matchedCounterMatchup = m
                        }
                    }
                }
            }

            // 6. Solo Lane 先選 (Blind Pick) vs 候選 (Counter Pick) in Pick turns
            val isSoloLane = !isBan && (targetRole == Role.TOP || targetRole == Role.MID)
            val enemyLaneOpponent = if (isSoloLane) findPickForRole(enemyPicks, targetRole) else null
            val isCounterPick = enemyLaneOpponent != null
            val isBlindPick = isSoloLane && enemyLaneOpponent == null

            var laneMatchupScore = 0.0
            var matchedDirectMatchup: com.loldraft.data.meta.MatchupCounter? = null
            if (enemyLaneOpponent != null) {
                val m =
                    patchMeta?.getMatchup(champ, enemyLaneOpponent.championId, targetRole)
                        ?: patchMeta?.getMatchup(champ, enemyLaneOpponent.championId)
                if (m != null) {
                    matchedDirectMatchup = m
                    val wrDelta = (m.winRate - 0.50) * 1.8
                    val counterNorm = ((m.counterScore - 50.0) / 40.0) * 0.3
                    val gdBonus = ((m.avgGoldDiffAt15 ?: 0.0) / 1000.0).coerceIn(-0.25, 0.25)
                    laneMatchupScore = (0.50 + wrDelta + counterNorm + gdBonus).coerceIn(0.05, 1.0)
                } else {
                    val oppProfile = tagRegistry.getProfile(enemyLaneOpponent.championId)
                    val myLaning = profile?.radar?.laningStrength ?: 5.0
                    val oppLaning = oppProfile?.radar?.laningStrength ?: 5.0
                    laneMatchupScore = (0.50 + (myLaning - oppLaning) * 0.04).coerceIn(0.35, 0.75)
                }
            }

            var blindPickScore = 0.0
            var blindConfidenceRating: ConfidenceRating? = null
            if (isBlindPick) {
                val dossierConfidence = targetProfile?.dossier?.blindPickConfidences?.get(champ)
                val calculatedConfidence =
                    dossierConfidence ?: blindConfidenceCalc.calculateConfidence(champ, targetCareerStats?.championRecords?.get(champ))
                blindConfidenceRating = calculatedConfidence.rating
                val blindMastery = (calculatedConfidence.confidenceScore / 100.0).coerceIn(0.0, 1.0)

                val flexBlindBonus = if (flexAnalysis.isFlex) 0.15 else 0.0
                val laningSafety = ((profile?.radar?.laningStrength ?: 5.0) / 10.0) * 0.25
                val metaStability =
                    when (metaStats?.tier) {
                        MetaTier.T0 -> 0.30
                        MetaTier.T1 -> 0.20
                        MetaTier.T2 -> 0.15
                        else -> 0.05
                    }
                blindPickScore = (blindMastery * 0.30 + flexBlindBonus + laningSafety + metaStability).coerceIn(0.15, 0.85)
            }

            // 6.5 Counter / denial score across all enemy picks
            var counterDenialScore = 0.0
            if (isCounterPick) {
                counterDenialScore = laneMatchupScore
            } else if (patchMeta != null && enemyPicks.isNotEmpty()) {
                var totalCounter = 0.0
                for (enemy in enemyPicks) {
                    val matchup = patchMeta.getMatchup(champ, enemy.championId)
                    if (matchup != null && matchup.counterScore > 50.0) {
                        totalCounter += (matchup.counterScore - 50.0) / 50.0
                    }
                }
                counterDenialScore = (totalCounter / enemyPicks.size).coerceIn(0.0, 1.0)
            }

            val proRecord = if (!isBan) findChampionRecord(targetCareerStats, champ) else null
            val proGamesPlayed = proRecord?.gamesPlayed ?: 0
            val isProUnplayed = !isBan && targetCareerStats != null && targetCareerStats.totalProGames >= 5 && proGamesPlayed == 0
            val isMetaMustPick = metaStats?.tier == MetaTier.T0 || (metaStats?.presenceRate ?: 0.0) >= 0.85

            // 7. Total composite intent score directly from balanced weights
            val totalIntentScore =
                if (isBan) {
                    val hasRespectBans = opponentBansAgainstTargetTeam != null && opponentBansAgainstTargetTeam.isNotEmpty()
                    val hasOppMastery = opponentPlayerProfilesByRole != null && opponentPlayerProfilesByRole.isNotEmpty()
                    val hasDuoDenial = matchedEnemyDuoStats != null
                    val hasProtectiveBan = matchedCounterMatchup != null
                    val baseScore = when {
                        hasDuoDenial ->
                            (enemyDuoDenialScore * 0.28) + (playerMasteryScore * 0.32) + (metaScore * 0.25) +
                                (compositionFitScore * 0.15)
                        hasProtectiveBan ->
                            (protectiveBanScore * 0.22) + (playerMasteryScore * 0.38) + (metaScore * 0.25) +
                                (compositionFitScore * 0.15)
                        hasRespectBans && hasOppMastery ->
                            (opponentRespectBanScore * 0.35) + (playerMasteryScore * 0.30) + (metaScore * 0.25) +
                                (compositionFitScore * 0.10)
                        hasRespectBans ->
                            (opponentRespectBanScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        hasOppMastery ->
                            (playerMasteryScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        else ->
                            (metaScore * 0.70) + (compositionFitScore * 0.30)
                    }
                    val resolvedBanRole =
                        when {
                            matchedOppRole != null -> matchedOppRole
                            matchedProtectiveLane != null -> matchedProtectiveLane
                            (draftState.bluePicks.size + draftState.redPicks.size) >= 6 && primaryRole in enemyLockedRoles -> {
                                val vacant = Role.entries.filterNot { it in enemyLockedRoles }
                                val proVacant =
                                    if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
                                        metaStats.roleDistribution.filter { it.key in vacant && it.value > 0 }.maxByOrNull { it.value }?.key
                                    } else null
                                val vacantFlex = proVacant
                                    ?: flexAnalysis.roleProbabilities.entries
                                        .filter { it.key in vacant && it.value >= 0.20 }
                                        .maxByOrNull { it.value }?.key
                                    ?: profile?.secondaryRoles?.firstOrNull { it in vacant }
                                vacantFlex ?: primaryRole
                            }
                            else -> primaryRole
                        }

                    if ((draftState.bluePicks.size + draftState.redPicks.size) >= 6) {
                        if (resolvedBanRole in enemyLockedRoles) {
                            baseScore * 0.20
                        } else if (primaryRole in enemyLockedRoles) {
                            val hasProFlex =
                                if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
                                    metaStats.roleDistribution.any { it.key !in enemyLockedRoles && it.value > 0 }
                                } else {
                                    flexAnalysis.roleProbabilities.any { it.key !in enemyLockedRoles && it.value >= 0.20 } ||
                                        (profile?.secondaryRoles?.any { it !in enemyLockedRoles } == true)
                                }
                            if (!hasProFlex) {
                                baseScore * 0.20
                            } else {
                                baseScore
                            }
                        } else {
                            baseScore
                        }
                    } else {
                        baseScore
                    }
                } else {
                    val rawScore =
                        when {
                            matchedDuoStats != null -> {
                                val baseDuo =
                                    if (hasDetailedProfiles || targetCareerStats != null) {
                                        (botDuoScore * 0.35) + (playerMasteryScore * 0.28) + (metaScore * 0.22) +
                                            (compositionFitScore * 0.15)
                                    } else {
                                        (botDuoScore * 0.40) + (metaScore * 0.32) + (compositionFitScore * 0.28)
                                    }
                                (baseDuo + duoCounterBonus).coerceAtMost(1.0)
                            }
                            isCounterPick -> {
                                if (hasDetailedProfiles || targetCareerStats != null) {
                                    (laneMatchupScore * 0.25) + (playerMasteryScore * 0.38) + (metaScore * 0.22) +
                                        (compositionFitScore * 0.15)
                                } else {
                                    (laneMatchupScore * 0.30) + (metaScore * 0.42) + (compositionFitScore * 0.28)
                                }
                            }
                            isBlindPick -> {
                                if (hasDetailedProfiles || targetCareerStats != null) {
                                    (blindPickScore * 0.18) + (playerMasteryScore * 0.42) + (metaScore * 0.25) +
                                        (compositionFitScore * 0.15)
                                } else {
                                    (blindPickScore * 0.20) + (metaScore * 0.45) + (compositionFitScore * 0.35)
                                }
                            }
                            hasDetailedProfiles || targetCareerStats != null ->
                                (playerMasteryScore * 0.45) + (metaScore * 0.30) +
                                    (compositionFitScore * 0.15) + (counterDenialScore * 0.10)
                            else ->
                                (metaScore * 0.45) + (compositionFitScore * 0.35) + (counterDenialScore * 0.20)
                        }
                    rawScore.coerceAtLeast(0.01)
                }

            // 8. Construct transparent rationale
            val reasons = mutableListOf<String>()

            if (isBan) {
                if (matchedCounterMatchup != null && matchedProtectedAlly != null && matchedProtectiveLane != null) {
                    val wrPct = String.format(Locale.US, "%.0f", matchedCounterMatchup.winRate * 100.0)
                    reasons.add(0, "Protective Ban: Neutralizes counter-pick vs $matchedProtectedAlly ($wrPct% WR)")
                }
                if (matchedEnemyDuoStats != null && matchedEnemyDuoPartner != null) {
                    val wrPct = String.format(Locale.US, "%.0f", matchedEnemyDuoStats.synergyWinRate * 100.0)
                    reasons.add(0, "Duo Denial: Disrupts enemy $matchedEnemyDuoPartner duo synergy ($wrPct% WR)")
                }
                if (oppBanRecord != null && oppBanRecord.banCount > 0) {
                    val oppPct = String.format(Locale.US, "%.0f", oppBanRecord.banRate * 100.0)
                    val teamLabel = targetTeamName ?: "opponent"
                    reasons.add(
                        "Respect ban: other teams ban vs $teamLabel in $oppPct% of games (${oppBanRecord.banCount}/${oppBanRecord.totalGames}G)",
                    )
                }
                if (matchedOpponentPlayer != null && matchedSignature != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer: $matchedSignature pick")
                } else if (matchedOpponentPlayer != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer")
                }
                if (metaStats?.tier == MetaTier.T0) {
                    reasons.add("Tier-0 (OP) meta ban priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
                } else if (metaStats?.tier == MetaTier.T1) {
                    reasons.add("Tier-1 priority meta ban")
                } else if (metaStats?.tier == MetaTier.T2) {
                    reasons.add("Tier-2 meta ban")
                }
                if (reasons.isEmpty()) {
                    reasons.add("High priority meta ban")
                }
            } else {
                val playerPrefix = if (targetPlayerName != null) "$targetPlayerName ($targetRole)" else null
                val masteryDesc =
                    when {
                        matchedSignature != null && proRecord != null -> {
                            val wrPct = String.format(Locale.US, "%.1f", proRecord.winRate * 100.0)
                            "$matchedSignature pick (${proRecord.gamesPlayed}G, $wrPct% WR)"
                        }
                        matchedSignature != null -> {
                            "$matchedSignature pick"
                        }
                        proRecord != null && proRecord.gamesPlayed > 0 -> {
                            val wrPct = String.format(Locale.US, "%.1f", proRecord.winRate * 100.0)
                            "Career pick (${proRecord.gamesPlayed}G, $wrPct% WR)"
                        }
                        isProUnplayed -> {
                            "0 pro games on record"
                        }
                        else -> null
                    }

                if (playerPrefix != null) {
                    if (masteryDesc != null) {
                        reasons.add("$playerPrefix: $masteryDesc")
                    } else {
                        reasons.add(playerPrefix)
                    }
                } else if (masteryDesc != null) {
                    reasons.add(masteryDesc)
                }

                if (metaStats?.tier == MetaTier.T0) {
                    reasons.add("Tier-0 (OP) meta priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
                } else if (metaStats?.tier == MetaTier.T1) {
                    reasons.add("Tier-1 priority meta pick")
                } else if (metaStats?.tier == MetaTier.T2) {
                    reasons.add("Tier-2 meta pick")
                }

                if (needsAp && (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65) reasons.add("Fills critical AP damage deficit")
                if (vacantRoles.isNotEmpty() && targetRole in vacantRoles) reasons.add("Fills vacant $targetRole lane")

                if (enemyLaneOpponent != null) {
                    if (matchedDirectMatchup != null) {
                        val wrPct = String.format(Locale.US, "%.0f", matchedDirectMatchup.winRate * 100.0)
                        val gdDiff = matchedDirectMatchup.avgGoldDiffAt15?.toInt()
                        val gdStr = if (gdDiff != null && gdDiff > 0) ", +${gdDiff} GD15" else if (gdDiff != null && gdDiff < 0) ", ${gdDiff} GD15" else ""
                        if (matchedDirectMatchup.winRate >= 0.52) {
                            reasons.add(0, "Counter Pick: Wins $wrPct% vs ${enemyLaneOpponent.championId}$gdStr")
                        } else if (matchedDirectMatchup.winRate <= 0.45) {
                            reasons.add("Unfavorable matchup vs ${enemyLaneOpponent.championId} ($wrPct% WR)")
                        }
                    } else {
                        reasons.add(0, "Counter Pick: Lane advantage vs ${enemyLaneOpponent.championId}")
                    }
                }

                if (isBlindPick) {
                    if (blindConfidenceRating == ConfidenceRating.S || blindConfidenceRating == ConfidenceRating.A) {
                        reasons.add(0, "Safe Blind Pick: Tier-$blindConfidenceRating stability")
                    } else if (metaStats?.tier == MetaTier.T0 || metaStats?.tier == MetaTier.T1) {
                        reasons.add(0, "Meta Blind Pick: High priority open pick")
                    }
                    if (flexAnalysis.isFlex) {
                        val rolesStr = flexAnalysis.roleProbabilities.filter { it.value >= 0.20 }.keys.joinToString("/")
                        reasons.add("Flex Pick: Conceals lane assignment ($rolesStr)")
                    }
                }

                if (counterDenialScore > 0.3 && !isCounterPick) reasons.add("Counters enemy composition")

                if (matchedDuoStats != null && matchedDuoPartner != null) {
                    val wrPct = String.format(Locale.US, "%.0f", matchedDuoStats.synergyWinRate * 100.0)
                    val gdStr =
                        if (matchedDuoStats.avgGoldDiffAt15 > 0) {
                            "+${matchedDuoStats.avgGoldDiffAt15.toInt()}"
                        } else {
                            "${matchedDuoStats.avgGoldDiffAt15.toInt()}"
                        }
                    reasons.add(0, "Bot Duo Synergy with $matchedDuoPartner ($wrPct% WR, $gdStr GD15)")
                }

                if (matchedDuoMatchup != null && enemyBot != null && enemySup != null) {
                    val wrPct = String.format(Locale.US, "%.0f", matchedDuoMatchup.blueWinRate * 100.0)
                    reasons.add(if (matchedDuoStats != null) 1 else 0, "2v2 Bot Duo Counter vs ${enemyBot.championId}+${enemySup.championId} ($wrPct% WR)")
                }

                val spentChamps = draftState.seriesContext?.spentChampions ?: emptySet()
                if (spentChamps.isNotEmpty() && targetCareerStats != null) {
                    val spentSignatures = targetCareerStats.signaturePicks.filter { it.championId in spentChamps }
                    if (spentSignatures.isNotEmpty() && isProUnplayed) {
                        reasons.add("Fearless fallback: ${spentSignatures.joinToString { it.championId }} spent in earlier games")
                    }
                }
            }

            val rationale =
                if (reasons.isNotEmpty()) {
                    reasons.joinToString("; ")
                } else if (isBan) {
                    "Strategic ban candidate"
                } else {
                    "Standard draft candidate"
                }

            scoredList.add(
                ChampionIntentCandidate(
                    championId = profile?.displayName ?: champ,
                    probability = 0.0, // calculated after sorting Top N
                    intentScore = roundToFourDecimals(totalIntentScore),
                    predictedRole = if (isBan) {
                        when {
                            matchedOppRole != null -> matchedOppRole
                            matchedProtectiveLane != null -> matchedProtectiveLane
                            (draftState.bluePicks.size + draftState.redPicks.size) >= 6 && primaryRole in enemyLockedRoles -> {
                                val vacant = Role.entries.filterNot { it in enemyLockedRoles }
                                val proVacant =
                                    if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
                                        metaStats.roleDistribution.filter { it.key in vacant && it.value > 0 }.maxByOrNull { it.value }?.key
                                    } else null
                                val vacantFlex = proVacant
                                    ?: flexAnalysis.roleProbabilities.entries
                                        .filter { it.key in vacant && it.value >= 0.20 }
                                        .maxByOrNull { it.value }?.key
                                    ?: profile?.secondaryRoles?.firstOrNull { it in vacant }
                                vacantFlex ?: primaryRole
                            }
                            else -> primaryRole
                        }
                    } else {
                        targetRole
                    },
                    metaScore = roundToFourDecimals(metaScore),
                    playerMasteryScore = roundToFourDecimals(playerMasteryScore),
                    compositionFitScore = roundToFourDecimals(compositionFitScore),
                    counterDenialScore = roundToFourDecimals(counterDenialScore),
                    playerName = if (isBan) matchedOpponentPlayer else targetPlayerName,
                    rationale = rationale,
                ),
            )
        }

        val dedupedCandidates =
            scoredList
                .distinctBy { ChampionNormalizer.toSlug(it.championId) }

        val totalPoolScore = dedupedCandidates.sumOf { it.intentScore }
        val candidatesWithGlobalProb =
            if (totalPoolScore > 0.0) {
                dedupedCandidates.map { candidate ->
                    candidate.copy(
                        probability = roundToFourDecimals(candidate.intentScore / totalPoolScore),
                    )
                }
            } else {
                val uniform = if (dedupedCandidates.isNotEmpty()) 1.0 / dedupedCandidates.size else 0.0
                dedupedCandidates.map { candidate ->
                    candidate.copy(probability = roundToFourDecimals(uniform))
                }
            }

        val sortedCandidates =
            candidatesWithGlobalProb
                .sortedByDescending { it.probability }

        // 多樣性選取：避免同一條路線出現兩個以上選項（特別是在 Pick 階段）
        val topCandidates =
            if (!isBan && vacantRoles.isNotEmpty()) {
                val selected = mutableListOf<ChampionIntentCandidate>()
                val roleCounts = mutableMapOf<Role, Int>()

                // 第 1 輪：各可用路線最多 1 個，確保各個空缺路線都能有代表選手推薦
                for (cand in sortedCandidates) {
                    if (selected.size >= topN) break
                    val role = cand.predictedRole
                    if (role != null) {
                        if ((roleCounts[role] ?: 0) == 0) {
                            selected.add(cand)
                            roleCounts[role] = 1
                        }
                    } else {
                        selected.add(cand)
                    }
                }

                // 第 2 輪：若還未滿 topN，同一路線最多允許第 2 個（絕不超過 2 個）
                if (selected.size < topN) {
                    for (cand in sortedCandidates) {
                        if (selected.size >= topN) break
                        if (selected.any { it.championId == cand.championId }) continue
                        val role = cand.predictedRole
                        if (role != null) {
                            if ((roleCounts[role] ?: 0) < 2) {
                                selected.add(cand)
                                roleCounts[role] = (roleCounts[role] ?: 0) + 1
                            }
                        } else {
                            selected.add(cand)
                        }
                    }
                }

                // 第 3 輪：若 vacantRoles 極少（如只剩 1 條路），前兩輪最多挑 1~2 個，此時才依分數補滿 topN
                if (selected.size < topN) {
                    for (cand in sortedCandidates) {
                        if (selected.size >= topN) break
                        if (selected.any { it.championId == cand.championId }) continue
                        selected.add(cand)
                    }
                }
                selected
            } else if (isBan) {
                // Ban 階段多樣性選取：嚴格限制同一條路線最多 3 個（絕不超過 3 個）
                val selected = mutableListOf<ChampionIntentCandidate>()
                val roleCounts = mutableMapOf<Role, Int>()

                fun getCandRole(cand: ChampionIntentCandidate): Role {
                    return cand.predictedRole
                        ?: tagRegistry.getProfile(cand.championId)?.primaryRole
                        ?: flexAnalyzer.analyzeChampion(cand.championId).primaryRole
                }

                for (cand in sortedCandidates) {
                    if (selected.size >= topN) break
                    val role = getCandRole(cand)
                    val count = roleCounts[role] ?: 0
                    if (count < 3) {
                        selected.add(cand)
                        roleCounts[role] = count + 1
                    }
                }

                if (selected.size < topN) {
                    for (cand in sortedCandidates) {
                        if (selected.size >= topN) break
                        if (selected.any { ChampionNormalizer.toSlug(it.championId) == ChampionNormalizer.toSlug(cand.championId) }) continue
                        val role = getCandRole(cand)
                        val count = roleCounts[role] ?: 0
                        if (count < 3) {
                            selected.add(cand)
                            roleCounts[role] = count + 1
                        }
                    }
                }
                selected
            } else {
                sortedCandidates.take(topN)
            }

        return IntentPredictionResult(
            turnSpec = turnSpec,
            actingSide = actingSide,
            actionType = turnSpec.actionType,
            predictions = topCandidates,
        )
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}
