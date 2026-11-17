package dev.adriangrzebyk.trader.game

/**
 * A ship's crew class (R67): every crew member of a ship is of its one class, and costs [pricePerCrew] HT, paid once when
 * the ship is commissioned (R61) or a module that needs crew is fitted (R65). Starting ships are class B (R55).
 */
enum class CrewClass(val pricePerCrew: Int) {
	A(20),
	B(10),
	C(6),
	D(2)
}
