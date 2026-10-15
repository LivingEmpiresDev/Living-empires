package com.unciv.logic.civilization.diplomacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeaceNegotiationPolicyTests {
    @Test fun losingSideHasMorePeacePressure() {
        val losing = PeaceNegotiationPolicy.peacePressure(20, 100, 0, 0)
        val winning = PeaceNegotiationPolicy.peacePressure(100, 20, 0, 0)
        assertTrue(losing > winning)
    }

    @Test fun additionalWarsIncreasePressure() {
        val single = PeaceNegotiationPolicy.peacePressure(50, 50, 0, 0)
        val multiple = PeaceNegotiationPolicy.peacePressure(50, 50, 2, 0)
        assertTrue(multiple > single)
    }

    @Test fun reparationsCannotExceedAvailableGold() {
        assertEquals(200, PeaceNegotiationPolicy.affordableReparations(500, 200))
        assertEquals(0, PeaceNegotiationPolicy.affordableReparations(500, -1))
        assertEquals(0, PeaceNegotiationPolicy.affordableReparations(-10, 100))
    }

    @Test fun cannotConcedeFinalCity() {
        assertFalse(PeaceNegotiationPolicy.canConcedeCity(1, 0))
        assertFalse(PeaceNegotiationPolicy.canConcedeCity(3, 2))
        assertTrue(PeaceNegotiationPolicy.canConcedeCity(3, 1))
    }
}
