package dev.adriangrzebyk.trader.trading

/**
 * [locations]: R31; the numbers are the accepted prototype's (revision 014), as the brief sets none. [producibleGoods]:
 * the goods a factory here can make (R46); none at Epsilon Eridani and Beta Hydri.
 */
enum class StarSystem(
	val displayName: String,
	val locations: LocationStats,
	private val goodsPrices: Map<Goods, Int>,
	private val indicators: Map<Goods, Int>,
	val producibleGoods: Set<Goods>
) {
	GAMMA_LEPORIS(
		"Gamma Leporis",
		LocationStats(portClass = 2, legalSystem = 3, police = 1, security = 2),
		mapOf(Goods.POLY to 2),
		mapOf(Goods.POLY to 1),
		setOf(Goods.POLY)
	),
	TAU_CETI(
		"Tau Ceti",
		LocationStats(portClass = 4, legalSystem = 2, police = 3, security = 4),
		mapOf(Goods.POLY to 6, Goods.ISO to 4),
		mapOf(Goods.POLY to -10, Goods.ISO to -9),
		setOf(Goods.ISO)
	),
	EPSILON_ERIDANI(
		"Epsilon Eridani",
		LocationStats(portClass = 5, legalSystem = 4, police = 4, security = 5),
		mapOf(Goods.POLY to 5, Goods.ISO to 10, Goods.FOOD to 14, Goods.CHIPS to 17),
		mapOf(Goods.POLY to -7, Goods.ISO to -7, Goods.FOOD to -8, Goods.CHIPS to -9),
		emptySet()
	),
	BETA_HYDRI(
		"Beta Hydri",
		LocationStats(portClass = 3, legalSystem = 1, police = 2, security = 1),
		mapOf(Goods.POLY to 6, Goods.ISO to 10, Goods.FOOD to 15, Goods.CHIPS to 18),
		mapOf(Goods.POLY to -8, Goods.ISO to -8, Goods.FOOD to -9, Goods.CHIPS to -10),
		emptySet()
	),
	MU_HERCULIS(
		"Mu Herculis",
		LocationStats(portClass = 3, legalSystem = 3, police = 2, security = 3),
		mapOf(Goods.POLY to 8, Goods.ISO to 11, Goods.CHIPS to 12),
		mapOf(Goods.POLY to -9, Goods.ISO to 1, Goods.CHIPS to -4),
		setOf(Goods.CHIPS)
	),
	SIGMA_DRACONIS(
		"Sigma Draconis",
		LocationStats(portClass = 4, legalSystem = 5, police = 5, security = 4),
		mapOf(Goods.POLY to 8, Goods.ISO to 4, Goods.FOOD to 10, Goods.CHIPS to 14),
		mapOf(Goods.POLY to -5, Goods.ISO to -1, Goods.FOOD to -3, Goods.CHIPS to -8),
		setOf(Goods.FOOD, Goods.ISO)
	);

	val goods: Set<Goods> get() = goodsPrices.keys

	/** Whether this system has a shipyard (R60): Beta Hydri, Epsilon Eridani and Tau Ceti. */
	fun hasShipyard(): Boolean = this == BETA_HYDRI || this == EPSILON_ERIDANI || this == TAU_CETI

	fun getPrice(goods: Goods): Int = goodsPrices.getValue(goods)

	fun getIndicator(goods: Goods): Int = indicators.getValue(goods)
}
