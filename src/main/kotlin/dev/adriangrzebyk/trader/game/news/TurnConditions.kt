package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * What this turn's events changed for the turn (R115): "this turn" runs from its News through Investment. Every value is
 * for [turn] only and reads as calm in any other. [jumpModifier] is added to every jump's target (E1, −4); [portsClosed]
 * forbids travelling to and from Ports (E10); [securityChange] and [policeChange] adjust every Planet's security and
 * every system's police (E3, E10, E12); [tripledWeaponsAt] is where black-market weapons sell for triple (E5).
 */
data class TurnConditions(
	val turn: Int,
	val jumpModifier: Int,
	val portsClosed: Boolean,
	val securityChange: Int,
	val policeChange: Int,
	val tripledWeaponsAt: StarSystem?
) {

	/** What's added to a jump's target in turn [now] (R68, E1). */
	fun jumpModifierIn(now: Int): Int = if (now == turn) jumpModifier else 0

	/** Whether Ports are closed in turn [now] (E10). */
	fun portsClosedIn(now: Int): Boolean = now == turn && portsClosed

	/** What's added to every Planet's security in turn [now] (R116; E10 +1, E12 +2). */
	fun securityChangeIn(now: Int): Int = if (now == turn) securityChange else 0

	/** What's added to every system's police in turn [now] (E3 +4, E10 +1, E12 +2; no mechanic yet). */
	fun policeChangeIn(now: Int): Int = if (now == turn) policeChange else 0

	/** The multiplier on black-market weapons sold at [system] in turn [now] (E5: 3 at Gamma Leporis, else 1). */
	fun weaponsMultiplierAt(system: StarSystem, now: Int): Int = if (now == turn && tripledWeaponsAt == system) 3 else 1

	fun withJumpModifier(delta: Int): TurnConditions = copy(jumpModifier = jumpModifier + delta)

	fun withPortsClosed(): TurnConditions = copy(portsClosed = true)

	fun withSecurityChange(delta: Int): TurnConditions = copy(securityChange = securityChange + delta)

	fun withPoliceChange(delta: Int): TurnConditions = copy(policeChange = policeChange + delta)

	fun withTripledWeaponsAt(system: StarSystem): TurnConditions = copy(tripledWeaponsAt = system)

	companion object {
		/** A turn no event has changed. */
		fun calm(turn: Int) = TurnConditions(turn, 0, false, 0, 0, null)
	}
}
