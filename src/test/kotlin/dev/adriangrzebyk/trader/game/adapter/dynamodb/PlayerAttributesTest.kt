package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// A story that adds a field to Player extends this round trip in the same PR (engineering standards §12).
class PlayerAttributesTest {

	// Every field set to a non-default value, so a field the mapper drops can't round-trip by accident.
	private val player = Player(
		email = "guest@example.com",
		name = "Guest",
		corporationName = "Guest Freight",
		money = 275,
		ships = listOf(
			Ship(name = "Meridian", system = StarSystem.GAMMA_LEPORIS, location = ShipLocation.PLANET),
			Ship(name = "Halcyon", system = StarSystem.TAU_CETI, location = ShipLocation.PORT),
		),
	)

	@Test
	fun shouldReadBackEveryFieldOfAPlayerIncludingItsShips() {
		assertThat(PlayerAttributes.from(PlayerAttributes.toAttributes(player))).isEqualTo(player)
	}

	@Test
	fun shouldReadBackAPlayerWithoutShips() {
		val newcomer = Player.newcomer("host@example.com", "Host", "Host Corp")

		assertThat(PlayerAttributes.from(PlayerAttributes.toAttributes(newcomer))).isEqualTo(newcomer)
	}

	@Test
	fun shouldStoreAPlayerUnderTheseAttributeNames() {
		val attributes = PlayerAttributes.toAttributes(player)

		assertThat(attributes.keys).containsExactlyInAnyOrder("email", "name", "corporationName", "money", "ships")
		assertThat(attributes.getValue("money").n()).isEqualTo("275")
		assertThat(attributes.getValue("ships").l().map { it.m() }).isEqualTo(player.ships.map(ShipAttributes::toAttributes))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredPlayerIsIncomplete() {
		assertThatThrownBy { PlayerAttributes.from(PlayerAttributes.toAttributes(player) - "money") }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("money")
	}
}
