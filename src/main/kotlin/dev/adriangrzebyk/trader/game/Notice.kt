package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * What a log line is about, for the pop-up cards (creator, 2026-11-18): an interception try or a moment of a battle at
 * [system]. [players] are the players involved (the interceptor and the intercepted, or everyone with a ship in the
 * battle), so the screen can word it for each and leave a battle round to the battle report of those fighting. [round]
 * is set for [NoticeKind.BATTLE_ROUND], [interception] for [NoticeKind.INTERCEPTION]. Public, like the line.
 */
data class Notice(
	val kind: NoticeKind,
	val system: StarSystem,
	val players: List<String>,
	val round: Int?,
	val interception: InterceptionRoll?
)

/** The moments that get a pop-up card. */
enum class NoticeKind { INTERCEPTION, BATTLE_STARTED, BATTLE_ROUND, SHIP_DESTROYED, BATTLE_OVER }

/**
 * One interception try (R70, R71): [interceptorEmail]'s [interceptorShip] against [arrivingEmail]'s [arrivingShip], each
 * side's roll as the log words it ("Meridian (2 + d6 3 + 1 = 6)"), and whether it [success]fully held the ship.
 */
data class InterceptionRoll(
	val interceptorEmail: String,
	val interceptorShip: String,
	val interceptorRoll: String,
	val arrivingEmail: String,
	val arrivingShip: String,
	val arrivingRoll: String,
	val success: Boolean
)
