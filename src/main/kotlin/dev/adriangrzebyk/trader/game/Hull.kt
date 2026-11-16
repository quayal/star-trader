package dev.adriangrzebyk.trader.game

/**
 * A ship's hull (R51), game data from the creator's table: how many modules it holds, its [intercept], [crew], base
 * [weapons], [shields] and [builtInCargo] spaces (0 where the table says N), what it costs to buy and to repair, and
 * whether it is [military] (illegal to own, R56) or [aerodynamic] (easier to land on a planet, R56).
 */
enum class Hull(
	val displayName: String,
	val moduleCapacity: Int,
	val intercept: Int,
	val crew: Int,
	val weapons: Int,
	val shields: Int,
	val builtInCargo: Int,
	val price: Int,
	val repairCost: Int,
	val military: Boolean,
	val aerodynamic: Boolean
) {
	DAGGER("Dagger", 1, 2, 1, 5, 4, 0, 250, 50, military = true, aerodynamic = true),
	CORCO_GAMMA("Corco Gamma", 2, 1, 1, 2, 1, 0, 80, 10, military = false, aerodynamic = true),
	FLUTE("Flute", 3, 3, 1, 3, 3, 0, 225, 30, military = false, aerodynamic = true),
	SWORD("Sword", 3, 3, 1, 5, 4, 0, 500, 80, military = true, aerodynamic = false),
	CORCO_ZETA("Corco Zeta", 4, 1, 1, 1, 1, 0, 80, 10, military = false, aerodynamic = false),
	CLARINET("Clarinet", 5, 2, 1, 3, 2, 0, 160, 20, military = false, aerodynamic = false),
	SPEAR("Spear", 5, 1, 2, 5, 4, 1, 600, 100, military = true, aerodynamic = false),
	CORCO_IOTA("Corco Iota", 6, 2, 1, 3, 3, 1, 190, 30, military = false, aerodynamic = false),
	PHOENIX("Phoenix", 6, 1, 1, 1, 1, 1, 85, 10, military = false, aerodynamic = false),
	CORCO_MU("Corco Mu", 9, 1, 1, 2, 1, 1, 160, 30, military = false, aerodynamic = false),
	MONARCH("Monarch", 12, 1, 3, 1, 1, 4, 150, 15, military = false, aerodynamic = false),
	LEVIATHAN("Leviathan", 18, 1, 4, 1, 1, 6, 250, 20, military = false, aerodynamic = false),
	PICCOLO("Piccolo", 0, 3, 1, 2, 2, 0, 70, 10, military = false, aerodynamic = true);

	/**
	 * Refuses [modules] this hull can't take (R52): more than [moduleCapacity] of those that take capacity, or a second
	 * Safe jump, which takes none but a ship has only one of.
	 */
	fun checkFits(modules: List<Module>) {
		val used = modules.count { it.takesModuleCapacity }
		if (used > moduleCapacity) {
			throw InvalidFittingException("A $displayName holds $moduleCapacity modules, not $used.")
		}
		if (modules.count { it == Module.SAFE_JUMP } > 1) {
			throw InvalidFittingException("A ship has at most one ${Module.SAFE_JUMP.displayName} module.")
		}
	}
}
