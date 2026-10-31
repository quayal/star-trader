package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Ship extends this round trip in the same PR (engineering standards §12).
class ShipAttributesTest {

	private val ship = Ship(name = "Lantern", system = StarSystem.BETA_HYDRI, location = ShipLocation.SPACE)

	@Test
	fun shouldReadBackEveryFieldOfAShip() {
		assertThat(ShipAttributes.from(ShipAttributes.toAttributes(ship))).isEqualTo(ship)
	}

	@Test
	fun shouldStoreAShipUnderTheseAttributeNames() {
		assertThat(ShipAttributes.toAttributes(ship)).isEqualTo(
			mapOf(
				"name" to AttributeValue.fromS("Lantern"),
				"system" to AttributeValue.fromS("BETA_HYDRI"),
				"location" to AttributeValue.fromS("SPACE"),
			),
		)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredShipIsIncomplete() {
		assertThatThrownBy { ShipAttributes.from(ShipAttributes.toAttributes(ship) - "system") }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("system")
	}
}
