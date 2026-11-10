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

	/** This player after being paid [amount] HT. */
	fun receive(amount: Int): Player = copy(money = money + amount)

	/** This player's ships in port at [system]: only they trade there (R23, R42; trading happens in port). */
	fun shipsInPortAt(system: StarSystem): List<Ship> = ships.filter { it.isInPortAt(system) }

	/**
	 * The most units this player can trade on [offer] when the counter allows [counterMax] (R34): a buy also capped by
	 * what they can afford at its price (R12), a sell by what their ships at that system hold (R10, R42). Never below 0.
	 */
	fun tradeMax(offer: Offer, counterMax: Int): Int {
		val cap = when (offer.type) {
			OfferType.BUY -> money / offer.price
			OfferType.SELL -> shipsInPortAt(offer.system).sumOf { it.holding(offer.goods) }
		}
		return minOf(counterMax, cap).coerceAtLeast(0)
	}

	/**
	 * This player after trading [amount] units on their [offer] (E05-S04), the counter allowing [counterMax]. [amount] is
	 * a whole number from 1 to [tradeMax] (S04-1). [loads] split it across their ships in port there, adding up to it;
	 * with no ship in port they're empty, and a buy's goods are all lost (S04-4, R23). A buy pays the offer's price per
	 * unit and loads what fits; a sell unloads and is paid (R34: at the offer's own price).
	 */
	fun trade(offer: Offer, counterMax: Int, amount: Int?, loads: List<ShipLoad>): Player {
		val max = tradeMax(offer, counterMax)
		if (max == 0) {
			throw InvalidTradeException(
				when (offer.type) {
					OfferType.BUY -> "You can't afford any units at ${offer.price} HT."
					OfferType.SELL -> "You have no ${offer.goods.name} in your ships in port at ${offer.system.displayName}, so you can't sell any."
				}
			)
		}
		if (amount == null || amount !in 1..max) {
			throw InvalidTradeException("Enter a whole number from 1 to $max.")
		}
		val here = shipsInPortAt(offer.system)
		if (loads.any { load -> here.none { it.name == load.shipName } }) {
			throw InvalidTradeException("Choose your ships in port at ${offer.system.displayName}.")
		}
		if (loads.map { it.shipName }.toSet().size != loads.size || loads.any { it.units < 0 }) {
			throw InvalidTradeException("Enter a whole number of units, 0 or more, for each ship.")
		}
		if (here.isNotEmpty() && loads.sumOf { it.units } != amount) {
			throw InvalidTradeException("Your ships' units must add up to $amount.")
		}
		val moved = loads.fold(this) { player, load ->
			val ship = checkNotNull(player.ship(load.shipName))
			player.withShip(
				when (offer.type) {
					OfferType.BUY -> ship.load(offer.goods, load.units)
					OfferType.SELL -> ship.unload(offer.goods, load.units)
				}
			)
		}
		return when (offer.type) {
			OfferType.BUY -> moved.pay(amount * offer.price)
			OfferType.SELL -> moved.receive(amount * offer.price)
		}
	}

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

	/**
	 * This player's offer as Dictator of [good], placed when it resolves in Trading (R38, E05-S02-2): checked like any
	 * offer (R03). Being the Dictator asked is checked by the resolution, which asked them.
	 */
	fun dictatorOffer(good: MarketGood, type: OfferType, price: Int?): Offer = Offer.place(email, good.system, good.goods, type, price)

	/** Whether this player is Wholesaler of [good] at its system (R28, R37). */
	fun isWholesalerOf(good: MarketGood): Boolean =
		marketPositions.any { it.system == good.system && it.goods == good.goods && it.tier == MarketTier.WHOLESALER }

	/** Whether this player is Dictator of [goods] at [system] (R28, R38). */
	fun isDictatorOf(system: StarSystem, goods: Goods): Boolean =
		marketPositions.any { it.system == system && it.goods == goods && it.tier == MarketTier.DICTATOR }

	/** This player's tier for [good] at its system, or null when they hold none there (R28, R40). */
	fun tierAt(good: MarketGood): MarketTier? = marketPositions.firstOrNull { it.isFor(good) }?.tier

	/** Whether a position roll can move this player up for [good] (E05-S06-3): not once they're its Dictator. */
	fun canRiseAt(good: MarketGood): Boolean = tierAt(good) != MarketTier.DICTATOR

	/**
	 * The tier a won position roll gives this player for [good] (R43): one up from the tier they hold, none → Retailer →
	 * Wholesaler → Dictator. Only for a player who [canRiseAt] it.
	 */
	fun promotionAt(good: MarketGood): MarketTier =
		tierAt(good)?.let { checkNotNull(it.next()) { "A Dictator can't go higher" } } ?: MarketTier.RETAILER

	/**
	 * This player after winning a position roll for [good]: they hold [promotionAt] it at once (E05-S06-5). It changes
	 * nothing for the rest of the turn, since each good resolves once a turn and its Dictator and Wholesalers are fixed
	 * when it starts.
	 */
	fun promotedAt(good: MarketGood): Player = withPosition(MarketPosition(good.system, good.goods, promotionAt(good)))

	/** Whether this player is Dictator of [good] at its system (R28). */
	fun isDictatorOf(good: MarketGood): Boolean = tierAt(good) == MarketTier.DICTATOR

	/** This player as Wholesaler of [good], once someone else became its Dictator (S06-3). */
	fun demotedAt(good: MarketGood): Player = withPosition(MarketPosition(good.system, good.goods, MarketTier.WHOLESALER))

	/** This player holding [position], in place of whatever they held for that good at that system. */
	private fun withPosition(position: MarketPosition): Player {
		val good = MarketGood(position.system, position.goods)
		return copy(marketPositions = marketPositions.filterNot { it.isFor(good) } + position)
	}

	private fun withShip(ship: Ship): Player = copy(ships = ships.map { if (it.name == ship.name) ship else it })

	companion object {
		/** A player in the lobby: money and ships arrive when the game starts (R40). */
		fun newcomer(email: String, name: String, corporationName: String) = Player(email, name, corporationName, 0, emptyList(), emptyList())
	}
}
