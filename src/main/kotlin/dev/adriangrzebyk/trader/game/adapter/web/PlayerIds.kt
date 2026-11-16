package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game

/**
 * The ids a game's players go by in everything sent to a browser: `p1` for the first to join (the creator), `p2` for the
 * second, and so on. Inside the game a player is keyed by their email, which is theirs alone: no other player is ever
 * sent it (BACKWARD_COMPATIBILITY.md §6). An id is the player's place in join order, which never changes, since players
 * are only ever added and only before the start; so it needs no storing, and means nothing outside its game.
 */
class PlayerIds(private val emails: List<String>) {

	constructor(game: Game) : this(game.players.map { it.email })

	/** The id of the player with [email], who must be in the game. */
	fun of(email: String): String {
		val place = emails.indexOf(email)
		check(place >= 0) { "No player of this game has that email" }
		return "p${place + 1}"
	}

	/** The ids of the players with [emails], in the same order. */
	fun ofAll(emails: List<String>): List<String> = emails.map { of(it) }
}
