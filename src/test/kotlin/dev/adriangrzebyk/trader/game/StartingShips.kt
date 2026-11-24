package dev.adriangrzebyk.trader.game

/**
 * [game], just started, with every player holding two ships in Port at the systems [picker] hands out, named from
 * [Ship.NAMES] two each in join order: the playtest setup most tests were written against. The real setup is one ship the
 * player names and places in turn 1's Bidding (R132), which [Game.placeStartingShip] and its tests cover.
 */
class StartingShips(private val picker: FakeStartingSystemPicker) {

	fun placedIn(game: Game): Game = game.copy(
		players = game.players.mapIndexed { index, player ->
			val (first, second) = picker.pickTwoDifferentSystems()
			player.copy(
				ships = listOf(
					emptyShip(Ship.NAMES[index * 2], first, ShipLocation.PORT),
					emptyShip(Ship.NAMES[index * 2 + 1], second, ShipLocation.PORT)
				)
			)
		}
	)
}
