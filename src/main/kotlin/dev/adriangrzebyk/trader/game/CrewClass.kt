package dev.adriangrzebyk.trader.game

/**
 * A ship's crew class (R67): every crew member of a ship is of its one class, and costs [pricePerCrew] HT, paid once when
 * the ship is commissioned (R61) or a module that needs crew is fitted (R65). Starting ships are class B (R55). A better
 * crew adds [jumpModifier] to the ship's jump target (R68).
 */
enum class CrewClass(val pricePerCrew: Int, val jumpModifier: Int) {
	A(20, 5),
	B(10, 3),
	C(6, 1),
	D(2, 0)
}
