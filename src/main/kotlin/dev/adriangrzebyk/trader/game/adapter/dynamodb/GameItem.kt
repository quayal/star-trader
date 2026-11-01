package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A [Game] as its DynamoDB item: one item per game, nested objects as maps and lists, each written by its own
 * class ([PlayerItem], [TurnItem]), plus the [version] the store's optimistic update checks.
 * Every field is mapped by name, so a field added to the domain doesn't compile here until it's read back;
 * [GameItemTest]'s round trip then catches a field that isn't written (engineering standards §12).
 */
internal class GameItem(val attributes: Map<String, AttributeValue>) {

	constructor(game: Game, version: Long) : this(
		mapOf(
			ID to AttributeValue.fromS(game.id),
			TURNS to number(game.turns),
			STATUS to AttributeValue.fromS(game.status.name),
			CREATOR_EMAIL to AttributeValue.fromS(game.creatorEmail),
			PLAYERS to AttributeValue.fromL(game.players.map { AttributeValue.fromM(PlayerItem(it).attributes) }),
			// Explicit NULL rather than a missing attribute: "not started" is a value, not an old item.
			TURN to (game.turn?.let { AttributeValue.fromM(TurnItem(it).attributes) } ?: AttributeValue.fromNul(true)),
			LOG to AttributeValue.fromL(game.log.map { AttributeValue.fromS(it) }),
			VERSION to number(version)
		)
	)

	val version: Long
		get() = attributes.attribute(VERSION).n().toLong()

	fun toGame(): Game = Game(
		id = attributes.attribute(ID).s(),
		turns = attributes.attribute(TURNS).n().toInt(),
		status = GameStatus.valueOf(attributes.attribute(STATUS).s()),
		creatorEmail = attributes.attribute(CREATOR_EMAIL).s(),
		players = attributes.attribute(PLAYERS).l().map { PlayerItem(it.m()).toPlayer() },
		turn = attributes.attribute(TURN).takeUnless { it.nul() == true }?.let { TurnItem(it.m()).toTurn() },
		log = attributes.attribute(LOG).l().map { it.s() }
	)

	companion object {
		const val ID = "id"
		const val VERSION = "version"
		private const val TURNS = "turns"
		private const val STATUS = "status"
		private const val CREATOR_EMAIL = "creatorEmail"
		private const val PLAYERS = "players"
		private const val TURN = "turn"
		private const val LOG = "log"
	}
}
