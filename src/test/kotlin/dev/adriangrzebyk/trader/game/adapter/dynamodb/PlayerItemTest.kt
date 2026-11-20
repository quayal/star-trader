package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Reputation
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Player extends this round trip in the same PR (engineering standards §12).
class PlayerItemTest {

	// Every field set to a non-default value, so a field the item drops can't round-trip by accident.
	private val player = Player(
		email = "guest@example.com",
		name = "Guest",
		corporationName = "Guest Freight",
		money = 275,
		ships = listOf(
			Ship(
				name = "Meridian",
				system = StarSystem.GAMMA_LEPORIS,
				location = ShipLocation.PLANET,
				cargo = mapOf(
					Goods.POLY to 4,
					Goods.CHIPS to 12
				),
				hull = Hull.FLUTE,
				modules = listOf(Module.ARSENAL, Module.PASSENGER),
				crewClass = Ship.STARTING_CREW_CLASS,
				boughtOnTurn = null,
				hullDamage = 0,
				damagedModules = emptyList()
			),
			Ship(
				name = "Halcyon",
				system = StarSystem.TAU_CETI,
				location = ShipLocation.PORT,
				cargo = emptyMap(),
				hull = Ship.LEGACY_HULL,
				modules = Ship.LEGACY_MODULES,
				crewClass = Ship.STARTING_CREW_CLASS,
				boughtOnTurn = null,
				hullDamage = 0,
				damagedModules = emptyList()
			)
		),
		marketPositions = listOf(
			MarketPosition(system = StarSystem.TAU_CETI, goods = Goods.POLY, tier = MarketTier.WHOLESALER),
			MarketPosition(system = StarSystem.MU_HERCULIS, goods = Goods.ISO, tier = MarketTier.DICTATOR)
		),
		warehouses = listOf(
			Warehouse(system = StarSystem.TAU_CETI, blocks = 2, cargo = mapOf(Goods.ISO to 3, Goods.CHIPS to 40)),
			Warehouse(system = StarSystem.MU_HERCULIS, blocks = 1, cargo = emptyMap())
		),
		factories = listOf(
			Factory(system = StarSystem.SIGMA_DRACONIS, goods = Goods.FOOD, size = 3, disabledTurns = 0),
			Factory(system = StarSystem.SIGMA_DRACONIS, goods = Goods.ISO, size = 5, disabledTurns = 0)
		),
		connections = Connections(political = 2, business = 7, criminal = 1),
		connectionPoints = 4,
		reputation = Reputation(13),
		loan = Loan(312, 14, 3, LoanTerm.FOUR),
		agents = listOf(
			RecruitedAgent(Agent.NOVAK, AgentPosition.Aboard("Wayfarer")),
			RecruitedAgent(Agent.JB, AgentPosition.OnOwn(StarSystem.TAU_CETI, ShipLocation.PLANET))
		)
	)

	@Test
	fun shouldReadBackEveryFieldOfAPlayerIncludingItsShips() {
		assertThat(PlayerItem(PlayerItem(player).attributes).toPlayer()).isEqualTo(player)
	}

	@Test
	fun shouldReadBackAPlayerWithoutShips() {
		val newcomer = Player.newcomer("host@example.com", "Host", "Host Corp")

		assertThat(PlayerItem(PlayerItem(newcomer).attributes).toPlayer()).isEqualTo(newcomer)
	}

	@Test
	fun shouldStoreAPlayerUnderTheseAttributeNames() {
		val attributes = PlayerItem(player).attributes

		assertThat(
			attributes.keys
		).containsExactlyInAnyOrder(
			"email",
			"name",
			"corporationName",
			"money",
			"ships",
			"marketPositions",
			"warehouses",
			"factories",
			"political",
			"business",
			"criminal",
			"connectionPoints",
			"reputation",
			"loan",
			"agents"
		)
		assertThat(attributes.getValue("money").n()).isEqualTo("275")
		assertThat(attributes.getValue("business").n()).isEqualTo("7")
		assertThat(attributes.getValue("connectionPoints").n()).isEqualTo("4")
		assertThat(attributes.getValue("ships").l().map { it.m() }).isEqualTo(player.ships.map { ShipItem(it).attributes })
	}

	@Test
	fun shouldReadAPlayerSavedBeforeConnectionsWithNoneAndNothingToPlace() {
		// E12: the game isn't published, so old items aren't kept compatible beyond this: a missing attribute is 0
		val older = PlayerItem(PlayerItem(player).attributes - "political" - "business" - "criminal" - "connectionPoints").toPlayer()

		assertThat(older.connections).isEqualTo(Connections.NONE)
		assertThat(older.connectionPoints).isZero()
		// E13: an item saved before reputation loads at the start value (R85).
		assertThat(PlayerItem(PlayerItem(player).attributes - "reputation").toPlayer().reputation).isEqualTo(Reputation.INITIAL)
		assertThat(PlayerItem(player).attributes.getValue("reputation").n()).isEqualTo("13")
	}

	@Test
	fun shouldStoreEachMarketPositionByConstantName() {
		val positions = PlayerItem(player).attributes.getValue("marketPositions").l().map { it.m() }

		assertThat(positions.first()).isEqualTo(
			mapOf(
				"system" to AttributeValue.fromS("TAU_CETI"),
				"goods" to AttributeValue.fromS("POLY"),
				"tier" to AttributeValue.fromS("WHOLESALER")
			)
		)
	}

	@Test
	fun shouldReadAPlayerStoredBeforeMarketPositionsExistedAsHoldingNone() {
		assertThat(PlayerItem(PlayerItem(player).attributes - "marketPositions").toPlayer().marketPositions).isEmpty()
	}

	@Test
	fun shouldStoreEachWarehouseAndFactoryAsItsOwnItem() {
		val attributes = PlayerItem(player).attributes

		assertThat(attributes.getValue("warehouses").l().map { it.m() }).isEqualTo(player.warehouses.map { WarehouseItem(it).attributes })
		assertThat(attributes.getValue("factories").l().map { it.m() }).isEqualTo(player.factories.map { FactoryItem(it).attributes })
	}

	@Test
	fun shouldReadAPlayerStoredBeforeWarehousesAndFactoriesExistedAsOwningNone() {
		val older = PlayerItem(PlayerItem(player).attributes - "warehouses" - "factories").toPlayer()

		assertThat(older.warehouses).isEmpty()
		assertThat(older.factories).isEmpty()
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredPlayerIsIncomplete() {
		assertThatThrownBy { PlayerItem(PlayerItem(player).attributes - "money").toPlayer() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("money")
	}
}
