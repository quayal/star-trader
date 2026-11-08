package dev.adriangrzebyk.trader.game

/**
 * Why an offer the counter reached traded nothing (E05-S04-5): its player couldn't afford a unit (R12) or had none of
 * the good in their ships there (R10). Private to that player: it tells something about their money (R29) or cargo.
 */
enum class Shortfall {
	CANT_AFFORD,
	NOTHING_TO_SELL;

	/** What the trader is shown: "You can't afford any units at 15 HT", "You have no ISO in your ships at Mu Herculis". */
	fun reason(offer: Offer): String = when (this) {
		CANT_AFFORD -> "You can't afford any units at ${offer.price} HT"
		NOTHING_TO_SELL -> "You have no ${offer.goods.name} in your ships at ${offer.system.displayName}"
	}
}
