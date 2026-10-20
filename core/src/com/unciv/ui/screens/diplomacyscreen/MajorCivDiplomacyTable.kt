package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.*
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.ConfirmPopup
import kotlin.math.roundToInt

class MajorCivDiplomacyTable(private val diplomacyScreen: DiplomacyScreen) {
    val viewingCiv = diplomacyScreen.viewingCiv

    fun getMajorCivDiplomacyTable(otherCiv: Civilization): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)!!

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        val helloText: String
        val helloVoice: String
        if (otherCivDiplomacyManager.isRelationshipLevelLE(RelationshipLevel.Enemy)) {
            helloText = otherCiv.nation.hateHello
            helloVoice = "${otherCiv.civName}.hateHello"
        } else {
            helloText = otherCiv.nation.neutralHello
            helloVoice = "${otherCiv.civName}.neutralHello"
        }
        val leaderIntroTable = LeaderIntroTable(otherCiv, helloText)
        diplomacyTable.add(leaderIntroTable).row()
        diplomacyTable.addSeparator()

        val diplomaticRelationshipsCanChange =
            !viewingCiv.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)!!

        if (!viewingCiv.isAtWarWith(otherCiv)) {
            diplomacyTable.add(getTradeButton(otherCiv)).row()


            if (!diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                diplomacyTable.add(getDeclareFriendshipButton(otherCiv)).row()

            if (!diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)
                && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            ) diplomacyTable.add(getDenounceButton(otherCiv, diplomacyManager)).row()

            if (diplomaticRelationshipsCanChange)
                diplomacyTable.add(diplomacyScreen.getDeclareWarButton(diplomacyManager, otherCiv)).row()

        } else if (diplomaticRelationshipsCanChange) {
            val negotiatePeaceButton =
                getNegotiatePeaceMajorCivButton(otherCiv, otherCivDiplomacyManager)

            diplomacyTable.add(negotiatePeaceButton).row()
        }


        // Show both directions: an embassy established by the other nation is not
        // necessarily the same as the embassy our civilization has established.
        val foreignEmbassyPresent =
            otherCivDiplomacyManager.hasModifier(DiplomaticModifiers.EstablishedEmbassy) ||
            otherCivDiplomacyManager.hasModifier(DiplomaticModifiers.SharedEmbassies)
        val foreignEmbassyStatus = if (foreignEmbassyPresent) {
            val foreignAmbassador = otherCivDiplomacyManager.ambassadorName
            if (foreignAmbassador.isNullOrBlank()) "Their embassy: Established (no ambassador)"
            else "Their embassy: Established — Ambassador: $foreignAmbassador"
        } else "Their embassy: Not established"
        diplomacyTable.add(foreignEmbassyStatus.toLabel(Color.LIGHT_GRAY)).row()

        // Living Empires: expose the appointed representative in the existing diplomacy screen.
        // Embassy eligibility is owned by DiplomacyManager, not duplicated in the UI.
        if (diplomacyManager.hasModifier(DiplomaticModifiers.EstablishedEmbassy)
            || diplomacyManager.hasModifier(DiplomaticModifiers.SharedEmbassies)
        ) {
            val representative = diplomacyManager.ambassadorName
            val embassyLabel = if (representative == null)
                "Embassy: No ambassador appointed"
            else "Ambassador: $representative"
            diplomacyTable.add(embassyLabel.toLabel(Color.LIGHT_GRAY)).row()

            val appointButton = "Appoint Ambassador".toTextButton()
            appointButton.onClick {
                AskTextPopup(
                    diplomacyScreen,
                    label = "Ambassador name",
                    defaultText = diplomacyManager.ambassadorName ?: "",
                    maxLength = 60,
                    validate = { it.trim().isNotEmpty() && it.trim().length <= 60 },
                    actionOnOk = { name ->
                        if (diplomacyManager.appointAmbassador(name))
                            diplomacyScreen.updateRightSide(otherCiv)
                    }
                ).open()
            }
            if (diplomacyScreen.isNotPlayersTurn()) appointButton.disable()
            diplomacyTable.add(appointButton).row()

            val withdrawButton = "Withdraw Our Embassy".toTextButton()
            withdrawButton.onClick {
                ConfirmPopup(diplomacyScreen, "Withdraw our embassy from [${otherCiv.civName}]?", "Withdraw Embassy") {
                    if (diplomacyManager.withdrawEmbassy())
                        diplomacyScreen.updateRightSide(otherCiv)
                }.open()
            }
            if (diplomacyScreen.isNotPlayersTurn()) withdrawButton.disable()
            diplomacyTable.add(withdrawButton).row()

            val missionButton = "Goodwill Mission (50 Gold, 5 turns)".toTextButton()
            missionButton.onClick {
                if (diplomacyManager.startAmbassadorGoodwillMission())
                    diplomacyScreen.updateRightSide(otherCiv)
            }
            if (diplomacyScreen.isNotPlayersTurn() ||
                !diplomacyManager.canStartAmbassadorGoodwillMission())
                missionButton.disable()
            diplomacyTable.add(missionButton).row()
            val tradeDelegationButton = "Trade Delegation (75 Gold, 4 turns)".toTextButton()
            tradeDelegationButton.onClick {
                if (diplomacyManager.startAmbassadorTradeDelegation())
                    diplomacyScreen.updateRightSide(otherCiv)
            }
            if (diplomacyScreen.isNotPlayersTurn() ||
                !diplomacyManager.canStartAmbassadorTradeDelegation())
                tradeDelegationButton.disable()
            diplomacyTable.add(tradeDelegationButton).row()
            val intelligenceButton = "Intelligence Briefing (40 Gold, 4 turns)".toTextButton()
            intelligenceButton.onClick {
                if (diplomacyManager.startAmbassadorIntelligenceBriefing())
                    diplomacyScreen.updateRightSide(otherCiv)
            }
            if (diplomacyScreen.isNotPlayersTurn() ||
                !diplomacyManager.canStartAmbassadorIntelligenceBriefing())
                intelligenceButton.disable()
            diplomacyTable.add(intelligenceButton).row()
            if (diplomacyManager.ambassadorMissionTurns > 0) {
                val missionName = when (diplomacyManager.ambassadorMissionType) {
                    "TradeDelegation" -> "Trade Delegation"
                    "IntelligenceBriefing" -> "Intelligence Briefing"
                    else -> "Goodwill Mission"
                }
                diplomacyTable.add("$missionName: ${diplomacyManager.ambassadorMissionTurns} turns remaining".toLabel(Color.LIGHT_GRAY)).row()
            }
            val demandButton = "Demand Tribute (25 Gold)".toTextButton()
            demandButton.onClick {
                diplomacyManager.demandDiplomaticTribute()
                diplomacyScreen.updateRightSide(otherCiv)
            }
            if (diplomacyScreen.isNotPlayersTurn() ||
                !diplomacyManager.canDemandDiplomaticTribute())
                demandButton.disable()
            diplomacyTable.add(demandButton).row()
            if (diplomacyManager.diplomaticDemandCooldown > 0)
                diplomacyTable.add("Demand cooldown: ${diplomacyManager.diplomaticDemandCooldown} turns".toLabel(Color.LIGHT_GRAY)).row()
        }

        // Trust is directional: this is how the other civilization evaluates us.
        // The score is derived from existing diplomatic betrayal history.
        val theirTrust = otherCivDiplomacyManager.trustScore()
        val trustDescription = when {
            theirTrust < 20f -> "Very low"
            theirTrust < 40f -> "Low"
            theirTrust < 60f -> "Neutral"
            theirTrust < 80f -> "High"
            else -> "Very high"
        }
        diplomacyTable.add("Their trust in us: $trustDescription (${theirTrust.toInt()}/100)"
            .toLabel(Color.LIGHT_GRAY)).row()

        // Explain the source of lost trust using the same persisted history as the AI.
        val trustCauses = listOf(
            DiplomaticModifiers.BetrayedDeclarationOfFriendship to "Broken friendship",
            DiplomaticModifiers.BetrayedDefensivePact to "Betrayed defensive pact",
            DiplomaticModifiers.BetrayedPromiseToNotSettleCitiesNearUs to "Broken settlement promise",
            DiplomaticModifiers.BetrayedPromiseToNotSpreadReligionToUs to "Broken religious promise",
            DiplomaticModifiers.BetrayedPromiseToNotSendingSpiesToUs to "Broken espionage promise",
            DiplomaticModifiers.BetrayedPromiseToNotAttackUs to "Broken non-aggression promise",
            DiplomaticModifiers.BetrayedPromiseToNotAttackOtherCiv to "Broken war promise",
            DiplomaticModifiers.DeclaredWarOnUs to "Declared war"
        )
        val activeCauses = trustCauses.filter { (modifier, _) ->
            (otherCivDiplomacyManager.diplomaticModifiers[modifier.name] ?: 0f) < 0f
        }
        if (activeCauses.isNotEmpty()) {
            diplomacyTable.add("Trust damaged by: ${activeCauses.joinToString { it.second }}"
                .toLabel(Color.LIGHT_GRAY)).row()
        }

        val demandsButton = "Demands".toTextButton()
        demandsButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(getDemandsTable(viewingCiv, otherCiv))
        }
        diplomacyTable.add(demandsButton).row()
        if (diplomacyScreen.isNotPlayersTurn()) demandsButton.disable()

        if (otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.getCenterTile()))
            diplomacyTable.add(diplomacyScreen.getGoToOnMapButton(otherCiv)).row()

        if (otherCiv.isHuman())
            diplomacyTable.add(diplomacyScreen.getHumanRelationshipTable(otherCivDiplomacyManager)).row()
        else {
            diplomacyTable.add(diplomacyScreen.getRelationshipTable(otherCivDiplomacyManager)).row()
            diplomacyTable.add(getDiplomacyModifiersTable(otherCivDiplomacyManager)).row()
        }
        val promisesTable = getPromisesTable(diplomacyManager, otherCivDiplomacyManager)
        if (promisesTable != null) diplomacyTable.add(promisesTable).row()

        // Starting playback here assumes the MajorCivDiplomacyTable is shown immediately
        UncivGame.Current.musicController.playVoice(helloVoice)

        return diplomacyTable
    }


    private fun getNegotiatePeaceMajorCivButton(
        otherCiv: Civilization,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton {
        val negotiatePeaceButton = "Negotiate Peace".toTextButton()
        negotiatePeaceButton.onClick {
            val tradeTable = diplomacyScreen.setTrade(otherCiv)
            val peaceTreaty = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
            tradeTable.tradeView.theirStagedOffers().add(peaceTreaty)
            tradeTable.tradeView.ourStagedOffers().add(peaceTreaty)
            tradeTable.offerColumnsTable.update()
            tradeTable.enableOfferButton(true)
        }

        if (diplomacyScreen.isNotPlayersTurn()) negotiatePeaceButton.disable()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
            negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared
            val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
            negotiatePeaceButton.setText(negotiatePeaceButton.text.toString() + "\n${turnsLeft.tr()}" + Fonts.turn)
        }
        return negotiatePeaceButton
    }

    private fun getDenounceButton(
        otherCiv: Civilization,
        diplomacyManager: DiplomacyManager
    ): TextButton {
        val denounceButton = "Denounce ([30] turns)".toTextButton()
        denounceButton.onClick {
            ConfirmPopup(diplomacyScreen, "Denounce [${otherCiv.civName}]?", "Denounce ([30] turns)") {
                diplomacyManager.denounce()
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.setRightSideFlavorText(
                    otherCiv,
                    if (otherCiv.nation.denounced.isNotEmpty()) otherCiv.nation.denounced else "We will remember this.",
                    "Very well."
                )

                val music = UncivGame.Current.musicController
                music.playVoice("${otherCiv.nation.name}.denounced")
            }.open()
        }
        if (diplomacyScreen.isNotPlayersTurn()) denounceButton.disable()
        return denounceButton
    }

    private fun getDeclareFriendshipButton(otherCiv: Civilization): TextButton {
        val declareFriendshipButton =
            "Offer Declaration of Friendship ([30] turns)".toTextButton()
        declareFriendshipButton.onClick {
            otherCiv.popupAlerts.add(
                PopupAlert(
                    AlertType.DeclarationOfFriendship,
                    viewingCiv.civID
                )
            )
            declareFriendshipButton.disable()
        }
        if (diplomacyScreen.isNotPlayersTurn() || otherCiv.popupAlerts
                .any { it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civID }
        )
            declareFriendshipButton.disable()
        return declareFriendshipButton
    }

    private fun getTradeButton(otherCiv: Civilization): TextButton {
        val tradeButton = "Trade".toTextButton()
        tradeButton.onClick {
            diplomacyScreen.setTrade(otherCiv).apply {
                offerColumnsTable.update()
            }
        }
        if (diplomacyScreen.isNotPlayersTurn()) tradeButton.disable()
        return tradeButton
    }

    private fun getPromisesTable(
        diplomacyManager: DiplomacyManager,
        otherCivDiplomacyManager: DiplomacyManager
    ): Table? {
        val promisesTable = Table()

        for (demand in Demand.entries){
            if (otherCivDiplomacyManager.hasFlag(demand.agreedToDemand)) {
                val turnsLeft = otherCivDiplomacyManager.getFlag(demand.agreedToDemand)
                val text = demand.wePromisedText.fillPlaceholders(turnsLeft.toString())
                promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
            }
            if (diplomacyManager.hasFlag(demand.agreedToDemand)) {
                val turnsLeft = diplomacyManager.getFlag(demand.agreedToDemand)
                val text = demand.theyPromisedText.fillPlaceholders(turnsLeft.toString())
                promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
            }
        }

        return if (promisesTable.cells.isEmpty) null else promisesTable
    }

    private fun getDiplomacyModifiersTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val diplomacyModifiersTable = Table()
        for (modifier in otherCivDiplomacyManager.diplomaticModifiers) {
            // Angry about attacked CS and destroyed CS do not stack
            if (modifier.key == DiplomaticModifiers.AttackedProtectedMinor.name
                && otherCivDiplomacyManager.hasModifier(DiplomaticModifiers.DestroyedProtectedMinor))
                continue

            val diplomaticModifier = DiplomaticModifiers.safeValueOf(modifier.key)
                ?: continue // This modifier is from the future, you cannot understand it yet
            var text = diplomaticModifier.text.tr() + " "
            if (modifier.value > 0) text += "+"
            text += modifier.value.roundToInt()
            val color = if (modifier.value < 0) Color.RED else Color.GREEN
            diplomacyModifiersTable.add(text.toLabel(color)).row()
        }
        return diplomacyModifiersTable
    }

    private fun getDemandsTable(viewingCiv: Civilization, otherCiv: Civilization): Table {
        val demandsTable = Table()
        demandsTable.defaults().pad(10f)

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)!!

        for (demand in Demand.entries) {
            if (!demand.show(viewingCiv))
                continue

            val button = demand.demandText.toTextButton()

            if (otherCiv.popupAlerts.any { it.type == demand.demandAlert && it.value == viewingCiv.civID } // Already demanded
                || diplomacyManager.hasFlag(demand.agreedToDemand)) { // already agreed
                button.disable()
            } else {
                button.onClick {
                    otherCiv.popupAlerts.add(PopupAlert(demand.demandAlert, viewingCiv.civID))
                    button.disable()
                }
            }
            demandsTable.add(button).row()
        }

        demandsTable.add(Constants.close.toTextButton().onClick { diplomacyScreen.updateRightSide(otherCiv) })
        return demandsTable
    }
}
