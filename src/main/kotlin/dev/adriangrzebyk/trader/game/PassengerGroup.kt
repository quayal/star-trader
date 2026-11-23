package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * [groups] passenger groups a player took aboard their ship [ship] in [from]'s Port, bound for [to]'s Port (R125). The
 * player owes the trip whatever happens to the ship: it is theirs, not the ship's, so a lost ship doesn't cancel it.
 * Each group takes one passenger seat of [ship] when it is settled.
 */
data class PassengerGroup(val ship: String, val from: StarSystem, val to: StarSystem, val groups: Int) {

	/** The route these groups travel. */
	fun route(): PassengerRoute = PassengerRoute(from, to)

	/** What they pay when they board (R125): 5 HT a group. */
	fun fare(): Int = groups * FARE_PER_GROUP

	/** What not delivering them costs (R125): 200% of the fare, 10 HT a group. */
	fun penalty(): Int = fare() * 2

	/** These groups and [other]'s, when both are the same ship's on the same route. */
	fun plus(other: PassengerGroup): PassengerGroup = copy(groups = groups + other.groups)

	/** Whether [other] is the same ship's on the same route. */
	fun isSameTripAs(other: PassengerGroup): Boolean = ship == other.ship && from == other.from && to == other.to

	/** What is left of these once [delivered] groups arrived: the groups that didn't (R125). */
	fun notDelivered(delivered: Int): PassengerGroup = copy(groups = groups - delivered)

	companion object {
		const val FARE_PER_GROUP = 5

		/** The reputation passengers need to travel with a corporation (R125). */
		const val MIN_REPUTATION = 15
	}
}
