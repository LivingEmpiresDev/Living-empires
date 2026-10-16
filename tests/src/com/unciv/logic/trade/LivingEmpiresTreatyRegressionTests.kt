package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.civilization.diplomacy.LivingEmpiresTreaties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression checks for the Stage 2/3 treaty offer contract. */
class LivingEmpiresTreatyRegressionTests {
    @Test fun nonAggressionPactRetainsTwentyTurnDuration() {
        val offer = TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty,
            duration = LivingEmpiresTreaties.duration)
        assertEquals(20, offer.duration)
        assertEquals(TradeOfferType.Treaty, offer.type)
        assertTrue(offer.isTradable())
    }

    @Test fun defensiveAllianceUsesTwentyTurnContract() {
        val offer = TradeOffer(Constants.defensivePact, TradeOfferType.Treaty,
            duration = LivingEmpiresTreaties.duration)
        assertEquals(20, offer.duration)
        assertEquals(Constants.defensivePact, offer.name)
    }

    @Test fun warDeclarationIsDistinctFromTreaty() {
        val offer = TradeOffer("Other civilization", TradeOfferType.WarDeclaration, duration = -1)
        assertFalse(offer.type == TradeOfferType.Treaty)
    }
}
