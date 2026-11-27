package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R128–R131: the Control phase closes every turn: police in Space, reputation control, recovery, the bonus.
class GameControlTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Ada's ships: Tau Ceti (police 5, legal 2) and Beta Hydri; Grace's: Gamma Leporis (police 3, legal 3) and Mu Herculis (police 0).
	private val investing = Game.lobby("game-1", 20, ada).addPlayer(grace).addPlayer(kim)
		.start("ada@example.com", START_DICE).let {
			StartingShips(
				FakeStartingSystemPicker(
					TAU_CETI to BETA_HYDRI,
					GAMMA_LEPORIS to MU_HERCULIS,
					TAU_CETI to BETA_HYDRI
				)
			).placedIn(it)
		}.let { ConnectionsPlaced(it).byEveryone() }
		.let { (1..5).fold(it) { game, _ -> game.advancePhase(FakeDice(2, 1, 3)) } }

	private val adaShip = playerOf(investing, "ada@example.com").ships.first().name
	private val graceShip = playerOf(investing, "grace@example.com").ships.first().name

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	private fun shipOf(game: Game, email: String, name: String): Ship = playerOf(game, email).ship(name)!!

	private fun inSpace(game: Game, email: String, name: String): Game =
		game.copy(players = game.players.map { if (it.email == email) it.landShip(name, ShipLocation.SPACE) else it })

	private fun withShip(game: Game, email: String, name: String, change: (Ship) -> Ship): Game = game.copy(
		players = game.players.map { player ->
			if (player.email ==
				email
			) {
				player.copy(ships = player.ships.map { if (it.name == name) change(it) else it })
			} else {
				player
			}
		}
	)

	private fun withReputation(game: Game, email: String, value: Int): Game =
		game.copy(players = game.players.map { if (it.email == email) it.withReputationChanged(value - it.reputation.value) else it })

	private fun withConnections(game: Game, email: String, connections: Connections): Game =
		game.copy(players = game.players.map { if (it.email == email) it.copy(connections = connections) else it })

	/** Everyone signals done, with [dice] for what Control rolls. */
	private fun controlled(game: Game, dice: Dice): Game =
		listOf("ada@example.com", "grace@example.com", "kim@example.com").fold(game) { state, email -> state.signalDoneInvesting(email, dice) }

	private fun lines(game: Game): List<String> = game.log.map { it.text }

	@Test
	fun shouldCloseTheTurnAtOnceWhenNothingIsToBeControlled() {
		val next = controlled(investing, NO_CONTROL_ROLLS)

		assertThat(next.turn!!.number).isEqualTo(2)
		assertThat(next.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
	}

	@Test
	fun shouldOpenControlOnlyOnceInvestmentIsClosedByEveryone() {
		val oneDone = investing.signalDoneInvesting("ada@example.com", NO_CONTROL_ROLLS)

		assertThat(oneDone.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)
	}

	@Test
	fun shouldLetAShipThatRollsAbovePoliceGoUnnoticed() {
		// R128: Tau Ceti's police is 5; a 6 evades, and an evasion isn't logged
		val game = controlled(inSpace(investing, "ada@example.com", adaShip), FakeDice(6))

		assertThat(shipOf(game, "ada@example.com", adaShip).location).isEqualTo(ShipLocation.SPACE)
		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(20)
		assertThat(lines(game).none { it.contains("police") }).isTrue()
	}

	@Test
	fun shouldForceAShipIntoPortWhenItRollsEqualToPoliceAndCostItTwoD6Reputation() {
		// R128: equal to police is caught; the 2d6 reputation loss is the next roll
		val game = controlled(inSpace(investing, "ada@example.com", adaShip), FakeDice(5, 7))

		assertThat(shipOf(game, "ada@example.com", adaShip).location).isEqualTo(ShipLocation.PORT)
		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(13 + 3)
		assertThat(lines(game)).contains(
			"$adaShip (Orion Freight) was caught by police at Tau Ceti: 5 vs police 5, forced into the Port.",
			"Orion Freight's reputation: −7 for caught by police in Space → 13"
		)
	}

	@Test
	fun shouldNeverCatchAShipWherePoliceIsZero() {
		// Mu Herculis has no police: even the lowest roll, 2, is higher
		val game = controlled(inSpace(investing, "grace@example.com", "Halcyon"), FakeDice(2))

		assertThat(shipOf(game, "grace@example.com", "Halcyon").location).isEqualTo(ShipLocation.SPACE)
	}

	@Test
	fun shouldAddTwoForAWorkingBattleCommModule() {
		// R128: 4 + 2 = 6 beats police 5, where a bare 4 would not
		val armed = withShip(inSpace(investing, "ada@example.com", adaShip), "ada@example.com", adaShip) {
			it.copy(modules = listOf(Module.BATTLE_COMM) + it.modules.drop(1))
		}

		val game = controlled(armed, FakeDice(4))

		assertThat(shipOf(game, "ada@example.com", adaShip).location).isEqualTo(ShipLocation.SPACE)
	}

	@Test
	fun shouldSendACaughtMilitaryShipToThePlanetWhereThePortRefusesItAndRollItsLanding() {
		// R128, R56, R116: Gamma Leporis has legal system 3 and security 3; police roll 3, reputation 2d6 = 6, landing 12 lands it
		val military = withShip(inSpace(investing, "grace@example.com", graceShip), "grace@example.com", graceShip) {
			it.copy(modules = listOf(Module.DOGFIGHT) + it.modules.drop(1))
		}

		val game = controlled(military, FakeDice(3, 6, 12))

		assertThat(shipOf(game, "grace@example.com", graceShip).location).isEqualTo(ShipLocation.PLANET)
		assertThat(playerOf(game, "grace@example.com").reputation.value).isEqualTo(14 + 3)
		assertThat(
			lines(game)
		).contains("$graceShip (Vega Lines) was caught by police at Gamma Leporis: 3 vs police 3, it must land on the Planet.")
	}

	@Test
	fun shouldForceACaughtMilitaryShipIntoThePortWhereThePortAdmitsIt() {
		// Tau Ceti's legal system is 2: military ships may enter (R56)
		val military = withShip(inSpace(investing, "ada@example.com", adaShip), "ada@example.com", adaShip) {
			it.copy(modules = listOf(Module.DOGFIGHT) + it.modules.drop(1))
		}

		val game = controlled(military, FakeDice(5, 4))

		assertThat(shipOf(game, "ada@example.com", adaShip).location).isEqualTo(ShipLocation.PORT)
	}

	@Test
	fun shouldRollEveryShipInSpaceAndLoseReputationForEachCatch() {
		val both = inSpace(inSpace(investing, "ada@example.com", adaShip), "grace@example.com", graceShip)

		val game = controlled(both, FakeDice(1, 2, 2, 3))

		assertThat(shipOf(game, "ada@example.com", adaShip).location).isEqualTo(ShipLocation.PORT)
		assertThat(shipOf(game, "grace@example.com", graceShip).location).isEqualTo(ShipLocation.PORT)
		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(18)
		assertThat(playerOf(game, "grace@example.com").reputation.value).isEqualTo(17 + 3)
	}

	@Test
	fun shouldRefuseToPutAMilitaryShipInAPortWithLegalSystemThreeOrFour() {
		// R56: Beta Hydri's legal system is 4
		val ship = emptyShip("Test", BETA_HYDRI, ShipLocation.SPACE).copy(modules = listOf(Module.DOGFIGHT))

		assertThat(ship.mayEnterPort()).isFalse()
		assertThat(ship.copy(system = TAU_CETI).mayEnterPort()).isTrue()
		assertThat(ship.copy(modules = listOf(Module.CARGO)).mayEnterPort()).isTrue()
		// R139: asked about a destination, its Port decides
		assertThat(ship.mayEnterPort(TAU_CETI)).isTrue()
		assertThat(ship.copy(system = TAU_CETI).mayEnterPort(BETA_HYDRI)).isFalse()
	}

	@Test
	fun shouldRefuseAShipWithIllegalGoodsInAPortWithLegalSystemThreeOrFour() {
		// R56 extended (creator, 2026-11-27): slaves, drugs or weapons aboard keep a ship out of the three lawful Ports
		val drugs = SpecialLot(SpecialGoods.DRUGS, 3, MU_HERCULIS)
		val ship = emptyShip("Test", BETA_HYDRI, ShipLocation.SPACE).copy(specialLots = listOf(drugs))

		assertThat(ship.mayEnterPort()).isFalse()
		assertThat(ship.mayEnterPort(TAU_CETI)).isTrue()
		assertThat(ship.copy(specialLots = listOf(SpecialLot(SpecialGoods.FURS, 10, BETA_HYDRI))).mayEnterPort()).isTrue()
	}

	@Test
	fun shouldLeaveAPlayerAboveFiveAlone() {
		val game = controlled(withReputation(investing, "ada@example.com", 6), NO_CONTROL_ROLLS)

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
	}

	@Test
	fun shouldClearAPlayerWhoseReputationRollIsNotHigherThanTheirReputation() {
		// R129: d6 3 against reputation 5
		val game = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(3))

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
		assertThat(lines(game)).contains("Orion Freight's reputation control (5): d6 3, nothing found.")
	}

	@Test
	fun shouldWaitForTheOtherPlayersToBackAPlayerWhoseReputationRollIsHigher() {
		val game = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))

		val audit = game.turn!!.controlAudit!!
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.CONTROL)
		assertThat(audit.subject).isEqualTo("ada@example.com")
		assertThat(audit.asked).containsExactly("grace@example.com", "kim@example.com")
		assertThat(game.viewFor("grace@example.com")!!.backingAsk).isEqualTo(audit)
		assertThat(game.viewFor("ada@example.com")!!.backingAsk).isNull()
	}

	@Test
	fun shouldRefuseAnAnswerFromThePlayerControlledOrWhenNothingIsAsked() {
		val waiting = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))

		assertThatThrownBy { waiting.answerBacking("ada@example.com", true, FakeDice.d6(1)) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("You aren't asked to back anyone.")
		assertThatThrownBy { investing.answerBacking("grace@example.com", true, FakeDice.d6(1)) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("No reputation control is waiting for a backer.")
	}

	@Test
	fun shouldMakeTheSecondRollOnceEveryoneAskedHasDeclined() {
		// d6 1 → innocent, reputation 8, +3 recovery in step 3
		val waiting = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))
		val oneDeclined = waiting.answerBacking("grace@example.com", false, FakeDice.d6(1))
		assertThat(oneDeclined.turn!!.controlAudit!!.asked).containsExactly("kim@example.com")

		val game = oneDeclined.answerBacking("kim@example.com", false, FakeDice.d6(1))

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(11)
		assertThat(lines(game)).contains("Orion Freight's reputation control: d6 1, Orion Freight was deemed innocent: reputation 8.")
		assertThat(game.log.single { it.text.contains("deemed innocent") }.notice)
			.isEqualTo(Notice.setback(NoticeKind.CONTROL_CLEARED, null, listOf("ada@example.com")))
	}

	@Test
	fun shouldStopAskingOnceSomeoneBacksAndNameThem() {
		val waiting = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", true, FakeDice.d6(1))

		assertThat(lines(game)).contains("Vega Lines backs Orion Freight.")
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
	}

	@Test
	fun shouldLowerTheSecondRollByThePoliticalConnectionsOfBothTheControlledAndTheBacker() {
		// 5 (political 6 → −1) and 4 (political 9 → −2): 6 − 1 − 2 = 3, a fine of 400 HT
		val setUp =
			withConnections(
				withConnections(withReputation(investing, "ada@example.com", 5), "ada@example.com", Connections(6, 0, 0)),
				"grace@example.com",
				Connections(9, 0, 0)
			)
		val waiting = controlled(setUp, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", true, FakeDice.d6(6))

		assertThat(lines(game)).contains("Orion Freight's reputation control: d6 6 − 3 = 3, Orion Freight was fined 400 HT: every connection −4.")
	}

	@Test
	fun shouldFineSmallTakeConnectionsAndSetReputationToSix() {
		// second d6 = 2: 100 HT, every connection −2, reputation 6, then +3
		val setUp = withConnections(withReputation(investing, "ada@example.com", 5), "ada@example.com", Connections(1, 5, 3))
		val before = playerOf(setUp, "ada@example.com").money
		val waiting = controlled(setUp, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(2)).answerBacking("kim@example.com", false, FakeDice.d6(2))

		val after = playerOf(game, "ada@example.com")
		assertThat(after.money).isEqualTo(before - 100)
		assertThat(after.connections).isEqualTo(Connections(0, 3, 1))
		assertThat(after.reputation.value).isEqualTo(9)
		assertThat(game.log.single { it.text.contains("was fined") }.notice)
			.isEqualTo(Notice.setback(NoticeKind.CONTROL_FINED, null, listOf("ada@example.com")))
	}

	@Test
	fun shouldSellAssetsWhenAFineCannotBePaidFromCash() {
		val broke = withReputation(investing, "ada@example.com", 5).let {
			it.copy(players = it.players.map { player -> if (player.email == "ada@example.com") player.copy(money = 40) else player })
		}
		val waiting = controlled(broke, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(3)).answerBacking("kim@example.com", false, FakeDice.d6(3))

		val after = playerOf(game, "ada@example.com")
		assertThat(after.ships.size).isLessThan(2)
		assertThat(lines(game).any { it.startsWith("Orion Freight sold to pay it: ") }).isTrue()
	}

	@Test
	fun shouldEliminateAPlayerTakenOverAndCostTheBackerFourPoliticalAndTwoOfTheOthers() {
		// second d6 = 4: taken over; the backer loses 4 political, 2 of each other connection and 2d6 reputation
		val setUp = withConnections(withReputation(investing, "ada@example.com", 5), "grace@example.com", Connections(6, 4, 1))
		val waiting = controlled(setUp, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", true, FakeDice(listOf(8), listOf(5)))

		val out = playerOf(game, "ada@example.com")
		assertThat(out.eliminated).isTrue()
		assertThat(out.ships).isEmpty()
		assertThat(out.money).isZero()
		assertThat(playerOf(game, "grace@example.com").connections).isEqualTo(Connections(2, 2, 0))
		assertThat(playerOf(game, "grace@example.com").reputation.value).isEqualTo(12 + 3)
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
		// R136: a red card each, the one eliminated and the one who backed them.
		assertThat(game.log.single { it.text.contains("taken over") }.notice)
			.isEqualTo(Notice.setback(NoticeKind.ELIMINATED, null, listOf("ada@example.com")))
		assertThat(game.log.filter { it.text.contains("backing") }.map { it.notice })
			.isNotEmpty.containsOnly(Notice.setback(NoticeKind.BACKING_LOST, null, listOf("grace@example.com")))
	}

	@Test
	fun shouldCloseAPlayersCorporationOnFiveOrMoreAndZeroTheBackersPoliticalConnections() {
		val setUp = withConnections(withReputation(investing, "ada@example.com", 5), "grace@example.com", Connections(6, 4, 1))
		val waiting = controlled(setUp, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", true, FakeDice(listOf(8), listOf(6)))

		assertThat(playerOf(game, "ada@example.com").eliminated).isTrue()
		assertThat(playerOf(game, "grace@example.com").connections).isEqualTo(Connections(0, 1, 0))
		assertThat(
			lines(game)
		).contains("Orion Freight's reputation control: d6 6 \u2212 1 = 5, Orion Freight was closed by the Federation and is out of the game.")
	}

	@Test
	fun shouldCostNoBackerAnythingWhenNobodyBacksAnEliminatedPlayer() {
		val waiting = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(5)).answerBacking("kim@example.com", false, FakeDice.d6(5))

		assertThat(playerOf(game, "ada@example.com").eliminated).isTrue()
		assertThat(playerOf(game, "grace@example.com").reputation.value).isEqualTo(20)
	}

	@Test
	fun shouldSkipAnEliminatedPlayerInEveryLaterPhaseAndKeepThemInTheGame() {
		val waiting = controlled(withReputation(investing, "ada@example.com", 5), FakeDice.d6(6))
		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(5)).answerBacking("kim@example.com", false, FakeDice.d6(5))

		assertThat(game.hasPlayer("ada@example.com")).isTrue()
		assertThat(game.players).hasSize(3)
		assertThat(game.status).isEqualTo(GameStatus.RUNNING)
		val closed = game.signalDoneBidding("grace@example.com", FakeDice(2, 1)).signalDoneBidding("kim@example.com", FakeDice(2, 1))
		assertThat(closed.turn!!.phase).isEqualTo(TurnPhase.INITIATIVE)
		assertThat(closed.turn!!.initiative).containsExactlyInAnyOrder("grace@example.com", "kim@example.com")
	}

	@Test
	fun shouldExamineEveryPlayerAtFiveOrBelowOneAfterAnotherInJoinOrder() {
		val setUp = withReputation(withReputation(investing, "ada@example.com", 2), "grace@example.com", 4)
		val first = controlled(setUp, FakeDice.d6(6))
		assertThat(first.turn!!.controlAudit!!.subject).isEqualTo("ada@example.com")

		val second = first.answerBacking("grace@example.com", false, FakeDice.d6(1, 6)).answerBacking("kim@example.com", false, FakeDice.d6(1, 6))

		assertThat(second.turn!!.controlAudit!!.subject).isEqualTo("grace@example.com")
		assertThat(second.turn!!.controlled).containsExactly("ada@example.com", "grace@example.com")
	}

	@Test
	fun shouldExamineAPlayerWhoseReputationPoliceControlTookToFiveOrBelow() {
		// step 1 then step 2: 2d6 loss 8 takes reputation 20 to 12, which isn't ≤ 5; start at 10 → 2
		val game =
			controlled(withReputation(inSpace(investing, "ada@example.com", adaShip), "ada@example.com", 10), FakeDice(listOf(5, 8), listOf(6)))

		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(2)
		assertThat(game.turn!!.controlAudit!!.subject).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldFinishTheGameWhenOnlyOneCorporationIsLeft() {
		val twoLeft = investing.copy(players = investing.players.map { if (it.email == "kim@example.com") it.eliminate() else it })
		val waiting = withReputation(twoLeft, "ada@example.com", 5).let {
			listOf("ada@example.com", "grace@example.com").fold(it) { game, email -> game.signalDoneInvesting(email, FakeDice.d6(6)) }
		}

		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(5))

		assertThat(game.status).isEqualTo(GameStatus.FINISHED)
		assertThat(game.winners().map { it.email }).containsExactly("grace@example.com")
		assertThat(game.standings().last().eliminated).isTrue()
	}

	@Test
	fun shouldPayNoBonusAndNoRecoveryToAnEliminatedPlayer() {
		val out = investing.copy(players = investing.players.map { if (it.email == "kim@example.com") it.eliminate() else it }).let {
			withReputation(it, "kim@example.com", 30)
		}

		val game = out.signalDoneInvesting("ada@example.com", NO_CONTROL_ROLLS).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS)

		assertThat(playerOf(game, "kim@example.com").money).isZero()
		assertThat(lines(game).none { it.startsWith("Reputation bonus") && it.contains("Kessler") }).isTrue()
	}

	@Test
	fun shouldRecoverAndPayTheBonusAfterTheControls() {
		// R128 steps 3 and 4: 8 after an innocent verdict recovers to 11; a player at 27 is paid 10 HT
		val setUp = withReputation(withReputation(investing, "ada@example.com", 5), "grace@example.com", 27)
		val before = playerOf(setUp, "grace@example.com").money
		val waiting = controlled(setUp, FakeDice.d6(6))

		val game = waiting.answerBacking("grace@example.com", false, FakeDice.d6(1)).answerBacking("kim@example.com", false, FakeDice.d6(1))

		assertThat(playerOf(game, "ada@example.com").reputation.value).isEqualTo(11)
		assertThat(playerOf(game, "grace@example.com").money).isEqualTo(before + 10)
	}
}
