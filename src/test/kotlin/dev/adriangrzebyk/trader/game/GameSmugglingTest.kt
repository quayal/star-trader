package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TurnConditions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R116–R118 (creator, 2026-11-20): landing on a Planet past its security. Tau Ceti's security is 7.
class GameSmugglingTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	private val jumping = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI),
			START_DICE
		).let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(1, 1, 2, 1, 2))
		.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)
		.pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

	/** Grace's Meridian, carrying 3 weapons for Gamma Leporis, has just arrived at Tau Ceti. */
	private val arrived = withGrace(jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI, JUMPS_SUCCEED)) { player ->
		player.copy(
			ships = player.ships.map {
				if (it.name == "Meridian") it.copy(specialLots = listOf(SpecialLot(SpecialGoods.WEAPONS, 3, GAMMA_LEPORIS))) else it
			}
		)
	}

	private fun withGrace(game: Game, change: (Player) -> Player): Game =
		game.copy(players = game.players.map { if (it.email == "grace@example.com") change(it) else it })

	private fun grace(game: Game): Player = game.players.first { it.email == "grace@example.com" }

	@Test
	fun shouldLandOnThePlanetAboveSecurity() {
		val landed = arrived.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(8))

		assertThat(grace(landed).ship("Meridian")!!.location).isEqualTo(ShipLocation.PLANET)
		assertThat(landed.log.last().text).isEqualTo("Meridian (Vega Lines) landed on Tau Ceti's Planet: 8 vs security 7.")
	}

	@Test
	fun shouldForceTheShipIntoThePortOnSecurityTakingItsIllegalGoodsAnd2d6Reputation() {
		val forced = arrived.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(7, 5))

		val meridian = grace(forced).ship("Meridian")!!
		assertThat(meridian.location).isEqualTo(ShipLocation.PORT)
		assertThat(meridian.specialLots).isEmpty()
		assertThat(grace(forced).reputation.value).isEqualTo(15)
		assertThat(
			forced.log.map {
				it.text
			}
		).contains("Meridian (Vega Lines) was forced into Tau Ceti's Port: 7 vs security 7. Illegal cargo confiscated.")
	}

	@Test
	fun shouldSeizeTheShipBelowSecurityEvenALegalOneWith3d6ReputationAndAgentsSteppingOff() {
		val legal = withGrace(arrived) { player ->
			player.copy(
				ships = player.ships.map { if (it.name == "Meridian") it.copy(specialLots = emptyList()) else it },
				agents = listOf(RecruitedAgent(Agent.NOVAK, AgentPosition.Aboard("Meridian")))
			)
		}

		val seized = legal.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(listOf(6, 4), listOf(3)))

		assertThat(grace(seized).ship("Meridian")).isNull()
		assertThat(grace(seized).reputation.value).isEqualTo(13)
		assertThat(grace(seized).agents).containsExactly(RecruitedAgent(Agent.NOVAK, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT)))
		assertThat(seized.log.map { it.text }).contains("Meridian (Vega Lines) was seized at Tau Ceti: 6 vs security 7.")
	}

	@Test
	fun shouldConfiscateAMilitaryShipForcedIntoThePort() {
		val military = withGrace(arrived) { player ->
			player.copy(ships = player.ships.map { if (it.name == "Meridian") it.copy(hull = Hull.DAGGER, modules = emptyList()) else it })
		}

		// a Dagger is Aerodynamic: 5 + 2 = 7
		val forced = military.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(5, 2))

		assertThat(grace(forced).ship("Meridian")).isNull()
		assertThat(
			forced.log.map {
				it.text
			}
		).contains("Meridian (Vega Lines) was forced into Tau Ceti's Port: 5 + 2 = 7 vs security 7. The ship was confiscated.")
	}

	@Test
	fun shouldAddBadgersThreeAndRevealHim() {
		val withBadger = withGrace(arrived) { it.copy(agents = listOf(RecruitedAgent(Agent.BADGER, AgentPosition.Aboard("Meridian")))) }

		val landed = withBadger.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(5))

		assertThat(grace(landed).ship("Meridian")!!.location).isEqualTo(ShipLocation.PLANET)
		assertThat(landed.log.map { it.text }).contains("Meridian (Vega Lines) landed on Tau Ceti's Planet: 5 + 3 = 8 vs security 7.")
	}

	@Test
	fun shouldKeepAShipOutOfClosedPortsAndHoldAForcedOneInSpace() {
		val epidemic = arrived.copy(news = arrived.news.withConditions(TurnConditions.calm(1).withPortsClosed().withSecurityChange(1)))

		assertThatThrownBy { epidemic.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY) }
			.isInstanceOf(InvalidLandingException::class.java)
			.hasMessage("Ports are closed this turn (epidemics): choose the Planet or Space.")
		// security 7 + 1 this turn
		val held = epidemic.landShip("grace@example.com", ShipLocation.PLANET, FakeDice(8, 2))
		assertThat(grace(held).ship("Meridian")!!.location).isEqualTo(ShipLocation.SPACE)
	}
}
