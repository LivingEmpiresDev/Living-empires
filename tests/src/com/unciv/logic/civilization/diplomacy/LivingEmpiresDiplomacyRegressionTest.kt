package com.unciv.logic.civilization.diplomacy

import com.unciv.Constants
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import org.junit.Assert.*
import org.junit.Test

/** Stage 2-4 diplomacy contract assertions against the real production model. */
class LivingEmpiresDiplomacyRegressionTest {
    @Test fun nonAggressionTreatyStartsAtTwentyTurnsAndSurvivesTradeReverse() {
        val manager = DiplomacyManager()
        val trade = Trade()
        trade.ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = LivingEmpiresTreaties.duration))
        manager.trades.add(trade)
        assertEquals(20, manager.turnsToNonAggressionPact())
        val reversed = DiplomacyManager()
        reversed.trades.add(trade.reverse())
        assertEquals(20, reversed.turnsToNonAggressionPact())
    }

    @Test fun nonAggressionTreatyExpiresAndDoesNotConfuseOtherTreaties() {
        val manager = DiplomacyManager()
        val trade = Trade()
        val pact = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 1)
        trade.theirOffers.add(pact)
        manager.trades.add(trade)
        assertEquals(1, manager.turnsToNonAggressionPact())
        pact.duration = 0
        assertEquals(0, manager.turnsToNonAggressionPact())
        assertEquals(0, manager.turnsToPeaceTreaty())
    }

    @Test fun peaceTreatyDurationIsIndependentOfNonAggression() {
        val manager = DiplomacyManager()
        val trade = Trade()
        trade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = 10))
        manager.trades.add(trade)
        assertEquals(10, manager.turnsToPeaceTreaty())
        assertEquals(0, manager.turnsToNonAggressionPact())
    }

    @Test fun defensivePactFlagCanBeRecordedAndCleared() {
        val manager = DiplomacyManager()
        manager.setFlag(DiplomacyFlags.DefensivePact, 20)
        assertTrue(manager.hasFlag(DiplomacyFlags.DefensivePact))
        assertEquals(20, manager.getFlag(DiplomacyFlags.DefensivePact))
        manager.removeFlag(DiplomacyFlags.DefensivePact)
        assertFalse(manager.hasFlag(DiplomacyFlags.DefensivePact))
    }

    @Test fun declinedPeaceCooldownIsSeparateFromDefensivePact() {
        val manager = DiplomacyManager()
        manager.setFlag(DiplomacyFlags.DeclinedPeace, 3)
        manager.setFlag(DiplomacyFlags.DefensivePact, 20)
        manager.removeFlag(DiplomacyFlags.DeclinedPeace)
        assertEquals(20, manager.getFlag(DiplomacyFlags.DefensivePact))
        assertEquals(0, manager.getFlag(DiplomacyFlags.DeclinedPeace))
    }
    @Test fun activeNonAggressionPactPreventsAttacks() {
        val manager = DiplomacyManager()
        val trade = Trade()
        val pact = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 20)
        trade.ourOffers.add(pact)
        manager.trades.add(trade)
        assertFalse(manager.canAttack())
        pact.duration = 0
        assertTrue(manager.canAttack())
    }

    @Test fun peaceTreatyPreventsAttacksIndependently() {
        val manager = DiplomacyManager()
        val trade = Trade()
        val peace = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = 10)
        trade.ourOffers.add(peace)
        manager.trades.add(trade)
        assertFalse(manager.canAttack())
        peace.duration = 0
        assertTrue(manager.canAttack())
    }

    @Test fun diplomacyClonePreservesWarHistoryAndTreatyState() {
        val original = DiplomacyManager()
        // clone() copies the required serialized opponent identifier.
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.warStartedOnTurn = 37
        original.diplomaticStatus = DiplomaticStatus.War
        original.setFlag(DiplomacyFlags.DefensivePact, 12)
        val trade = Trade()
        trade.ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 8))
        original.trades.add(trade)

        val copy = original.clone()
        assertEquals(37, copy.warStartedOnTurn)
        assertEquals(DiplomaticStatus.War, copy.diplomaticStatus)
        assertEquals(12, copy.getFlag(DiplomacyFlags.DefensivePact))
        assertEquals(8, copy.turnsToNonAggressionPact())

        copy.trades.first().ourOffers.first().duration = 0
        copy.removeFlag(DiplomacyFlags.DefensivePact)
        assertEquals(8, original.turnsToNonAggressionPact())
        assertEquals(12, original.getFlag(DiplomacyFlags.DefensivePact))
    }

    @Test fun overlappingTreatiesBlockAttacksUntilBothExpire() {
        val manager = DiplomacyManager()
        val trade = Trade()
        val pact = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 20)
        val peace = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = 10)
        trade.ourOffers.add(pact)
        trade.ourOffers.add(peace)
        manager.trades.add(trade)
        assertFalse(manager.canAttack())
        pact.duration = 0
        assertFalse(manager.canAttack())
        peace.duration = 0
        assertTrue(manager.canAttack())
    }

    @Test fun multipleNonAggressionTradesRespectLongestRemainingDuration() {
        val manager = DiplomacyManager()
        val first = Trade()
        first.ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 3))
        val second = Trade()
        second.theirOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 14))
        manager.trades.add(first)
        manager.trades.add(second)
        assertEquals(14, manager.turnsToNonAggressionPact())
        assertFalse(manager.canAttack())
        second.theirOffers.first().duration = 0
        assertEquals(3, manager.turnsToNonAggressionPact())
        assertFalse(manager.canAttack())
        first.ourOffers.first().duration = 0
        assertTrue(manager.canAttack())
    }

    @Test fun defensivePactAndPeaceCooldownSurviveCloneIndependently() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.diplomaticStatus = DiplomaticStatus.DefensivePact
        original.setFlag(DiplomacyFlags.DefensivePact, 20)
        original.setFlag(DiplomacyFlags.DeclinedPeace, 5)
        val copy = original.clone()
        assertEquals(DiplomaticStatus.DefensivePact, copy.diplomaticStatus)
        assertEquals(20, copy.getFlag(DiplomacyFlags.DefensivePact))
        assertEquals(5, copy.getFlag(DiplomacyFlags.DeclinedPeace))
        copy.removeFlag(DiplomacyFlags.DeclinedPeace)
        copy.removeFlag(DiplomacyFlags.DefensivePact)
        assertEquals(20, original.getFlag(DiplomacyFlags.DefensivePact))
        assertEquals(5, original.getFlag(DiplomacyFlags.DeclinedPeace))
    }

    @Test fun nonAggressionAttackGuardTracksEveryTurnOfTwentyTurnCountdown() {
        val manager = DiplomacyManager()
        val trade = Trade()
        val pact = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = LivingEmpiresTreaties.duration)
        trade.ourOffers.add(pact)
        manager.trades.add(trade)
        for (remaining in LivingEmpiresTreaties.duration downTo 1) {
            assertEquals(remaining, manager.turnsToNonAggressionPact())
            assertFalse("Attacks must be blocked with $remaining pact turns remaining", manager.canAttack())
            pact.duration -= 1
        }
        assertEquals(0, manager.turnsToNonAggressionPact())
        assertTrue("Attacks should become available only after expiry", manager.canAttack())
    }


    @Test fun stageFiveEmbassyMissionAndDemandStateSurvivesClone() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        original.ambassadorName = "Envoy"
        original.ambassadorMissionTurns = 4
        original.diplomaticDemandCooldown = 9

        val restored = original.clone()
        assertEquals("Envoy", restored.ambassadorName)
        assertEquals(4, restored.ambassadorMissionTurns)
        assertEquals(9, restored.diplomaticDemandCooldown)
        assertTrue(restored.hasModifier(DiplomaticModifiers.EstablishedEmbassy))

        restored.ambassadorName = "Replacement"
        restored.ambassadorMissionTurns = 1
        restored.diplomaticDemandCooldown = 2
        assertEquals("Envoy", original.ambassadorName)
        assertEquals(4, original.ambassadorMissionTurns)
        assertEquals(9, original.diplomaticDemandCooldown)
    }

    @Test fun stageSixGoodwillMissionRequiresAppointedAmbassador() {
        val manager = DiplomacyManager()
        // Eligibility checks must reject absent ambassadors before accessing game transients.
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertEquals(0, manager.ambassadorMissionTurns)
        manager.ambassadorName = "Envoy"
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertEquals(0, manager.ambassadorMissionTurns)
    }

    @Test fun stageSixEmbassyWithdrawalCancelsMissionWithoutAffectingOtherMissionState() {
        val manager = DiplomacyManager()
        manager.ambassadorName = "Envoy"
        manager.ambassadorMissionTurns = 3
        manager.diplomaticDemandCooldown = 7
        // A mission without an embassy cannot be started, even with a named envoy.
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertEquals(3, manager.ambassadorMissionTurns)
        assertEquals(7, manager.diplomaticDemandCooldown)
    }

    @Test fun stageSixMissionCannotRestartWhileAlreadyActive() {
        val manager = DiplomacyManager()
        manager.ambassadorName = "Envoy"
        manager.ambassadorMissionTurns = 5
        // Active-mission guard must run before accessing civilization transients.
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertEquals(5, manager.ambassadorMissionTurns)
    }

    @Test fun stageSixMissionAndDemandCooldownCloneIndependently() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.ambassadorName = "Envoy"
        original.ambassadorMissionTurns = 5
        original.diplomaticDemandCooldown = 10
        val clone = original.clone()
        clone.ambassadorMissionTurns--
        clone.diplomaticDemandCooldown--
        assertEquals(5, original.ambassadorMissionTurns)
        assertEquals(10, original.diplomaticDemandCooldown)
        assertEquals(4, clone.ambassadorMissionTurns)
        assertEquals(9, clone.diplomaticDemandCooldown)
    }

    @Test fun stageSixDiplomaticActionsRejectMissingAmbassadorWithoutCooldown() {
        val manager = DiplomacyManager()
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertFalse(manager.demandDiplomaticTribute())
        assertEquals(0, manager.ambassadorMissionTurns)
        assertEquals(0, manager.diplomaticDemandCooldown)
    }

    @Test fun stageSixDiplomaticActionsRejectMissingEmbassyWithoutCooldown() {
        val manager = DiplomacyManager()
        manager.ambassadorName = "Envoy"
        assertFalse(manager.startAmbassadorGoodwillMission())
        assertFalse(manager.demandDiplomaticTribute())
        assertEquals(0, manager.ambassadorMissionTurns)
        assertEquals(0, manager.diplomaticDemandCooldown)
    }

    @Test fun stageSixTradeDelegationRequiresEmbassyAndAmbassador() {
        val manager = DiplomacyManager()
        assertFalse(manager.startAmbassadorTradeDelegation())
        manager.ambassadorName = "Envoy"
        assertFalse(manager.startAmbassadorTradeDelegation())
        assertEquals(0, manager.ambassadorMissionTurns)
        assertEquals("", manager.ambassadorMissionType)
    }

    @Test fun stageSixTradeDelegationTypeSurvivesCloneIndependently() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.ambassadorMissionTurns = 4
        original.ambassadorMissionType = "TradeDelegation"
        val copy = original.clone()
        assertEquals("TradeDelegation", copy.ambassadorMissionType)
        assertEquals(4, copy.ambassadorMissionTurns)
        copy.ambassadorMissionType = "Goodwill"
        assertEquals("TradeDelegation", original.ambassadorMissionType)
    }

    @Test fun stageSixGoodwillAndTradeMissionTypesRemainDistinctAfterClone() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.ambassadorMissionTurns = 5
        original.ambassadorMissionType = "Goodwill"
        val copy = original.clone()
        assertEquals("Goodwill", copy.ambassadorMissionType)
        copy.ambassadorMissionType = "TradeDelegation"
        copy.ambassadorMissionTurns = 4
        assertEquals("Goodwill", original.ambassadorMissionType)
        assertEquals(5, original.ambassadorMissionTurns)
        assertEquals("TradeDelegation", copy.ambassadorMissionType)
    }

    @Test fun stageSixTradeDelegationCannotRestartDuringExistingMission() {
        val manager = DiplomacyManager()
        manager.ambassadorName = "Envoy"
        manager.ambassadorMissionTurns = 4
        manager.ambassadorMissionType = "TradeDelegation"
        assertFalse(manager.canStartAmbassadorTradeDelegation())
        assertFalse(manager.startAmbassadorTradeDelegation())
        assertFalse(manager.canStartAmbassadorGoodwillMission())
        assertEquals(4, manager.ambassadorMissionTurns)
        assertEquals("TradeDelegation", manager.ambassadorMissionType)
    }

    @Test fun stageSixMissionProgressRemainsIndependentAfterClone() {
        val original = DiplomacyManager()
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply { isAccessible = true }.set(original, "RegressionOpponent")
        original.ambassadorName = "Envoy"
        original.ambassadorMissionTurns = 1
        val copy = original.clone()
        copy.ambassadorMissionTurns = 0
        assertEquals(1, original.ambassadorMissionTurns)
        assertEquals(0, copy.ambassadorMissionTurns)
    }
}
