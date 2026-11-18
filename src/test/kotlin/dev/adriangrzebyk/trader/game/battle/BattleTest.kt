package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R74–R78: a battle round once its salvos have landed, without a game around it.
class BattleTest {

	private val ada = "ada@example.com"
	private val grace = "grace@example.com"

	// Ada's Wayfarer and Lantern against Grace's Meridian at Tau Ceti; nobody was intercepted.
	private val started = Battle.start(
		TAU_CETI,
		listOf(BattleShip("Wayfarer", ada), BattleShip("Lantern", ada), BattleShip("Meridian", grace)),
		emptyList(),
		emptyList()
	)

	// Every ship can absorb 3 hits.
	private val survivable = mapOf("Wayfarer" to 3, "Lantern" to 3, "Meridian" to 3)

	private fun fire(ship: String, target: String, rockets: Boolean = false) = BattleOrder(ship, BattleAction.FIRE, target, rockets)

	private fun salvo(firer: String, target: String, hits: Int, rocketRoll: Int? = null) =
		Salvo(firer, target, rocketRoll != null, 6, 7, 2, hits, rocketRoll, false)

	private val ordered = started
		.giveOrders(ada, listOf(fire("Wayfarer", "Meridian"), fire("Lantern", "Meridian")))
		.giveOrders(grace, listOf(fire("Meridian", "Wayfarer")))

	@Test
	fun shouldAddUpTheHitsOnEachShipForItsOwnerToPlace() {
		val round = ordered.fired(
			listOf(salvo("Wayfarer", "Meridian", 1), salvo("Lantern", "Meridian", 2), salvo("Meridian", "Wayfarer", 1)),
			survivable
		)

		assertThat(round.destroyed).isEmpty()
		assertThat(round.battle.stage).isEqualTo(BattleStage.HITS)
		assertThat(round.battle.pendingHits)
			.containsExactlyInAnyOrder(PendingHits("Meridian", grace, 3), PendingHits("Wayfarer", ada, 1))
	}

	@Test
	fun shouldDestroyAShipThatCannotAbsorbItsHits() {
		val round = ordered.fired(listOf(salvo("Wayfarer", "Meridian", 2), salvo("Lantern", "Meridian", 2)), survivable)

		assertThat(round.destroyed).containsExactly("Meridian")
		assertThat(round.battle.stage).isEqualTo(BattleStage.LANDING)
		assertThat(round.log.last()).isEqualTo("Meridian is destroyed")
	}

	@Test
	fun shouldDestroyAShipWithARocketRollAtOrBelowTheHitsAndLetItHoldAbove() {
		val killed = ordered.fired(listOf(salvo("Wayfarer", "Meridian", 2, rocketRoll = 2)), survivable)
		val holds = ordered.fired(listOf(salvo("Wayfarer", "Meridian", 2, rocketRoll = 3)), survivable)

		assertThat(killed.destroyed).containsExactly("Meridian")
		assertThat(killed.battle.pendingHits).isEmpty()
		assertThat(holds.destroyed).isEmpty()
		assertThat(holds.battle.pendingHits).containsExactly(PendingHits("Meridian", grace, 2))
	}

	@Test
	fun shouldReportWhatEachSalvoCameTo() {
		// R75–R78: Meridian is destroyed by Lantern's rocket, Wayfarer waits for Ada to place its hit.
		val round = ordered.fired(
			listOf(salvo("Wayfarer", "Meridian", 0), salvo("Lantern", "Meridian", 2, rocketRoll = 1), salvo("Meridian", "Wayfarer", 1)),
			survivable
		)

		assertThat(round.report.round).isEqualTo(1)
		assertThat(round.report.results).containsExactly(
			SalvoResult("Wayfarer", ada, "Meridian", grace, false, 0, SalvoOutcome.NO_DAMAGE),
			SalvoResult("Lantern", ada, "Meridian", grace, true, 2, SalvoOutcome.DESTROYED),
			SalvoResult("Meridian", grace, "Wayfarer", ada, false, 1, SalvoOutcome.AWAITING_PLACEMENT)
		)
	}

