package com.unciv.dev

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessFiles
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.MapSaver
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.screens.LanguagePickerScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.Display
import com.unciv.utils.PlatformDisplay
import kotlin.system.exitProcess
import java.io.File
import java.util.Locale

/**
 * Turn-speed measurement for [Objective Judge Horizon](https://github.com/Pr1nted/objective-judge-horizon),
 * a benchmark that measures turn-based strategy games the same way on the same
 * machine.
 *
 * This is a developer tool, the same kind of thing as [FasterUIDevelopment] and
 * [com.unciv.logic.NextTurnLatencyTest] -- it lives in `tests/`, nothing in
 * `core/` or `desktop/` refers to it, and no build ships it. Deleting this one
 * file removes it completely; OJH keeps an outside driver and falls back to it.
 *
 * ## Why it is here rather than outside
 *
 * OJH can already drive Unciv from outside, and does. But Java cannot see
 * Kotlin's default arguments, so from outside the only way to start a turn is
 * the generated `GameInfo.nextTurn$default` -- a compiler artifact, not API.
 * From here it is `gameInfo.nextTurn()`. A signature change breaks the outside
 * driver silently, at runtime, after the benchmark has reported a number;
 * it breaks this file at compile time, in Unciv's own `./gradlew check`.
 *
 * It also takes `--map`, so Unciv can be measured on a map converted from
 * another game rather than on a generated world of about the right size. That
 * is the difference between "a 63-player large map" and "the same world every
 * other game in the comparison played".
 *
 * ## Running it
 *
 * ```
 * ./gradlew :tests:ojh -Pojh.args="turns --turns 250 --players 63 --map /path/map.json"
 * ./gradlew :tests:ojh -Pojh.args="fps --seconds 5 --late-turns 20 --players 8"
 * ```
 *
 * or directly, with the tests classpath:
 *
 * ```
 * java -cp <tests runtime classpath> com.unciv.dev.OjhBenchmark turns --turns 250
 * ```
 *
 * ## Output
 *
 * OJH's line protocol on stdout, one line per fact, everything else on stderr.
 * Both modes name the world they measured, because a frame rate or a turn time
 * means nothing without it:
 *
 * ```
 * OJH players <n>
 * OJH regions <n> tiles
 * OJH view spectator|civ          whether the map is drawn with fog of war
 * ```
 *
 * then, for `turns`:
 *
 * ```
 * OJH ready                       the game is loaded; turn 1 starts next
 * OJH turn <n> <seconds>
 * ```
 *
 * and for `fps`:
 *
 * ```
 * OJH renderer <gl renderer>, OpenGL <major>.<minor> via LWJGL3
 * OJH resolution <w>x<h>
 * OJH vsync off
 * OJH scene <name> <frames> <seconds> <p50 ms> <p95 ms> <p99 ms> <1% low fps>
 * ```
 */
object OjhBenchmark {

    private fun say(line: String) = println(line)

    /**
     * A spectator sees every tile, an ordinary civ sees what it has explored, and
     * the map scenes cost very different amounts in the two cases -- about a
     * third, measured. Whichever a run used belongs in its output.
     */
    internal fun view(info: GameInfo): String =
        if (info.civilizations.any { it.isSpectator() }) "spectator" else "civ"
    private fun note(line: String) = System.err.println(line)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()
            ?: throw IllegalArgumentException("the first argument is the mode: turns or fps")
        require(mode == "turns" || mode == "fps") { "unknown mode $mode" }
        var turns = 100
        var seconds = 5.0
        var lateTurns = 20
        var players = 8
        var size = "small"
        var mapPath: String? = null
        var seed: Long = 0

        var i = 1
        while (i < args.size) {
            val value = { args.getOrNull(i + 1) ?: throw IllegalArgumentException("${args[i]} needs a value") }
            when (args[i]) {
                "--turns" -> { turns = value().toInt(); i++ }
                "--players" -> { players = value().toInt(); i++ }
                "--size" -> { size = value(); i++ }
                "--map" -> { mapPath = value(); i++ }
                "--seed" -> { seed = value().toLong(); i++ }
                "--seconds" -> { seconds = value().toDouble(); i++ }
                "--late-turns" -> { lateTurns = value().toInt(); i++ }
                else -> throw IllegalArgumentException("unknown argument ${args[i]}")
            }
            i++
        }
        require(turns > 0) { "--turns must be at least 1" }
        require(players >= 2) { "--players must be at least 2" }

        if (mode == "fps") {
            fps(players, size, mapPath, seed, seconds, lateTurns)
            return
        }

        // Gdx.files only, not a HeadlessApplication: that starts a render loop
        // on a non-daemon thread, so the JVM never exits once the turns are
        // done. A turn needs files and nothing else.
        Gdx.files = HeadlessFiles()

