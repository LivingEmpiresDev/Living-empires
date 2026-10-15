package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 2 treaty regression checks. No graphics or Android device required. */
class NonAggressionPactTests {
    private fun managerWithPact(ourSide: Boolean, duration: Int = LivingEmpiresTreaties.duration): DiplomacyManager {
        val manager = DiplomacyManager()
        // The deserializer populates this field; its Kotlin setter is private.
        DiplomacyManager::class.java.getDeclaredField("otherCivName").apply {
            isAccessible = true
            set(manager, "stage2-test-opponent")
        }
        val trade = Trade()
        val offer = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = duration)
        if (ourSide) trade.ourOffers.add(offer) else trade.theirOffers.add(offer)
        manager.trades.add(trade)
        return manager
    }

    @Test fun pactStartsAtTwentyTurnsOnEitherSide() {
        assertEquals(20, LivingEmpiresTreaties.duration)
        assertEquals(20, managerWithPact(true).turnsToNonAggressionPact())
        assertEquals(20, managerWithPact(false).turnsToNonAggressionPact())
    }

    @Test fun pactCountsDownAndExpires() {
        val manager = managerWithPact(true)
        val offer = manager.trades.single().ourOffers.single()
        for (remaining in 20 downTo 1) {
            assertEquals(remaining, manager.turnsToNonAggressionPact())
            offer.duration--
        }
        assertEquals(0, manager.turnsToNonAggressionPact())
    }

    @Test fun expiredPactDoesNotBlockRenewal() {
        val manager = managerWithPact(false, duration = 0)
        assertEquals(0, manager.turnsToNonAggressionPact())
        manager.trades.add(Trade().apply {
            ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 20))
        })
        assertEquals(20, manager.turnsToNonAggressionPact())
    }

    @Test fun pactIsPreservedByDiplomacyClone() {
        val manager = managerWithPact(false)
        val clone = manager.clone()
        assertEquals(20, clone.turnsToNonAggressionPact())
        clone.trades.single().theirOffers.single().duration = 19
        assertEquals(20, manager.turnsToNonAggressionPact())
        assertEquals(19, clone.turnsToNonAggressionPact())
    }

    @Test fun noPactReturnsZero() {
        assertEquals(0, DiplomacyManager().turnsToNonAggressionPact())
    }
}
