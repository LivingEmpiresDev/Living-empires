package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.BeliefType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class DiplomacyManagerTests {

    private val testGame = TestGame()

    fun addCiv(cityStateType: String? = null, defaultUnitTile: Tile? = null) = testGame.addCiv(cityStateType = cityStateType).apply { testGame.addUnit("Warrior", this@apply, defaultUnitTile) }
    // We need to add units so they are not considered defeated, since defeated civs are filtered out of knowncivs
    private val a = addCiv()
    private val b = addCiv()
    private val c = addCiv()
    private val d = addCiv()


    private fun meet(civilization: Civilization, otherCivilization: Civilization) {
        civilization.diplomacyFunctions.makeCivilizationsMeet(otherCivilization)
    }

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
    }

    @Test
    fun `war exhaustion accumulates per turn and resets for a new war`() {
        meet(a, b)
        val ourDiplomacy = a.getDiplomacyManager(b)!!
        val theirDiplomacy = b.getDiplomacyManager(a)!!
        ourDiplomacy.diplomaticStatus = DiplomaticStatus.War
        theirDiplomacy.diplomaticStatus = DiplomaticStatus.War

        ourDiplomacy.advanceWarExhaustion()
        assertTrue(ourDiplomacy.warExhaustion > 0f)
        assertEquals(1, ourDiplomacy.turnsAtWar)
        assertEquals(0f, theirDiplomacy.warExhaustion)

        ourDiplomacy.resetWarExhaustionForNewWar()
        assertEquals(0f, ourDiplomacy.warExhaustion)
        assertEquals(0, ourDiplomacy.turnsAtWar)
    }

    @Test
    fun `war exhaustion survives diplomacy cloning and cools after peace`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        diplomacy.diplomaticStatus = DiplomaticStatus.War
        repeat(3) { diplomacy.advanceWarExhaustion() }
        val copy = diplomacy.clone()
        assertEquals(diplomacy.warExhaustion, copy.warExhaustion)
        assertEquals(diplomacy.turnsAtWar, copy.turnsAtWar)

        diplomacy.diplomaticStatus = DiplomaticStatus.Peace
        diplomacy.advanceWarExhaustion()
        assertTrue(diplomacy.warExhaustion < copy.warExhaustion)
        assertEquals(0, diplomacy.turnsAtWar)
    }

    @Test
    fun `war exhaustion stays bounded through a prolonged war`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        diplomacy.diplomaticStatus = DiplomaticStatus.War
        repeat(1000) { diplomacy.advanceWarExhaustion() }

        assertEquals(100f, diplomacy.warExhaustion)
        assertEquals(1000, diplomacy.turnsAtWar)
        assertEquals(20f, diplomacy.peaceMotivationThreshold())
    }

    @Test
    fun `war exhaustion cools to zero and peace removes motivation penalty`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        diplomacy.diplomaticStatus = DiplomaticStatus.War
        repeat(20) { diplomacy.advanceWarExhaustion() }
        diplomacy.diplomaticStatus = DiplomaticStatus.Peace
        assertEquals(10f, diplomacy.peaceMotivationThreshold())
        repeat(100) { diplomacy.advanceWarExhaustion() }
        assertEquals(0f, diplomacy.warExhaustion)
        assertEquals(0, diplomacy.turnsAtWar)
    }

    @Test
    fun `trust reflects betrayal history and stays directional after cloning`() {
        meet(a, b)
        val outgoing = a.getDiplomacyManager(b)!!
        val incoming = b.getDiplomacyManager(a)!!
        assertEquals(50f, outgoing.trustScore())
        assertEquals(50f, incoming.trustScore())

        outgoing.setModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, -20f)
        outgoing.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -15f)
        assertEquals(15f, outgoing.trustScore())
        assertEquals(50f, incoming.trustScore())
        assertEquals(15f, outgoing.clone().trustScore())

        outgoing.setModifier(DiplomaticModifiers.BetrayedPromiseToNotAttackUs, -100f)
        assertEquals(0f, outgoing.trustScore())
        outgoing.setModifier(DiplomaticModifiers.BetrayedPromiseToNotAttackUs, 0f)
        assertEquals(15f, outgoing.trustScore())
    }

    @Test
    fun `trust recovers naturally as diplomatic betrayal decays each turn`() {
        meet(a, b)
        val ourDiplomacy = a.getDiplomacyManager(b)!!
        val theirDiplomacy = b.getDiplomacyManager(a)!!
        ourDiplomacy.setModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, -20f)
        assertEquals(30f, ourDiplomacy.trustScore())
        assertEquals(50f, theirDiplomacy.trustScore())

        with(DiplomacyTurnManager) { ourDiplomacy.nextTurn() }

        assertTrue(ourDiplomacy.trustScore() > 30f)
        assertTrue(ourDiplomacy.trustScore() < 50f)
        assertEquals(50f, theirDiplomacy.trustScore())
        assertEquals(ourDiplomacy.trustScore(), ourDiplomacy.clone().trustScore())
    }

    @Test
    fun `war declaration and multiple betrayals reduce trust with bounded recovery`() {
        meet(a, b)
        val ourDiplomacy = a.getDiplomacyManager(b)!!
        val theirDiplomacy = b.getDiplomacyManager(a)!!

        ourDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, -20f)
        assertEquals(30f, ourDiplomacy.trustScore())
        ourDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -40f)
        assertEquals(0f, ourDiplomacy.trustScore())
        assertEquals(50f, theirDiplomacy.trustScore())

        // The existing diplomacy turn manager gradually forgets both offences.
        repeat(10) { with(DiplomacyTurnManager) { ourDiplomacy.nextTurn() } }
        assertTrue(ourDiplomacy.trustScore() >= 0f)
        assertTrue(ourDiplomacy.trustScore() <= 50f)
        assertEquals(50f, theirDiplomacy.trustScore())

        // Removing the historical penalties restores baseline, never exceeding 100.
        ourDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, 0f)
        ourDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, 0f)
        assertEquals(50f, ourDiplomacy.trustScore())
    }

    @Test
    fun `AI defensive pact refuses severe betrayal regardless of positive opinion`() {
        meet(a, b)
        val ourDiplomacy = a.getDiplomacyManager(b)!!
        // Strong positive diplomatic history cannot override a severe betrayal.
        ourDiplomacy.setModifier(DiplomaticModifiers.YearsOfPeace, 200f)
        ourDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -40f)
        assertEquals(10f, ourDiplomacy.trustScore())
        assertFalse(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToSignDefensivePact(a, b))

        // Recovery above the threshold removes the trust-specific refusal.
        ourDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -30f)
        assertEquals(20f, ourDiplomacy.trustScore())
        assertTrue(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToSignDefensivePact(a, b))
        assertEquals(50f, b.getDiplomacyManager(a)!!.trustScore())
    }

    @Test
    fun `AI embassy trust gate rejects betrayal and permits recovery`() {
        meet(a, b)
        val receivingCivDiplomacy = b.getDiplomacyManager(a)!!
        // An embassy offer from A is evaluated using B's trust in A.
        receivingCivDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -40f)
        assertEquals(10f, receivingCivDiplomacy.trustScore())
        assertFalse(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToAcceptEmbassy(a, b))

        // Repairing the betrayal penalty restores eligibility, and the AI must
        // actually accept when no other diplomatic or strategic blocker exists.
        receivingCivDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, 0f)
        assertEquals(50f, receivingCivDiplomacy.trustScore())
        testGame.addCity(a, testGame.getTile(HexCoord.Zero))
        testGame.addCity(b, testGame.getTile(2, 0))
        assertTrue(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToAcceptEmbassy(a, b))

        // The exact threshold is inclusive: 20 is allowed, 19 is refused.
        receivingCivDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -30f)
        assertEquals(20f, receivingCivDiplomacy.trustScore())
        assertTrue(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToAcceptEmbassy(a, b))
        receivingCivDiplomacy.setModifier(DiplomaticModifiers.BetrayedDefensivePact, -31f)
        assertEquals(19f, receivingCivDiplomacy.trustScore())
        assertFalse(com.unciv.logic.automation.civilization.DiplomacyAutomation.wantsToAcceptEmbassy(a, b))

        // The receiver's distrust must not change the reverse relationship.
        assertEquals(50f, a.getDiplomacyManager(b)!!.trustScore())
    }

    @Test
    fun `ambassador appointment requires an established embassy`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        assertTrue(!diplomacy.appointAmbassador("Ada"))
        assertEquals(null, diplomacy.ambassadorName)

        diplomacy.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        assertTrue(diplomacy.appointAmbassador("Ada"))
        assertEquals("Ada", diplomacy.ambassadorName)
        assertTrue(!diplomacy.appointAmbassador("  "))
        assertEquals("Ada", diplomacy.ambassadorName)
    }

    @Test
    fun `ambassador assignment survives cloning and clears on embassy closure`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        diplomacy.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        assertTrue(diplomacy.appointAmbassador("Amara"))
        assertEquals("Amara", diplomacy.clone().ambassadorName)

        a.diplomacyFunctions.removeEmbassies(b)
        assertEquals(null, diplomacy.ambassadorName)
        assertEquals(null, b.getDiplomacyManager(a)!!.ambassadorName)
    }

    @Test
    fun `AI embassy appointment never creates an embassy or replaces a named envoy`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        // No embassy means no representative can be assigned.
        assertTrue(!diplomacy.appointAmbassador("Envoy"))
        assertEquals(null, diplomacy.ambassadorName)

        diplomacy.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        assertTrue(diplomacy.appointAmbassador("Existing Envoy"))
        // A later automated appointment must not overwrite a player-defined or existing name.
        assertEquals("Existing Envoy", diplomacy.ambassadorName)
    }

    @Test
    fun `ambassador names are validated and appointments remain directional`() {
        meet(a, b)
        val outgoing = a.getDiplomacyManager(b)!!
        val incoming = b.getDiplomacyManager(a)!!
        outgoing.setModifier(DiplomaticModifiers.SharedEmbassies, 1f)
        incoming.setModifier(DiplomaticModifiers.SharedEmbassies, 1f)

        assertTrue(!outgoing.appointAmbassador("x".repeat(61)))
        assertEquals(null, outgoing.ambassadorName)
        assertTrue(outgoing.appointAmbassador("  Mira  "))
        assertEquals("Mira", outgoing.ambassadorName)
        assertEquals(null, incoming.ambassadorName)

        assertTrue(incoming.appointAmbassador("Niko"))
        assertEquals("Mira", outgoing.ambassadorName)
        assertEquals("Niko", incoming.ambassadorName)
    }

    @Test
    fun `ambassador replacement preserves outgoing diplomatic identity`() {
        meet(a, b)
        val outgoing = a.getDiplomacyManager(b)!!
        val incoming = b.getDiplomacyManager(a)!!
        outgoing.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        incoming.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)

        assertTrue(outgoing.appointAmbassador("Amara Vale"))
        assertTrue(incoming.appointAmbassador("Niko Renn"))
        assertTrue(outgoing.appointAmbassador("Mira Solari"))
        assertEquals("Mira Solari", outgoing.ambassadorName)
        assertEquals("Niko Renn", incoming.ambassadorName)

        // Removing embassies is a bilateral diplomatic event, not just a UI reset.
        a.diplomacyFunctions.removeEmbassies(b)
        assertEquals(null, outgoing.ambassadorName)
        assertEquals(null, incoming.ambassadorName)
        assertFalse(outgoing.appointAmbassador("Amara Vale"))
        assertFalse(incoming.appointAmbassador("Niko Renn"))
    }

    @Test
    fun `AI ambassador assignment requires an embassy and keeps stable identity`() {
        meet(a, b)
        val diplomacy = a.getDiplomacyManager(b)!!
        com.unciv.logic.automation.civilization.DiplomacyAutomation.appointEmbassyRepresentatives(a)
        assertEquals(null, diplomacy.ambassadorName)

        diplomacy.setModifier(DiplomaticModifiers.EstablishedEmbassy, 1f)
        com.unciv.logic.automation.civilization.DiplomacyAutomation.appointEmbassyRepresentatives(a)
        val assignedName = diplomacy.ambassadorName
        assertTrue(assignedName != null && assignedName.contains(" "))
        assertFalse(assignedName!!.contains("$" + "{"))
        com.unciv.logic.automation.civilization.DiplomacyAutomation.appointEmbassyRepresentatives(a)
        assertEquals(assignedName, diplomacy.ambassadorName)
    }

    @Test
    fun `getCommonKnownCivs does not include either DiplomacyManagers's civs`() {
        meet(a, b)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(a !in commonKnownCivs)
        assertTrue(b !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs includes civs met by both civs`() {
        meet(a,b)
        meet(b,c)
        meet(c,a)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(c in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs does not include civs met by only one civ`() {
        meet(a,b)
        meet(a,c)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(c !in commonKnownCivs)
    }

    @Test
    fun getCommonKnownCivsIsEqualForMirroredDiplomacyManagers() {
        meet(a,b)
        meet(a,c)
        meet(b,c)
        meet(a,d)
        meet(b,d)

        assertEquals(
            a.getDiplomacyManager(b)!!.getCommonKnownCivs(),
            b.getDiplomacyManager(a)!!.getCommonKnownCivs()
        )
    }

    @Test
    fun `should have 0 opinion when just met`() {
        // when
        meet(a, b)

        // then
        val opinionOfOtherCiv = a.getDiplomacyManager(b)!!.opinionOfOtherCiv()
        assertEquals(0f, opinionOfOtherCiv)
    }

    @Test
    fun `should change opinion when denouncing`() {
        // given
        meet(a, b)

        // when
        a.getDiplomacyManager(b)!!.denounce()

        // then
        val aOpinionOfB = a.getDiplomacyManager(b)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a)!!.opinionOfOtherCiv()

        // B is upset that A denounced them
        assertEquals(DiplomacyManager.INITIAL_OPINION_CHANGE_WHEN_DENOUNCED, bOpinionOfA)
     
        // A denounces B because A already dislikes B
        // denouncing simply reflects that - it does not change their opinion further
        assertEquals(0f, aOpinionOfB)
    }

    @Test
    fun `should change opinions when liberating city`() {
        // given
        meet(a, b)
        meet(a, c)
        meet(c, b)
        meet(a, d)
        meet(b, d)
        meet(c, d)

        testGame.gameInfo.currentPlayerCiv = addCiv() // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civID

        val bCity = testGame.addCity(b, testGame.getTile(HexCoord.Zero), initialPopulation = 2)
        testGame.addCity(b, testGame.getTile(1,1))  // another city otherwise b is destroyed when bCity is captured
        bCity.puppetCity(c)

        // when
        bCity.liberateCity(a)

        // then
        val aOpinionOfB = a.getDiplomacyManager(b)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a)!!.opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a)!!.opinionOfOtherCiv()
        val dOpinionOfA = d.getDiplomacyManager(a)!!.opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(66f, bOpinionOfA) // massive boost, liberated their city
        assertEquals(0f, cOpinionOfA) // city conquering counters liberated city
        assertEquals(6f, dOpinionOfA) // small boost, liberated another civ's city
    }

    @Test
    fun `should change opinions when conquering city`() {
        // given
        meet(a, b)
        meet(a, c)
        meet(c, b)

        testGame.gameInfo.currentPlayerCiv = addCiv() // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civID

        val bCity = testGame.addCity(b, testGame.getTile(HexCoord.Zero), initialPopulation = 2)
        testGame.addCity(b, testGame.getTile(1,1))  // another city otherwise b is destroyed when bCity is captured

        // when
        bCity.puppetCity(a)

        // then
        val aOpinionOfB = a.getDiplomacyManager(b)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a)!!.opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a)!!.opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(-85f, bOpinionOfA) // massive penality, conquered their city
        assertEquals(-8f, cOpinionOfA) // warmonging penalty
    }

    @Test
    fun `should make city state friend when over threshold`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a)!!.addInfluence(31f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and no other civ are allied`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should not make city state allied when over threshold and other civ has more influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(70f)

        // when
        cityState.getDiplomacyManager(b)!!.addInfluence(61f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
        assertTrue(cityState.getDiplomacyManager(b)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and most influencial`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // when
        cityState.getDiplomacyManager(b)!!.addInfluence(70f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertTrue(cityState.getDiplomacyManager(b)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should make city state angry when at war regardless of previous influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // when
        a.getDiplomacyManager(cityState)!!.declareWar()

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Unforgivable))
        assertEquals(-60f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should gain previous influence in city state after indirect war`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic", testGame.getTile(HexCoord.Zero)) // making peace tries to move units around, so we need to initialize their positions
        val e = addCiv(defaultUnitTile = testGame.getTile(HexCoord(1,0)))
        meet(e, cityState)
        // we cannot be allied and simoultaneously having a city state declare indirect war on us
        cityState.getDiplomacyManager(e)!!.addInfluence(31f)
        cityState.getDiplomacyManager(e)!!.declareWar(DeclareWarReason(WarType.DefensivePactWar, a))

        // when
        e.getDiplomacyManager(cityState)!!.makePeace()

        // then
        assertTrue(cityState.getDiplomacyManager(e)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertEquals(31f, cityState.getDiplomacyManager(e)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in city state on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(29f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in hostile city state on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        cityState.cityStatePersonality = CityStatePersonality.Hostile
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(28.5f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in city state when sharing religion on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral

        meet(a, cityState)

        // to spread religion, need cities
        testGame.addCity(a, testGame.getTile(HexCoord.Zero))
        val cityStateCapital = testGame.addCity(cityState, testGame.getTile(HexCoord(1,0)), initialPopulation = 2)

        val religion = testGame.addReligion(a)
        val belief = testGame.createBelief(BeliefType.Founder, "[+1 Food] from every [Shrine]")
        religion.addBeliefs(listOf(belief))
        cityStateCapital.religion.addPressure(religion.name, 1000)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(29.25f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should increase influence in city state when under resting points`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(-30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(-29f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should increase influence in city state when under resting points and sharing religion`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        // to spread religion, need cities
        testGame.addCity(a, testGame.getTile(HexCoord.Zero))
        val cityStateCapital = testGame.addCity(cityState, testGame.getTile(HexCoord(1,0)), initialPopulation = 2)

        val religion = testGame.addReligion(a)
        val belief = testGame.createBelief(BeliefType.Founder, "[+1 Food] from every [Shrine]")
        religion.addBeliefs(listOf(belief))
        cityStateCapital.religion.addPressure(religion.name, 1000)

        cityState.getDiplomacyManager(a)!!.addInfluence(-30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(-28.5f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should give science for research agreement`() {
        // given
        meet(a, b)

        testGame.addCity(a, testGame.getTile(HexCoord.Zero), initialPopulation = 10)
        testGame.addCity(b, testGame.getTile(HexCoord(1,0)), initialPopulation = 20)

        val expectedSciencePerTurnCivA = 13 // 10 pop, 3 palace. Smaller than 23 science per turn of civ B (20 pop, 3 palace)
        val turns = 10

        // when
        a.getDiplomacyManager(b)!!.setFlag(DiplomacyFlags.ResearchAgreement, turns)
        b.getDiplomacyManager(a)!!.setFlag(DiplomacyFlags.ResearchAgreement, turns)
        repeat(turns) {
            a.getDiplomacyManager(b)!!.nextTurn()
            b.getDiplomacyManager(a)!!.nextTurn()
        }

        // then
        assertFalse(a.getDiplomacyManager(b)!!.hasFlag(DiplomacyFlags.ResearchAgreement))
        assertFalse(b.getDiplomacyManager(a)!!.hasFlag(DiplomacyFlags.ResearchAgreement))
        assertEquals(expectedSciencePerTurnCivA * turns, a.tech.scienceFromResearchAgreements)
        assertEquals(expectedSciencePerTurnCivA * turns, b.tech.scienceFromResearchAgreements)
    }

    @Test
    fun `liberating city-state keeps third-party influence and applies Civ5 liberator formula`() {
        // given — CS with varied influence; capture then liberate (resurrection)
        val cityState = addCiv(cityStateType = "Militaristic")
        val liberator = addCiv()
        val conqueror = addCiv()
        val ally = addCiv()
        val rival = addCiv()

        meet(liberator, cityState)
        meet(conqueror, cityState)
        meet(ally, cityState)
        meet(rival, cityState)
        meet(liberator, conqueror)
        meet(liberator, ally)
        meet(liberator, rival)

        testGame.gameInfo.currentPlayerCiv = addCiv()
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civID

        val csCity = testGame.addCity(cityState, testGame.getTile(HexCoord.Zero), initialPopulation = 2)
        cityState.getDiplomacyManager(ally)!!.setInfluence(93f)
        cityState.getDiplomacyManager(rival)!!.setInfluence(-20f)
        cityState.getDiplomacyManager(liberator)!!.setInfluence(19f)

        csCity.puppetCity(conqueror)
        assertTrue(cityState.isDefeated())
        assertEquals(93f, cityState.getDiplomacyManager(ally)!!.getInfluence(), 0.01f)
        assertEquals(-20f, cityState.getDiplomacyManager(rival)!!.getInfluence(), 0.01f)

        // when
        csCity.liberateCity(liberator)

        // then — third parties unchanged (no full diplo wipe); liberator gets Civ5-style bonus
        assertFalse(cityState.isDefeated())
        assertEquals(cityState, csCity.civ)
        assertEquals(93f, cityState.getDiplomacyManager(ally)!!.getInfluence(), 0.01f)
        assertEquals(-20f, cityState.getDiplomacyManager(rival)!!.getInfluence(), 0.01f)
        // max(max(-60, 93, -20), 19) + 105 = 93 + 105 = 198
        assertEquals(198f, cityState.getDiplomacyManager(liberator)!!.getInfluence(), 0.01f)
    }

    @Test
    fun `defensive pact expiration synchronizes both civilizations on the first turn`() {
        meet(a, b)
        val aToB = a.getDiplomacyManager(b)!!
        val bToA = b.getDiplomacyManager(a)!!
        aToB.diplomaticStatus = DiplomaticStatus.DefensivePact
        bToA.diplomaticStatus = DiplomaticStatus.DefensivePact
        aToB.setFlag(DiplomacyFlags.DefensivePact, 1)
        bToA.setFlag(DiplomacyFlags.DefensivePact, 1)

        aToB.nextTurn()

        assertEquals(DiplomaticStatus.Peace, aToB.diplomaticStatus)
        assertEquals(DiplomaticStatus.Peace, bToA.diplomaticStatus)
        assertFalse(aToB.hasFlag(DiplomacyFlags.DefensivePact))
        assertFalse(bToA.hasFlag(DiplomacyFlags.DefensivePact))
        // The second civilization's turn must not resurrect the pact.
        bToA.nextTurn()
        assertEquals(DiplomaticStatus.Peace, aToB.diplomaticStatus)
        assertEquals(DiplomaticStatus.Peace, bToA.diplomaticStatus)
    }

    @Test
    fun `expired defensive pact must not turn an active war into peace`() {
        meet(a, b)
        val aToB = a.getDiplomacyManager(b)!!
        val bToA = b.getDiplomacyManager(a)!!
        aToB.diplomaticStatus = DiplomaticStatus.War
        bToA.diplomaticStatus = DiplomaticStatus.War
        aToB.setFlag(DiplomacyFlags.DefensivePact, 1)
        bToA.setFlag(DiplomacyFlags.DefensivePact, 1)

        aToB.nextTurn()

        assertEquals(DiplomaticStatus.War, aToB.diplomaticStatus)
        assertEquals(DiplomaticStatus.War, bToA.diplomaticStatus)
        assertFalse(aToB.hasFlag(DiplomacyFlags.DefensivePact))
        assertFalse(bToA.hasFlag(DiplomacyFlags.DefensivePact))
    }

}
