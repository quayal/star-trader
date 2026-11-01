package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
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
)

/** Public: no money here, ever (R29). */
data class PlayerResponseDto(val email: String, val name: String, val corporationName: String, val ships: List<ShipResponseDto>)

data class ShipResponseDto(val name: String, val system: String, val systemName: String, val location: String)

data class TurnResponseDto(val number: Int, val phase: String)

data class YouResponseDto(val money: Int)

/** Only [viewerEmail]'s own money goes into the response; null [GameResponseDto.you] for a viewer who isn't a player. */
fun Game.toResponseDto(viewerEmail: String) = GameResponseDto(
	id,
	turns,
	status.name,
	creatorEmail,
	players.map { it.toResponseDto() },
	turn?.let { TurnResponseDto(it.number, it.phase.name) },
	log,
	players.firstOrNull { it.email == viewerEmail }?.let { YouResponseDto(it.money) }
)

private fun Player.toResponseDto() = PlayerResponseDto(email, name, corporationName, ships.map { it.toResponseDto() })

private fun Ship.toResponseDto() = ShipResponseDto(name, system.name, system.displayName, location.name)
