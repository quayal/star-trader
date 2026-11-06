package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's membership in one game; [email] is the membership key. [money] is private to its owner (R29);
 * [marketPositions] are public (R28), and a player holds none until they earn one (R40, R43).
 */
data class Player(
	val email: String,
	val name: String,
	val corporationName: String,
	val money: Int,
	val ships: List<Ship>,
	val marketPositions: List<MarketPosition>
) {

	/** This player after paying [amount] HT. */
	fun pay(amount: Int): Player = copy(money = money - amount)

	/** This player set up for the first turn (R40): [money] HT and [ships]. */
	fun startWith(money: Int, ships: List<Ship>): Player = copy(money = money, ships = ships)

	/** Where this player's ships start, for the public log: "Corp — Wayfarer at Tau Ceti, Lantern at Mu Herculis". */
	fun startingPositions(): String = "$corporationName — " + ships.joinToString(", ") { it.position() }

	/** This player's ship called [name], or null if they have none by that name. */
	fun ship(name: String?): Ship? = ships.firstOrNull { it.name == name }

	/** This player after [ship] jumped to [destination] (see [Ship.jumpTo]). */
	fun jumpShip(ship: Ship, destination: StarSystem): Player = withShip(ship.jumpTo(destination))

	/** This player after their ship [shipName] ended up at [location] (see [Ship.landAt]). */
	fun landShip(shipName: String, location: ShipLocation): Player = withShip(checkNotNull(ship(shipName)).landAt(location))

	/**
	 * This player's initiative bid of [amount] (R27, S05-1, S05-2): a whole number of HT from 0 up to their money.
	 */
	fun bid(amount: Int?): InitiativeBid {
		if (amount == null) {
			throw InvalidBidException("Enter a whole number of HT, 0 or more.")
		}
		if (amount < 0) {
			throw InvalidBidException("A bid can't be negative. Enter 0 to bid nothing.")
		}
		if (amount > money) {
			throw InvalidBidException("You have $money HT. Enter $money or less.")
		}
		return InitiativeBid(email, amount)
	}

	/**
	 * This player's secret offer (R17), valid on its own ([Offer.place]) and never for a good they're Dictator of at
	 * [system]: they place that offer when it resolves in Trading (R38).
	 */
	fun offer(system: StarSystem, goods: Goods, type: OfferType, price: Int?): Offer {
		val offer = Offer.place(email, system, goods, type, price)
		if (isDictatorOf(system, goods)) {
			throw InvalidOfferException("You're Dictator of ${goods.name} here — you place that offer when it resolves in Trading.")
		}
		return offer
	}

	/** Whether this player is Dictator of [goods] at [system] (R28, R38). */
	fun isDictatorOf(system: StarSystem, goods: Goods): Boolean =
		marketPositions.any { it.system == system && it.goods == goods && it.tier == MarketTier.DICTATOR }

	private fun withShip(ship: Ship): Player = copy(ships = ships.map { if (it.name == ship.name) ship else it })

	companion object {
		/** A player in the lobby: money and ships arrive when the game starts (R40). */
		fun newcomer(email: String, name: String, corporationName: String) = Player(email, name, corporationName, 0, emptyList(), emptyList())
	}
}
