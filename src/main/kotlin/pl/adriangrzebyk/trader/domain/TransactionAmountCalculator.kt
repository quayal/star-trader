package pl.adriangrzebyk.trader.domain

import org.springframework.stereotype.Component

@Component
class TransactionAmountCalculator {

	fun getAmountToTrade(winningOffer: TradeOffer, diceRoll: Int): Int {
		val system = requireNotNull(winningOffer.system)
		val goods = requireNotNull(winningOffer.goods)

		val askingPrice = winningOffer.price
		val currentPrice = system.getPrice(goods)
		val priceModifier = askingPrice - currentPrice
		val supplyDemand = system.getIndicator(goods) + diceRoll

		return if (winningOffer.offerType == OfferType.SELL) {
			SDCalculator.getIndicatorForSelling(priceModifier) - supplyDemand
		} else {
			supplyDemand - SDCalculator.getIndicatorForBuying(priceModifier)
		}
	}
}
