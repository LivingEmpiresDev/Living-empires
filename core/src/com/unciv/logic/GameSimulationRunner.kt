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

        return Result(
            startingTurn = start,
            endingTurn = game.turns,
            requestedTurns = turnCount,
            completed = game.turns >= target,
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
