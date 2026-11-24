package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E09: buying ships at a shipyard, leaving the yard, refitting and selling (R60–R67).
class GameShipyardTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada's Wayfarer is at Tau Ceti and Lantern at Beta Hydri; Grace's Meridian at Gamma Leporis, Halcyon at Mu Herculis (R40). */
	private val started = Game.lobby("game-1", 20, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}

	/** Turn 1's Investment. */
	private val investing = toInvestment(started)

	/** A Clarinet with 4 Cargo, Light weapons and Safe jump, like a starting ship: 160 + 16 + 17 + 10 HT and 1 crew. */
	private val clarinetModules = List(4) { Module.CARGO } + Module.LIGHT_WEAPONS + Module.SAFE_JUMP

	private fun toInvestment(game: Game): Game = (1..5).fold(game) { current, _ -> current.advancePhase(FakeDice(2, 1)) }

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	private fun shipOf(game: Game, name: String): Ship = game.players.flatMap { it.ships }.first { it.name == name }

	private fun buySpiral(game: Game, crewClass: CrewClass = CrewClass.B): Game =
		game.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.CLARINET, clarinetModules, crewClass)

	private fun withFactoryFor(game: Game, email: String): Game =
		game.copy(players = game.players.map { if (it.email == email) it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 6, 0))) else it })

	private fun withCargo(game: Game, shipName: String, cargo: Map<Goods, Int>): Game = game.copy(
		players = game.players.map { player -> player.copy(ships = player.ships.map { if (it.name == shipName) it.copy(cargo = cargo) else it }) }
	)

	private fun withWarehouse(game: Game, email: String, warehouse: Warehouse): Game =
		game.copy(players = game.players.map { if (it.email == email) it.copy(warehouses = listOf(warehouse)) else it })

	// ── E09-S01, S02: buying ───────────────────────────────────────────────────────

	@Test
	fun shouldBuyAShipIntoTheYardAndChargeHullModulesAndCrew() {
		val game = buySpiral(investing, CrewClass.A)

		val spiral = shipOf(game, "Spiral")
		assertThat(spiral.location).isEqualTo(ShipLocation.SHIPYARD)
		assertThat(spiral.system).isEqualTo(TAU_CETI)
		assertThat(spiral.cargo).isEmpty()
		assertThat(spiral.crewClass).isEqualTo(CrewClass.A)
		assertThat(spiral.boughtOnTurn).isEqualTo(1)
		// 160 hull + 4 × 4 + 17 + 10 modules + 1 crew × 20 (class A)
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(investing, "ada@example.com").money - 223)
	}

	@Test
	fun shouldLogTheOrderWithoutHullOrPrice() {
		val game = buySpiral(investing)

		assertThat(game.log.last()).isEqualTo(LogEntry.event(1, "Orion Freight ordered Spiral at the Tau Ceti shipyard"))
	}

	@Test
	fun shouldBuyAShipWithNoShipAtTheYard() {
		// R61: in Investment a player is everywhere; Grace has no ship at Epsilon Eridani.
		val game = investing.buyShip("grace@example.com", "Coral", EPSILON_ERIDANI, Hull.PHOENIX, emptyList(), CrewClass.D)

		assertThat(shipOf(game, "Coral").system).isEqualTo(EPSILON_ERIDANI)
		assertThat(playerOf(game, "grace@example.com").money).isEqualTo(playerOf(investing, "grace@example.com").money - 87)
	}

	@Test
	fun shouldTrimTheNameAndRefuseOneThatIsEmptyOrTooLong() {
		assertThat(
			investing.buyShip("ada@example.com", "  Sea Wolf ", TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D).players[0].ships.last().name
		)
			.isEqualTo("Sea Wolf")
		listOf(null, "   ", "A".repeat(21)).forEach { name ->
			assertThatThrownBy { investing.buyShip("ada@example.com", name, TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D) }
				.isInstanceOf(InvalidInvestmentException::class.java)
				.hasMessage("Choose a name of 1 to 20 characters.")
		}
	}

	@Test
	fun shouldRefuseANameThatCannotBeAShipsAddressInAUrl() {
		// The refit and sell endpoints carry the name in the path (E09 code review).
		listOf("A/B", "a\\b", ".", "..", "--").forEach { name ->
			assertThatThrownBy { investing.buyShip("ada@example.com", name, TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D) }
				.isInstanceOf(InvalidInvestmentException::class.java)
				.hasMessage(Ship.NAME_CHARACTERS_MESSAGE)
		}
		assertThat(investing.buyShip("ada@example.com", "St. Jude's Ark-2", TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D).freeShipNames())
			.isNotEmpty()
	}

	@Test
	fun shouldRefuseANameAnyShipInTheGameHasIgnoringCase() {
		// R62: Meridian is Grace's starting ship.
		assertThatThrownBy { investing.buyShip("ada@example.com", "meridian", TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("There's already a ship called meridian in this game.")
	}

	@Test
	fun shouldSuggestTheNamesNobodyUsesYet() {
		val game = buySpiral(investing)

		// The two players' starting ships took the first four names.
		assertThat(investing.freeShipNames()).startsWith("Bright Reach", "Corvid").contains("Spiral", "Regent")
		assertThat(investing.freeShipNames()).hasSize(Ship.NAMES.size - 4)
		assertThat(game.freeShipNames()).doesNotContain("Spiral").hasSize(Ship.NAMES.size - 5)
	}

	@Test
	fun shouldRefuseASystemWithoutAShipyard() {
		assertThatThrownBy { investing.buyShip("ada@example.com", "Spiral", GAMMA_LEPORIS, Hull.PHOENIX, emptyList(), CrewClass.D) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Gamma Leporis has no shipyard. Choose Beta Hydri, Epsilon Eridani or Tau Ceti.")
	}

	@Test
	fun shouldRefuseMilitaryHullsAndModules() {
		// R56: only through an opportunity.
		assertThatThrownBy { investing.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.SWORD, emptyList(), CrewClass.D) }
			.hasMessage("The Sword is military: it can't be bought at a shipyard.")
		assertThatThrownBy { investing.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.CLARINET, listOf(Module.ARSENAL), CrewClass.D) }
			.hasMessage("Arsenal is military: it can't be bought at a shipyard.")
	}

	@Test
	fun shouldRefuseMoreModulesThanTheHullHolds() {
		assertThatThrownBy {
			investing.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.CORCO_GAMMA, List(3) { Module.CARGO }, CrewClass.D)
		}.isInstanceOf(InvalidFittingException::class.java).hasMessage("A Corco Gamma holds 2 modules, not 3.")
	}

	@Test
	fun shouldRefuseAShipThePlayerCannotPayInFull() {
		// 250 hull + 18 × 4 + 4 crew × 20 = 402 HT; Ada has 300 HT.
		assertThatThrownBy { investing.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.LEVIATHAN, List(18) { Module.CARGO }, CrewClass.A) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("This ship costs 402 HT; you have 300 HT.")
	}

	@Test
	fun shouldOnlyBuyInInvestment() {
		assertThatThrownBy { buySpiral(started) }.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("Investment isn't open.")
		assertThat(shipOf(buySpiral(withFactoryFor(investing, "ada@example.com")), "Spiral").location).isEqualTo(ShipLocation.SHIPYARD)
	}

	// ── E09-S03: in the yard, and leaving it ───────────────────────────────────────

	@Test
	fun shouldStayInTheYardForTheWholeNextTurnAndLeaveItInItsInvestment() {
		val bought = buySpiral(investing)
		val turn2Bidding = bought.signalDoneInvesting(
			"ada@example.com",
			NO_CONTROL_ROLLS
		).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS)

		assertThat(shipOf(turn2Bidding, "Spiral").location).isEqualTo(ShipLocation.SHIPYARD)
		val turn2Investment = toInvestment(turn2Bidding)
		assertThat(shipOf(turn2Investment, "Spiral").location).isEqualTo(ShipLocation.PORT)
		assertThat(shipOf(turn2Investment, "Spiral").system).isEqualTo(TAU_CETI)
		assertThat(turn2Investment.log.map { it.text }).contains("Spiral leaves the Tau Ceti shipyard")
	}

	@Test
	fun shouldStayInTheYardThroughOpportunitySoItNeverTakesThatOutput() {
		// R63, R102: output (Opportunity) comes before the ship leaves the yard (when Investment opens).
		val bought = buySpiral(withFactoryFor(investing, "ada@example.com"))
		val turn2 = bought.signalDoneInvesting("ada@example.com", NO_CONTROL_ROLLS).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS)
		val opportunity = (1..4).fold(turn2) { current, _ -> current.advancePhase(FakeDice(2, 1)) }

		assertThat(opportunity.turn!!.phase).isEqualTo(TurnPhase.OPPORTUNITY)
		assertThat(shipOf(opportunity, "Spiral").location).isEqualTo(ShipLocation.SHIPYARD)
		assertThatThrownBy {
			opportunity.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Spiral", 6)))
		}.isInstanceOf(InvalidCollectException::class.java)
		val investing = opportunity.collectOutput("ada@example.com", emptyList())
			.finishOpportunities("ada@example.com").finishOpportunities("grace@example.com")
		assertThat(shipOf(investing, "Spiral").location).isEqualTo(ShipLocation.PORT)
		assertThat(investing.log.last().text).isEqualTo("Spiral leaves the Tau Ceti shipyard")
	}

	@Test
	fun shouldNotJumpAShipStillInTheYard() {
		val ship = Ship.commission("Spiral", TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D, 1)

		assertThatThrownBy {
			ship.jumpTo(GAMMA_LEPORIS)
		}.isInstanceOf(InvalidJumpException::class.java).hasMessage("Spiral is still in the shipyard.")
	}

	@Test
	fun shouldNotCountAShipInTheYardAsPresenceForTrading() {
		val game = buySpiral(investing)

		assertThat(playerOf(game, "ada@example.com").shipsInPortAt(TAU_CETI).map { it.name }).containsExactly("Wayfarer")
	}

	@Test
	fun shouldEndTheJumpSlotAtOnceOfAPlayerWithNothingToJump() {
		// R64: Ada sold both her ships, so turn 2 doesn't wait for her.
		val sold = investing.sellShip("ada@example.com", "Wayfarer").sellShip("ada@example.com", "Lantern")
		val jumps = sold.signalDoneInvesting(
			"ada@example.com",
			NO_CONTROL_ROLLS
		).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS).advancePhase(FakeDice(2, 1))
		val first = jumps.turn!!.initiative.first()
		val ordered = jumps.pickTurnOrderPlace(first, 1, NO_NEWS)

		val done = if (ordered.turn!!.jumper() == "grace@example.com") ordered.finishJumping("grace@example.com") else ordered
		assertThat(done.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(done.log.map { it.text }).contains("Orion Freight has no ship to jump")
	}

	// ── E09-S04: refitting ─────────────────────────────────────────────────────────

	@Test
	fun shouldAddAModuleAtAShipyardForItsPriceAndCrew() {
		// Wayfarer is in port at Tau Ceti, which has a shipyard; Passenger is 15 HT + 1 crew × 10 (class B).
		val game = investing.sellShip("ada@example.com", "Lantern").removeModule("ada@example.com", "Wayfarer", Module.LIGHT_WEAPONS)
			.addModule("ada@example.com", "Wayfarer", Module.PASSENGER)
		val before = playerOf(investing.sellShip("ada@example.com", "Lantern"), "ada@example.com").money

		assertThat(shipOf(game, "Wayfarer").modules).contains(Module.PASSENGER).doesNotContain(Module.LIGHT_WEAPONS)
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(before + 8 - 25)
	}

	@Test
	fun shouldRefitAShipStillInTheYard() {
		val game = investing.buyShip("ada@example.com", "Spiral", TAU_CETI, Hull.PHOENIX, emptyList(), CrewClass.D)
			.addModule("ada@example.com", "Spiral", Module.CARGO)

		assertThat(shipOf(game, "Spiral").modules).containsExactly(Module.CARGO)
	}

	@Test
	fun shouldRefitInPortAtASystemWithoutAShipyard() {
		// R65 (creator, 2026-11-17): modules are fitted in any port. Grace's Meridian is in port at Gamma Leporis, which has no shipyard.
		val game = investing.removeModule("grace@example.com", "Meridian", Module.LIGHT_WEAPONS)
			.addModule("grace@example.com", "Meridian", Module.PASSENGER)

		assertThat(shipOf(game, "Meridian").modules).contains(Module.PASSENGER).doesNotContain(Module.LIGHT_WEAPONS)
	}

	@Test
	fun shouldRefuseARefitOfAShipInSpaceAtAShipyardSystem() {
		// Refit needs the Port (creator, 2026-11-17): Space is for battles, Planets for smuggling.
		val inSpace = investing.copy(
			players = investing.players.map { player ->
				player.copy(
					ships = player.ships.map {
						if (it.name ==
							"Wayfarer"
						) {
							it.landAt(ShipLocation.SPACE)
						} else {
							it
						}
					}
				)
			}
		)

		assertThatThrownBy { inSpace.addModule("ada@example.com", "Wayfarer", Module.CARGO) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer must be in port to refit.")
	}

	// ── R89: replacing a crew ─────────────────────────────────────────────────────

	@Test
	fun shouldReplaceACrewInPortForTheNewCrewsFullPrice() {
		// Wayfarer (a starting Clarinet, class B, 1 crew) is in port at Tau Ceti; a class A crew costs 1 × 20 HT, the old one isn't paid back.
		val before = playerOf(investing, "ada@example.com").money
		val game = investing.replaceCrew("ada@example.com", "Wayfarer", CrewClass.A)

		assertThat(shipOf(game, "Wayfarer").crewClass).isEqualTo(CrewClass.A)
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(before - 20)
	}

	@Test
	fun shouldReplaceTheCrewOfAShipStillInTheYard() {
		val game = buySpiral(investing, CrewClass.D).replaceCrew("ada@example.com", "Spiral", CrewClass.C)

		assertThat(shipOf(game, "Spiral").crewClass).isEqualTo(CrewClass.C)
	}

	@Test
	fun shouldRefuseTheSameClassAShipInSpaceTooLittleMoneyOrOutsideInvestment() {
		assertThatThrownBy { investing.replaceCrew("ada@example.com", "Wayfarer", CrewClass.B) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer's crew is already class B.")
		val inSpace = investing.copy(
			players = investing.players.map { player ->
				player.copy(ships = player.ships.map { if (it.name == "Wayfarer") it.landAt(ShipLocation.SPACE) else it })
			}
		)
		assertThatThrownBy { inSpace.replaceCrew("ada@example.com", "Wayfarer", CrewClass.A) }
			.hasMessage("Wayfarer must be in port to refit.")
		val broke = investing.copy(players = investing.players.map { if (it.email == "ada@example.com") it.pay(it.money - 5, "Test") else it })
		assertThatThrownBy { broke.replaceCrew("ada@example.com", "Wayfarer", CrewClass.A) }
			.hasMessage("A class A crew for Wayfarer costs 20 HT; you have 5 HT.")
		assertThatThrownBy { started.replaceCrew("ada@example.com", "Wayfarer", CrewClass.A) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldRefuseAModuleTheHullHasNoRoomForOrAMilitaryOne() {
		assertThatThrownBy { investing.addModule("ada@example.com", "Wayfarer", Module.CARGO) }
			.isInstanceOf(InvalidFittingException::class.java)
		assertThatThrownBy { investing.addModule("ada@example.com", "Wayfarer", Module.DOGFIGHT) }
			.hasMessage("Dogfight is military: it can't be bought.")
	}

	@Test
	fun shouldRefuseToRemoveACargoModuleWhoseSpaceIsInUse() {
		val loaded = withCargo(investing, "Wayfarer", mapOf(Goods.ISO to 8))

		assertThatThrownBy { loaded.removeModule("ada@example.com", "Wayfarer", Module.CARGO) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer carries 8 units of POLY and ISO; without that module it would have only 6 cargo spaces.")
	}

	@Test
	fun shouldRefuseToRemoveAModuleTheShipHasNot() {
		assertThatThrownBy { investing.removeModule("ada@example.com", "Wayfarer", Module.PASSENGER) }
			.hasMessage("Wayfarer has no Passenger module.")
	}

	// ── E09-S05: selling ───────────────────────────────────────────────────────────

	@Test
	fun shouldSellAShipForHalfItsHullAndModulesAndLogIt() {
		val game = investing.sellShip("ada@example.com", "Wayfarer")

		assertThat(playerOf(game, "ada@example.com").ships.map { it.name }).containsExactly("Lantern")
		// (160 + 4 × 4 + 17 + 10) / 2, rounded down
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(investing, "ada@example.com").money + 101)
		assertThat(game.log.last()).isEqualTo(LogEntry.event(1, "Orion Freight sold Wayfarer"))
	}

	@Test
	fun shouldMoveTheCargoOfASoldShipIntoTheWarehouseThereAsFarAsItFitsAndLoseTheRest() {
		// One block: 10 spaces, 4 used. ISO fills the 6 left, so 2 POLY and 1 ISO are lost; FOOD always fits.
		val stocked = withWarehouse(investing, "ada@example.com", Warehouse(TAU_CETI, 1, mapOf(Goods.POLY to 4)))
		val loaded = withCargo(stocked, "Wayfarer", mapOf(Goods.FOOD to 9, Goods.ISO to 7, Goods.POLY to 2))

		val warehouse = playerOf(loaded.sellShip("ada@example.com", "Wayfarer"), "ada@example.com").warehouseAt(TAU_CETI)!!

		assertThat(warehouse.cargo).isEqualTo(mapOf(Goods.POLY to 4, Goods.FOOD to 9, Goods.ISO to 6))
	}

	@Test
	fun shouldSellAShipWithCargoAndNoWarehouseThereLosingTheCargo() {
		val loaded = withCargo(investing, "Wayfarer", mapOf(Goods.ISO to 3))

		val game = loaded.sellShip("ada@example.com", "Wayfarer")

		assertThat(playerOf(game, "ada@example.com").warehouses).isEmpty()
		assertThat(game.log.last().text).doesNotContain("ISO") // cargo is private (R29)
	}

	@Test
	fun shouldSellAShipStillInTheYard() {
		val game = buySpiral(investing).sellShip("ada@example.com", "Spiral")

		assertThat(playerOf(game, "ada@example.com").ships.map { it.name }).doesNotContain("Spiral")
	}

	@Test
	fun shouldRefuseToSellAShipThatIsNotTheirsOrOutsideInvestment() {
		assertThatThrownBy { investing.sellShip("ada@example.com", "Meridian") }.hasMessage("Choose one of your ships.")
		assertThatThrownBy { started.sellShip("ada@example.com", "Wayfarer") }.hasMessage("Investment isn't open.")
	}
}
