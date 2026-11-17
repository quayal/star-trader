package dev.adriangrzebyk.trader.game

/**
 * One end of a move of goods as the player names it (R58): their warehouse in the port the move happens in, or one of
 * their ships by [Ship.name] (null when none was named).
 */
sealed interface CargoHolder {

	/** The player's warehouse at the system of the ship at the other end. */
	data object Warehouse : CargoHolder

	/** The player's ship [name]. */
	data class Ship(val name: String?) : CargoHolder
}
