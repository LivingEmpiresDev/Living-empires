package com.unciv.logic.civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class DefensivePactTests {
    private val testGame = TestGame()

    fun addCiv() = testGame.addCiv().apply { testGame.addUnit("Warrior", this@apply, null) }

    private val a = addCiv()
    private val b = addCiv()
    private val c = addCiv()



    private fun meetAll() {
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        a.diplomacyFunctions.makeCivilizationsMeet(c)
        b.diplomacyFunctions.makeCivilizationsMeet(c)
    }

    @Test
    fun `Civs with Defensive Pacts are called in`() {
        meetAll()
        a.getDiplomacyManager(b)!!.signDefensivePact(100)
        Assert.assertTrue(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact
            && b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
        c.getDiplomacyManager(a)!!.declareWar()

        Assert.assertTrue(c.isAtWarWith(b) && c.isAtWarWith(a))
        Assert.assertTrue(b.isAtWarWith(c) && a.isAtWarWith(c))
        Assert.assertTrue(b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact
            || b.getDiplomacyManager(a)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
    }

    @Test
    fun `Enemy attacking pact member automatically brings defensive ally into war`() {
        meetAll()
        // a is the player under attack, b is their defensive ally, c is the aggressor.
        a.getDiplomacyManager(b)!!.signDefensivePact(20)
        Assert.assertFalse(a.isAtWarWith(c))
        Assert.assertFalse(b.isAtWarWith(c))

        c.getDiplomacyManager(a)!!.declareWar()

        Assert.assertTrue("The attacked civilization must be at war with the aggressor", a.isAtWarWith(c))
        Assert.assertTrue("The defensive ally must automatically join against the aggressor", b.isAtWarWith(c))
        Assert.assertTrue("War must be reciprocal", c.isAtWarWith(b))
        Assert.assertFalse("Defensive allies must not fight each other", a.isAtWarWith(b))
    }

    @Test
    fun `Defensive Pact cancel when attacking`() {
        meetAll()
        a.getDiplomacyManager(b)!!.signDefensivePact(100)
        a.getDiplomacyManager(c)!!.declareWar()

        Assert.assertFalse(a.getDiplomacyManager(b)!!.diplomaticStatus == DiplomaticStatus.DefensivePact)
    }

    @Test
    fun `Defensive Pact timeout`() {
        meetAll()
        val ab = a.getDiplomacyManager(b)!!
        val ba = b.getDiplomacyManager(a)!!
        // Even a caller requesting one turn must receive Living Empires' fixed 20-turn pact.
        ab.signDefensivePact(1)
        Assert.assertEquals(DiplomaticStatus.DefensivePact, ab.diplomaticStatus)
        Assert.assertEquals(DiplomaticStatus.DefensivePact, ba.diplomaticStatus)

        repeat(19) {
            ab.nextTurn()
            ba.nextTurn()
        }
        Assert.assertEquals(DiplomaticStatus.DefensivePact, ab.diplomaticStatus)
        Assert.assertEquals(DiplomaticStatus.DefensivePact, ba.diplomaticStatus)

        ab.nextTurn()
        ba.nextTurn()
        Assert.assertEquals(DiplomaticStatus.Peace, ab.diplomaticStatus)
        Assert.assertEquals(DiplomaticStatus.Peace, ba.diplomaticStatus)
    }

    @Test
    fun `Breaking Defensive Pact`() {
        meetAll()
        val abDiploManager = a.getDiplomacyManager(b)!!
        abDiploManager.signDefensivePact(100)
        abDiploManager.declareWar()
        Assert.assertTrue(abDiploManager.otherCivDiplomacy().hasModifier(DiplomaticModifiers.BetrayedDefensivePact)) // Defender should be extra mad
        Assert.assertTrue(abDiploManager.hasModifier(DiplomaticModifiers.DefensivePact)) // Aggressor should still be friendly to the defender, they did nothing wrong
        Assert.assertFalse(abDiploManager.otherCivDiplomacy().hasModifier(DiplomaticModifiers.DefensivePact)) // Defender should not be friendly to the aggressor.
    }

    @Test
    fun `Breaking Defensive Pact with friends`() {
        meetAll()
        val abDiploManager = a.getDiplomacyManager(b)!!
        val bcDiploManager = b.getDiplomacyManager(c)!!
        abDiploManager.signDefensivePact(100)
        bcDiploManager.signDeclarationOfFriendship()

        abDiploManager.declareWar()
        Assert.assertTrue(c.getDiplomacyManager(a)!!.hasModifier(DiplomaticModifiers.BetrayedDefensivePact))
    }
}
