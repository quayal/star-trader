package dev.adriangrzebyk.trader.game

class FakeGameIdGenerator(private val id: String) : GameIdGenerator {

	override fun generate(): String = id
}
