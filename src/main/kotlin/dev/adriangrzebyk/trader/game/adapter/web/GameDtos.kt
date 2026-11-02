package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.Turn
import java.math.BigDecimal

// BigDecimal, not Int: Jackson would otherwise round 2.5 down to 2 and create a game the player didn't ask for.
data class CreateGameRequestDto(val turns: BigDecimal? = null) {

	/** The length as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeTurns(): Int? = turns?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/** One player's view of a game: public state for everyone, plus [you] with what only the viewer may see (R29). */
data class GameResponseDto(
	val id: String,
	val turns: Int,
	val status: String,
	val creatorEmail: String,
	val players: List<PlayerResponseDto>,
	val turn: TurnResponseDto?,
	val log: List<String>,
	val you: YouResponseDto?
) {

	/** Only [viewerEmail]'s own money goes into the response; null [you] for a viewer who isn't a player. */
	constructor(game: Game, viewerEmail: String) : this(
		game.id,
		game.turns,
		game.status.name,
		game.creatorEmail,
		game.players.map { PlayerResponseDto(it) },
		game.turn?.let { TurnResponseDto(it) },
		game.log,
		game.players.firstOrNull { it.email == viewerEmail }?.let { YouResponseDto(it.money) }
	)
}

/** Public: no money here, ever (R29). */
data class PlayerResponseDto(val email: String, val name: String, val corporationName: String, val ships: List<ShipResponseDto>) {

	constructor(player: Player) : this(player.email, player.name, player.corporationName, player.ships.map { ShipResponseDto(it) })
}

data class ShipResponseDto(val name: String, val system: String, val systemName: String, val location: String) {

	constructor(ship: Ship) : this(ship.name, ship.system.name, ship.system.displayName, ship.location.name)
}

/** Public: who is done bidding is shared, so everyone sees who the game is waiting for (D09). */
data class TurnResponseDto(val number: Int, val phase: String, val doneBidding: List<String>) {

	constructor(turn: Turn) : this(turn.number, turn.phase.name, turn.doneBidding)
}

data class YouResponseDto(val money: Int)
