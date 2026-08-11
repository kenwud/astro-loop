package com.astroloop.game.data

/**
 * A weapon's *presentation* and its place in the evolution tree. Deliberately holds **no combat
 * numbers**.
 *
 * It used to carry `baseDamage`, `baseCooldown`, `baseProjectileSpeed` and `baseProjectileCount`
 * alongside the real ones on the `Weapon` subclasses, and by 2026-08-11 the two had drifted apart
 * in **24 places** — Flak Cannon 33 here against 44 in the class, Phoenix Flare 1 projectile
 * against 8, Oblivion Beam a 1.0s cooldown against 0.1s. Nothing read the copies, which is exactly
 * why nobody noticed: a duplicate no code depends on has nothing holding it honest.
 *
 * They are gone rather than corrected. Syncing two tables leaves two tables, and they would drift
 * again the first time a weapon was tuned. **`WeaponFactory.create(id)` is the one place a
 * weapon's numbers live.** If a screen ever needs to show them, it should ask a real weapon
 * instance rather than a second copy of the truth.
 */
data class WeaponDef(
    val id: String,
    val name: String,
    val description: String,
    val evolutionPassive: String? = null,
    val evolutionWeaponId: String? = null,
    val levelBonuses: List<String> = listOf(
        "Base weapon",
        "+30% damage",
        "+1 projectile",
        "+25% damage, +1 projectile",
        "+1 projectile, MAX LEVEL"
    )
) {
    fun getLevelDescription(level: Int): String {
        return levelBonuses.getOrElse(level - 1) { "MAX" }
    }
}

object WeaponDefinitions {

    val weapons = listOf(
        WeaponDef(
            id = "pulse_cannon",
            name = "Pulse Cannon",
            description = "Auto-targeting energy bolts",
            evolutionPassive = "duplicator_core",
            evolutionWeaponId = "storm_cannon",
            levelBonuses = listOf(
                "1 bolt",
                "+1 bolt",
                "+1 bolt",
                "+1 bolt",
                "+1 bolt"
            )
        ),
        WeaponDef(
            id = "energy_saw",
            name = "Energy Saw",
            description = "Spinning disc that shreds on contact",
            evolutionPassive = "momentum_drive",
            evolutionWeaponId = "warp_saw",
            levelBonuses = listOf(
                "1 blade",
                "Bigger blade",
                "Bigger blade",
                "Bigger blade",
                "Bigger blade, MAX"
            )
        ),
        WeaponDef(
            id = "scatter_shot",
            name = "Scatter Shot",
            description = "Close spread of pellets",
            evolutionPassive = "vampiric_core",
            evolutionWeaponId = "leech_burst",
            levelBonuses = listOf(
                "5 pellets",
                "+2 pellets",
                "+2 pellets",
                "+2 pellets",
                "+2 pellets"
            )
        ),
        WeaponDef(
            id = "homing_missiles",
            name = "Homing Missiles",
            description = "Lock-on missiles",
            evolutionPassive = "tb26",
            evolutionWeaponId = "autonomous_ace",
            levelBonuses = listOf(
                "1 missile",
                "+1 missile",
                "+1 missile",
                "+1 missile",
                "+1 missile"
            )
        ),
        WeaponDef(
            id = "ion_orbiters",
            name = "Ion Orbiters",
            description = "Orbiting energy spheres",
            evolutionPassive = "cryo_field",
            evolutionWeaponId = "frost_ring",
            levelBonuses = listOf(
                "2 orbiters",
                "+1 orbiter",
                "+1 orbiter",
                "+1 orbiter",
                "+1 orbiter"
            )
        ),
        WeaponDef(
            id = "railgun",
            name = "Railgun",
            description = "Piercing sniper shot",
            evolutionPassive = "glass_cannon",
            evolutionWeaponId = "oblivion_beam",
            levelBonuses = listOf(
                "Pierces 10",
                "Wider shot",
                "Wider shot",
                "Wider shot",
                "Beam-wide, MAX"
            )
        ),
        WeaponDef(
            id = "space_mines",
            name = "Space Mines",
            description = "Dropped proximity mines",
            evolutionPassive = "lucky_star",
            evolutionWeaponId = "jackpot_mines",
            levelBonuses = listOf(
                "1 mine",
                "Bigger explosion",
                "+1 mine",
                "Bigger explosion",
                "+1 mine"
            )
        ),
        WeaponDef(
            id = "solar_storm",
            name = "Solar Storm",
            description = "Random lightning strikes",
            evolutionPassive = "phoenix_core",
            evolutionWeaponId = "phoenix_flare",
            levelBonuses = listOf(
                "1 target",
                "+1 target",
                "+1 target",
                "+1 target",
                "+1 target"
            )
        ),
        WeaponDef(
            id = "nova_blast",
            name = "Nova Blast",
            description = "Ring burst around your ship",
            evolutionPassive = "revenge_protocol",
            evolutionWeaponId = "lingering_nova",
            levelBonuses = listOf(
                "Base burst",
                "Bigger radius",
                "Bigger radius",
                "Bigger radius",
                "Bigger radius"
            )
        ),
        WeaponDef(
            id = "needle_gun",
            name = "Needle Gun",
            description = "Rapid piercing needles",
            evolutionPassive = "nano_repair",
            evolutionWeaponId = "siphon_needles",
            levelBonuses = listOf(
                "3 needles",
                "+1 needle",
                "+1 needle",
                "+1 needle",
                "+1 needle"
            )
        ),
        WeaponDef(
            id = "cluster_bomb",
            name = "Cluster Bomb",
            description = "Bomb that scatters bomblets",
            evolutionPassive = "magnet_field",
            evolutionWeaponId = "hunter_killer",
            levelBonuses = listOf(
                "2 bomblets",
                "+1 bomblet",
                "+1 bomblet",
                "+1 bomblet",
                "+1 bomblet"
            )
        ),
        WeaponDef(
            id = "flak_cannon",
            name = "Flak Cannon",
            description = "Direct-fire exploding shells",
            evolutionPassive = "extra_weapon_slot",
            evolutionWeaponId = "flak_barrage",
            levelBonuses = listOf(
                "1 shell",
                "+1 shell",
                "+1 shell",
                "+1 shell",
                "+1 shell"
            )
        )
    )

