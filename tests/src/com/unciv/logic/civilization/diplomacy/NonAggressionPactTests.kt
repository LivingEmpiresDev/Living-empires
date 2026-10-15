package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.utils.Json
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import org.junit.Assert.assertEquals
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

    @Test fun acceptedTreatyIsSymmetricAcrossBothCivilizations() {
        val first = managerWithPact(true)
        val second = DiplomacyManager()
        second.trades.add(first.trades.single().reverse())
        assertEquals(20, first.turnsToNonAggressionPact())
        assertEquals(20, second.turnsToNonAggressionPact())
        first.trades.single().ourOffers.single().duration = 12
        second.trades.single().theirOffers.single().duration = 12
        assertEquals(12, first.turnsToNonAggressionPact())
        assertEquals(12, second.turnsToNonAggressionPact())
    }

    @Test fun twentyFiveTurnTreatyLifecycleAndRenewal() {
        val first = managerWithPact(true)
        val second = DiplomacyManager()
        second.trades.add(first.trades.single().reverse())
        for (turn in 0..25) {
            val remaining = (20 - turn).coerceAtLeast(0)
            assertEquals("First civ on turn $turn", remaining, first.turnsToNonAggressionPact())
            assertEquals("Second civ on turn $turn", remaining, second.turnsToNonAggressionPact())
            if (turn < 25) {
                for (manager in listOf(first, second))
                    for (trade in manager.trades)
                        for (offer in trade.ourOffers + trade.theirOffers)
                            if (offer.type == TradeOfferType.Treaty && offer.duration > 0)
                                offer.duration--
            }
        }
        first.trades.add(Trade().apply {
            ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 20))
        })
        assertEquals(20, first.turnsToNonAggressionPact())
    }

    @Test fun reversedTradeOffersDoNotShareMutableCountdown() {
        val first = managerWithPact(true)
        val reversed = first.trades.single().reverse()
        reversed.theirOffers.single().duration = 7
        assertEquals(20, first.turnsToNonAggressionPact())
        assertEquals(7, reversed.theirOffers.single().duration)
    }

    @Test fun directAttackGateBlocksBothSidesUntilExpiry() {
        val first = managerWithPact(true)
        val second = managerWithPact(false)
        for (turn in 0..20) {
            val blocked = turn < 20
            assertEquals("Attacker A on turn $turn", blocked, !first.canAttack())
            assertEquals("Attacker B on turn $turn", blocked, !second.canAttack())
            if (turn < 20) {
                first.trades.single().ourOffers.single().duration--
                second.trades.single().theirOffers.single().duration--
            }
        }
    }

    @Test fun directWarDeclarationIsRejectedForBothTreatySides() {
        // Exercise the actual declaration entry point, not just canAttack().
        // A missing game context would throw if the call reached DeclareWar.declareWar.
        for (ourSide in listOf(true, false)) {
            val manager = managerWithPact(ourSide)
            manager.diplomaticStatus = DiplomaticStatus.Peace
            manager.declareWar()
            assertEquals(DiplomaticStatus.Peace, manager.diplomaticStatus)
            assertEquals(20, manager.turnsToNonAggressionPact())
        }
    }

    @Test fun diplomacySnapshotRetainsRemainingPactDuration() {
        // DiplomacyManager.clone() is used for game-state copies. Verify a
        // mid-treaty snapshot keeps its remaining duration independently.
        for (ourSide in listOf(true, false)) {
            val original = managerWithPact(ourSide, duration = 11)
            original.diplomaticStatus = DiplomaticStatus.Peace
            val snapshot = original.clone()
            assertEquals(11, snapshot.turnsToNonAggressionPact())
            assertEquals(DiplomaticStatus.Peace, snapshot.diplomaticStatus)
            val copiedOffer = if (ourSide) snapshot.trades.single().ourOffers.single()
                              else snapshot.trades.single().theirOffers.single()
            copiedOffer.duration = 10
            assertEquals(11, original.turnsToNonAggressionPact())
            assertEquals(10, snapshot.turnsToNonAggressionPact())
        }
    }

    @Test fun jsonRoundTripRetainsMidPactDurationOnBothSides() {
        // Exercise serialization, rather than relying on DiplomacyManager.clone().
        val json = Json()
        for (ourSide in listOf(true, false)) {
            val original = managerWithPact(ourSide, duration = 11)
            original.diplomaticStatus = DiplomaticStatus.Peace
            val encoded = json.toJson(original)
            val restored = json.fromJson(DiplomacyManager::class.java, encoded)
            assertEquals(11, restored.turnsToNonAggressionPact())
            assertEquals(DiplomaticStatus.Peace, restored.diplomaticStatus)
            assertEquals(original.otherCivName, restored.otherCivName)
        }
    }

    @Test fun overlappingPactsUseLongestActiveDuration() {
        val manager = managerWithPact(true, duration = 4)
        manager.trades.add(Trade().apply {
            theirOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Treaty, duration = 9))
        })
        assertEquals(9, manager.turnsToNonAggressionPact())
        assertEquals(false, manager.canAttack())
        manager.trades.last().theirOffers.single().duration = 0
        assertEquals(4, manager.turnsToNonAggressionPact())
        assertEquals(false, manager.canAttack())
        manager.trades.first().ourOffers.single().duration = 0
        assertEquals(0, manager.turnsToNonAggressionPact())
        assertEquals(true, manager.canAttack())
    }

    @Test fun unrelatedOfferWithTreatyNameCannotBlockWar() {
        val manager = DiplomacyManager()
        manager.trades.add(Trade().apply {
            ourOffers.add(TradeOffer(LivingEmpiresTreaties.nonAggressionPact, TradeOfferType.Gold, duration = 20))
        })
        assertEquals(0, manager.turnsToNonAggressionPact())
        assertEquals(true, manager.canAttack())
    }

    @Test fun actualTurnProcessorCountsDownWithoutDoubleDecrement() {
        // Call the production trade-turn processor (rather than modifying
        // offer.duration directly). Stop before expiry, which needs a full game.
        val manager = managerWithPact(true)
        val method = DiplomacyTurnManager::class.java.getDeclaredMethod("nextTurnTrades", DiplomacyManager::class.java)
        method.isAccessible = true
        for (elapsed in 1..19) {
            method.invoke(DiplomacyTurnManager, manager)
            assertEquals("Elapsed turns: $elapsed", 20 - elapsed, manager.turnsToNonAggressionPact())
        }
    }

    @Test fun productionTurnProcessorUnlocksAttackExactlyOnTurnTwenty() {
        // Keep a second, unrelated timed offer active so that the trade remains
        // present at expiry; the notification path needs a full Civilization.
        val manager = managerWithPact(true)
        manager.trades.single().ourOffers.add(
            TradeOffer("stage2-unrelated-timed-offer", TradeOfferType.Treaty, duration = 30)
        )
        val method = DiplomacyTurnManager::class.java.getDeclaredMethod("nextTurnTrades", DiplomacyManager::class.java)
        method.isAccessible = true
        for (elapsed in 1..20) {
            method.invoke(DiplomacyTurnManager, manager)
            assertEquals("Elapsed turns: $elapsed", (20 - elapsed).coerceAtLeast(0), manager.turnsToNonAggressionPact())
            assertEquals("Attack gate on turn $elapsed", elapsed == 20, manager.canAttack())
        }
        assertEquals(1, manager.trades.size)
        assertEquals(10, manager.trades.single().ourOffers.last().duration)
    }

    @Test fun noPactReturnsZero() {
        assertEquals(0, DiplomacyManager().turnsToNonAggressionPact())
    }
}
