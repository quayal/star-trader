package dev.adriangrzebyk.trader.trading

enum class StarSystem(
	val displayName: String,
	private val goodsPrices: Map<Goods, Int>,
	private val indicators: Map<Goods, Int>,
) {
	GAMMA_LEPORIS(
		"Gamma Leporis",
		mapOf(Goods.POLY to 2),
		mapOf(Goods.POLY to 1),
	),
	TAU_CETI(
		"Tau Ceti",
		mapOf(Goods.POLY to 6, Goods.ISO to 4),
		mapOf(Goods.POLY to -10, Goods.ISO to -9),
	),
	EPSILON_ERIDANI(
		"Epsilon Eridani",
		mapOf(Goods.POLY to 5, Goods.ISO to 10, Goods.FOOD to 14, Goods.CHIPS to 17),
		mapOf(Goods.POLY to -7, Goods.ISO to -7, Goods.FOOD to -8, Goods.CHIPS to -9),
	),
	BETA_HYDRI(
		"Beta Hydri",
		mapOf(Goods.POLY to 6, Goods.ISO to 10, Goods.FOOD to 15, Goods.CHIPS to 18),
		mapOf(Goods.POLY to -8, Goods.ISO to -8, Goods.FOOD to -9, Goods.CHIPS to -10),
	),
	MU_HERCULIS(
		"Mu Herculis",
		mapOf(Goods.POLY to 8, Goods.ISO to 11, Goods.CHIPS to 12),
		mapOf(Goods.POLY to -9, Goods.ISO to 1, Goods.CHIPS to -4),
	),
	SIGMA_DRACONIS(
		"Sigma Draconis",
		mapOf(Goods.POLY to 8, Goods.ISO to 4, Goods.FOOD to 10, Goods.CHIPS to 14),
		mapOf(Goods.POLY to -5, Goods.ISO to -1, Goods.FOOD to -3, Goods.CHIPS to -8),
	);

	val goods: Set<Goods> get() = goodsPrices.keys

	fun getPrice(goods: Goods): Int = goodsPrices.getValue(goods)

	fun getIndicator(goods: Goods): Int = indicators.getValue(goods)
}
