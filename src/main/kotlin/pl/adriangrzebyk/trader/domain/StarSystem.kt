package pl.adriangrzebyk.trader.domain

enum class StarSystem(val displayName: String, goods: Collection<Goods>) {
	GAMMA_LEPORIS("Gamma Leporis", listOf(Goods.POLY)),
	TAU_CETI("Tau Ceti", listOf(Goods.POLY, Goods.ISO)),
	EPSILON_ERIDANI("Epsilon Eridani", Goods.entries),
	BETA_HYDRI("Beta Hydri", Goods.entries),
	MU_HERCULIS("Mu Herculis", listOf(Goods.POLY, Goods.ISO, Goods.CHIPS)),
	SIGMA_DRACONIS("Sigma Draconis", Goods.entries);

	val goods: Set<Goods> = goods.toSet()
}
