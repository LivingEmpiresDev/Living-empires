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

}
