package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.SalvoOutcome
import dev.adriangrzebyk.trader.game.battle.SalvoResult
import dev.adriangrzebyk.trader.game.battle.SalvoRolls
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [BattleReport] as the attribute map nested in its turn's item (engineering standards §12). */
internal class BattleReportItem(val attributes: Map<String, AttributeValue>) {

	constructor(report: BattleReport) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(report.system.name),
			ROUND to number(report.round),
			RESULTS to AttributeValue.fromL(
				report.results.map { result ->
					AttributeValue.fromM(
						mapOf(
							FIRER to AttributeValue.fromS(result.firer),
							FIRER_OWNER_EMAIL to AttributeValue.fromS(result.firerOwnerEmail),
							TARGET to AttributeValue.fromS(result.target),
							TARGET_OWNER_EMAIL to AttributeValue.fromS(result.targetOwnerEmail),
							ROCKETS to AttributeValue.fromBool(result.rockets),
							HITS to number(result.hits),
							OUTCOME to AttributeValue.fromS(result.outcome.name)
						) + listOfNotNull(result.rolls?.let { ROLLS to AttributeValue.fromM(SalvoRollsItem(it).attributes) })
					)
				}
			)
		)
	)

	fun toBattleReport(): BattleReport = BattleReport(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		round = attributes.attribute(ROUND).n().toInt(),
		results = attributes.attribute(RESULTS).l().map { item ->
			val fields = item.m()
			SalvoResult(
				firer = fields.attribute(FIRER).s(),
				firerOwnerEmail = fields.attribute(FIRER_OWNER_EMAIL).s(),
				target = fields.attribute(TARGET).s(),
				targetOwnerEmail = fields.attribute(TARGET_OWNER_EMAIL).s(),
				rockets = fields.attribute(ROCKETS).bool(),
				hits = fields.attribute(HITS).n().toInt(),
				outcome = SalvoOutcome.valueOf(fields.attribute(OUTCOME).s()),
				rolls = fields[ROLLS]?.m()?.let { SalvoRollsItem(it).toSalvoRolls() }
			)
		}
	)

	private companion object {
		const val SYSTEM = "system"
		const val ROUND = "round"
		const val RESULTS = "results"
		const val FIRER = "firer"
		const val FIRER_OWNER_EMAIL = "firerOwnerEmail"
		const val TARGET = "target"
		const val TARGET_OWNER_EMAIL = "targetOwnerEmail"
		const val ROCKETS = "rockets"
		const val HITS = "hits"
		const val OUTCOME = "outcome"
		const val ROLLS = "rolls"
	}
}

/** A salvo's [SalvoRolls] as the attribute map nested in its result; a rocket roll only when there was one. */
internal class SalvoRollsItem(val attributes: Map<String, AttributeValue>) {

	constructor(rolls: SalvoRolls) : this(
		mapOf(
			FIREPOWER to number(rolls.firepower),
			ROLL to number(rolls.roll),
			SHIELDS to number(rolls.shields)
		) + listOfNotNull(rolls.rocketRoll?.let { ROCKET_ROLL to number(it) })
	)

	fun toSalvoRolls(): SalvoRolls = SalvoRolls(
		firepower = attributes.attribute(FIREPOWER).n().toInt(),
		roll = attributes.attribute(ROLL).n().toInt(),
		shields = attributes.attribute(SHIELDS).n().toInt(),
		rocketRoll = attributes[ROCKET_ROLL]?.n()?.toInt()
	)

	private companion object {
		const val FIREPOWER = "firepower"
		const val ROLL = "roll"
		const val SHIELDS = "shields"
		const val ROCKET_ROLL = "rocketRoll"
	}
}
