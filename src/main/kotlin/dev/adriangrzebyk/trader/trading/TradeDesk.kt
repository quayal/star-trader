package dev.adriangrzebyk.trader.trading

class TradeDesk(
	private val tradeOfferBook: TradeOfferBook,
	private val diceRoller: DiceRoller,
	private val bestOfferCalculator: BestOfferCalculator = BestOfferCalculator(),
	private val transactionAmountCalculator: TransactionAmountCalculator = TransactionAmountCalculator()
) {

	fun submit(playerName: String, system: StarSystem, goods: Goods, offerType: OfferType, price: Int, turn: Int): TradeOffer {
		if (goods !in system.goods) {
			throw InvalidTradeOfferException("${goods.name} is not traded in ${system.displayName}")
		}
		if (price !in MIN_PRICE..MAX_PRICE) {
			throw InvalidTradeOfferException("Price must be between $MIN_PRICE and $MAX_PRICE")
		}

		val offer = TradeOffer(
			turn = turn,
			player = Player(playerName),
			system = system,
			goods = goods,
			offerType = offerType,
			price = price,
			initiative = tradeOfferBook.nextInitiative()
		)
		tradeOfferBook.add(offer)
		return offer
	}

	fun allOffers(): List<TradeOffer> = tradeOfferBook.all()

	fun resolve(turn: Int, system: StarSystem, goods: Goods): TradeResult? {
		val offers = tradeOfferBook.forTurn(turn)
			.filter { it.system == system && it.goods == goods }
			.toSet()
		if (offers.isEmpty()) {
			return null
		}

		val diceRoll = diceRoller.roll2D()
		val winningOffer = bestOfferCalculator.getWinningOffer(system, goods, offers, diceRoll)
		val amount = transactionAmountCalculator.getAmountToTrade(winningOffer, diceRoll)
		return TradeResult(winningOffer, amount)
	}

	companion object {
		const val MIN_PRICE = 1
		const val MAX_PRICE = 20
	}
}
