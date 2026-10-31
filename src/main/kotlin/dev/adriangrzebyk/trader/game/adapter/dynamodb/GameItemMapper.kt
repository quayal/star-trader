package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Translates a [Game] to and from its DynamoDB item: one item per game, nested objects as maps and lists.
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
	private const val EMAIL = "email"
	private const val NAME = "name"
	private const val CORPORATION_NAME = "corporationName"
	private const val MONEY = "money"
	private const val SHIPS = "ships"
	private const val SYSTEM = "system"
	private const val LOCATION = "location"
	private const val NUMBER = "number"
	private const val PHASE = "phase"

	fun toItem(game: Game, version: Long): Map<String, AttributeValue> = mapOf(
		ID to AttributeValue.fromS(game.id),
		TURNS to number(game.turns),
		STATUS to AttributeValue.fromS(game.status.name),
		CREATOR_EMAIL to AttributeValue.fromS(game.creatorEmail),
		PLAYERS to AttributeValue.fromL(game.players.map { AttributeValue.fromM(toAttributes(it)) }),
		// Explicit NULL rather than a missing attribute: "not started" is a value, not an old item.
		TURN to (game.turn?.let { AttributeValue.fromM(toAttributes(it)) } ?: AttributeValue.fromNul(true)),
		LOG to AttributeValue.fromL(game.log.map { AttributeValue.fromS(it) }),
		VERSION to number(version),
	)

	fun toGame(item: Map<String, AttributeValue>): Game = Game(
		id = item.attribute(ID).s(),
		turns = item.attribute(TURNS).n().toInt(),
		status = GameStatus.valueOf(item.attribute(STATUS).s()),
		creatorEmail = item.attribute(CREATOR_EMAIL).s(),
		players = item.attribute(PLAYERS).l().map { toPlayer(it.m()) },
		turn = item.attribute(TURN).takeUnless { it.nul() == true }?.let { toTurn(it.m()) },
		log = item.attribute(LOG).l().map { it.s() },
	)

	fun version(item: Map<String, AttributeValue>): Long = item.attribute(VERSION).n().toLong()

	private fun toAttributes(player: Player): Map<String, AttributeValue> = mapOf(
		EMAIL to AttributeValue.fromS(player.email),
		NAME to AttributeValue.fromS(player.name),
		CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
		MONEY to number(player.money),
		SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(toAttributes(it)) }),
	)

	private fun toPlayer(attributes: Map<String, AttributeValue>): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
		money = attributes.attribute(MONEY).n().toInt(),
		ships = attributes.attribute(SHIPS).l().map { toShip(it.m()) },
	)

	private fun toAttributes(ship: Ship): Map<String, AttributeValue> = mapOf(
		NAME to AttributeValue.fromS(ship.name),
		SYSTEM to AttributeValue.fromS(ship.system.name),
		LOCATION to AttributeValue.fromS(ship.location.name),
	)

	private fun toShip(attributes: Map<String, AttributeValue>): Ship = Ship(
		name = attributes.attribute(NAME).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		location = ShipLocation.valueOf(attributes.attribute(LOCATION).s()),
	)

	private fun toAttributes(turn: Turn): Map<String, AttributeValue> = mapOf(
		NUMBER to number(turn.number),
		PHASE to AttributeValue.fromS(turn.phase.name),
	)

	private fun toTurn(attributes: Map<String, AttributeValue>): Turn = Turn(
		number = attributes.attribute(NUMBER).n().toInt(),
		phase = TurnPhase.valueOf(attributes.attribute(PHASE).s()),
	)

	private fun number(value: Number): AttributeValue = AttributeValue.fromN(value.toString())

	private fun Map<String, AttributeValue>.attribute(name: String): AttributeValue =
		this[name] ?: throw IllegalStateException("Stored game item has no '$name' attribute")
}
