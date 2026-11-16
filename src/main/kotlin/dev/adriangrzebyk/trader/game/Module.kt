package dev.adriangrzebyk.trader.game

/**
 * A module fitted to a hull (R52), game data from the creator's table. [rocketWeapons] is the figure in brackets in the
 * table (0 where there is none). Safe jump upgrades the standard jump module, so it takes no module capacity
 * ([takesModuleCapacity]). A [military] module is illegal to own (R56).
 */
enum class Module(
	val displayName: String,
	val weapons: Int,
	val rocketWeapons: Int,
	val crew: Int,
	val cargoSpaces: Int,
	val passengers: Int,
	val price: Int,
	val military: Boolean,
	val takesModuleCapacity: Boolean = true
) {
	ARSENAL("Arsenal", 7, 14, 1, 0, 0, 100, military = true),
	SAFE_JUMP("Safe jump", 0, 0, 0, 0, 0, 10, military = false, takesModuleCapacity = false),
	BATTLE_COMM("Battle comm", 6, 0, 2, 0, 0, 50, military = true),
	CARGO("Cargo", 0, 0, 0, 2, 0, 4, military = false),
	HEAVY_WEAPONS("Heavy weapons", 7, 10, 1, 0, 0, 80, military = true),
	DOGFIGHT("Dogfight", 4, 7, 0, 0, 0, 30, military = true),
	LIGHT_WEAPONS("Light weapons", 3, 5, 0, 0, 0, 17, military = false),
	PASSENGER("Passenger", 0, 0, 1, 0, 2, 15, military = false)
}