        val game = UncivGame(true)
        UncivGame.Current = game
        game.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 1_000_000    // autosaving is not part of a turn
        }
        game.files = UncivFiles(Gdx.files)
        RulesetCache.loadRulesets(noMods = true)

        val bootStart = System.nanoTime()
        val info = start(players, size, mapPath, seed)
        val bootSeconds = (System.nanoTime() - bootStart) / 1e9

        say("OJH players ${info.civilizations.count { it.playerType == PlayerType.AI && !it.isBarbarian }}")
        say("OJH regions ${info.tileMap.values.size} tiles")
        say("OJH view ${view(info)}")
        note(String.format(Locale.ROOT, "loaded in %.3f s", bootSeconds))
        say("OJH ready")

        // A reproducible baseline scenario: every new game starts without an
        // active bilateral defensive pact or a recorded bilateral war.
        // Assert the baseline before AI takes its first turn.
        for (civ in info.civilizations) {
            for ((otherName, manager) in civ.diplomacy) {
                val other = info.civilizations.firstOrNull { it.civName == otherName } ?: continue
                val reverse = other.diplomacy[civ.civName] ?: continue
                val war = com.unciv.logic.civilization.diplomacy.DiplomaticStatus.War
                check((manager.diplomaticStatus == war) == (reverse.diplomaticStatus == war)) {
                    "SIM initial diplomacy asymmetrical war civ=${civ.civName} other=$otherName"
                }
            }
        }
        note("SIM scenario initialWarReciprocity=passed")
        // Controlled scenario: install a 20-turn non-aggression treaty in an
        // actual initialized game's diplomacy manager, then verify the guard.
        // Restore the original trades before the AI simulation proceeds.
        val scenarioPair = info.civilizations.filter { !it.isBarbarian && !it.isSpectator() }
            .flatMap { civ -> civ.diplomacy.keys.mapNotNull { otherName ->
                val other = info.civilizations.firstOrNull { it.civName == otherName }
                if (other != null && !other.isBarbarian && !other.isSpectator()) civ to other else null
            } }.firstOrNull()
        if (scenarioPair != null) {
            val (first, second) = scenarioPair
            val manager = first.diplomacy[second.civName]!!
            val temporary = com.unciv.logic.trade.Trade()
            val offer = com.unciv.logic.trade.TradeOffer(
                com.unciv.logic.civilization.diplomacy.LivingEmpiresTreaties.nonAggressionPact,
                com.unciv.logic.trade.TradeOfferType.Treaty,
                duration = com.unciv.logic.civilization.diplomacy.LivingEmpiresTreaties.duration
            )
            temporary.ourOffers.add(offer)
            manager.trades.add(temporary)
            try {
                for (remaining in 20 downTo 1) {
                    check(manager.turnsToNonAggressionPact() == remaining)
                    check(!manager.canAttack())
                    offer.duration--
                }
                check(manager.canAttack())
                note("SIM scenario treaty20TurnGuard=passed")
                // A peace treaty independently blocks attacks; overlapping treaties
                // must both expire before combat is permitted.
                offer.duration = 20
                val peace = com.unciv.logic.trade.TradeOffer(
                    Constants.peaceTreaty,
                    com.unciv.logic.trade.TradeOfferType.Treaty,
                    duration = 10
                )
                temporary.ourOffers.add(peace)
                check(!manager.canAttack())
                offer.duration = 0
                check(!manager.canAttack())
                peace.duration = 0
                check(manager.canAttack())
                note("SIM scenario overlappingPeaceAndNonAggression=passed")
                // A defensive pact timer and rejected-peace cooldown are
                // independent, including after cloning diplomacy for save state.
                manager.setFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact, 20)
                manager.setFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace, 5)
                val saved = manager.clone()
                check(saved.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact) == 20)
                check(saved.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace) == 5)
                saved.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace)
                check(saved.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact) == 20)
                check(manager.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace) == 5)
                manager.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)
                manager.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace)
                note("SIM scenario defensivePactAndPeaceCooldownClone=passed")


            } finally {
                manager.trades.remove(temporary)
            }
        } else {
            note("SIM scenario treaty20TurnGuard=skipped no initialized diplomatic pair")
        }
        // Run a controlled three-civilization war on a separate fresh game.
        // The primary benchmark game remains untouched.
        run {
            val scenario = start(players.coerceAtLeast(3), size, mapPath, seed + 1)
            // Let normal AI turns establish settlements before diplomacy tests.
            // AI peace automation requires both belligerents to own cities.
            for (setupTurn in 1..12) {
                if (scenario.civilizations.count { it.isMajorCiv() && it.cities.isNotEmpty() } >= 3) break
                scenario.nextTurn()
            }
            val majors = scenario.civilizations.filter { it.isMajorCiv() && !it.isSpectator() }
            check(majors.size >= 3) { "SIM defensive intervention needs three major civilizations" }
            val defender = majors[0]
            val ally = majors[1]
            val aggressor = majors[2]
            defender.diplomacyFunctions.makeCivilizationsMeet(ally)
            defender.diplomacyFunctions.makeCivilizationsMeet(aggressor)
            aggressor.diplomacyFunctions.makeCivilizationsMeet(ally)
            val pact = defender.getDiplomacyManager(ally)!!
            pact.signDefensivePact(20)
            check(pact.diplomaticStatus == com.unciv.logic.civilization.diplomacy.DiplomaticStatus.DefensivePact)
            aggressor.getDiplomacyManager(defender)!!.declareWar()
            check(defender.isAtWarWith(aggressor)) { "SIM defender did not enter war" }
            check(ally.isAtWarWith(aggressor)) { "SIM defensive ally failed to join war" }
            check(!defender.isAtWarWith(ally)) { "SIM defensive allies entered war against each other" }
            note("SIM scenario defensiveIntervention=passed")
            // Resolve the deliberately created war through the production peace
            // transition and verify both sides agree on the resulting state.
            val peaceManager = defender.getDiplomacyManager(aggressor)!!
            peaceManager.makePeace()
            check(!defender.isAtWarWith(aggressor)) { "SIM defender remained at war after peace" }
            check(!aggressor.isAtWarWith(defender)) { "SIM aggressor remained at war after peace" }
            check(peaceManager.diplomaticStatus == com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            check(aggressor.getDiplomacyManager(defender)!!.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            note("SIM scenario bilateralPeace=passed")
            // Accepted peace terms must prevent an immediate re-declaration.
            // Exercise the production declaration guard, not merely canAttack().
            val peaceTrade = com.unciv.logic.trade.Trade()
            val peaceOffer = com.unciv.logic.trade.TradeOffer(
                Constants.peaceTreaty,
                com.unciv.logic.trade.TradeOfferType.Treaty,
                duration = 10
            )
            peaceTrade.ourOffers.add(peaceOffer)
            peaceManager.trades.add(peaceTrade)
            check(!peaceManager.canDeclareWar())
            peaceManager.declareWar()
            check(!defender.isAtWarWith(aggressor)) { "SIM war declared during active peace treaty" }
            peaceOffer.duration = 0
            check(peaceManager.canDeclareWar()) { "SIM declaration still blocked after peace treaty expiry" }
            note("SIM scenario peaceTreatyBlocksRedeclaration=passed")
            // The same non-aggression protection must apply to joint-war
            // and join-war declarations, not only direct attacks.
            peaceOffer.duration = 0
            val pactTrade = com.unciv.logic.trade.Trade()
            val pactOffer = com.unciv.logic.trade.TradeOffer(
                com.unciv.logic.civilization.diplomacy.LivingEmpiresTreaties.nonAggressionPact,
                com.unciv.logic.trade.TradeOfferType.Treaty,
                duration = 20
            )
            pactTrade.ourOffers.add(pactOffer)
            peaceManager.trades.add(pactTrade)
            for (kind in listOf(
                com.unciv.logic.civilization.diplomacy.WarType.DirectWar,
                com.unciv.logic.civilization.diplomacy.WarType.TeamWar,
                com.unciv.logic.civilization.diplomacy.WarType.JoinWar
            )) {
                peaceManager.declareWar(com.unciv.logic.civilization.diplomacy.DeclareWarReason(kind))
                check(!defender.isAtWarWith(aggressor)) {
                    "SIM non-aggression pact bypassed by $kind"
                }
            }
            pactOffer.duration = 0
            check(peaceManager.canDeclareWar())
            note("SIM scenario nonAggressionBlocksVoluntaryWarTypes=passed")
            // A fresh AI war should not produce an immediate peace proposal
            // while the DeclinedPeace cooldown is active.
            val aiManager = aggressor.getDiplomacyManager(defender)!!
            aiManager.declareWar()
            check(aggressor.isAtWarWith(defender))
            aiManager.setFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace, 5)
            val requestsBefore = defender.tradeRequests.count {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            // Kotlin internal members are not callable across the core/tests
            // module boundary; invoke the compiled core method reflectively.
            val automation = com.unciv.logic.automation.civilization.DiplomacyAutomation
            val peaceMethod = automation.javaClass.declaredMethods.first {
                it.name.startsWith("offerPeaceTreaty") && it.parameterTypes.contentEquals(arrayOf(com.unciv.logic.civilization.Civilization::class.java))
            }
            peaceMethod.isAccessible = true
            peaceMethod.invoke(automation, aggressor)
            val requestsAfter = defender.tradeRequests.count {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            check(requestsAfter == requestsBefore) { "SIM AI offered peace during DeclinedPeace cooldown" }
            note("SIM scenario aiPeaceCooldown=passed")
            // Verify the AI routine also handles a cleared cooldown without
            // creating duplicate requests when invoked twice in one turn.
            aiManager.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace)
            // A freshly declared war carries DeclaredWar cooldown flags on both sides.
            // Clear them to make this a legitimate long-running-war peace scenario.
            aiManager.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
            defender.getDiplomacyManager(aggressor)!!.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
            aiManager.warStartedOnTurn = aggressor.gameInfo.turns - 50
            // Remove the early-game motivation gate by giving the aggressor
            // several simultaneous enemies; this raises peace pressure.
            for (extraEnemy in majors.drop(3).take(3)) {
                aggressor.diplomacyFunctions.makeCivilizationsMeet(extraEnemy)
                aggressor.getDiplomacyManager(extraEnemy)!!.declareWar()
                aggressor.getDiplomacyManager(extraEnemy)!!.removeFlag(
                    com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar
                )
                extraEnemy.getDiplomacyManager(aggressor)!!.removeFlag(
                    com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar
                )
            }
            note("SIM peaceEligibility aggressorCities=${aggressor.cities.size} defenderCities=${defender.cities.size} activeWars=${aggressor.getCivsAtWarWith().size} declaredWarFlag=${aiManager.hasFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)} declinedPeaceFlag=${aiManager.hasFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclinedPeace)}")
            val beforeRepeat = defender.tradeRequests.count {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            peaceMethod.invoke(automation, aggressor)
            val afterFirst = defender.tradeRequests.count {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            peaceMethod.invoke(automation, aggressor)
            val afterSecond = defender.tradeRequests.count {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            check(afterSecond == afterFirst) { "SIM duplicate AI peace proposals in same turn" }
            note("SIM scenario aiPeaceOfferDedup=passed generated=${afterFirst - beforeRepeat}")
            // AI-generated proposals must always contain reciprocal peace
            // treaty offers and nonnegative monetary concessions.
            for (request in defender.tradeRequests.filter {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }) {
                val trade = request.trade
                check(trade.ourOffers.any { it.name == Constants.peaceTreaty })
                check(trade.theirOffers.any { it.name == Constants.peaceTreaty })
                check((trade.ourOffers + trade.theirOffers).filter {
                    it.type == com.unciv.logic.trade.TradeOfferType.Gold ||
                        it.type == com.unciv.logic.trade.TradeOfferType.Gold_Per_Turn
                }.all { it.amount >= 0 }) { "SIM AI proposed negative peace reparations" }
            }
            check(afterSecond > beforeRepeat) { "SIM expected an actual AI peace proposal after settling and multi-front war pressure" }
            note("SIM scenario aiPeaceOfferTerms=passed inspected=${afterSecond - beforeRepeat}")
            val actualOffer = defender.tradeRequests.first {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }
            check(actualOffer.trade.ourOffers.any {
                it.name == Constants.peaceTreaty && it.duration > 0
            })
            check(actualOffer.trade.theirOffers.any {
                it.name == Constants.peaceTreaty && it.duration > 0
            })
            note("SIM scenario aiPeaceOfferDuration=passed")
            // A peace proposal is not itself a signed treaty: neither side
            // should leave the war until the request is accepted.
            check(aggressor.isAtWarWith(defender) && defender.isAtWarWith(aggressor)) {
                "SIM AI peace proposal ended the war without acceptance"
            }
            note("SIM scenario aiPeaceOfferPending=passed")
            // The offer must be addressed to the opposing civilization and
            // identify the aggressor as the requesting civilization.
            check(defender.tradeRequests.any {
                it.requestingCiv == aggressor.civID && it.trade.isPeaceTreaty()
            }) { "SIM generated peace offer missing from defender inbox" }
            note("SIM scenario aiPeaceOfferDelivery=passed")
            // Accept the generated proposal through production trade logic,
            // not by calling makePeace directly.
            val acceptance = com.unciv.logic.trade.TradeLogic(defender, aggressor)
            acceptance.currentTrade.ourOffers.addAll(actualOffer.trade.ourOffers)
            acceptance.currentTrade.theirOffers.addAll(actualOffer.trade.theirOffers)
            acceptance.acceptTrade()
            check(!defender.isAtWarWith(aggressor) && !aggressor.isAtWarWith(defender)) {
                "SIM accepted AI peace proposal did not resolve bilateral war"
            }
            note("SIM scenario aiPeaceOfferAcceptance=passed")
            val acceptedDefenderManager = defender.getDiplomacyManager(aggressor)!!
            val acceptedAggressorManager = aggressor.getDiplomacyManager(defender)!!
            check(acceptedDefenderManager.turnsToPeaceTreaty() > 0)
            check(acceptedAggressorManager.turnsToPeaceTreaty() > 0)
            check(!acceptedDefenderManager.canDeclareWar() && !acceptedAggressorManager.canDeclareWar()) {
                "SIM accepted AI peace treaty failed to protect both civilizations"
            }
            note("SIM scenario aiPeaceAcceptanceTreatyGuard=passed")
            // Confirm accepting one bilateral peace proposal does not
            // accidentally terminate unrelated wars from the same scenario.
            check(aggressor.getCivsAtWarWith().isNotEmpty()) {
                "SIM bilateral peace unexpectedly ended every multi-front war"
            }
            note("SIM scenario aiPeacePreservesOtherWars=passed")
            // Ensure accepting the treaty does not erase the pending
            // diplomatic relationship with the original defensive ally.
            check(!defender.isAtWarWith(ally)) {
                "SIM accepting AI peace unexpectedly created a war with defender's ally"
            }
            check(defender.getDiplomacyManager(aggressor)!!.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            check(aggressor.getDiplomacyManager(defender)!!.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            note("SIM scenario aiPeaceStatusSymmetry=passed")
            // The accepted agreement must also survive diplomacy-manager
            // cloning, as used by game-state snapshots.
            val savedDefenderDiplomacy = acceptedDefenderManager.clone()
            val savedAggressorDiplomacy = acceptedAggressorManager.clone()
            check(savedDefenderDiplomacy.turnsToPeaceTreaty() ==
                acceptedDefenderManager.turnsToPeaceTreaty())
            check(savedAggressorDiplomacy.turnsToPeaceTreaty() ==
                acceptedAggressorManager.turnsToPeaceTreaty())
            check(savedDefenderDiplomacy.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            check(savedAggressorDiplomacy.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.Peace)
            note("SIM scenario acceptedPeaceSnapshot=passed")
            // A negotiated peace must not demand the opponent's capital or
            // final city as reparations.
            val cityConcessions = actualOffer.trade.ourOffers.filter {
                it.type == com.unciv.logic.trade.TradeOfferType.City
            } + actualOffer.trade.theirOffers.filter {
                it.type == com.unciv.logic.trade.TradeOfferType.City
            }
            check(cityConcessions.size <= 1) { "SIM AI peace offer demands multiple cities" }
            check(cityConcessions.none {
                it.name == defender.getCapital()?.id || it.name == aggressor.getCapital()?.id
            }) { "SIM AI peace offer demands a capital" }
            note("SIM scenario aiPeaceCityConcessionSafeguards=passed")

            // Exercise expiry and snapshot isolation without mutating the
            // accepted agreement in the live game.
            val snapshotTreaty = savedDefenderDiplomacy.trades.flatMap {
                it.ourOffers + it.theirOffers
            }.firstOrNull { it.name == Constants.peaceTreaty && it.duration > 0 }
            check(snapshotTreaty != null) { "SIM cloned peace agreement has no active treaty" }
            val liveTreatyTurns = acceptedDefenderManager.turnsToPeaceTreaty()
            snapshotTreaty.duration = 0
            check(acceptedDefenderManager.turnsToPeaceTreaty() == liveTreatyTurns) {
                "SIM editing snapshot mutated live peace agreement"
            }
            check(savedDefenderDiplomacy.turnsToPeaceTreaty() >= 0)
            note("SIM scenario acceptedPeaceSnapshotIsolation=passed")

            // The AI peace agreement must not silently dissolve a separate
            // alliance or terminate a third party's ongoing war.
            check(defender.getDiplomacyManager(ally)!!.diplomaticStatus ==
                com.unciv.logic.civilization.diplomacy.DiplomaticStatus.DefensivePact) {
                "SIM bilateral AI peace unexpectedly dissolved defensive pact"
            }
            check(ally.isAtWarWith(aggressor)) {
                "SIM bilateral AI peace unexpectedly ended ally's separate war"
            }
            note("SIM scenario aiPeacePreservesDefensiveAlliance=passed")
















        }
        var previousWarPairs: Set<String> = emptySet()
        var observedWarStart = false
        var observedWarEnd = false
        var observedDefensivePact = false
        var observedDefensivePactExpiry = false
        val previousPactFlags = mutableMapOf<String, Int>()
        for (turn in 1..turns) {
            val t0 = System.nanoTime()
            info.nextTurn()
            val seconds = (System.nanoTime() - t0) / 1e9
            say(String.format(Locale.ROOT, "OJH turn %d %.6f", turn, seconds))
            // Supplemental diagnostics on stderr preserve the OJH stdout protocol.
            // These are observations of real AI game turns, not forced diplomatic scenarios.
            val warPairs = info.civilizations.flatMap { civ ->
                civ.diplomacy.filterValues {
                    it.diplomaticStatus == com.unciv.logic.civilization.diplomacy.DiplomaticStatus.War
                }.keys.map { other -> listOf(civ.civName, other).sorted().joinToString("|") }
            }.toSet()
            note("SIM diplomacy turn=$turn warPairs=${warPairs.size}")
            val started = warPairs - previousWarPairs
            val ended = previousWarPairs - warPairs
            if (started.isNotEmpty()) observedWarStart = true
            if (ended.isNotEmpty()) observedWarEnd = true
            note("SIM transitions turn=$turn warsStarted=${started.size} warsEnded=${ended.size}")
            // Diagnostic: a one-turn difference in defensive pact flags may be caused
            // by sequential civilization turn processing rather than a broken pact.
            val activePacts = info.civilizations.flatMap { civ ->
                civ.diplomacy.mapNotNull { (otherName, manager) ->
                    val flag = manager.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)
                    if (flag > 0) "${civ.civName}>$otherName:$flag" else null
                }
            }
            if (activePacts.isNotEmpty()) note("SIM defensivePacts turn=$turn entries=${activePacts.sorted().joinToString(",")}")
            // Observe natural pact expiry without claiming a forced scenario.
            for (civ in info.civilizations) {
                for ((otherName, manager) in civ.diplomacy) {
                    val key = "${civ.civName}|$otherName"
                    val remaining = manager.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)
                    val previous = previousPactFlags[key] ?: 0
                    if (remaining > 0) observedDefensivePact = true
                    if (previous > 0 && remaining == 0) {
                        observedDefensivePactExpiry = true
                        note("SIM pactExpiry turn=$turn civ=${civ.civName} other=$otherName previous=$previous")
                    }
                    previousPactFlags[key] = remaining
                }
            }
            // Preserve the full context of the first asymmetric pact observation.
            // This is diagnostic evidence, not a replacement for the invariant.
            for (civ in info.civilizations) {
                for ((otherName, manager) in civ.diplomacy) {
                    val other = info.civilizations.firstOrNull { it.civName == otherName } ?: continue
                    val reverse = other.diplomacy[civ.civName] ?: continue
                    val defensive = com.unciv.logic.civilization.diplomacy.DiplomaticStatus.DefensivePact
                    if ((manager.diplomaticStatus == defensive) != (reverse.diplomaticStatus == defensive)) {
                        note("SIM pactMismatch turn=$turn civ=${civ.civName} other=$otherName " +
                            "forwardStatus=${manager.diplomaticStatus} reverseStatus=${reverse.diplomaticStatus} " +
                            "forwardFlag=${manager.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)} " +
                            "reverseFlag=${reverse.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)}")
                    }
                }
            }
            // Enforce reciprocal diplomacy state for actual simulated civilizations.
            // Defensive pacts and war declarations must agree on both sides.
            for (civ in info.civilizations) {
                for ((otherName, manager) in civ.diplomacy) {
                    val other = info.civilizations.firstOrNull { it.civName == otherName } ?: continue
                    val reciprocal = other.diplomacy[civ.civName] ?: continue
                    val war = com.unciv.logic.civilization.diplomacy.DiplomaticStatus.War
                    val defensive = com.unciv.logic.civilization.diplomacy.DiplomaticStatus.DefensivePact
                    check((manager.diplomaticStatus == war) == (reciprocal.diplomaticStatus == war)) {
                        "SIM diplomacy asymmetrical war state turn=$turn civ=${civ.civName} other=$otherName"
                    }
                    check((manager.diplomaticStatus == defensive) == (reciprocal.diplomaticStatus == defensive)) {
                        "SIM diplomacy asymmetrical defensive pact turn=$turn civ=${civ.civName} other=$otherName forward=${manager.diplomaticStatus} reverse=${reciprocal.diplomaticStatus} forwardFlag=${manager.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)} reverseFlag=${reciprocal.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DefensivePact)}"
                    }
                }
            }
            previousWarPairs = warPairs
        }

        note("SIM coverage warStartObserved=$observedWarStart warEndObserved=$observedWarEnd")
        note("SIM coverage defensivePactObserved=$observedDefensivePact defensivePactExpiryObserved=$observedDefensivePactExpiry")
        // Unciv's own Concurrency pool is not daemon, so without this the JVM
        // sits for a minute after the last turn with nothing left to do.
        System.out.flush()
        kotlin.system.exitProcess(0)
    }

    /**
     * A game played entirely by the game's own AI, on `mapPath` when one is given.
     *
     * The settings are the ones OJH's outside driver already uses, so a number
     * from here is comparable with every number it has published: King, Quick,
     * no barbarians, player order unshuffled, and the ruleset's major
     * civilizations as AI. Unciv requires a human -- `setTransients` does
     * `civilizations.first { it.isHuman() }` -- so a Spectator is that human.
     * It takes no turn, so one nextTurn() is still one full round of AI.
     * Players asked for above the number of major civilizations become city
     * states, which is what the outside driver does too.
     */
    private fun start(players: Int, size: String, mapPath: String?, seed: Long): GameInfo {
        val setup = GameSetupInfo()
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!

        val chosen = ArrayList<Player>()
        for (nation in ruleset.nations.values) {
            if (chosen.size >= players) break
            if (!nation.isMajorCiv) continue
            chosen.add(Player(nation.name, PlayerType.AI))
        }
        val cityStates = (players - chosen.size).coerceAtLeast(0)
        chosen.add(Player(Constants.spectator, PlayerType.Human))

        setup.gameParameters.apply {
            baseRuleset = ruleset.name
            difficulty = "King"
            speed = "Quick"
            noBarbarians = true
            numberOfCityStates = cityStates
            shufflePlayerOrder = false
            this.players = chosen
        }
        note("${chosen.size - 1} AI civilizations, $cityStates city states, 1 spectator")

        if (mapPath != null) {
            val file = File(mapPath)
            require(file.isFile) { "no map at $mapPath" }
            // Any map Unciv's own editor can open, including one converted from
            // another game, so every game in a comparison plays one world.
            setup.mapFile = Gdx.files.absolute(file.absolutePath)
            setup.mapParameters.type = MapGeneratedMainType.custom
            setup.mapParameters.name = file.name
            val preview = MapSaver.loadMapParameters(setup.mapFile!!)
            note("map ${file.name}: ${preview.mapSize.width}x${preview.mapSize.height}, ${preview.shape}")
        } else {
            setup.mapParameters.type = MapType.pangaea
            setup.mapParameters.mapSize = when (size) {
                "tiny" -> MapSize.Tiny
                "small" -> MapSize.Small
                "medium" -> MapSize.Medium
                "large" -> MapSize.Large
                "huge" -> MapSize.Huge
                else -> throw IllegalArgumentException("unknown --size $size")
            }
            if (seed != 0L) setup.mapParameters.seed = seed
        }

        return GameStarter.startNewGame(setup)
    }

    // ------------------------------------------------------------------ fps

    /**
     * Frame rate, in the real window.
     *
     * The outside driver does this too, and does it well -- but it reaches the
     * research screen by trying constructors by reflection until one of them
     * takes, because from outside there is no other way to open a screen. From
     * here it is `TechPickerScreen(civ)`, so the heaviest panel is the panel it
     * actually measures rather than whichever one happened to construct.
     */
    private fun fps(players: Int, size: String, mapPath: String?, seed: Long,
                    seconds: Double, lateTurns: Int) {
        // Headless AWT, as desktop/build.gradle.kts already asks for. GLFW owns
        // the process's NSApplication and polls it from the first thread, which
        // is why this mode needs -XstartOnFirstThread; the first use of AWT
        // (FontDesktop rasterising a glyph) queues -[NSApplication run] onto
        // that same run loop, glfwPollEvents runs it, and it never returns --
        // the window stays up at 0% CPU with nothing timed. Set here as well as
        // in the Gradle task so a plain `java -cp ... OjhBenchmark fps` is
        // correct, and set before anything can load AWT; glyph rasterisation
        // needs only Font and BufferedImage, which work headless.
        System.setProperty("java.awt.headless", "true")

        // UncivGame.create asks Display for the screen mode, and the desktop
        // launcher is what normally sets it. Every method on the interface has
        // a default, so an empty one is a complete one: this is measuring frame
        // rate in a window, not exercising screen modes.
        Display.platform = object : PlatformDisplay {}

        val config = Lwjgl3ApplicationConfiguration()
        config.setTitle("Unciv - OJH")
        config.setWindowedMode(1600, 900)
        config.useVsync(false)            // a cap is a measurement of the cap
        config.setForegroundFPS(0)
        // Without this the window stops rendering the moment anything else is
        // clicked, and a frame rate measured on a paused window is zero.
        config.setPauseWhenLostFocus(false)
        // libGDX sleeps 1000/idleFPS ms on an iteration where no window drew.
        // With continuous rendering on below, every iteration draws and this
        // never fires -- it is here so that a frame the game chooses not to
        // draw costs 0.1 ms rather than 16.
        config.setIdleFPS(10000)
        config.setPauseWhenMinimized(false)
        config.setAutoIconify(false)
        // macOS gives a covered window about one frame a second, so it has to
        // be in front of whatever else is on screen.
        Lwjgl3Application(FpsRun(players, size, mapPath, seed, seconds, lateTurns), config)
    }

    private class FpsRun(
        private val players: Int,
        private val size: String,
        private val mapPath: String?,
        private val seed: Long,
        private val seconds: Double,
        private val lateTurns: Int
    ) : UncivGame(false) {

        private var stage = 0
        private var last = 0L
        private var sceneName: String? = null
        private var sceneEnd = 0L
        private var warmup = 0
        private val frames = ArrayList<Double>()
        private val reported = HashSet<String>()
        private var started: GameInfo? = null
        private var lateGame: GameInfo? = null
        private var turnsDone = false
        private var failure: String? = null

        private fun begin(name: String, warmupFrames: Int) {
            sceneName = name
            frames.clear()
            warmup = warmupFrames
            sceneEnd = 0
        }

        private fun elapsed(now: Long): Boolean {
            if (sceneEnd == 0L) sceneEnd = now + (seconds * 1e9).toLong()
            return now >= sceneEnd
        }

        private fun report() {
            val name = sceneName ?: return
            sceneName = null
            if (frames.size < 10) {
                OjhBenchmark.say("OJH noscene $name too few frames were drawn to time")
                reported.add(name)
                return
            }
            val sorted = frames.sorted()
            val total = frames.sum()
            fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()] * 1000.0
            // The 1% low is the mean of the slowest hundredth, which is what a
            // player feels as a stutter -- not the single worst frame.
            val worst = sorted.subList((sorted.size * 99) / 100, sorted.size)
            val low = if (worst.isEmpty()) 0.0 else 1.0 / (worst.sum() / worst.size)
            OjhBenchmark.say(String.format(Locale.ROOT, "OJH scene %s %d %.5f %.3f %.3f %.3f %.2f",
                name, frames.size, total, pct(0.50), pct(0.95), pct(0.99), low))
            reported.add(name)
        }

        private fun holder() = (screen as WorldScreen).mapHolder

        private fun load(info: GameInfo) {
            Concurrency.run("OJH load") { loadGame(info) }
        }

        override fun create() {
            // What DesktopLauncher does before the game starts, and what
            // FasterUIDevelopment does for the same reason: the real font
            // rasteriser, because a stub one would be measuring a different
            // thing being drawn.
            Fonts.fontImplementation = FontDesktop()
            super.create()

            // After super.create(), not before: UncivGame.create() ends with
            // `Gdx.graphics.isContinuousRendering = settings.continuousRendering`
            // and that setting defaults to false, so anything set earlier is
            // overwritten. Without it libGDX draws on demand and the scene
            // machine only advances on the frames a posted runnable happens to
            // force. An unfocused window on macOS gets about one frame a
            // second, which measures macOS rather than this game.
            Gdx.graphics.setContinuousRendering(true)
            (Gdx.graphics as Lwjgl3Graphics).window.focusWindow()
        }

        override fun render() {
            val now = System.nanoTime()
            super.render()
            if (sceneName != null && last != 0L) {
                if (warmup > 0) warmup-- else frames.add((now - last) / 1e9)
            }
            last = now
            if (failure != null) return finish(failure)
            advance(now)
        }

        private fun advance(now: Long) {
            when (stage) {
                0 -> {
                    if (screen is LanguagePickerScreen) {
                        settings.language = "English"
                        settings.updateLocaleFromLanguage()
                        settings.isFreshlyCreated = false
                        goToMainMenu()
                        return
                    }
                    if (screen is MainMenuScreen) {
                        val gl = Gdx.graphics.glVersion
                        OjhBenchmark.say("OJH renderer ${gl.rendererString}, OpenGL "
                            + "${gl.majorVersion}.${gl.minorVersion} via LWJGL3")
                        OjhBenchmark.say("OJH resolution ${Gdx.graphics.backBufferWidth}x${Gdx.graphics.backBufferHeight}")
                        OjhBenchmark.say("OJH vsync off")
                        begin("menu", 60)
                        stage = 1
                    }
                }
                1 -> if (elapsed(now)) {
                    report()
                    started = OjhBenchmark.start(players, size, mapPath, seed)
                    // Frame rate depends on how much world is on screen, so the
                    // run has to record the world: civ count, tile count, and
                    // whether fog of war hides most of it.
                    OjhBenchmark.say("OJH players ${started!!.civilizations.count {
                        it.playerType == PlayerType.AI && !it.isBarbarian }}")
                    OjhBenchmark.say("OJH regions ${started!!.tileMap.values.size} tiles")
                    OjhBenchmark.say("OJH view ${OjhBenchmark.view(started!!)}")
                    load(started!!)
                    stage = 2
                }
                2 -> if (screen is WorldScreen) { begin("map-start", 90); stage = 3 }
                3 -> if (elapsed(now)) {
                    report(); holder().zoom(holder().minZoom); begin("map-out", 60); stage = 4
                }
                4 -> if (elapsed(now)) {
                    report(); holder().zoom(holder().maxZoom); begin("map-in", 60); stage = 5
                }
                5 -> if (elapsed(now)) {
                    report(); holder().zoom(1f); begin("map-pan", 60); stage = 6
                }
                6 -> {
                    val h = holder()
                    h.scrollX += 6f
                    h.updateVisualScroll()
                    if (elapsed(now)) {
                        report()
                        val world = screen as WorldScreen
                        val civ = started!!.civilizations.firstOrNull { it.playerType == PlayerType.Human }
                        if (civ == null) {
                            OjhBenchmark.say("OJH noscene panel the game has no human civilization to open it for")
                            reported.add("panel")
                            stage = 8
                        } else {
                            // The research tree, by name: it is the heaviest
                            // information screen this game has.
                            replaceCurrentScreen { TechPickerScreen(civ) }
                            begin("panel", 60)
                            lateGame = null
                            panelReturn = world
                            stage = 7
                        }
                    }
                }
                7 -> if (elapsed(now)) {
                    report(); replaceCurrentScreen { panelReturn!! }; stage = 8
                }
                8 -> {
                    val clone = started!!.clone()
                    clone.setTransients()
                    Concurrency.run("OJH turns") {
                        try {
                            repeat(lateTurns) { clone.nextTurn() }
                            lateGame = clone
                            turnsDone = true
                        } catch (e: Throwable) {
                            failure = "playing turns failed: $e"
                        }
                    }
                    begin("end-turn", 10)
                    stage = 9
                }
                9 -> if (turnsDone) { report(); load(lateGame!!); stage = 10 }
                10 -> if (screen is WorldScreen) { begin("map-late", 90); stage = 11 }
                11 -> if (elapsed(now)) { report(); finish(null) }
            }
        }

        /** The world screen to come back to after the panel scene. */
        private var panelReturn: WorldScreen? = null

        private fun finish(why: String?) {
            for (scene in scenesOf()) {
                if (!reported.contains(scene))
                    OjhBenchmark.say("OJH noscene $scene ${why ?: "not reached"}")
            }
            System.out.flush()
            exitProcess(0)
        }

        /** Every scene OJH times. One a game has not got is `noscene`, never zero. */
        private fun scenesOf() = listOf(
            "menu", "map-start", "map-out", "map-in", "map-pan", "panel", "end-turn", "map-late")
    }

}
