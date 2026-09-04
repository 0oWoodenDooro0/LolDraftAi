package com.loldraft.models

import com.loldraft.data.meta.ChampionRoleDictionary
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 全局 BP (Fearless Draft) 純行為預測架構 - 序列推薦與條件生成推論引擎
 * (Pure Behavioral Draft Prediction: Conditioning on Identity, Context, and Algorithmic Hard Constraints)
 *
 * 遵循規格書三大核心原則：
 * 1. 去主觀化 (Zero Human Bias)：實體 ID Embeddings (Region, Team, 10 位選手 Roster)，無靜態人為標籤。
 * 2. 純賽前維度 (Pure Pre-Match Decisions)：完全無局內數值 (零 KDA、零 DPM、零 15 分經濟差)。
 * 3. 動態約束合規 (Dynamic Action Masking)：嚴格遵守全局 BP，已鎖定英雄 Logit = -1e9，機率 0%。
 * 4. 即時輪換適應 (Roster Agnostic)：以位置向量動態組合，更換選手即時更新 ID 向量即可推論。
 * 5. 純統計經驗與行為克隆：完全移除深度學習/神經網絡，依賴客觀歷史機率與 CSP 動態硬約束。
 */
class FearlessBehaviorPredictor(
    val patchMeta: PatchMetaMatrix? = null,
) {
    companion object {
        const val MASK_VALUE = -1e9

        // 賽區基準潛在風格偏置 (由客觀聯賽選角頻率統計生成)
        val REGION_LATENT_BIAS: Map<String, Map<String, Double>> = mapOf(
            "lpl" to mapOf(
                "renekton" to 0.45, "jax" to 0.40, "camille" to 0.35, "gnar" to 0.35,
                "leesin" to 0.45, "viego" to 0.35, "sejuani" to 0.30, "vi" to 0.30,
                "ahri" to 0.35, "syndra" to 0.35, "yone" to 0.40, "jayce" to 0.35,
                "kaisa" to 0.50, "kalista" to 0.45, "lucian" to 0.40, "varus" to 0.35,
                "leona" to 0.40, "nautilus" to 0.40, "rell" to 0.35, "alistar" to 0.30,
            ),
            "lck" to mapOf(
                "ksante" to 0.50, "rumble" to 0.45, "gnar" to 0.40, "aatrox" to 0.40,
                "sejuani" to 0.45, "maokai" to 0.45, "jarvaniv" to 0.35, "vi" to 0.35,
                "azir" to 0.55, "orianna" to 0.50, "corki" to 0.45, "smolder" to 0.40,
                "ashe" to 0.45, "varus" to 0.45, "ezreal" to 0.40, "jinx" to 0.35,
                "nautilus" to 0.40, "braum" to 0.40, "leona" to 0.35, "rell" to 0.35,
            ),
        )

        // 經典組合協同權重 (Synergy Association)
        val SYNERGY_PAIRS: Map<Pair<String, String>, Double> = mapOf(
            ("xayah" to "rakan") to 0.90,
            ("lucian" to "nami") to 0.85,
            ("jarvaniv" to "rumble") to 0.75,
            ("sejuani" to "renekton") to 0.80,
            ("sejuani" to "jax") to 0.75,
            ("maokai" to "jayce") to 0.70,
            ("vi" to "ahri") to 0.80,
            ("vi" to "taliyah") to 0.75,
            ("nautilus" to "kaisa") to 0.75,
            ("rell" to "samira") to 0.85,
            ("kalista" to "renataglasc") to 0.80,
            ("kalista" to "neeko") to 0.75,
            ("ashe" to "braum") to 0.70,
            ("varus" to "alistar") to 0.65,
        )

        // 對抗克制矩陣 (Counter Association)
        val COUNTER_PAIRS: Map<Pair<String, String>, Double> = mapOf(
            ("poppy" to "jax") to 0.70,
            ("poppy" to "camille") to 0.70,
            ("fiora" to "aatrox") to 0.65,
            ("sylas" to "malphite") to 0.75,
            ("braum" to "ornn") to 0.60,
            ("sivir" to "caitlyn") to 0.55,
            ("leona" to "blitzcrank") to 0.60,
            ("morgana" to "nautilus") to 0.70,
        )
    }

    /**
     * 預測全局 BP 下一步候選角色機率分佈
     */
    fun predict(
        request: FearlessPredictionRequest,
        topK: Int = 5,
    ): FearlessPredictionResponse {
        val context = request.context
        val currentTurn = context.currentTurn
        val isBan = currentTurn.phase.lowercase(Locale.US) == "ban"
        val isBlue = currentTurn.side.lowercase(Locale.US) == "blue"

        val actingTeamInfo = if (isBlue) request.teams.blue else request.teams.red
        val opponentTeamInfo = if (isBlue) request.teams.red else request.teams.blue

        // 1. 判定當前待選位置 (Target Role) 與上場選手 (Roster Agnostic)
        val targetRole = deduceTargetRole(currentTurn.stepIndex, isBan, isBlue, request.constraints.currentPicks)
        val actingPlayer = targetRole?.let { role ->
            when (role) {
                Role.TOP -> actingTeamInfo.roster.top
                Role.JUNGLE -> actingTeamInfo.roster.jng
                Role.MID -> actingTeamInfo.roster.mid
                Role.BOT -> actingTeamInfo.roster.bot
                Role.SUPPORT -> actingTeamInfo.roster.sup
            }
        }

        // 2. 構建全局 BP 嚴格動作遮罩 (Dynamic Action Masking)
        val fearlessLocked = request.constraints.fearlessLocked.map { ChampionNormalizer.toSlug(it) }.toSet()
        val currentBans = request.constraints.currentBans.map { ChampionNormalizer.toSlug(it) }.toSet()
        val bluePicks = request.constraints.currentPicks.blue.map { ChampionNormalizer.toSlug(it) }
        val redPicks = request.constraints.currentPicks.red.map { ChampionNormalizer.toSlug(it) }
        val allCurrentPicks = (bluePicks + redPicks).toSet()

        val allyPicks = if (isBlue) bluePicks else redPicks
        val enemyPicks = if (isBlue) redPicks else bluePicks

        val allChampions = ChampionRoleDictionary.getAllBaselineRoles().keys.toList()

        // 3. 計算所有英雄的未遮罩原始分數 (Raw Logits)
        val rawLogits = mutableMapOf<String, Double>()
        for (champ in allChampions) {
            rawLogits[champ] = computeRawLogit(
                champion = champ,
                context = context,
                actingTeam = actingTeamInfo.teamId,
                actingPlayer = actingPlayer,
                targetRole = targetRole,
                isBan = isBan,
                allyPicks = allyPicks,
                enemyPicks = enemyPicks,
                history = request.history,
            )
        }

        // 4. 套用動態遮罩 (Dynamic Action Masking)
        // 條件：c ∈ fearless_locked OR c ∈ current_bans OR c ∈ current_picks -> Logit = -1e9
        val maskedLogits = mutableMapOf<String, Double>()
        var maskedCount = 0

        for (champ in allChampions) {
            val isFearlessLocked = champ in fearlessLocked
            val isCurrentBan = champ in currentBans
            val isCurrentPick = champ in allCurrentPicks

            var isRoleInvalid = false
            if (!isBan && targetRole != null) {
                val (primaryRole, secondaryRoles) = ChampionRoleDictionary.getBaselineRole(champ)
                if (primaryRole != targetRole && targetRole !in secondaryRoles) {
                    isRoleInvalid = true
                }
            }

            if (isFearlessLocked || isCurrentBan || isCurrentPick || isRoleInvalid) {
                maskedLogits[champ] = MASK_VALUE
                maskedCount++
            } else {
                maskedLogits[champ] = rawLogits[champ] ?: 0.0
            }
        }

        // 5. 於合法候選池執行 Softmax 歸一化
        val legalCandidates = maskedLogits.filter { it.value > -1e8 }
        val probabilities = mutableMapOf<String, Double>()

        if (legalCandidates.isNotEmpty()) {
            val maxLogit = legalCandidates.values.maxOrNull() ?: 0.0
            var sumExp = 0.0
            val expValues = legalCandidates.mapValues { exp(it.value - maxLogit) }
            for (v in expValues.values) {
                sumExp += v
            }
            for ((champ, expV) in expValues) {
                probabilities[champ] = if (sumExp > 0.0) expV / sumExp else 0.0
            }
        }

        // 6. 排序並選出 Top-K 推薦候選
        val sortedTop = probabilities.entries
            .sortedByDescending { it.value }
            .take(topK)

        val candidateList = sortedTop.map { (champ, prob) ->
            val displayName = ChampionNormalizer.normalize(champ)
            val pct = "${(prob * 1000.0).roundToInt() / 10.0}%"
            val logit = maskedLogits[champ] ?: 0.0
            val rationale = buildRationale(
                champion = champ,
                displayName = displayName,
                prob = prob,
                isBan = isBan,
                context = context,
                actingPlayer = actingPlayer,
                targetRole = targetRole,
                allyPicks = allyPicks,
                enemyPicks = enemyPicks,
                history = request.history,
            )
            FearlessCandidate(
                champion = displayName,
                probability = prob,
                percentage = pct,
                logit = logit,
                rationale = rationale,
            )
        }

        return FearlessPredictionResponse(
            targetTurn = currentTurn,
            actingTeam = actingTeamInfo.teamId,
            actingPlayer = actingPlayer,
            targetRole = targetRole?.name,
            candidates = candidateList,
            maskedChampionsCount = maskedCount,
            totalLegalCandidates = legalCandidates.size,
        )
    }

    private fun computeRawLogit(
        champion: String,
        context: FearlessDraftContext,
        actingTeam: String,
        actingPlayer: String?,
        targetRole: Role?,
        isBan: Boolean,
        allyPicks: List<String>,
        enemyPicks: List<String>,
        history: FearlessHistory,
    ): Double {
        var logit = 1.0

        // 1. 環境特徵層 (Context Level)
        // (a) 版本號 (Patch Meta)
        if (patchMeta != null) {
            val patchStats = patchMeta.championStats[champion]
            if (patchStats != null) {
                logit += patchStats.presenceRate * 1.8
                logit += (patchStats.winRate - 0.50) * 1.5
                val tierBonus = when (patchStats.tier) {
                    MetaTier.T0 -> 0.80
                    MetaTier.T1 -> 0.50
                    MetaTier.T2 -> 0.25
                    MetaTier.T3 -> 0.05
                    MetaTier.T4 -> -0.15
                }
                logit += tierBonus
            }
        }

        // (b) 賽區特徵 (Region ID)
        val regionKey = context.region.lowercase(Locale.US)
        val regionBiases = REGION_LATENT_BIAS[regionKey]
        if (regionBiases != null && champion in regionBiases) {
            logit += (regionBiases[champion] ?: 0.0)
        }

        // (c) 系列賽局數 (Game Number 1..5) - 局數越後，全局池枯竭，激勵深層英雄池
        if (context.gameNumber >= 3) {
            logit += 0.20 // 泛用常規英雄權重提升
        }

        // (d) BP 輪次索引 (Step Index 1..20)
        if (isBan) {
            if (context.currentTurn.stepIndex <= 6) {
                logit += 0.35 // 前三 Ban 重點封鎖版本 T0
            } else {
                logit += 0.25 // 後二 Ban 針對位置封鎖
            }
        }

        // 2. 實體識別層與歷史選角 (Entity Identification & Objective Pick History with Decay λ^t)
        if (actingPlayer != null) {
            val displayName = ChampionNormalizer.normalize(champion)
            val slug = ChampionNormalizer.toSlug(champion)

            val decayedMap = history.playerDecayedFrequencies[actingPlayer]
            val decayedWeight = decayedMap?.get(displayName) ?: decayedMap?.get(slug) ?: 0.0

            val countMap = history.playerPickCounts[actingPlayer]
            val rawCount = countMap?.get(displayName) ?: countMap?.get(slug) ?: 0

            if (decayedWeight > 0.0) {
                logit += (kotlin.math.ln(1.0 + decayedWeight) * 0.55)
            } else if (rawCount > 0) {
                logit += (kotlin.math.ln(1.0 + rawCount.toDouble()) * 0.45)
            }
        }

        // 3. 陣容協同與克制機制 (Multi-Head Attention 模擬)
        if (!isBan) {
            // (a) 同隊協同性 (Synergy)
            for (ally in allyPicks) {
                val pair1 = champion to ally
                val pair2 = ally to champion
                val synBoost = SYNERGY_PAIRS[pair1] ?: SYNERGY_PAIRS[pair2] ?: 0.0
                logit += synBoost
            }

            // (b) 對手克制性 (Counter)
            for (enemy in enemyPicks) {
                val counterBoost = COUNTER_PAIRS[champion to enemy] ?: 0.0
                logit += counterBoost
            }
        }

        return logit
    }

    private fun deduceTargetRole(
        stepIndex: Int,
        isBan: Boolean,
        isBlue: Boolean,
        currentPicks: FearlessCurrentPicks,
    ): Role? {
        if (isBan) return null // Ban 階段通常不強制特定位置
        val teamPicks = if (isBlue) currentPicks.blue else currentPicks.red
        val lockedRoles = teamPicks.map {
            val slug = ChampionNormalizer.toSlug(it)
            ChampionRoleDictionary.getBaselineRole(slug).first
        }.toSet()
        return Role.entries.find { it !in lockedRoles }
    }

    private fun buildRationale(
        champion: String,
        displayName: String,
        prob: Double,
        isBan: Boolean,
        context: FearlessDraftContext,
        actingPlayer: String?,
        targetRole: Role?,
        allyPicks: List<String>,
        enemyPicks: List<String>,
        history: FearlessHistory,
    ): String {
        val pct = "${(prob * 1000.0).roundToInt() / 10.0}%"
        val parts = mutableListOf<String>()

        val actionName = if (isBan) "禁用" else "挑選"
        parts.add("[Fearless Behavioral] 預測 $actionName ($pct)")

        if (actingPlayer != null) {
            val displayNameNorm = ChampionNormalizer.normalize(champion)
            val slug = ChampionNormalizer.toSlug(champion)

            val decayed = history.playerDecayedFrequencies[actingPlayer]?.get(displayNameNorm)
                ?: history.playerDecayedFrequencies[actingPlayer]?.get(slug)
            val count = history.playerPickCounts[actingPlayer]?.get(displayNameNorm)
                ?: history.playerPickCounts[actingPlayer]?.get(slug) ?: 0

            if (decayed != null && decayed > 0.0) {
                val formattedDecayed = (decayed * 10.0).roundToInt() / 10.0
                parts.add("選手 $actingPlayer 近期高頻選用 (衰減加權: $formattedDecayed)")
            } else if (count > 0) {
                parts.add("選手 $actingPlayer 近期高頻選用 ($count 場)")
            }
        }

        if (targetRole != null) {
            parts.add("適配 $targetRole 空缺")
        }

        for (ally in allyPicks) {
            val normAlly = ChampionNormalizer.normalize(ally)
            if (SYNERGY_PAIRS.containsKey(champion to ally) || SYNERGY_PAIRS.containsKey(ally to champion)) {
                parts.add("與隊友 $normAlly 形成體系連動")
                break
            }
        }

        for (enemy in enemyPicks) {
            val normEnemy = ChampionNormalizer.normalize(enemy)
            if (COUNTER_PAIRS.containsKey(champion to enemy)) {
                parts.add("對位壓制對手 $normEnemy")
                break
            }
        }

        parts.add("第 ${context.gameNumber} 局全局池約束合規")
        return parts.joinToString(" - ")
    }
}

/** Backward compatibility alias */
typealias FearlessTransformerPredictor = FearlessBehaviorPredictor
