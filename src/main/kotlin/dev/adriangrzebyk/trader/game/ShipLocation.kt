package dev.adriangrzebyk.trader.game

/**
 * Where a ship is inside its system (R30, R60). [landingLog] is how the public log tells a ship ended up here (S03-3); a
 * ship can land in every location but [SHIPYARD], which only a newly bought ship is in (R60).
 */
enum class ShipLocation(val landingLog: String) {
	PORT("lands at the port"),
	PLANET("lands on the planet"),
	SPACE("stays in space"),
	SHIPYARD("is in the shipyard");

	/** Whether a ship that jumped may end up here (R30): not the shipyard (R60). */
	fun isLandable(): Boolean = this != SHIPYARD
}
