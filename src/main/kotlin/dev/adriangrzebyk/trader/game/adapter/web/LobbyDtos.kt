package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.Player

/** A player asking to join (R120), as the lobby shows them: by request id, never by email. */
data class JoinRequestResponseDto(val id: String, val name: String, val corporationName: String) {

	constructor(player: Player, ids: JoinRequestIds) : this(ids.of(player.email), player.name, player.corporationName)
}

/** Who created an open game (R119). */
data class CreatorResponseDto(val name: String, val corporationName: String) {

	constructor(player: Player) : this(player.name, player.corporationName)
}

/**
 * One game in the list of open games (R119), for [viewerEmail]: who created it, the corporations already in, its length,
 * and where the viewer stands (`IN`, `ASKED` or `NONE`). Nothing else of the game: players' emails never leave the server.
 */
data class OpenGameResponseDto(
	val id: String,
	val turns: Int,
	val creator: CreatorResponseDto,
	val corporations: List<String>,
	val players: Int,
	val maxPlayers: Int,
	val standing: String
) {

	constructor(game: Game, viewerEmail: String) : this(
		game.id,
		game.turns,
		CreatorResponseDto(game.creator()),
		game.players.map { it.corporationName },
		game.players.size,
		Game.MAX_PLAYERS,
		game.lobbyStandingOf(viewerEmail).name
	)
}
