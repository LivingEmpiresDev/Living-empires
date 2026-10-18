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

}