	@Test
	fun shouldReportASalvoAtAShipBreakingAwayAsMissed() {
		val breaking = started
			.giveOrders(ada, listOf(fire("Wayfarer", "Meridian"), fire("Lantern", "Meridian")))
			.giveOrders(grace, listOf(BattleOrder("Meridian", BattleAction.BREAK_AWAY, null, false)))

		val round = breaking.fired(listOf(Salvo("Wayfarer", "Meridian", false, 6, 0, 2, 0, null, true)), survivable)

		assertThat(round.report.results.single().outcome).isEqualTo(SalvoOutcome.BROKE_AWAY)
	}

	@Test
	fun shouldLetABreakingAwayShipLeaveAndLogTheRound() {
		val breaking = started
			.giveOrders(ada, listOf(fire("Wayfarer", "Meridian"), fire("Lantern", "Meridian")))
			.giveOrders(grace, listOf(BattleOrder("Meridian", BattleAction.BREAK_AWAY, null, false)))
		val missed = Salvo("Wayfarer", "Meridian", false, 6, 0, 2, 0, null, true)

		val round = breaking.fired(listOf(missed, missed.copy(firer = "Lantern")), survivable)

		assertThat(round.battle.fightingShips()).containsExactly("Wayfarer", "Lantern")
		assertThat(round.battle.leftShips).containsExactly(BattleShip("Meridian", grace))
		assertThat(round.battle.stage).isEqualTo(BattleStage.LANDING)
		assertThat(round.log).containsExactly(
			"Battle at Tau Ceti, round 1",
			"Meridian breaks away",
			"Salvo: Wayfarer → Meridian misses: Meridian broke away",
			"Salvo: Lantern → Meridian misses: Meridian broke away"
		)
	}

	@Test
	fun shouldRefuseHitsPlacedOnOtherShipsThanTheOnesHit() {
		val battle = ordered.fired(listOf(salvo("Wayfarer", "Meridian", 1)), survivable).battle

		assertThatThrownBy { battle.hitsToPlace(grace, setOf("Halcyon")) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Place the hits on each of your ships that took them: Meridian.")
		assertThat(battle.hitsToPlace(grace, setOf("Meridian"))).containsExactly(PendingHits("Meridian", grace, 1))
	}

	@Test
	fun shouldOfferTheSurvivorsForLandingOnlyOnceTheBattleIsOverAndUntilPlaced() {
		val over = ordered.fired(listOf(salvo("Wayfarer", "Meridian", 2), salvo("Lantern", "Meridian", 2)), survivable).battle

		assertThat(ordered.shipsToLand(ada)).isEmpty()
		assertThat(over.isJustOver()).isTrue()
		assertThat(over.shipsToLand(ada)).containsExactly("Wayfarer", "Lantern")
		assertThatThrownBy { over.landedBy(ada, setOf("Wayfarer")) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Choose where each of your ships from the battle ends up: Wayfarer, Lantern.")

		val landed = over.landedBy(ada, setOf("Wayfarer", "Lantern"))

		assertThat(landed.shipsToLand(ada)).isEmpty()
		assertThat(landed.isJustOver()).isFalse()
		assertThat(landed.isFinished()).isTrue()
	}

	@Test
	fun shouldCostReputationToFireAtAShipThatTriedNothingOncePerBattle() {
		// R86, E13-S04: Meridian tried to intercept Wayfarer, so Ada fires back for free; Grace firing at Lantern, which
		// tried nothing, pays once; with no tries at all (R80), anyone who fires pays.
		val tried = Battle.start(
			TAU_CETI,
			started.ships,
			listOf(BattleInterception("Wayfarer", "Meridian")),
			listOf(BattleInterception("Wayfarer", "Meridian"))
		)
			.giveOrders(ada, listOf(fire("Wayfarer", "Meridian"), fire("Lantern", "Meridian")))
			.giveOrders(grace, listOf(fire("Meridian", "Lantern")))

		assertThat(tried.firingPenalties()).containsExactly(grace)
		assertThat(tried.firingPenalizedFor(listOf(grace)).firingPenalties()).isEmpty()
		assertThat(ordered.firingPenalties()).containsExactly(ada, grace)
	}

	@Test
	fun shouldKeepOnlyTheTriesBetweenItsOwnShips() {
		// R86: a try earlier in the turn counts in a later battle (R80) only when both ships fight in it.
		val battle = Battle.start(
			TAU_CETI,
			started.ships,
			emptyList(),
			listOf(BattleInterception("Wayfarer", "Meridian"), BattleInterception("Corvid", "Meridian"))
		)

		assertThat(battle.tries).containsExactly(BattleInterception("Wayfarer", "Meridian"))
	}
}
