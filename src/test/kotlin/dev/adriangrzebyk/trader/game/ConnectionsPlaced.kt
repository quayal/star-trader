package dev.adriangrzebyk.trader.game

/**
 * [game], started with [START_DICE], once every player placed their 6 starting connection points as political 3,
 * business 3, criminal 0 (R82): for tests about anything past turn 1's Bidding gate, with a factory cap of 6 (R84).
 */
class ConnectionsPlaced(private val game: Game) {

	fun byEveryone(): Game = game.players.fold(game) { placed, player -> placed.placeConnections(player.email, 3, 3, 0) }
}
