package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Translates the top level of a [Game] to and from its DynamoDB item: one item per game, nested objects as maps
 * and lists, each written by its own mapper ([PlayerAttributes], [TurnAttributes]).
 * Every field is mapped by name, so a field added to the domain doesn't compile here until it's read back;
 * [GameItemMapperTest]'s round trip then catches a field that isn't written (engineering standards §12).
 */
internal object GameItemMapper {

	const val ID = "id"
	const val VERSION = "version"
	private const val TURNS = "turns"
	private const val STATUS = "status"
	private const val CREATOR_EMAIL = "creatorEmail"
	private const val PLAYERS = "players"
	private const val TURN = "turn"
	private const val LOG = "log"

	fun toItem(game: Game, version: Long): Map<String, AttributeValue> = mapOf(
		ID to AttributeValue.fromS(game.id),
		TURNS to number(game.turns),
		STATUS to AttributeValue.fromS(game.status.name),
		CREATOR_EMAIL to AttributeValue.fromS(game.creatorEmail),
		PLAYERS to AttributeValue.fromL(game.players.map { AttributeValue.fromM(PlayerAttributes.toAttributes(it)) }),
		// Explicit NULL rather than a missing attribute: "not started" is a value, not an old item.
		TURN to (game.turn?.let { AttributeValue.fromM(TurnAttributes.toAttributes(it)) } ?: AttributeValue.fromNul(true)),
		LOG to AttributeValue.fromL(game.log.map { AttributeValue.fromS(it) }),
		VERSION to number(version),
	)

	fun toGame(item: Map<String, AttributeValue>): Game = Game(
		id = item.attribute(ID).s(),
		turns = item.attribute(TURNS).n().toInt(),
		status = GameStatus.valueOf(item.attribute(STATUS).s()),
		creatorEmail = item.attribute(CREATOR_EMAIL).s(),
		players = item.attribute(PLAYERS).l().map { PlayerAttributes.from(it.m()) },
		turn = item.attribute(TURN).takeUnless { it.nul() == true }?.let { TurnAttributes.from(it.m()) },
		log = item.attribute(LOG).l().map { it.s() },
	)

	fun version(item: Map<String, AttributeValue>): Long = item.attribute(VERSION).n().toLong()
}
