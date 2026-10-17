package com.unciv.logic.civilization.diplomacy

/**
 * Deterministic, side-effect-free peace negotiation policy.
 *
 * Positive pressure means the civilization should be more willing to end its war.
 * Keeping the policy independent of game objects allows headless regression testing.
 */
object PeaceNegotiationPolicy {
    /** A conservative bound to avoid impossible or exploitative gold demands. */
    fun affordableReparations(requestedGold: Int, availableGold: Int): Int =
        requestedGold.coerceAtLeast(0).coerceAtMost(availableGold.coerceAtLeast(0))

    /**
     * @param ownForce current military force rating
     * @param enemyForce opposing military force rating
     * @param additionalWars number of other simultaneous wars
     * @param turnsAtWar elapsed turns of war
     */
    fun peacePressure(ownForce: Int, enemyForce: Int, additionalWars: Int, turnsAtWar: Int): Int {
        val ours = ownForce.coerceAtLeast(0).toLong()
        val theirs = enemyForce.coerceAtLeast(0).toLong()
        val forcePressure = if (ours + theirs == 0L) 0
            else ((theirs - ours) * 100L / (ours + theirs)).toInt().coerceIn(-100, 100)
        val fronts = additionalWars.coerceIn(0, 10) * 15
        val exhaustion = turnsAtWar.coerceIn(0, 100) / 2
        return (forcePressure + fronts + exhaustion).coerceIn(-100, 200)
    }

    /** Never offer the last city of a civilization as a peace concession. */
    fun canConcedeCity(cityCount: Int, alreadyConceded: Int): Boolean =
        cityCount > 1 && alreadyConceded >= 0 && alreadyConceded < cityCount - 1
}
