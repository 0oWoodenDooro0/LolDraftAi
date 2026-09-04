package com.loldraft.data.meta

import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer

object ChampionDatabaseBuilder {

    fun buildAll(): List<ChampionProfile> {
        val list = mutableListOf<ChampionProfile>()

        fun add(
            name: String,
            role: Role,
            sec: Set<Role> = emptySet(),
            laning: Double,
            engage: Double,
            disengage: Double,
            waveclear: Double,
            late: Double,
            phys: Double,
            magic: Double,
            trueDmg: Double = 0.0,
            durability: Double,
            tankTier: TankinessTier,
            ccSec: Double,
            hasReliableCc: Boolean,
            ccTier: CcTier,
            spike: PowerSpikeCurve = PowerSpikeCurve.BALANCED,
            tags: Set<ChampionTag>
        ) {
            val primaryDmg = when {
                phys >= 0.65 -> DamageType.PHYSICAL
                magic >= 0.65 -> DamageType.MAGIC
                trueDmg >= 0.40 -> DamageType.TRUE_DAMAGE
                else -> DamageType.MIXED
            }
            list.add(
                ChampionProfile(
                    championId = name,
                    displayName = name,
                    primaryRole = role,
                    secondaryRoles = sec,
                    damageProfile = DamageProfile(phys, magic, trueDmg, primaryDmg),
                    ccRating = CrowdControlRating(ccSec, hasReliableCc, ccTier),
                    durability = DurabilityProfile(durability, tankTier),
                    radar = FiveDimensionRadar(laning, engage, disengage, waveclear, late),
                    powerSpike = spike,
                    tags = tags
                )
            )
        }

        // ================= TOP LANERS =================
        add("Aatrox", Role.TOP, emptySet(), 8.0, 7.0, 5.0, 7.5, 7.0, 0.95, 0.05, 0.0, 7.5, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY))
        add("Akshan", Role.MID, setOf(Role.TOP), 8.0, 5.5, 6.0, 7.5, 7.5, 0.90, 0.10, 0.0, 4.0, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.ASSASSIN, ChampionTag.EARLY_BULLY))
        add("Ambessa", Role.TOP, setOf(Role.JUNGLE), 8.5, 7.5, 5.0, 6.0, 7.5, 0.95, 0.05, 0.0, 7.2, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.SKIRMISHER))
        add("Camille", Role.TOP, setOf(Role.SUPPORT), 7.8, 8.8, 5.0, 6.0, 9.0, 0.55, 0.05, 0.40, 7.2, TankinessTier.BRUISER, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.PICK_POTENTIAL, ChampionTag.SPLIT_PUSHER))
        add("Cho'Gath", Role.TOP, setOf(Role.MID), 6.5, 6.5, 6.0, 7.0, 8.0, 0.15, 0.75, 0.10, 9.0, TankinessTier.FRONTLINE_TANK, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.WAVECLEAR_STALL))
        add("Darius", Role.TOP, emptySet(), 9.2, 5.5, 4.0, 7.5, 6.0, 0.85, 0.0, 0.15, 7.8, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY))
        add("Dr. Mundo", Role.TOP, setOf(Role.JUNGLE), 6.5, 5.0, 4.5, 7.0, 8.5, 0.70, 0.30, 0.0, 9.5, TankinessTier.FRONTLINE_TANK, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.POKE))
        add("Fiora", Role.TOP, emptySet(), 8.2, 5.0, 6.5, 5.0, 9.5, 0.70, 0.0, 0.30, 6.8, TankinessTier.BRUISER, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.SPLIT_PUSHER, ChampionTag.HYPER_CARRY))
        add("Gangplank", Role.TOP, setOf(Role.MID), 7.5, 6.0, 6.0, 8.5, 9.2, 0.85, 0.15, 0.0, 5.0, TankinessTier.SQUISHY, 1.0, false, CcTier.LIGHT, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.SKIRMISHER, ChampionTag.GLOBAL_PRESENCE, ChampionTag.HYPER_CARRY))
        add("Garen", Role.TOP, emptySet(), 8.0, 5.5, 4.5, 8.0, 6.5, 0.85, 0.0, 0.15, 8.0, TankinessTier.BRUISER, 1.2, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY))
        add("Gnar", Role.TOP, emptySet(), 7.5, 8.5, 6.5, 6.5, 7.0, 0.85, 0.15, 0.0, 6.8, TankinessTier.BRUISER, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.HARD_ENGAGE, ChampionTag.EARLY_BULLY))
        add("Gwen", Role.TOP, setOf(Role.JUNGLE), 7.0, 5.5, 6.5, 7.5, 9.5, 0.10, 0.80, 0.10, 6.8, TankinessTier.BRUISER, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.HYPER_CARRY))
        add("Illaoi", Role.TOP, emptySet(), 8.5, 4.5, 3.5, 8.0, 7.0, 0.95, 0.05, 0.0, 7.8, TankinessTier.BRUISER, 1.0, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.SPLIT_PUSHER))
        add("Irelia", Role.TOP, setOf(Role.MID), 8.2, 7.5, 5.5, 7.5, 8.0, 0.80, 0.20, 0.0, 6.8, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.SKIRMISHER))
        add("Jax", Role.TOP, setOf(Role.JUNGLE), 7.0, 6.5, 5.0, 5.5, 9.0, 0.70, 0.30, 0.0, 7.2, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.SPLIT_PUSHER, ChampionTag.HYPER_CARRY))
        add("Jayce", Role.TOP, setOf(Role.MID), 8.0, 5.5, 5.5, 8.5, 7.5, 0.90, 0.10, 0.0, 5.0, TankinessTier.SQUISHY, 0.8, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("K'Sante", Role.TOP, setOf(Role.MID), 7.5, 7.8, 7.2, 6.8, 8.2, 0.75, 0.15, 0.10, 9.0, TankinessTier.FRONTLINE_TANK, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.SKIRMISHER))
        add("Kayle", Role.TOP, setOf(Role.MID), 4.5, 4.0, 6.0, 7.5, 9.8, 0.40, 0.55, 0.05, 4.0, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY))
        add("Kennen", Role.TOP, emptySet(), 7.8, 9.0, 6.0, 7.0, 8.0, 0.10, 0.90, 0.0, 4.2, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Kled", Role.TOP, setOf(Role.MID), 8.5, 8.0, 4.0, 6.5, 6.5, 0.95, 0.05, 0.0, 7.5, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY))
        add("Malphite", Role.TOP, setOf(Role.MID, Role.SUPPORT), 5.5, 9.5, 3.5, 6.0, 7.5, 0.20, 0.80, 0.0, 9.2, TankinessTier.FRONTLINE_TANK, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Mordekaiser", Role.TOP, setOf(Role.JUNGLE), 8.0, 6.5, 4.5, 7.5, 7.8, 0.05, 0.95, 0.0, 8.0, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.PICK_POTENTIAL))
        add("Nasus", Role.TOP, setOf(Role.MID), 5.0, 4.0, 4.5, 6.0, 9.0, 0.85, 0.15, 0.0, 8.5, TankinessTier.BRUISER, 1.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.JUGGERNAUT, ChampionTag.SPLIT_PUSHER))
        add("Olaf", Role.TOP, setOf(Role.JUNGLE), 8.8, 6.5, 4.0, 6.8, 6.5, 0.75, 0.0, 0.25, 7.5, TankinessTier.BRUISER, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY))
        add("Ornn", Role.TOP, emptySet(), 7.0, 9.0, 6.0, 6.5, 8.8, 0.30, 0.70, 0.0, 9.5, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Pantheon", Role.SUPPORT, setOf(Role.MID, Role.TOP), 8.5, 7.8, 6.0, 6.0, 6.0, 0.90, 0.10, 0.0, 6.5, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY, ChampionTag.GLOBAL_PRESENCE))
        add("Poppy", Role.SUPPORT, setOf(Role.JUNGLE, Role.TOP), 6.8, 6.5, 8.8, 5.5, 6.5, 0.80, 0.20, 0.0, 8.8, TankinessTier.FRONTLINE_TANK, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.DISENGAGE_PEEL))
        add("Quinn", Role.TOP, emptySet(), 8.5, 5.0, 6.5, 6.0, 6.8, 0.95, 0.05, 0.0, 3.8, TankinessTier.SQUISHY, 1.2, true, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.ASSASSIN, ChampionTag.SPLIT_PUSHER))
        add("Renekton", Role.TOP, setOf(Role.MID), 8.8, 6.8, 4.5, 7.5, 5.5, 0.90, 0.10, 0.0, 7.8, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY))
        add("Riven", Role.TOP, emptySet(), 8.5, 6.5, 6.5, 7.5, 7.8, 0.95, 0.05, 0.0, 6.5, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.SKIRMISHER, ChampionTag.EARLY_BULLY))
        add("Rumble", Role.TOP, setOf(Role.MID, Role.JUNGLE), 8.8, 8.0, 5.0, 8.0, 7.2, 0.10, 0.90, 0.0, 6.8, TankinessTier.BRUISER, 1.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.EARLY_BULLY))
        add("Sett", Role.TOP, setOf(Role.SUPPORT, Role.MID), 8.5, 7.5, 5.0, 7.0, 6.8, 0.80, 0.0, 0.20, 8.0, TankinessTier.BRUISER, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY))
        add("Shen", Role.TOP, setOf(Role.SUPPORT), 7.2, 7.5, 8.0, 4.5, 7.0, 0.40, 0.60, 0.0, 8.8, TankinessTier.FRONTLINE_TANK, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.GLOBAL_PRESENCE, ChampionTag.DISENGAGE_PEEL))
        add("Singed", Role.TOP, emptySet(), 6.5, 6.5, 7.5, 8.5, 7.5, 0.05, 0.95, 0.0, 8.5, TankinessTier.FRONTLINE_TANK, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.DISENGAGE_PEEL))
        add("Sion", Role.TOP, emptySet(), 6.5, 7.5, 5.0, 7.8, 7.5, 0.80, 0.20, 0.0, 9.2, TankinessTier.FRONTLINE_TANK, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Tahm Kench", Role.SUPPORT, setOf(Role.TOP), 7.5, 6.5, 8.5, 5.5, 7.5, 0.10, 0.90, 0.0, 9.5, TankinessTier.FRONTLINE_TANK, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.DISENGAGE_PEEL))
        add("Teemo", Role.TOP, setOf(Role.SUPPORT), 8.5, 4.0, 5.5, 6.5, 7.5, 0.10, 0.90, 0.0, 3.8, TankinessTier.SQUISHY, 1.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("Trundle", Role.TOP, setOf(Role.JUNGLE), 8.0, 5.0, 5.0, 6.0, 7.5, 0.85, 0.15, 0.0, 8.0, TankinessTier.BRUISER, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.SPLIT_PUSHER))
        add("Tryndamere", Role.TOP, setOf(Role.MID), 8.0, 5.0, 5.0, 7.5, 8.5, 0.95, 0.05, 0.0, 6.5, TankinessTier.BRUISER, 1.0, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.SPLIT_PUSHER, ChampionTag.HYPER_CARRY))
        add("Urgot", Role.TOP, emptySet(), 8.0, 6.5, 4.5, 7.0, 7.5, 0.85, 0.0, 0.15, 7.8, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT))
        add("Volibear", Role.TOP, setOf(Role.JUNGLE), 8.2, 7.5, 4.5, 7.5, 6.8, 0.60, 0.40, 0.0, 8.2, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.DIVER))
        add("Yorick", Role.TOP, emptySet(), 7.5, 4.5, 4.5, 7.5, 7.5, 0.90, 0.10, 0.0, 7.8, TankinessTier.BRUISER, 1.2, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT, ChampionTag.SPLIT_PUSHER))

        // ================= JUNGLE =================
        add("Amumu", Role.JUNGLE, setOf(Role.SUPPORT), 5.5, 9.5, 4.0, 6.5, 7.5, 0.10, 0.90, 0.0, 9.0, TankinessTier.FRONTLINE_TANK, 3.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Bel'Veth", Role.JUNGLE, emptySet(), 6.8, 7.0, 5.0, 7.0, 9.2, 0.75, 0.0, 0.25, 6.2, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.HYPER_CARRY))
        add("Briar", Role.JUNGLE, setOf(Role.TOP), 7.0, 8.0, 3.5, 6.5, 7.5, 0.90, 0.10, 0.0, 6.8, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER))
        add("Diana", Role.JUNGLE, setOf(Role.MID), 6.5, 8.5, 4.0, 7.8, 7.5, 0.10, 0.90, 0.0, 6.0, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE))
        add("Ekko", Role.JUNGLE, setOf(Role.MID), 6.8, 7.0, 6.5, 7.0, 8.2, 0.10, 0.90, 0.0, 5.0, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.DIVER))
        add("Elise", Role.JUNGLE, emptySet(), 8.2, 8.0, 5.5, 5.5, 5.0, 0.10, 0.90, 0.0, 4.5, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.PICK_POTENTIAL, ChampionTag.EARLY_BULLY))
        add("Evelynn", Role.JUNGLE, emptySet(), 6.0, 7.5, 5.5, 6.5, 8.5, 0.05, 0.95, 0.0, 3.8, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Fiddlesticks", Role.JUNGLE, setOf(Role.SUPPORT), 6.0, 9.5, 6.0, 8.0, 8.2, 0.05, 0.95, 0.0, 4.5, TankinessTier.SQUISHY, 3.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Gragas", Role.TOP, setOf(Role.JUNGLE, Role.MID), 7.5, 8.5, 7.5, 7.5, 7.8, 0.10, 0.90, 0.0, 7.5, TankinessTier.BRUISER, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.DISENGAGE_PEEL, ChampionTag.HARD_ENGAGE))
        add("Graves", Role.JUNGLE, setOf(Role.TOP), 8.0, 6.0, 6.0, 8.0, 8.5, 0.90, 0.10, 0.0, 5.5, TankinessTier.BRUISER, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY))
        add("Hecarim", Role.JUNGLE, emptySet(), 7.0, 8.8, 4.5, 8.0, 7.5, 0.85, 0.15, 0.0, 7.5, TankinessTier.BRUISER, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE))
        add("Ivern", Role.JUNGLE, emptySet(), 6.5, 6.0, 8.5, 5.0, 7.5, 0.05, 0.95, 0.0, 4.5, TankinessTier.SQUISHY, 2.2, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Jarvan IV", Role.JUNGLE, emptySet(), 7.2, 9.2, 4.0, 6.2, 6.2, 0.85, 0.15, 0.0, 7.5, TankinessTier.BRUISER, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE))
        add("Karthus", Role.JUNGLE, setOf(Role.BOT), 6.5, 5.0, 4.0, 9.0, 9.5, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY, ChampionTag.GLOBAL_PRESENCE))
        add("Kayn", Role.JUNGLE, emptySet(), 6.5, 7.5, 6.0, 8.0, 8.0, 0.85, 0.15, 0.0, 6.5, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.DIVER))
        add("Kha'Zix", Role.JUNGLE, emptySet(), 7.5, 6.5, 6.0, 6.0, 7.5, 0.95, 0.05, 0.0, 4.0, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Kindred", Role.JUNGLE, emptySet(), 7.5, 5.5, 6.5, 6.0, 9.0, 0.90, 0.10, 0.0, 4.0, TankinessTier.SQUISHY, 0.8, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY))
        add("Lee Sin", Role.JUNGLE, emptySet(), 7.8, 8.0, 7.0, 5.5, 5.2, 0.90, 0.10, 0.0, 6.2, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.PICK_POTENTIAL, ChampionTag.EARLY_BULLY))
        add("Lillia", Role.JUNGLE, setOf(Role.TOP), 6.8, 7.5, 6.5, 8.0, 8.5, 0.05, 0.85, 0.10, 5.5, TankinessTier.BRUISER, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.SKIRMISHER, ChampionTag.HARD_ENGAGE))
        add("Master Yi", Role.JUNGLE, emptySet(), 6.0, 5.0, 4.5, 7.0, 9.5, 0.70, 0.0, 0.30, 5.0, TankinessTier.BRUISER, 0.0, false, CcTier.NONE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.HYPER_CARRY))
        add("Maokai", Role.JUNGLE, setOf(Role.SUPPORT, Role.TOP), 6.0, 9.0, 7.5, 6.5, 7.5, 0.15, 0.85, 0.0, 8.8, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE, ChampionTag.DISENGAGE_PEEL))
        add("Nidalee", Role.JUNGLE, emptySet(), 8.5, 6.0, 6.5, 7.5, 5.5, 0.10, 0.90, 0.0, 4.0, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("Nocturne", Role.JUNGLE, emptySet(), 7.5, 8.5, 4.5, 7.5, 6.5, 0.90, 0.10, 0.0, 6.5, TankinessTier.BRUISER, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.GLOBAL_PRESENCE))
        add("Nunu & Willump", Role.JUNGLE, emptySet(), 5.5, 8.0, 5.5, 7.5, 5.5, 0.20, 0.80, 0.0, 8.0, TankinessTier.FRONTLINE_TANK, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.VANGUARD_TANK))
        add("Rammus", Role.JUNGLE, emptySet(), 6.0, 8.5, 4.0, 5.5, 7.0, 0.10, 0.80, 0.10, 9.5, TankinessTier.FRONTLINE_TANK, 3.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Rek'Sai", Role.JUNGLE, setOf(Role.TOP), 8.0, 8.0, 5.0, 6.5, 5.5, 0.95, 0.05, 0.0, 7.0, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY))
        add("Rengar", Role.JUNGLE, setOf(Role.TOP), 7.8, 7.0, 5.0, 6.5, 7.2, 0.95, 0.05, 0.0, 4.5, TankinessTier.SQUISHY, 1.2, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Sejuani", Role.JUNGLE, setOf(Role.TOP), 6.0, 8.8, 6.8, 6.0, 7.2, 0.20, 0.80, 0.0, 9.0, TankinessTier.FRONTLINE_TANK, 3.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Shaco", Role.JUNGLE, setOf(Role.SUPPORT), 7.5, 6.0, 7.0, 5.5, 6.0, 0.70, 0.30, 0.0, 4.0, TankinessTier.SQUISHY, 1.5, true, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Shyvana", Role.JUNGLE, setOf(Role.TOP), 6.0, 6.5, 4.0, 8.0, 8.0, 0.40, 0.60, 0.0, 7.8, TankinessTier.BRUISER, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT))
        add("Skarner", Role.JUNGLE, setOf(Role.TOP), 6.8, 8.5, 5.5, 6.8, 7.5, 0.40, 0.60, 0.0, 9.2, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Taliyah", Role.JUNGLE, setOf(Role.MID), 7.5, 7.5, 6.8, 8.5, 8.5, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.PICK_POTENTIAL))
        add("Udyr", Role.JUNGLE, setOf(Role.TOP), 7.8, 6.5, 5.5, 8.0, 7.5, 0.50, 0.50, 0.0, 8.2, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.JUGGERNAUT))
        add("Vi", Role.JUNGLE, emptySet(), 6.8, 9.2, 3.8, 6.0, 6.5, 0.90, 0.10, 0.0, 7.0, TankinessTier.BRUISER, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL))
        add("Viego", Role.JUNGLE, emptySet(), 7.0, 7.5, 5.0, 7.0, 8.8, 0.85, 0.15, 0.0, 6.5, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.SKIRMISHER, ChampionTag.HYPER_CARRY))
        add("Warwick", Role.JUNGLE, setOf(Role.TOP), 8.0, 7.5, 4.5, 6.0, 6.5, 0.50, 0.50, 0.0, 7.5, TankinessTier.BRUISER, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY))
        add("Wukong", Role.JUNGLE, setOf(Role.TOP), 6.5, 8.8, 5.0, 5.8, 7.0, 0.90, 0.10, 0.0, 7.2, TankinessTier.BRUISER, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE))
        add("Xin Zhao", Role.JUNGLE, emptySet(), 8.2, 8.0, 5.0, 6.0, 6.5, 0.85, 0.15, 0.0, 7.2, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY))
        add("Zac", Role.JUNGLE, setOf(Role.TOP, Role.SUPPORT), 6.0, 9.5, 6.0, 6.5, 8.0, 0.10, 0.90, 0.0, 9.2, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))

        // ================= MID LANERS =================
        add("Ahri", Role.MID, emptySet(), 7.5, 7.8, 7.0, 8.0, 7.0, 0.05, 0.80, 0.15, 4.0, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.PICK_POTENTIAL))
        add("Akali", Role.MID, setOf(Role.TOP), 7.5, 7.0, 7.0, 6.0, 8.2, 0.20, 0.80, 0.0, 4.2, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN))
        add("Anivia", Role.MID, emptySet(), 7.0, 7.5, 7.8, 9.5, 8.8, 0.05, 0.95, 0.0, 4.5, TankinessTier.SQUISHY, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.WAVECLEAR_STALL))
        add("Annie", Role.MID, setOf(Role.SUPPORT), 7.2, 8.5, 4.5, 7.0, 7.8, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Aurelion Sol", Role.MID, emptySet(), 6.0, 7.5, 6.0, 9.0, 9.8, 0.05, 0.95, 0.0, 4.2, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY, ChampionTag.WAVECLEAR_STALL))
        add("Aurora", Role.MID, setOf(Role.TOP), 7.8, 8.0, 7.0, 7.5, 8.2, 0.05, 0.95, 0.0, 4.5, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.SKIRMISHER))
        add("Azir", Role.MID, emptySet(), 7.2, 8.0, 7.5, 8.5, 9.2, 0.05, 0.95, 0.0, 4.2, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY, ChampionTag.WAVECLEAR_STALL))
        add("Cassiopeia", Role.MID, setOf(Role.TOP), 7.8, 7.5, 7.0, 8.0, 9.2, 0.05, 0.95, 0.0, 4.5, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY))
        add("Fizz", Role.MID, emptySet(), 7.2, 8.0, 7.5, 6.0, 7.5, 0.10, 0.90, 0.0, 4.2, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Galio", Role.MID, setOf(Role.SUPPORT), 7.0, 8.5, 6.5, 8.0, 7.0, 0.10, 0.90, 0.0, 8.0, TankinessTier.FRONTLINE_TANK, 2.8, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.GLOBAL_PRESENCE, ChampionTag.HARD_ENGAGE))
        add("Hwei", Role.MID, setOf(Role.SUPPORT), 7.8, 7.5, 7.5, 9.0, 8.8, 0.05, 0.95, 0.0, 3.8, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.ARTILLERY_MAGE, ChampionTag.WAVECLEAR_STALL))
        add("Kassadin", Role.MID, emptySet(), 4.5, 7.5, 6.5, 6.0, 9.8, 0.05, 0.95, 0.0, 5.5, TankinessTier.SQUISHY, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.ASSASSIN, ChampionTag.HYPER_CARRY))
        add("Katarina", Role.MID, emptySet(), 6.5, 6.0, 6.0, 6.5, 8.5, 0.30, 0.70, 0.0, 3.8, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.HYPER_CARRY))
        add("LeBlanc", Role.MID, emptySet(), 8.2, 7.2, 6.5, 6.0, 6.8, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Lissandra", Role.MID, emptySet(), 7.5, 8.8, 6.0, 8.0, 7.5, 0.05, 0.95, 0.0, 5.0, TankinessTier.SQUISHY, 3.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Malzahar", Role.MID, emptySet(), 7.2, 7.0, 5.5, 8.5, 8.0, 0.05, 0.95, 0.0, 4.2, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.PICK_POTENTIAL, ChampionTag.WAVECLEAR_STALL))
        add("Mel", Role.MID, setOf(Role.SUPPORT), 7.5, 7.0, 7.0, 8.0, 8.0, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE))
        add("Naafiri", Role.MID, setOf(Role.JUNGLE), 7.8, 7.5, 5.0, 7.0, 7.8, 0.95, 0.05, 0.0, 4.5, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN))
        add("Neeko", Role.MID, setOf(Role.SUPPORT), 8.0, 8.8, 5.5, 8.0, 7.2, 0.05, 0.95, 0.0, 4.2, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Orianna", Role.MID, emptySet(), 7.5, 8.0, 7.5, 8.8, 8.5, 0.10, 0.90, 0.0, 4.0, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.WAVECLEAR_STALL, ChampionTag.DISENGAGE_PEEL))
        add("Qiyana", Role.MID, setOf(Role.JUNGLE), 7.5, 8.5, 6.0, 7.0, 7.8, 0.95, 0.05, 0.0, 4.2, TankinessTier.SQUISHY, 1.8, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.HARD_ENGAGE))
        add("Ryze", Role.MID, setOf(Role.TOP), 7.0, 7.0, 6.5, 8.8, 9.0, 0.05, 0.95, 0.0, 5.5, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.WAVECLEAR_STALL))
        add("Swain", Role.SUPPORT, setOf(Role.MID, Role.BOT), 7.2, 7.5, 5.5, 7.5, 8.0, 0.05, 0.95, 0.0, 7.0, TankinessTier.BRUISER, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HARD_ENGAGE))
        add("Sylas", Role.MID, setOf(Role.TOP, Role.JUNGLE), 6.8, 7.5, 5.0, 6.5, 8.2, 0.10, 0.90, 0.0, 6.5, TankinessTier.BRUISER, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.SKIRMISHER, ChampionTag.DIVER))
        add("Syndra", Role.MID, emptySet(), 8.2, 7.0, 7.2, 8.5, 8.2, 0.05, 0.90, 0.05, 3.8, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.PICK_POTENTIAL))
        add("Talon", Role.MID, setOf(Role.JUNGLE), 8.0, 7.0, 6.0, 7.0, 6.8, 0.95, 0.05, 0.0, 4.0, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.ASSASSIN))
        add("Twisted Fate", Role.MID, setOf(Role.BOT, Role.TOP), 7.0, 7.0, 6.0, 8.0, 7.8, 0.15, 0.85, 0.0, 4.0, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.GLOBAL_PRESENCE, ChampionTag.PICK_POTENTIAL))
        add("Veigar", Role.MID, setOf(Role.BOT), 6.5, 6.5, 7.5, 8.0, 9.5, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BURST_MAGE, ChampionTag.WAVECLEAR_STALL))
        add("Vel'Koz", Role.SUPPORT, setOf(Role.MID), 7.5, 5.0, 6.5, 8.5, 7.8, 0.05, 0.75, 0.20, 3.2, TankinessTier.SQUISHY, 1.8, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ARTILLERY_MAGE, ChampionTag.POKE))
        add("Vex", Role.MID, emptySet(), 7.8, 8.2, 6.5, 8.0, 8.0, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Viktor", Role.MID, emptySet(), 7.5, 6.5, 6.5, 9.2, 9.2, 0.05, 0.95, 0.0, 4.2, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.WAVECLEAR_STALL, ChampionTag.HYPER_CARRY))
        add("Vladimir", Role.MID, setOf(Role.TOP), 6.5, 6.5, 6.5, 7.5, 9.8, 0.05, 0.95, 0.0, 6.0, TankinessTier.BRUISER, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY))
        add("Xerath", Role.SUPPORT, setOf(Role.MID), 8.0, 5.5, 5.5, 9.0, 8.0, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ARTILLERY_MAGE, ChampionTag.POKE))
        add("Yasuo", Role.MID, setOf(Role.TOP, Role.BOT), 7.8, 7.5, 6.8, 8.0, 9.0, 0.90, 0.10, 0.0, 6.0, TankinessTier.BRUISER, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.HYPER_CARRY))
        add("Yone", Role.MID, setOf(Role.TOP), 7.5, 8.5, 6.0, 7.8, 9.2, 0.65, 0.25, 0.10, 6.2, TankinessTier.BRUISER, 1.8, true, CcTier.HEAVY, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.SKIRMISHER, ChampionTag.HARD_ENGAGE, ChampionTag.HYPER_CARRY))
        add("Zed", Role.MID, setOf(Role.JUNGLE), 8.2, 7.2, 6.5, 7.5, 7.8, 0.95, 0.05, 0.0, 4.0, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Ziggs", Role.BOT, setOf(Role.MID), 7.8, 5.5, 6.5, 9.5, 8.0, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 1.2, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ARTILLERY_MAGE, ChampionTag.POKE, ChampionTag.WAVECLEAR_STALL))
        add("Zoe", Role.MID, emptySet(), 8.2, 6.5, 6.0, 7.2, 7.8, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.POKE, ChampionTag.PICK_POTENTIAL))

        // ================= BOT (ADCS) =================
        add("Aphelios", Role.BOT, emptySet(), 7.0, 6.0, 5.0, 8.0, 9.5, 0.95, 0.05, 0.0, 3.5, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY))
        add("Ashe", Role.BOT, setOf(Role.SUPPORT), 7.8, 8.5, 6.0, 7.0, 7.5, 0.85, 0.15, 0.0, 3.5, TankinessTier.SQUISHY, 3.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL))
        add("Caitlyn", Role.BOT, emptySet(), 8.8, 4.0, 5.5, 8.0, 8.0, 0.95, 0.05, 0.0, 3.5, TankinessTier.SQUISHY, 1.5, true, CcTier.LIGHT, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY, ChampionTag.POKE))
        add("Corki", Role.BOT, setOf(Role.MID), 7.5, 5.0, 5.0, 8.5, 8.8, 0.85, 0.15, 0.0, 3.8, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.POKE, ChampionTag.HYPER_CARRY))
        add("Draven", Role.BOT, emptySet(), 9.5, 6.0, 5.0, 6.5, 7.0, 0.95, 0.05, 0.0, 4.0, TankinessTier.SQUISHY, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY))
        add("Ezreal", Role.BOT, emptySet(), 7.5, 4.5, 8.0, 6.5, 7.8, 0.75, 0.25, 0.0, 3.8, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.POKE))
        add("Jhin", Role.BOT, emptySet(), 8.0, 7.0, 6.0, 7.5, 8.2, 0.95, 0.05, 0.0, 3.5, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.PICK_POTENTIAL, ChampionTag.POKE))
        add("Jinx", Role.BOT, emptySet(), 6.2, 4.0, 4.5, 8.5, 9.5, 0.95, 0.05, 0.0, 3.5, TankinessTier.SQUISHY, 1.5, true, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.WAVECLEAR_STALL))
        add("Kai'Sa", Role.BOT, emptySet(), 6.5, 6.5, 6.0, 7.2, 9.0, 0.55, 0.40, 0.05, 4.0, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.DIVER))
        add("Kalista", Role.BOT, emptySet(), 9.0, 7.5, 6.0, 6.5, 6.0, 0.95, 0.05, 0.0, 3.8, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY))
        add("Kog'Maw", Role.BOT, emptySet(), 6.0, 4.0, 4.5, 7.0, 9.8, 0.45, 0.55, 0.0, 3.2, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY))
        add("Lucian", Role.BOT, setOf(Role.MID), 8.5, 5.5, 6.0, 7.0, 7.0, 0.85, 0.15, 0.0, 4.0, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY))
        add("Miss Fortune", Role.BOT, setOf(Role.SUPPORT), 8.5, 7.5, 4.5, 7.5, 7.8, 0.85, 0.15, 0.0, 3.5, TankinessTier.SQUISHY, 1.0, false, CcTier.LIGHT, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY))
        add("Nilah", Role.BOT, emptySet(), 6.8, 8.0, 5.5, 6.5, 9.0, 0.95, 0.05, 0.0, 5.0, TankinessTier.BRUISER, 1.8, true, CcTier.HEAVY, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.SKIRMISHER, ChampionTag.HARD_ENGAGE))
        add("Samira", Role.BOT, emptySet(), 7.5, 8.0, 5.0, 6.5, 8.0, 0.90, 0.10, 0.0, 4.5, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.DIVER))
        add("Sivir", Role.BOT, emptySet(), 7.5, 4.0, 7.0, 9.0, 8.5, 0.95, 0.05, 0.0, 3.8, TankinessTier.SQUISHY, 0.0, false, CcTier.NONE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.WAVECLEAR_STALL))
        add("Smolder", Role.BOT, setOf(Role.MID), 6.0, 4.5, 5.0, 8.0, 9.8, 0.65, 0.20, 0.15, 3.5, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.POKE))
        add("Tristana", Role.BOT, setOf(Role.MID), 8.2, 6.5, 6.0, 8.0, 9.0, 0.95, 0.05, 0.0, 3.8, TankinessTier.SQUISHY, 1.0, true, CcTier.LIGHT, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.EARLY_BULLY))
        add("Twitch", Role.BOT, setOf(Role.SUPPORT), 5.5, 5.0, 5.5, 6.5, 9.8, 0.75, 0.15, 0.10, 3.2, TankinessTier.SQUISHY, 0.8, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.ASSASSIN))
        add("Varus", Role.BOT, setOf(Role.MID), 8.5, 7.5, 5.0, 8.2, 7.2, 0.65, 0.35, 0.0, 3.5, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("Vayne", Role.BOT, setOf(Role.TOP), 5.5, 4.5, 7.0, 4.5, 9.8, 0.65, 0.0, 0.35, 3.8, TankinessTier.SQUISHY, 1.5, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.SKIRMISHER))
        add("Xayah", Role.BOT, emptySet(), 7.8, 6.5, 8.5, 7.8, 8.8, 0.95, 0.05, 0.0, 3.8, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.DISENGAGE_PEEL))
        add("Zeri", Role.BOT, emptySet(), 6.0, 5.0, 6.5, 7.5, 9.2, 0.80, 0.20, 0.0, 3.8, TankinessTier.SQUISHY, 0.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY))

        // ================= SUPPORTS =================
        add("Alistar", Role.SUPPORT, emptySet(), 6.5, 9.5, 7.5, 3.5, 7.0, 0.10, 0.90, 0.0, 9.5, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE, ChampionTag.DISENGAGE_PEEL))
        add("Bard", Role.SUPPORT, emptySet(), 7.5, 7.8, 7.5, 4.5, 7.5, 0.10, 0.90, 0.0, 5.0, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.PICK_POTENTIAL))
        add("Blitzcrank", Role.SUPPORT, emptySet(), 7.8, 9.2, 5.0, 3.5, 5.5, 0.20, 0.80, 0.0, 7.5, TankinessTier.BRUISER, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.PICK_POTENTIAL, ChampionTag.HARD_ENGAGE))
        add("Brand", Role.SUPPORT, setOf(Role.JUNGLE, Role.MID), 8.0, 7.0, 4.0, 8.5, 8.0, 0.05, 0.95, 0.0, 3.8, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.POKE))
        add("Braum", Role.SUPPORT, emptySet(), 6.8, 7.0, 9.5, 3.5, 7.5, 0.10, 0.90, 0.0, 9.2, TankinessTier.FRONTLINE_TANK, 2.8, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.DISENGAGE_PEEL))
        add("Heimerdinger", Role.SUPPORT, setOf(Role.TOP, Role.MID), 8.5, 6.0, 6.5, 8.5, 7.5, 0.05, 0.95, 0.0, 3.8, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BATTLEMAGE, ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("Janna", Role.SUPPORT, emptySet(), 7.2, 5.0, 9.8, 4.0, 7.8, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Karma", Role.SUPPORT, setOf(Role.MID, Role.TOP), 8.5, 6.5, 8.0, 6.5, 6.5, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.POKE, ChampionTag.EARLY_BULLY))
        add("Leona", Role.SUPPORT, emptySet(), 7.2, 9.8, 3.5, 3.5, 6.0, 0.10, 0.90, 0.0, 9.0, TankinessTier.FRONTLINE_TANK, 3.5, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Lulu", Role.SUPPORT, emptySet(), 7.5, 5.0, 9.5, 4.0, 8.0, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Lux", Role.SUPPORT, setOf(Role.MID), 7.5, 6.0, 6.5, 8.0, 7.0, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.BURST_MAGE, ChampionTag.POKE))
        add("Milio", Role.SUPPORT, emptySet(), 7.0, 4.5, 9.0, 4.0, 8.2, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.0, true, CcTier.MODERATE, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Morgana", Role.SUPPORT, setOf(Role.JUNGLE), 7.5, 6.5, 8.5, 7.0, 7.2, 0.05, 0.95, 0.0, 4.0, TankinessTier.SQUISHY, 3.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.DISENGAGE_PEEL))
        add("Nami", Role.SUPPORT, emptySet(), 7.8, 7.2, 8.5, 4.0, 6.5, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Nautilus", Role.SUPPORT, setOf(Role.MID), 7.5, 9.5, 4.5, 4.0, 6.0, 0.15, 0.85, 0.0, 8.8, TankinessTier.FRONTLINE_TANK, 3.5, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL))
        add("Pyke", Role.SUPPORT, setOf(Role.MID), 8.0, 8.5, 7.0, 4.0, 5.0, 0.95, 0.05, 0.0, 5.5, TankinessTier.BRUISER, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.EARLY_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL))
        add("Rakan", Role.SUPPORT, emptySet(), 6.8, 9.5, 8.0, 4.5, 7.5, 0.10, 0.90, 0.0, 6.0, TankinessTier.BRUISER, 2.8, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.HARD_ENGAGE, ChampionTag.DISENGAGE_PEEL))
        add("Rell", Role.SUPPORT, setOf(Role.JUNGLE), 6.0, 9.5, 6.5, 4.0, 7.0, 0.10, 0.90, 0.0, 8.5, TankinessTier.FRONTLINE_TANK, 3.2, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE))
        add("Renata Glasc", Role.SUPPORT, emptySet(), 7.0, 6.5, 9.0, 4.0, 8.0, 0.10, 0.90, 0.0, 4.0, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.DISENGAGE_PEEL))
        add("Senna", Role.SUPPORT, setOf(Role.BOT), 7.5, 5.5, 6.5, 6.0, 9.5, 0.85, 0.15, 0.0, 3.5, TankinessTier.SQUISHY, 1.8, true, CcTier.MODERATE, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.MARKSMAN, ChampionTag.ENCHANTER, ChampionTag.HYPER_CARRY))
        add("Seraphine", Role.SUPPORT, setOf(Role.BOT, Role.MID), 7.5, 7.5, 7.5, 8.5, 8.2, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.BURST_MAGE, ChampionTag.HARD_ENGAGE))
        add("Sona", Role.SUPPORT, emptySet(), 7.0, 6.5, 7.5, 5.0, 9.0, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 2.0, true, CcTier.HEAVY, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.ENCHANTER))
        add("Soraka", Role.SUPPORT, emptySet(), 7.5, 4.0, 8.5, 4.5, 8.0, 0.05, 0.95, 0.0, 3.2, TankinessTier.SQUISHY, 1.8, true, CcTier.LIGHT, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Taric", Role.SUPPORT, emptySet(), 6.5, 6.5, 8.5, 4.0, 8.5, 0.20, 0.80, 0.0, 8.8, TankinessTier.FRONTLINE_TANK, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.WARDEN_TANK, ChampionTag.DISENGAGE_PEEL))
        add("Thresh", Role.SUPPORT, emptySet(), 7.2, 8.5, 8.2, 4.0, 6.8, 0.20, 0.80, 0.0, 7.2, TankinessTier.BRUISER, 2.8, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.PICK_POTENTIAL, ChampionTag.DISENGAGE_PEEL))
        add("Yuumi", Role.SUPPORT, emptySet(), 6.5, 5.0, 8.0, 3.0, 8.5, 0.05, 0.95, 0.0, 3.0, TankinessTier.SQUISHY, 1.5, false, CcTier.LIGHT, PowerSpikeCurve.HYPER_SCALING, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Zilean", Role.SUPPORT, setOf(Role.MID), 7.2, 5.0, 8.5, 7.0, 8.5, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.2, true, CcTier.HEAVY, PowerSpikeCurve.LATE_GAME_SPIKE, setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL))
        add("Zyra", Role.SUPPORT, setOf(Role.JUNGLE), 8.2, 7.5, 6.0, 8.0, 7.2, 0.05, 0.95, 0.0, 3.5, TankinessTier.SQUISHY, 2.5, true, CcTier.HEAVY, PowerSpikeCurve.MID_GAME_SPIKE, setOf(ChampionTag.CATCHER, ChampionTag.BURST_MAGE, ChampionTag.POKE))

        // Ensure every canonical champion in ChampionNormalizer has an entry
        val existingSlugs = list.map { ChampionNormalizer.toSlug(it.championId) }.toSet()
        val canonicals = ChampionNormalizer.getCanonicalNames()

        for (canon in canonicals) {
            val slug = ChampionNormalizer.toSlug(canon)
            if (!existingSlugs.contains(slug)) {
                val (primaryRole, secondaryRoles) = ChampionRoleDictionary.getBaselineRole(canon)
                // Construct standard balanced profile for this champion based on primary role
                val defaultProfile = when (primaryRole) {
                    Role.TOP -> ChampionProfile(
                        championId = canon,
                        displayName = canon,
                        primaryRole = primaryRole,
                        secondaryRoles = secondaryRoles,
                        damageProfile = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(7.5, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.5, 7.0, 5.5, 7.0, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.BRUISER_TAG, ChampionTag.EARLY_BULLY)
                    )
                    Role.JUNGLE -> ChampionProfile(
                        championId = canon,
                        displayName = canon,
                        primaryRole = primaryRole,
                        secondaryRoles = secondaryRoles,
                        damageProfile = DamageProfile(0.70, 0.30, 0.0, DamageType.MIXED),
                        ccRating = CrowdControlRating(2.0, true, CcTier.MODERATE),
                        durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(6.8, 8.0, 5.5, 7.0, 7.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE)
                    )
                    Role.MID -> ChampionProfile(
                        championId = canon,
                        displayName = canon,
                        primaryRole = primaryRole,
                        secondaryRoles = secondaryRoles,
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.5, 7.5, 6.5, 8.2, 8.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.BURST_MAGE, ChampionTag.WAVECLEAR_STALL)
                    )
                    Role.BOT -> ChampionProfile(
                        championId = canon,
                        displayName = canon,
                        primaryRole = primaryRole,
                        secondaryRoles = secondaryRoles,
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.0, false, CcTier.LIGHT),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.5, 5.0, 5.5, 7.5, 9.0),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY)
                    )
                    Role.SUPPORT -> ChampionProfile(
                        championId = canon,
                        displayName = canon,
                        primaryRole = primaryRole,
                        secondaryRoles = secondaryRoles,
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(5.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.2, 7.5, 8.0, 4.5, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL)
                    )
                }
                list.add(defaultProfile)
            }
        }

        return list.distinctBy { ChampionNormalizer.toSlug(it.championId) }
    }

    private val ChampionTag.Companion.BRUISER_TAG: ChampionTag
        get() = ChampionTag.JUGGERNAUT
}
