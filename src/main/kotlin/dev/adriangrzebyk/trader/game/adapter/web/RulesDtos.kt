package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.MarketChallenge
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Reputation
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.sabotage.SabotageTable

/**
 * The game's fixed rules for the Guide tab, the crew labels and the map's jump modifiers (playtest, 2026-11-20), read from
 * the domain's own tables so they can't drift from what the game does. Public: nothing in it belongs to a game or a player.
 */
data class RulesResponseDto(
	val phases: List<String>,
	val agents: List<AgentRuleResponseDto>,
	val crewClasses: List<CrewRuleResponseDto>,
	val safeJumpBonus: Int,
	val novakJumpBonus: Int,
	val battleCommInterceptBonus: Int,
	val triggerInterceptBonus: Int,
	val wolfInitiativeBonus: Int,
	val shortySabotageBonus: Int,
	val sabotage: List<SabotageRowResponseDto>,
	val sabotageBlame: Int,
	val challenge: ChallengeRulesResponseDto,
	val reputation: ReputationRulesResponseDto,
	val hulls: List<HullRuleResponseDto>,
	val modules: List<ModuleRuleResponseDto>,
	val hangarFee: Int = Ship.HANGAR_FEE
) {

	constructor() : this(
		TurnPhase.entries.map { it.displayName },
		Agent.entries.map { AgentRuleResponseDto(it) },
		CrewClass.entries.map { CrewRuleResponseDto(it) },
		Module.SAFE_JUMP.jumpModifier,
		Agent.NOVAK_JUMP_BONUS,
		Ship.BATTLE_COMM_INTERCEPT_BONUS,
		Agent.TRIGGER_INTERCEPT_BONUS,
		Agent.WOLF_INITIATIVE_BONUS,
		Agent.SHORTY_SABOTAGE_BONUS,
		SabotageTable().rows().map { SabotageRowResponseDto(it) },
		SabotageTable.BLAME,
		ChallengeRulesResponseDto(),
		ReputationRulesResponseDto(),
		Hull.entries.map { HullRuleResponseDto(it) },
		Module.entries.map { ModuleRuleResponseDto(it) }
	)
}

/**
 * Every hull (R51) for the Guide's table (playtest, 2026-11-21: plan a ship outside Investment), military ones too, which
 * come only through an opportunity (R56).
 */
data class HullRuleResponseDto(
	val id: String,
	val name: String,
	val price: Int,
	val moduleCapacity: Int,
	val crew: Int,
	val cargoSpaces: Int,
	val weapons: Int,
	val shields: Int,
	val intercept: Int,
	val repairCost: Int,
	val military: Boolean,
	val aerodynamic: Boolean
) {

	constructor(hull: Hull) : this(
		hull.name,
		hull.displayName,
		hull.price,
		hull.moduleCapacity,
		hull.crew,
		hull.builtInCargo,
		hull.weapons,
		hull.shields,
		hull.intercept,
		hull.repairCost,
		hull.military,
		hull.aerodynamic
	)
}

/** Every module (R52) for the Guide's table, with its [rocketWeapons] (the table's bracketed figure) and jump modifier. */
data class ModuleRuleResponseDto(
	val id: String,
	val name: String,
	val price: Int,
	val crew: Int,
	val weapons: Int,
	val rocketWeapons: Int,
	val cargoSpaces: Int,
	val passengers: Int,
	val military: Boolean,
	val takesModuleCapacity: Boolean,
	val jumpModifier: Int
) {

	constructor(module: Module) : this(
		module.name,
		module.displayName,
		module.price,
		module.crew,
		module.weapons,
		module.rocketWeapons,
		module.cargoSpaces,
		module.passengers,
		module.military,
		module.takesModuleCapacity,
		module.jumpModifier
	)
}

/** One agent (R108) and whether it can be recruited yet ([inGame]: Badger and Frank wait for their features). */
data class AgentRuleResponseDto(val name: String, val effect: String, val inGame: Boolean) {

	constructor(agent: Agent) : this(agent.displayName, agent.effect, agent.recruitable)
}

/** One crew class (R67): its price per crew and what it adds to jumps (R68) and interception (R71). */
data class CrewRuleResponseDto(val id: String, val pricePerCrew: Int, val jumpModifier: Int, val interceptModifier: Int) {

	constructor(crewClass: CrewClass) : this(crewClass.name, crewClass.pricePerCrew, crewClass.jumpModifier, crewClass.interceptModifier)
}

/** One row of the sabotage table (R98): a total from [from] gives [effect], and the saboteur loses [reputationLoss] (null: blame). */
data class SabotageRowResponseDto(val from: Int?, val effect: Int, val reputationLoss: Int?) {

	constructor(row: SabotageTable.Row) : this(row.from.takeIf { it != Int.MIN_VALUE }, row.effect, row.reputationLoss)
}

/** Reputation's fixed numbers (R85–R88): the range, the end-of-turn recovery, buying, and the bonus table. */
data class ReputationRulesResponseDto(
	val start: Int,
	val max: Int,
	val recovery: Int,
	val recoveryUpTo: Int,
	val buyCost: Int,
	val bonus: Map<Int, Int>
) {

	constructor() : this(
		Reputation.START,
		Reputation.MAX,
		Reputation.RECOVERY,
		Reputation.RECOVERY_UP_TO,
		Reputation.BUY_COST,
		Reputation.BONUS
	)
}

/** Challenging a market position (R126): the fee, what a win costs the target and the least difference that can win. */
data class ChallengeRulesResponseDto(val fee: Int, val targetReputationLoss: Int, val minDifference: Int) {

	constructor() : this(MarketChallenge.FEE, MarketChallenge.TARGET_REPUTATION_LOSS, MarketChallenge.MIN_DIFFERENCE)
}
