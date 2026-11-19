package dev.adriangrzebyk.trader.game

/**
 * A module fitted to a hull (R52), game data from the creator's table. [rocketWeapons] is the figure in brackets in the
 * table (0 where there is none). Safe jump upgrades the standard jump module, so it takes no module capacity
 * ([takesModuleCapacity]) and adds [jumpModifier] to the ship's jump target (R68). A [military] module is illegal to own
 * (R56).
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
	val takesModuleCapacity: Boolean = true,
	val jumpModifier: Int = 0
) {
	ARSENAL("Arsenal", 7, 14, 1, 0, 0, 100, military = true),
	SAFE_JUMP("Safe jump", 0, 0, 0, 0, 0, 10, military = false, takesModuleCapacity = false, jumpModifier = 2),
	BATTLE_COMM("Battle comm", 6, 0, 2, 0, 0, 50, military = true),
	CARGO("Cargo", 0, 0, 0, 2, 0, 4, military = false),
	HEAVY_WEAPONS("Heavy weapons", 7, 10, 1, 0, 0, 80, military = true),
	DOGFIGHT("Dogfight", 4, 7, 0, 0, 0, 30, military = true),
	LIGHT_WEAPONS("Light weapons", 3, 5, 0, 0, 0, 17, military = false),
	PASSENGER("Passenger", 0, 0, 1, 0, 2, 15, military = false);

	/** Whether this module can be bought at a shipyard (R61, R65): civilian only (R56). */
	fun isForSale(): Boolean = !military

	/** What fitting this module costs on a ship of [crewClass] (R65): its price plus its crew at that class. */
	fun fittingCost(crewClass: CrewClass): Int = price + crew * crewClass.pricePerCrew

	/** What repairing this module when it's damaged costs (R79): 10 HT, or 4 HT for a Cargo module. */
	fun repairCost(): Int = if (this == CARGO) 4 else 10

	/** What removing this module pays back (R65): half its price, rounded down; its crew isn't paid back. */
	fun resaleValue(): Int = price / 2

	companion object {
		/** Every module a shipyard sells, in declaration order (R61, R56: no military one). */
		fun forSale(): List<Module> = entries.filter { it.isForSale() }
	}
}
