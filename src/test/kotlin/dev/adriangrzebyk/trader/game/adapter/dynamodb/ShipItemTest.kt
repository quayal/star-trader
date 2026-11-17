package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Ship extends this round trip in the same PR (engineering standards §12).
class ShipItemTest {

	private val ship = Ship(
		name = "Lantern",
		system = StarSystem.BETA_HYDRI,
		location = ShipLocation.SPACE,
		cargo = mapOf(Goods.ISO to 3, Goods.FOOD to 9),
		hull = Hull.MONARCH,
		modules = listOf(Module.CARGO, Module.LIGHT_WEAPONS, Module.CARGO, Module.SAFE_JUMP),
		crewClass = CrewClass.C,
		boughtOnTurn = 4
	)

	@Test
	fun shouldReadBackEveryFieldOfAShip() {
		assertThat(ShipItem(ShipItem(ship).attributes).toShip()).isEqualTo(ship)
	}

	@Test
	fun shouldReadBackAnEmptyShip() {
		val empty = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThat(ShipItem(ShipItem(empty).attributes).toShip()).isEqualTo(empty)
	}

	@Test
	fun shouldStoreAShipUnderTheseAttributeNames() {
		assertThat(ShipItem(ship).attributes).isEqualTo(
			mapOf(
				"name" to AttributeValue.fromS("Lantern"),
				"system" to AttributeValue.fromS("BETA_HYDRI"),
				"location" to AttributeValue.fromS("SPACE"),
				"cargo" to AttributeValue.fromM(mapOf("ISO" to AttributeValue.fromN("3"), "FOOD" to AttributeValue.fromN("9"))),
				"hull" to AttributeValue.fromS("MONARCH"),
				"modules" to AttributeValue.fromL(
					listOf("CARGO", "LIGHT_WEAPONS", "CARGO", "SAFE_JUMP").map { AttributeValue.fromS(it) }
				),
				"crewClass" to AttributeValue.fromS("C"),
				"boughtOnTurn" to AttributeValue.fromN("4")
			)
		)
	}

	@Test
	fun shouldReadAShipStoredBeforeCargoExistedAsEmpty() {
		assertThat(ShipItem(ShipItem(ship).attributes - "cargo").toShip().cargo).isEmpty()
	}

	@Test
	fun shouldReadAShipStoredBeforeHullsExistedAsAClarinetWithFiveCargoModules() {
		val stored = ShipItem(ship).attributes - "hull" - "modules"

		val loaded = ShipItem(stored).toShip()

		assertThat(loaded.hull).isEqualTo(Hull.CLARINET)
		assertThat(loaded.modules).containsExactly(Module.CARGO, Module.CARGO, Module.CARGO, Module.CARGO, Module.CARGO)
		assertThat(loaded.cargoSpaces()).isEqualTo(10)
		assertThat(loaded.cargo).isEqualTo(ship.cargo)
	}

	@Test
	fun shouldReadAShipStoredBeforeShipyardsExistedAsAClassBShipThatWasNeverBought() {
		// E09: every ship was a starting ship then (R55).
		val loaded = ShipItem(ShipItem(ship).attributes - "crewClass" - "boughtOnTurn").toShip()

		assertThat(loaded.crewClass).isEqualTo(CrewClass.B)
		assertThat(loaded.boughtOnTurn).isNull()
	}

	@Test
	fun shouldNotStoreAPurchaseTurnForAShipThatWasNeverBought() {
		assertThat(ShipItem(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)).attributes).doesNotContainKey("boughtOnTurn")
	}

	@Test
	fun shouldKeepTheOrderOfItsModules() {
		val loaded = ShipItem(ShipItem(ship).attributes).toShip()

		assertThat(loaded.modules).containsExactly(Module.CARGO, Module.LIGHT_WEAPONS, Module.CARGO, Module.SAFE_JUMP)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredShipIsIncomplete() {
		assertThatThrownBy { ShipItem(ShipItem(ship).attributes - "system").toShip() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("system")
	}
}
