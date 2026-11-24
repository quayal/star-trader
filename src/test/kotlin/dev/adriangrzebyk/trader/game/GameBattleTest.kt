package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.battle.BattleAction
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleOrder
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.game.battle.BattleStage
import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import dev.adriangrzebyk.trader.game.battle.PendingHits
import dev.adriangrzebyk.trader.game.battle.SalvoOutcome
import dev.adriangrzebyk.trader.game.battle.SalvoResult
import dev.adriangrzebyk.trader.game.battle.SalvoRolls
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E10: interception, battle rounds, hits and landing after the battle (R69–R78).
class GameBattleTest {

	/** [d6] in order, and 2d6 rolls of 4 for any reputation a try or a shot costs (R86, E13-S04). */
	private fun dice(vararg d6: Int) = FakeDice(List(8) { 4 }, d6.toList())

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Ships: Ada's Wayfarer at Tau Ceti and Lantern at Beta Hydri; Grace's Meridian at Gamma Leporis and Halcyon at
	// Mu Herculis; Kim's Bright Reach at Sigma Draconis and Corvid at Epsilon Eridani. All starting Clarinets: weapons 6
	// (rockets 5, from Light weapons only), shields 2, intercept 2, class B. Turn order: Grace, Ada, Kim (see GameShipJumpTest).
	private val jumping = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start("ada@example.com", START_DICE).let {
			StartingShips(
				FakeStartingSystemPicker(
					TAU_CETI to BETA_HYDRI,
					GAMMA_LEPORIS to MU_HERCULIS,
					SIGMA_DRACONIS to EPSILON_ERIDANI
				)
			).placedIn(it)
		}.let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(1, 1, 2, 1, 2))
		.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)
		.pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

	// Ada's Wayfarer waits in Space at Tau Ceti, where Grace's Meridian arrives.
	private val waiting = withShip(jumping, "Wayfarer") { it.copy(location = ShipLocation.SPACE) }
	private val arrived = waiting.jumpShip("grace@example.com", "Meridian", TAU_CETI, JUMPS_SUCCEED)

	// Wayfarer 2 + 5 = 7 beats Meridian 2 + 2 = 4.
	private val fighting = arrived.answerInterception("ada@example.com", "Wayfarer", dice(5, 2))

	@Test
	fun shouldAskPlayersWithShipsInSpaceThereWhetherToIntercept() {
		// R70
		val interception = arrived.turn!!.interception!!

		assertThat(interception.asked).containsExactly("ada@example.com")
		assertThat(interception.ship).isEqualTo("Meridian")
		assertThat(arrived.log.last()).isEqualTo(LogEntry.event(1, "Interception: Orion Freight may try to stop Meridian at Tau Ceti"))
	}

	@Test
	fun shouldAskNobodyWhenNoOtherPlayerIsInSpaceThere() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI, JUMPS_SUCCEED)

		assertThat(game.turn!!.interception).isNull()
		assertThat(game.turn!!.landingShip).isEqualTo("Meridian")
	}

	@Test
	fun shouldRefuseLandingAndJumpingWhileTheInterceptionIsDecided() {
		assertThatThrownBy { arrived.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Waiting for the decision on intercepting Meridian.")
		assertThatThrownBy { arrived.jumpShip("grace@example.com", "Halcyon", BETA_HYDRI, JUMPS_SUCCEED) }
			.isInstanceOf(InvalidGameOperationException::class.java)
	}

	@Test
	fun shouldRefuseAnInterceptionAnswerFromAPlayerWhoIsNotAsked() {
		assertThatThrownBy { arrived.answerInterception("kim@example.com", null, dice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
	}

	@Test
	fun shouldRefuseAShipThatIsNotInSpaceThere() {
		assertThatThrownBy { arrived.answerInterception("ada@example.com", "Lantern", dice()) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Choose one of your ships in Space at Tau Ceti: Wayfarer.")
	}

	@Test
	fun shouldLetTheShipLandWhenNobodyTries() {
		val game = arrived.answerInterception("ada@example.com", null, dice())

		assertThat(game.turn!!.interception).isNull()
		assertThat(game.turn!!.battle).isNull()
		assertThat(game.log.last()).isEqualTo(LogEntry.event(1, "Nobody tries to intercept Meridian"))
		assertThat(game.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY).turn!!.landingShip).isNull()
	}

	@Test
	fun shouldGiveATieToTheArrivingShip() {
		// R70: 2 + 3 vs 2 + 3
		val game = arrived.answerInterception("ada@example.com", "Wayfarer", dice(3, 3))

		assertThat(game.turn!!.battle).isNull()
		assertThat(game.log.takeLast(2).map { it.text }).containsExactly(
			"Interception: Wayfarer (2 + d6 3 + 0 = 5) vs Meridian (2 + d6 3 + 0 = 5): escapes",
			// R86 (E13-S04): a try costs reputation whether or not it succeeds.
			"Orion Freight's reputation: \u22124 for trying to intercept (rolled 2d6: 4) \u2192 16"
		)
	}

	@Test
	fun shouldGiveATryAndItsReputationLossOneNoticeWithBothRolls() {
		// Creator, 2026-11-18: the pop-up card of a try, worded for each player from these facts.
		val game = arrived.answerInterception("ada@example.com", "Wayfarer", dice(3, 3))

		val notice = Notice(
			NoticeKind.INTERCEPTION,
			TAU_CETI,
			listOf("ada@example.com", "grace@example.com"),
			null,
			InterceptionRoll(
				"ada@example.com",
				"Wayfarer",
				"Wayfarer (2 + d6 3 + 0 = 5)",
				"grace@example.com",
				"Meridian",
				"Meridian (2 + d6 3 + 0 = 5)",
				false
			)
		)
		assertThat(game.log.takeLast(2).map { it.notice }).containsExactly(notice, notice)
	}

	@Test
	fun shouldGiveNoNoticeWhenNobodyTries() {
		assertThat(arrived.answerInterception("ada@example.com", null, dice()).log.last().notice).isNull()
	}

	@Test
	fun shouldGiveTheBattleItsStartRoundAndEndNotices() {
		val game = fighting
			.giveBattleOrders("ada@example.com", listOf(breakAway("Wayfarer")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer")), dice())
		val both = listOf("ada@example.com", "grace@example.com")

		assertThat(fighting.log.last().notice).isEqualTo(Notice(NoticeKind.BATTLE_STARTED, TAU_CETI, both, null, null))
		assertThat(game.log.takeLast(4).map { it.notice }).containsExactly(
			Notice(NoticeKind.BATTLE_ROUND, TAU_CETI, both, 1, null),
			Notice(NoticeKind.BATTLE_ROUND, TAU_CETI, both, 1, null),
			Notice(NoticeKind.BATTLE_ROUND, TAU_CETI, both, 1, null),
			// Wayfarer broke away, so it is listed after the ship still fighting.
			Notice(NoticeKind.BATTLE_OVER, TAU_CETI, both.reversed(), null, null)
		)
	}

	@Test
	fun shouldUseUpTheJumpOfAShipThatTriedToIntercept() {
		// R72: Wayfarer tried and failed; it can't jump in Ada's slot.
		val game = arrived.answerInterception("ada@example.com", "Wayfarer", dice(1, 6))
			.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY)
			.answerHangar("grace@example.com", false)
			.finishJumping("grace@example.com")

		assertThat(game.turn!!.jumpedShips).contains("Wayfarer")
		assertThatThrownBy { game.jumpShip("ada@example.com", "Wayfarer", MU_HERCULIS, JUMPS_SUCCEED) }
			.isInstanceOf(InvalidJumpException::class.java)
	}

	@Test
	fun shouldStartABattleWithEveryShipInSpaceThere() {
		// R73
		val battle = fighting.turn!!.battle!!

		assertThat(battle.ships).containsExactly(BattleShip("Wayfarer", "ada@example.com"), BattleShip("Meridian", "grace@example.com"))
		assertThat(battle.interceptions).containsExactly(BattleInterception("Meridian", "Wayfarer"))
		assertThat(fighting.log.takeLast(3).map { it.text }).containsExactly(
			"Interception: Wayfarer (2 + d6 5 + 0 = 7) vs Meridian (2 + d6 2 + 0 = 4): intercepted",
			"Orion Freight's reputation: \u22124 for trying to intercept (rolled 2d6: 4) \u2192 16",
			"Battle at Tau Ceti: Wayfarer, Meridian"
		)
	}

	@Test
	fun shouldKeepOrdersSecretUntilEveryoneHasGivenThem() {
		val game = fighting.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian")), dice())

		val battle = game.turn!!.battle!!
		assertThat(battle.waitingFor()).containsExactly("grace@example.com")
		assertThat(battle.ordersOf("grace@example.com")).isEmpty()
		assertThat(battle.ordersOf("ada@example.com")).hasSize(1)
	}

	@Test
	fun shouldRefuseBreakingAwayForTheInterceptedShip() {
		assertThatThrownBy { fighting.giveBattleOrders("grace@example.com", listOf(breakAway("Meridian")), dice()) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Meridian was intercepted by Wayfarer: it can't break away while Wayfarer fights.")
	}

	@Test
	fun shouldRefuseFiringAtOwnShipOrMissingAnOrder() {
		assertThatThrownBy { fighting.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Wayfarer")), dice()) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Choose an enemy ship in the battle for Wayfarer to fire at.")
		assertThatThrownBy { fighting.giveBattleOrders("ada@example.com", emptyList(), dice()) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Give one order to each of your ships in the battle: Wayfarer.")
	}

	@Test
	fun shouldFireEverySalvoTogetherAndWaitForTheHitsToBePlaced() {
		// Wayfarer 6 + d6 6 = 12 vs shields 2: 1 hit. Meridian with rockets 5 + d6 4 = 9: 1 hit; d6 3 holds.
		val fired = fireFirstRound()

		val battle = fired.turn!!.battle!!
		assertThat(battle.stage).isEqualTo(BattleStage.HITS)
		assertThat(
			battle.pendingHits
		).containsExactly(PendingHits("Meridian", "grace@example.com", 1), PendingHits("Wayfarer", "ada@example.com", 1))
		assertThat(fired.log.takeLast(4).map { it.text }).containsExactly(
			"Battle at Tau Ceti, round 1",
			"Salvo: Wayfarer → Meridian, 6 + d6 6 = 12 vs shields 2: 1 hit",
			"Rocket salvo: Meridian → Wayfarer, 5 + d6 4 = 9 vs shields 2: 1 hit; d6 3: holds",
			// R86 (E13-S04): Wayfarer fired at the ship that arrived, which tried to intercept nobody; Meridian returned fire for free.
			"Orion Freight's reputation: \u22124 for firing at a ship that didn't try to intercept (rolled 2d6: 4) \u2192 12"
		)
	}

	@Test
	fun shouldKeepWhatTheRoundsSalvosDidOnTheTurnUntilTheHitsArePlaced() {
		// R75–R78: each salvo's outcome, for the players to be told; placing the hits settles it.
		val fired = fireFirstRound()

		assertThat(fired.turn!!.battleReport!!.results).containsExactly(
			SalvoResult(
				"Wayfarer",
				"ada@example.com",
				"Meridian",
				"grace@example.com",
				false,
				1,
				SalvoOutcome.AWAITING_PLACEMENT,
				SalvoRolls(6, 6, 2, null)
			),
			SalvoResult(
				"Meridian",
				"grace@example.com",
				"Wayfarer",
				"ada@example.com",
				true,
				1,
				SalvoOutcome.AWAITING_PLACEMENT,
				SalvoRolls(5, 4, 2, 3)
			)
		)
		val placed = fired.placeBattleHits("grace@example.com", mapOf("Meridian" to HitPlacement(1, emptyList(), emptyMap())))
		assertThat(placed.turn!!.battleReport!!.results.map { it.outcome })
			.containsExactly(SalvoOutcome.PLACED, SalvoOutcome.AWAITING_PLACEMENT)
	}

	@Test
	fun shouldKeepTheReportWhenTheLastShipsDestroyEachOtherAndTheBattleIsGone() {
		// Both rocket salvos: 5 + d6 6 = 11 vs shields 2: 1 hit; d6 1 destroys each. Nobody is left to land.
		val game = fighting
			.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian", rockets = true)), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer", rockets = true)), dice(6, 1, 6, 1))

		assertThat(game.turn!!.battle).isNull()
		assertThat(game.turn!!.battleReport!!.results.map { it.outcome }).containsOnly(SalvoOutcome.DESTROYED)
	}

	@Test
	fun shouldRefuseRocketsFromAShipWithNoWorkingRocketModule() {
		// R75: only modules fire rockets; Wayfarer's Light weapons are damaged.
		val damaged = withShip(fighting, "Wayfarer") { it.copy(damagedModules = listOf(Module.LIGHT_WEAPONS)) }

		assertThatThrownBy { damaged.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian", rockets = true)), dice()) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Wayfarer has no working rocket module, so it can't fire rockets.")
	}

	@Test
	fun shouldStartTheNextRoundOnceEveryoneHasPlacedTheirHits() {
		val placed = fireFirstRound()
			.placeBattleHits("grace@example.com", mapOf("Meridian" to HitPlacement(0, listOf(ModuleHit(Module.CARGO, false, 1)), emptyMap())))
			.placeBattleHits("ada@example.com", mapOf("Wayfarer" to HitPlacement(1, emptyList(), emptyMap())))

		val battle = placed.turn!!.battle!!
		assertThat(battle.round).isEqualTo(2)
		assertThat(battle.stage).isEqualTo(BattleStage.ORDERS)
		assertThat(shipOf(placed, "Meridian").damagedCount(Module.CARGO)).isEqualTo(1)
		assertThat(shipOf(placed, "Wayfarer").hullDamage).isEqualTo(1)
	}

	@Test
	fun shouldMissAShipThatBreaksAwayAndEndTheBattle() {
		val game = fighting
			.giveBattleOrders("ada@example.com", listOf(breakAway("Wayfarer")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer")), dice())

		val battle = game.turn!!.battle!!
		assertThat(battle.stage).isEqualTo(BattleStage.LANDING)
		assertThat(battle.leftShips).containsExactly(BattleShip("Wayfarer", "ada@example.com"))
		assertThat(battle.waitingFor()).containsExactlyInAnyOrder("ada@example.com", "grace@example.com")
		assertThat(game.log.takeLast(4).map { it.text }).containsExactly(
			"Battle at Tau Ceti, round 1",
			"Wayfarer breaks away",
			"Salvo: Meridian → Wayfarer misses: Wayfarer broke away",
			"The battle at Tau Ceti is over"
		)
	}

	@Test
	fun shouldGoOnWithTheJumpsOnceEverySurvivorHasLanded() {
		val over = fighting
			.giveBattleOrders("ada@example.com", listOf(breakAway("Wayfarer")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer")), dice())

		val game = over
			.landAfterBattle("ada@example.com", mapOf("Wayfarer" to ShipLocation.PORT), LANDS_SAFELY)
			.landAfterBattle("grace@example.com", mapOf("Meridian" to ShipLocation.PLANET), LANDS_SAFELY)

		assertThat(game.turn!!.battle).isNull()
		assertThat(game.turn!!.landingShip).isNull()
		assertThat(shipOf(game, "Meridian").location).isEqualTo(ShipLocation.PLANET)
		assertThat(shipOf(game, "Wayfarer").location).isEqualTo(ShipLocation.PORT)
		// Grace still has Halcyon to jump.
		assertThat(game.turn!!.jumper()).isEqualTo("grace@example.com")
		assertThat(game.jumpShip("grace@example.com", "Halcyon", BETA_HYDRI, JUMPS_SUCCEED).turn!!.jumpedShips).contains("Halcyon")
	}

	@Test
	fun shouldRefuseLandingBeforeTheBattleIsOver() {
		assertThatThrownBy { fighting.landAfterBattle("ada@example.com", mapOf("Wayfarer" to ShipLocation.PORT), LANDS_SAFELY) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("The battle isn't over yet.")
	}

	@Test
	fun shouldDestroyAShipWithARocketRollAtOrBelowTheHits() {
		// Wayfarer fires first: 6 + d6 2 = 8, 1 hit. Meridian's rockets: 5 + d6 6 = 11: 1 hit; d6 1 destroys Wayfarer, which still fired.
		val game = fighting
			.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer", rockets = true)), dice(2, 6, 1))

		assertThat(game.players.first { it.email == "ada@example.com" }.ship("Wayfarer")).isNull()
		assertThat(game.log.map { it.text }).contains("Wayfarer is destroyed")
		val battle = game.turn!!.battle!!
		assertThat(battle.ships).containsExactly(BattleShip("Meridian", "grace@example.com"))
		// The battle is over, but Meridian's hit still waits to be placed.
		assertThat(battle.pendingHits).containsExactly(PendingHits("Meridian", "grace@example.com", 1))
		val placed = game.placeBattleHits("grace@example.com", mapOf("Meridian" to HitPlacement(1, emptyList(), emptyMap())))
		assertThat(placed.turn!!.battle!!.stage).isEqualTo(BattleStage.LANDING)
		assertThat(placed.turn!!.battle!!.waitingFor()).containsExactly("grace@example.com")
	}

	@Test
	fun shouldDestroyAShipThatCannotAbsorbItsHits() {
		// A bare Piccolo (shields 2, no modules) with 2 hull hits already can't survive another.
		val piccolo = withShip(fighting, "Wayfarer") { it.copy(hull = Hull.PICCOLO, modules = emptyList(), hullDamage = 2) }
		val game = piccolo
			.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer")), dice(2, 1))

		// Wayfarer: 2 + d6 2 = 4, no hits. Meridian: 6 + d6 1 = 7 vs shields 2: 1 hit, more than it can take.
		assertThat(game.players.first { it.email == "ada@example.com" }.ship("Wayfarer")).isNull()
		assertThat(game.turn!!.battle!!.stage).isEqualTo(BattleStage.LANDING)
	}

	@Test
	fun shouldRefuseAnotherPlayersHits() {
		assertThatThrownBy {
			fireFirstRound().placeBattleHits("kim@example.com", mapOf("Meridian" to HitPlacement(1, emptyList(), emptyMap())))
		}.isInstanceOf(InvalidGameOperationException::class.java)
	}

	/** [fighting] once the battle is over and the turn has reached Investment. */
	private val afterBattleInvesting: Game
		get() = fighting.copy(turn = fighting.turn!!.withBattle(null).landed().toTrading().endTrading().toInvestment())

	@Test
	fun shouldRepairADamagedShipAtAShipyardDuringInvestment() {
		// R79: a Clarinet's hull for 20 HT, at Tau Ceti's port.
		val investing =
			withShip(afterBattleInvesting, "Wayfarer") {
				it.copy(location = ShipLocation.PORT, hullDamage = 1)
			}

		val repaired = investing.repairShip("ada@example.com", "Wayfarer")

		assertThat(shipOf(repaired, "Wayfarer").hullDamage).isZero()
		assertThat(repaired.players.first { it.email == "ada@example.com" }.money)
			.isEqualTo(investing.players.first { it.email == "ada@example.com" }.money - 20)
	}

	@Test
	fun shouldRefuseARepairAwayFromAShipyard() {
		// R79, R133: a hull is repaired only at a shipyard, though refits happen in any port (R65); modules are repaired anywhere.
		val investing =
			withShip(afterBattleInvesting, "Wayfarer") {
				it.copy(system = GAMMA_LEPORIS, location = ShipLocation.PORT, hullDamage = 1)
			}

		assertThatThrownBy { investing.repairShip("ada@example.com", "Wayfarer") }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer: The hull is repaired at a shipyard: in port at Beta Hydri, Epsilon Eridani or Tau Ceti.")
	}

	@Test
	fun shouldFreeTheInterceptedShipOnceItsInterceptorHasLeftTheBattle() {
		// R74: Lantern also waits in Space, so the battle goes on after Wayfarer, the interceptor, breaks away.
		val withLantern = withShip(withShip(jumping, "Wayfarer") { it.copy(location = ShipLocation.SPACE) }, "Lantern") {
			it.copy(system = TAU_CETI, location = ShipLocation.SPACE)
		}
		val battle = withLantern.jumpShip("grace@example.com", "Meridian", TAU_CETI, JUMPS_SUCCEED)
			.answerInterception("ada@example.com", "Wayfarer", dice(5, 2))
		// Lantern 6 + 2 = 8 vs shields 2: 1 hit on Meridian; Meridian 6 + 2 = 8: 1 hit on Lantern.
		val round2 = battle
			.giveBattleOrders("ada@example.com", listOf(breakAway("Wayfarer"), fire("Lantern", "Meridian")), dice())
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Lantern")), dice(2, 2))
			.placeBattleHits("ada@example.com", mapOf("Lantern" to HitPlacement(1, emptyList(), emptyMap())))
			.placeBattleHits("grace@example.com", mapOf("Meridian" to HitPlacement(1, emptyList(), emptyMap())))

		assertThat(round2.turn!!.battle!!.round).isEqualTo(2)
		assertThat(round2.turn!!.battle!!.canBreakAway("Meridian")).isTrue()
		val over = round2
			.giveBattleOrders("grace@example.com", listOf(breakAway("Meridian")), dice())
			.giveBattleOrders("ada@example.com", listOf(fire("Lantern", "Meridian")), dice())
		assertThat(over.turn!!.battle!!.stage).isEqualTo(BattleStage.LANDING)
		assertThat(over.log.map { it.text }).contains("Salvo: Lantern → Meridian misses: Meridian broke away")
	}

	@Test
	fun shouldStartABattleWithNobodyInterceptedWhenAShipStaysInSpaceNextToOthers() {
		// R80
		val game = arrived.answerInterception("ada@example.com", null, dice())
			.landShip("grace@example.com", ShipLocation.SPACE, LANDS_SAFELY)

		val battle = game.turn!!.battle!!
		assertThat(battle.ships).containsExactly(BattleShip("Wayfarer", "ada@example.com"), BattleShip("Meridian", "grace@example.com"))
		assertThat(battle.interceptions).isEmpty()
		assertThat(battle.canBreakAway("Meridian")).isTrue()
		assertThat(game.log.last().text).isEqualTo("Battle at Tau Ceti: Wayfarer, Meridian")
		// Both decline: the battle is over without a shot, and the jumps go on once both have placed their ships.
		val declined = game
			.giveBattleOrders("ada@example.com", listOf(breakAway("Wayfarer")), dice())
			.giveBattleOrders("grace@example.com", listOf(breakAway("Meridian")), dice())
			.landAfterBattle("ada@example.com", mapOf("Wayfarer" to ShipLocation.SPACE), LANDS_SAFELY)
			.landAfterBattle("grace@example.com", mapOf("Meridian" to ShipLocation.SPACE), LANDS_SAFELY)
		assertThat(declined.turn!!.battle).isNull()
		assertThat(declined.turn!!.jumper()).isEqualTo("grace@example.com")
	}

	@Test
	fun shouldLetAShipReturnFireForFreeAtAShipThatTriedToInterceptItEarlierThisTurn() {
		// R86 (playtest bug, 2026-11-18): Wayfarer tried and failed, Meridian then stayed in Space (R80); the battle has no
		// interceptions, but Wayfarer's try still makes it the aggressor, so only Ada's shot costs reputation.
		val game = arrived.answerInterception("ada@example.com", "Wayfarer", dice(3, 3))
			.landShip("grace@example.com", ShipLocation.SPACE, LANDS_SAFELY)
			.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer")), dice())
			.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian")), dice(1, 1, 1, 1))

		val reputationLines = game.log.map { it.text }.filter { it.contains("for firing") }
		assertThat(reputationLines).containsExactly(
			"Orion Freight's reputation: −4 for firing at a ship that didn't try to intercept (rolled 2d6: 4) → 12"
		)
	}

	@Test
	fun shouldStartNoBattleWhenAShipLandsAtThePort() {
		val game = arrived.answerInterception("ada@example.com", null, dice()).landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY)

		assertThat(game.turn!!.battle).isNull()
	}

	private fun fireFirstRound(): Game = fighting
		.giveBattleOrders("ada@example.com", listOf(fire("Wayfarer", "Meridian")), dice())
		.giveBattleOrders("grace@example.com", listOf(fire("Meridian", "Wayfarer", rockets = true)), dice(6, 4, 3))

	private fun fire(ship: String, target: String, rockets: Boolean = false) = BattleOrder(ship, BattleAction.FIRE, target, rockets)

	private fun breakAway(ship: String) = BattleOrder(ship, BattleAction.BREAK_AWAY, null, false)

	private fun shipOf(game: Game, name: String): Ship = game.players.firstNotNullOf { it.ship(name) }

	private fun withShip(game: Game, name: String, change: (Ship) -> Ship): Game =
		game.copy(players = game.players.map { player -> player.copy(ships = player.ships.map { if (it.name == name) change(it) else it }) })
}
