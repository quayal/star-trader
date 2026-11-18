package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

class InvestmentService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * Records [callerEmail] as done investing inside [GameStore.update], so when the last two players signal at the same
	 * moment Investment closes and the turn ends exactly once, and a signal that arrives after the close is refused
	 * (A04, R44).
	 */
	fun signalDone(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.signalDoneInvesting(callerEmail) }

	/**
	 * [callerEmail] confirms where their factories' output goes inside [GameStore.update], so a double press stores it once
	 * and the placements are checked against the saved ships and warehouses (A04, R47).
	 */
	fun collect(gameId: String, callerEmail: String, placements: List<OutputPlacement>): Game =
		gameStore.update(gameId) { it.collectOutput(callerEmail, placements) }

	/**
	 * [callerEmail] buys [blocks] warehouse blocks at [system] inside [GameStore.update], so the money is checked against the
	 * saved balance and two purchases at once can't spend the same HT (A04, R45).
	 */
	fun buyWarehouseBlocks(gameId: String, callerEmail: String, system: StarSystem, blocks: Int): Game =
		gameStore.update(gameId) { it.buyWarehouseBlocks(callerEmail, system, blocks) }

	/**
	 * [callerEmail] buys [units] of production of [goods] at [system] inside [GameStore.update], so the cost is checked
	 * against the saved balance and the saved prices, and a replayed change builds and charges once (A04, R46).
	 */
	fun buyFactoryUnits(gameId: String, callerEmail: String, system: StarSystem, goods: Goods, units: Int): Game =
		gameStore.update(gameId) { it.buyFactoryUnits(callerEmail, system, goods, units) }

	/**
	 * [callerEmail] buys a ship inside [GameStore.update], so its name is checked against the saved ships and its price
	 * against the saved balance, and a replayed change buys it once (A04, R61, R62).
	 */
	fun buyShip(
		gameId: String,
		callerEmail: String,
		name: String?,
		shipyard: StarSystem,
		hull: Hull,
		modules: List<Module>,
		crewClass: CrewClass
	): Game = gameStore.update(gameId) { it.buyShip(callerEmail, name, shipyard, hull, modules, crewClass) }

	/**
	 * [callerEmail] raises their [kind] connections inside [GameStore.update], so the cost is checked against the saved
	 * balance and two raises at once can't both pass the once-per-Investment check (A04, R83).
	 */
	fun raiseConnection(gameId: String, callerEmail: String, kind: ConnectionKind): Game =
		gameStore.update(gameId) { it.raiseConnection(callerEmail, kind) }

	/** [callerEmail] buys reputation inside [GameStore.update], so two presses can't both pass the once-per-Investment check (A04, R87). */
	fun buyReputation(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.buyReputation(callerEmail, dice) }

	/** [callerEmail] fits [module] to their ship [shipName] inside [GameStore.update] (A04, R65). */
	fun addModule(gameId: String, callerEmail: String, shipName: String, module: Module): Game =
		gameStore.update(gameId) { it.addModule(callerEmail, shipName, module) }

	/** [callerEmail] removes [module] from their ship [shipName] inside [GameStore.update] (A04, R65). */
	fun removeModule(gameId: String, callerEmail: String, shipName: String, module: Module): Game =
		gameStore.update(gameId) { it.removeModule(callerEmail, shipName, module) }

	/** [callerEmail] replaces the crew of their ship [shipName] inside [GameStore.update], so a double press pays once (A04, R89). */
	fun replaceCrew(gameId: String, callerEmail: String, shipName: String, crewClass: CrewClass): Game =
		gameStore.update(gameId) { it.replaceCrew(callerEmail, shipName, crewClass) }

	/** [callerEmail] repairs their ship [shipName] inside [GameStore.update], so a double press pays once (A04, R79). */
	fun repairShip(gameId: String, callerEmail: String, shipName: String): Game =
		gameStore.update(gameId) { it.repairShip(callerEmail, shipName) }

	/** [callerEmail] sells their ship [shipName] inside [GameStore.update], so a double press sells it once (A04, R66). */
	fun sellShip(gameId: String, callerEmail: String, shipName: String): Game = gameStore.update(gameId) { it.sellShip(callerEmail, shipName) }
}
