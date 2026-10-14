package pl.adriangrzebyk.trader.domain

class Player(val name: String) {
	var money: Int = 0
	var reputation: Int = 0
	var loan: Loan? = null
	var positions: Set<MarketPosition> = emptySet()
}
