package dev.adriangrzebyk.trader.game

/** Where a ship is inside its system (R30). [landingLog] is how the public log tells a ship ended up here (S03-3). */
enum class ShipLocation(val landingLog: String) {
	PORT("lands at the port"),
	PLANET("lands on the planet"),
	SPACE("stays in space")
}
