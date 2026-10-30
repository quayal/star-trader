package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.Player
import java.math.BigDecimal

// BigDecimal, not Int: Jackson would otherwise round 2.5 down to 2 and create a game the player didn't ask for.
data class CreateGameRequestDto(val turns: BigDecimal? = null) {

	/** The length as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeTurns(): Int? = turns?.let { runCatching { it.intValueExact() }.getOrNull() }
}

data class GameResponseDto(val id: String, val turns: Int, val status: String, val creatorEmail: String, val players: List<PlayerResponseDto>,)

data class PlayerResponseDto(val email: String, val name: String, val corporationName: String,)

fun Game.toResponseDto() = GameResponseDto(id, turns, status.name, creatorEmail, players.map { it.toResponseDto() })

private fun Player.toResponseDto() = PlayerResponseDto(email, name, corporationName)
