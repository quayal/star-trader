package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Player
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Translates a [Game] to and from its DynamoDB item: one item per game, players as a list of maps.
 * Every field is mapped by name, so a field added to [Game] or [Player] doesn't compile here until it's
 * read back; [GameItemMapperTest]'s round trip then catches a field that isn't written.
 */
internal object GameItemMapper {

	const val ID = "id"
	const val VERSION = "version"
	private const val TURNS = "turns"
	private const val STATUS = "status"
	private const val CREATOR_EMAIL = "creatorEmail"
	private const val PLAYERS = "players"
	private const val EMAIL = "email"
	private const val NAME = "name"
	private const val CORPORATION_NAME = "corporationName"

	fun toItem(game: Game, version: Long): Map<String, AttributeValue> = mapOf(
		ID to AttributeValue.fromS(game.id),
		TURNS to AttributeValue.fromN(game.turns.toString()),
		STATUS to AttributeValue.fromS(game.status.name),
		CREATOR_EMAIL to AttributeValue.fromS(game.creatorEmail),
		PLAYERS to AttributeValue.fromL(game.players.map { AttributeValue.fromM(toAttributes(it)) }),
		VERSION to AttributeValue.fromN(version.toString()),
	)

	fun toGame(item: Map<String, AttributeValue>): Game = Game(
		id = item.attribute(ID).s(),
		turns = item.attribute(TURNS).n().toInt(),
		status = GameStatus.valueOf(item.attribute(STATUS).s()),
		creatorEmail = item.attribute(CREATOR_EMAIL).s(),
		players = item.attribute(PLAYERS).l().map { toPlayer(it.m()) },
	)

	fun version(item: Map<String, AttributeValue>): Long = item.attribute(VERSION).n().toLong()

	private fun toAttributes(player: Player): Map<String, AttributeValue> = mapOf(
		EMAIL to AttributeValue.fromS(player.email),
		NAME to AttributeValue.fromS(player.name),
		CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
	)

	private fun toPlayer(attributes: Map<String, AttributeValue>): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
	)

	private fun Map<String, AttributeValue>.attribute(name: String): AttributeValue =
		this[name] ?: throw IllegalStateException("Stored game item has no '$name' attribute")
}
