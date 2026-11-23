package dev.adriangrzebyk.trader.game

/**
 * What one of a player's ships can do with its modules now, wherever it is (R122, R123): [storable], the legal modules
 * it can put in the warehouse at its Port; [stored], the modules stored there it has room for; [transfers], the military
 * modules it can move to each other ship of theirs in that Port. Investment decides whether they may (like refits, R65).
 */
data class ModuleMoves(val storable: List<Module>, val stored: List<Module>, val transfers: List<ModuleTransfer>) {

	companion object {
		/** A ship out of port: none. */
		val NONE = ModuleMoves(emptyList(), emptyList(), emptyList())
	}
}

/** A military [module] that can move to the ship [toShip] (R123). */
data class ModuleTransfer(val module: Module, val toShip: String)
