package dev.adriangrzebyk.trader.game

/**
 * A ship's crew class (R67): every crew member of a ship is of its one class, and costs [pricePerCrew] HT, paid once when
 * the ship is commissioned (R61) or a module that needs crew is fitted (R65). Starting ships are class B (R55). A better
 * crew adds [jumpModifier] to the ship's jump target (R68) and [interceptModifier] to its interception rolls (R71).
 */
enum class CrewClass(val pricePerCrew: Int, val jumpModifier: Int, val interceptModifier: Int) {
	A(20, 5, 1),
	B(10, 3, 0),
	C(6, 1, 0),
	D(2, 0, -1)
}
