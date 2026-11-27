package dev.adriangrzebyk.trader.board

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
		LocationStats(portClass = 1, legalSystem = 3, police = 3, security = 3),
		mapOf(Goods.POLY to 2),
		mapOf(Goods.POLY to 1),
		setOf(Goods.POLY)
	),
	TAU_CETI(
		"Tau Ceti",
		LocationStats(portClass = 3, legalSystem = 2, police = 5, security = 7),
		mapOf(Goods.POLY to 6, Goods.ISO to 4),
		mapOf(Goods.POLY to -10, Goods.ISO to -9),
		setOf(Goods.ISO)
	),
	EPSILON_ERIDANI(
		"Epsilon Eridani",
		LocationStats(portClass = 3, legalSystem = 4, police = 7, security = 9),
		mapOf(Goods.POLY to 5, Goods.ISO to 10, Goods.FOOD to 14, Goods.CHIPS to 17),
		mapOf(Goods.POLY to -7, Goods.ISO to -7, Goods.FOOD to -8, Goods.CHIPS to -9),
		emptySet()
	),
	BETA_HYDRI(
		"Beta Hydri",
		LocationStats(portClass = 4, legalSystem = 4, police = 8, security = 10),
		mapOf(Goods.POLY to 6, Goods.ISO to 10, Goods.FOOD to 15, Goods.CHIPS to 18),
		mapOf(Goods.POLY to -8, Goods.ISO to -8, Goods.FOOD to -9, Goods.CHIPS to -10),
		emptySet()
	),
	MU_HERCULIS(
		"Mu Herculis",
		LocationStats(portClass = 0, legalSystem = 1, police = 0, security = 3),
		mapOf(Goods.POLY to 8, Goods.ISO to 11, Goods.CHIPS to 12),
		mapOf(Goods.POLY to -9, Goods.ISO to 1, Goods.CHIPS to -4),
		setOf(Goods.CHIPS)
	),
	SIGMA_DRACONIS(
		"Sigma Draconis",
		LocationStats(portClass = 1, legalSystem = 1, police = 2, security = 4),
		mapOf(Goods.POLY to 8, Goods.ISO to 4, Goods.FOOD to 10, Goods.CHIPS to 14),
		mapOf(Goods.POLY to -5, Goods.ISO to -1, Goods.FOOD to -3, Goods.CHIPS to -8),
		setOf(Goods.FOOD, Goods.ISO)
	);

	val goods: Set<Goods> get() = goodsPrices.keys

	/** Whether this system has a shipyard (R60): Beta Hydri, Epsilon Eridani and Tau Ceti. */
	fun hasShipyard(): Boolean = this == BETA_HYDRI || this == EPSILON_ERIDANI || this == TAU_CETI

	/** Whether this system's shipyard repairs a ship with military parts (R133): only Tau Ceti's, where they may enter (R56). */
	fun repairsMilitaryShips(): Boolean = this == TAU_CETI

	/** Whether this system's Port has a Parking Hangar (R127): every system but Sigma Draconis and Mu Herculis. */
	fun hasHangar(): Boolean = this != SIGMA_DRACONIS && this != MU_HERCULIS

	fun getPrice(goods: Goods): Int = goodsPrices.getValue(goods)

	fun getIndicator(goods: Goods): Int = indicators.getValue(goods)

	/**
	 * The base chance of a jump from here to [destination] (R68): a 2d6 roll of this or less succeeds before the ship's
	 * modifiers. Each direction of a route has its own; null for this system itself, which is no jump.
	 */
	fun baseJumpChanceTo(destination: StarSystem): Int? = if (destination == this) null else JUMP_CHANCES.getValue(this to destination)

	companion object {
		/** The systems with a shipyard (R60), in map order. */
		fun shipyards(): List<StarSystem> = entries.filter { it.hasShipyard() }

		/** R68, the creator's table: each route as (from, chance from → to, chance to → from, to). */
		private val ROUTES = listOf(
			Route(GAMMA_LEPORIS, 7, 5, BETA_HYDRI),
			Route(GAMMA_LEPORIS, 4, 4, SIGMA_DRACONIS),
			Route(GAMMA_LEPORIS, 2, 2, MU_HERCULIS),
			Route(GAMMA_LEPORIS, 7, 5, TAU_CETI),
			Route(GAMMA_LEPORIS, 7, 6, EPSILON_ERIDANI),
			Route(BETA_HYDRI, 4, 6, SIGMA_DRACONIS),
			Route(BETA_HYDRI, 3, 6, MU_HERCULIS),
			Route(BETA_HYDRI, 8, 8, TAU_CETI),
			Route(BETA_HYDRI, 8, 8, EPSILON_ERIDANI),
			Route(SIGMA_DRACONIS, 5, 6, MU_HERCULIS),
			Route(SIGMA_DRACONIS, 7, 5, TAU_CETI),
			Route(SIGMA_DRACONIS, 7, 5, EPSILON_ERIDANI),
			Route(MU_HERCULIS, 6, 4, EPSILON_ERIDANI),
			Route(MU_HERCULIS, 9, 4, TAU_CETI),
			Route(TAU_CETI, 9, 9, EPSILON_ERIDANI)
		)

		private val JUMP_CHANCES: Map<Pair<StarSystem, StarSystem>, Int> =
			ROUTES.flatMap { listOf((it.from to it.to) to it.there, (it.to to it.from) to it.back) }.toMap()
	}

	private class Route(val from: StarSystem, val there: Int, val back: Int, val to: StarSystem)
}