    // Evolution weapons (unlocked by combining weapon + passive)
    val evolutions = listOf(
        WeaponDef(
            id = "storm_cannon",
            name = "Storm Cannon",
            description = "A spiral of bolts that fills the field",
        ),
        WeaponDef(
            id = "warp_saw",
            name = "Warp Saw",
            description = "The blade detaches and hunts alone",
        ),
        WeaponDef(
            id = "leech_burst",
            name = "Leech Burst",
            description = "Pellets that heal you on hit",
        ),
        WeaponDef(
            id = "autonomous_ace",
            name = "Autonomous Ace",
            description = "Missiles split and chase new targets",
        ),
        WeaponDef(
            id = "frost_ring",
            name = "Frost Ring",
            description = "Two rings of orbs, always turning",
        ),
        WeaponDef(
            id = "oblivion_beam",
            name = "Oblivion Beam",
            description = "Always-on piercing lance",
        ),
        WeaponDef(
            id = "jackpot_mines",
            name = "Gambler's Mines",
            description = "Mines that sometimes hit the jackpot",
        ),
        WeaponDef(
            id = "phoenix_flare",
            name = "Phoenix Flare",
            description = "Three rings erupt where enemies stand",
        ),
        WeaponDef(
            id = "lingering_nova",
            name = "Lingering Nova",
            description = "Blasts, leaves a core, blasts again",
        ),
        WeaponDef(
            id = "siphon_needles",
            name = "Siphon Needles",
            description = "Piercing needles that heal you on hit",
        ),
        WeaponDef(
            id = "hunter_killer",
            name = "Hunter-Killer",
            description = "Homing torpedo, fired twice as often",
        ),
        WeaponDef(
            id = "flak_barrage",
            name = "Flak Barrage",
            description = "A burst of five exploding shells",
        )
    )

    fun getWeaponDef(id: String): WeaponDef? {
        return weapons.find { it.id == id } ?: evolutions.find { it.id == id }
    }

    fun getBaseWeapons(): List<WeaponDef> = weapons

    fun getEvolutionFor(weaponId: String, passiveId: String): WeaponDef? {
        val weapon = weapons.find { it.id == weaponId } ?: return null
        if (weapon.evolutionPassive == passiveId) {
            return evolutions.find { it.id == weapon.evolutionWeaponId }
        }
        return null
    }

    fun isEvolution(id: String): Boolean = evolutions.any { it.id == id }

    fun getWeaponDisplayName(weaponId: String): String {
        return getWeaponDef(weaponId)?.name ?: weaponId
    }

    fun getEvolutionDisplayName(evolutionId: String, activePilotId: String): String {
        if (evolutionId == "autonomous_ace") {
            return if (activePilotId == "pilot_astro") "TB-26-X" else "Autonomous Ace"
        }
        return getWeaponDef(evolutionId)?.name ?: evolutionId
    }

    /**
     * Pilot-aware weapon icon id. Astro's Autonomous Ace (TB-26-X) uses its own icon, but
     * only outside Astro-Loop runs — there the drone is the generic green one — mirroring the
     * TB-26 drone rule in PassiveDefinitions.getDroneColor().
     */
    fun getWeaponIconId(weaponId: String, activePilotId: String, isAstroLoop: Boolean = false): String {
        if (weaponId == "autonomous_ace" && activePilotId == "pilot_astro" && !isAstroLoop) return "tb26_x"
        return weaponId
    }
}
