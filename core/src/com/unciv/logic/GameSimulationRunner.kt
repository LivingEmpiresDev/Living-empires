package com.unciv.logic

/**
 * Drives the existing AI turn engine without requiring a WorldScreen.
 *
 * The caller must supply an initialized [GameInfo] (for example, from
 * [GameStarter.startNewGame]) and initialize the normal game resources first.
 *
 * This is a simulation engine adapter, not a standalone JVM launcher.
 */
object GameSimulationRunner {
    data class CivilizationResult(
        val name: String,
        val alive: Boolean,
        val cities: Int,
        val won: Boolean,
    )

    data class Result(
        val startingTurn: Int,
        val endingTurn: Int,
        val requestedTurns: Int,
        val completed: Boolean,
        val civilizations: List<CivilizationResult>,
        val activeWars: Int,
        val activeDefensivePacts: Int,
        val activeNonAggressionPacts: Int,
        val activeTrades: Int,
    )

    /**
     * Run up to [turnCount] complete world turns through normal AI automation.
     * The game engine stops early on victory or when no major civilizations remain.
     * Exceptions are intentionally propagated so CI can flag a failed simulation.
     */
    fun run(game: GameInfo, turnCount: Int = 100): Result {
        require(turnCount > 0) { "turnCount must be positive" }
        require(!game.simulateUntilWin) { "A simulation is already active" }

        val start = game.turns
        val target = Math.addExact(start, turnCount)
        val oldMax = game.simulateMaxTurns
        try {
            game.simulateMaxTurns = target
            game.simulateUntilWin = true
            game.nextTurn()
        } finally {
            game.simulateUntilWin = false
            game.simulateMaxTurns = oldMax
        }

        // Count each bilateral relationship once. These are end-of-run snapshots,
        // not cumulative diplomatic event counts.
        val relationships = game.civilizations.filter { it.isMajorCiv() }
            .flatMap { civ -> civ.diplomacy.values.filter { it.otherCiv.isMajorCiv() }
                .map { civ.civID to it } }
            .filter { (civId, manager) -> civId < manager.otherCiv.civID }

        return Result(
            startingTurn = start,
            endingTurn = game.turns,
            requestedTurns = turnCount,
            completed = game.turns >= target,
            activeWars = relationships.count { (_, d) -> d.diplomaticStatus.name == "War" },
            activeDefensivePacts = relationships.count { (_, d) -> d.diplomaticStatus.name == "DefensivePact" },
            activeNonAggressionPacts = relationships.count { (_, d) -> d.turnsToNonAggressionPact() > 0 },
            activeTrades = relationships.sumOf { (_, d) -> d.trades.size },
            civilizations = game.civilizations.filter { it.isMajorCiv() }.map { civ ->
                CivilizationResult(
                    name = civ.civName,
                    alive = civ.isAlive(),
                    cities = civ.cities.size,
                    won = civ.victoryManager.hasWon(),
                )
            },
        )
    }
}
