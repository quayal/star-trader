package pl.adriangrzebyk.trader.domain

class MarketPosition(
	val goods: Goods? = null,
	val system: StarSystem? = null,
	val position: CompetitivePosition? = null,
)
