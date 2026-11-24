package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * What a log line is about, for the pop-up cards (creator, 2026-11-18): an interception try, a moment of a battle, or a sabotage (R101) at
 * [system]; or, with no system and no [players] (everyone), news that took place or an opportunity that expired (playtest,
 * 2026-11-21); or a roll at [system] that went against [players] ([NoticeKind.SETBACKS], R124; a challenge of a market position,
 * R126, names both its players, the challenger first). [players] are the players involved (the interceptor and the intercepted,
 * or everyone with a ship in the battle), so the screen can word it for each and leave a battle round to the battle report of those
 * fighting. [round] is set for [NoticeKind.BATTLE_ROUND], [interception] for [NoticeKind.INTERCEPTION]. Public, like the line.
 */
data class Notice(
	val kind: NoticeKind,
	val system: StarSystem?,
	val players: List<String>,
	val round: Int?,
	val interception: InterceptionRoll?
) {

	companion object {
		/**
		 * A roll at [system] that went against [players] (R124, creator 2026-11-23): each of them gets a card of its own,
		 * everyone else the line as a toast. [kind] is one of [NoticeKind.SETBACKS].
		 */
		/**
		 * A position roll that rose its [trader] a tier at [system] (R43, creator 2026-11-24): a card for them, in green, and for
		 * the [demoted] Dictators it replaced, in red. Everyone else reads the line in the log.
		 */
		fun positionGained(system: StarSystem, trader: String, demoted: List<String>): Notice =
			Notice(NoticeKind.POSITION_GAINED, system, listOf(trader) + demoted, null, null)

		fun setback(kind: NoticeKind, system: StarSystem, players: List<String>): Notice {
			require(kind in NoticeKind.SETBACKS) { "$kind isn't a setback." }
			return Notice(kind, system, players, null, null)
		}
	}
}

/** The moments that get a pop-up card. */
enum class NoticeKind {
	INTERCEPTION,
	BATTLE_STARTED,
	BATTLE_ROUND,
	SHIP_DESTROYED,
	BATTLE_OVER,
	SABOTAGE,
	BATTLE_SUMMARY,
	NEWS_EVENT,
	OPPORTUNITY_EXPIRED,
	SHIP_SEIZED,
	LANDING_FORCED,
	JUMP_FAILED,
	EXPEDITION_LOST,
	LOST_TO_PIRATES,
	NO_PROMOTION,
	DEMOTED,
	CHALLENGE_SUCCEEDED,
	CHALLENGE_FAILED,
	POLICE_CONTROL,
	POSITION_GAINED;

	companion object {
		/** The rolls that went against a player (R124): their card is for them alone. */
		val SETBACKS = setOf(
			SHIP_SEIZED,
			LANDING_FORCED,
			JUMP_FAILED,
			EXPEDITION_LOST,
			LOST_TO_PIRATES,
			NO_PROMOTION,
			DEMOTED,
			CHALLENGE_SUCCEEDED,
			CHALLENGE_FAILED,
			POLICE_CONTROL
		)
	}
}

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
) {

	/** The public log line (R71): "Interception: Meridian (2 + d6 3 + 1 = 6) vs Wayfarer (…): intercepted" or "…: escapes". */
	fun logText(): String = "Interception: $interceptorRoll vs $arrivingRoll: ${if (success) "intercepted" else "escapes"}"
}
