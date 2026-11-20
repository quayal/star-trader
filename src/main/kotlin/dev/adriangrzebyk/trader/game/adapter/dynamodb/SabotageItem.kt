package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.sabotage.Sabotage
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Sabotage] as the attribute map nested in its turn's item, its target by kind (engineering standards §12). */
internal class SabotageItem(val attributes: Map<String, AttributeValue>) {

	constructor(sabotage: Sabotage) : this(
		mapOf(
			SABOTEUR to AttributeValue.fromS(sabotage.saboteurEmail),
			OWNER to AttributeValue.fromS(sabotage.target.ownerEmail),
			SYSTEM to AttributeValue.fromS(sabotage.target.system.name),
			BLAMED to AttributeValue.fromS(sabotage.blamedEmail),
			COST to number(sabotage.cost),
			EFFECT_MODIFIER to number(sabotage.effectModifier),
			REPUTATION_MODIFIER to number(sabotage.reputationModifier),
			LOST to AttributeValue.fromM(sabotage.lost.entries.associate { (goods, units) -> goods.name to number(units) }),
			SETTLED to AttributeValue.fromBool(sabotage.settled)
		) + when (val target = sabotage.target) {
			is SabotageTarget.Ship -> mapOf(KIND to AttributeValue.fromS(SHIP), SHIP_NAME to AttributeValue.fromS(target.name))
			is SabotageTarget.Warehouse -> mapOf(KIND to AttributeValue.fromS(WAREHOUSE))
			is SabotageTarget.Factory -> mapOf(KIND to AttributeValue.fromS(FACTORY), GOODS to AttributeValue.fromS(target.goods.name))
		} + listOfNotNull(
			sabotage.effectRoll?.let { EFFECT_ROLL to number(it) },
			sabotage.reputationRoll?.let { REPUTATION_ROLL to number(it) },
			sabotage.foiledBy?.let { FOILED_BY to AttributeValue.fromS(it) }
		)
	)

	fun toSabotage(): Sabotage = Sabotage(
		saboteurEmail = attributes.attribute(SABOTEUR).s(),
		target = target(),
		blamedEmail = attributes.attribute(BLAMED).s(),
		cost = attributes.attribute(COST).n().toInt(),
		effectRoll = attributes[EFFECT_ROLL]?.n()?.toInt(),
		effectModifier = attributes.attribute(EFFECT_MODIFIER).n().toInt(),
		reputationRoll = attributes[REPUTATION_ROLL]?.n()?.toInt(),
		reputationModifier = attributes.attribute(REPUTATION_MODIFIER).n().toInt(),
		foiledBy = attributes[FOILED_BY]?.s(),
		lost = attributes.attribute(LOST).m().entries.associate { (goods, units) -> Goods.valueOf(goods) to units.n().toInt() },
		settled = attributes.attribute(SETTLED).bool()
	)

	private fun target(): SabotageTarget {
		val owner = attributes.attribute(OWNER).s()
		val system = StarSystem.valueOf(attributes.attribute(SYSTEM).s())
		return when (attributes.attribute(KIND).s()) {
			SHIP -> SabotageTarget.Ship(owner, system, attributes.attribute(SHIP_NAME).s())
			FACTORY -> SabotageTarget.Factory(owner, system, Goods.valueOf(attributes.attribute(GOODS).s()))
			else -> SabotageTarget.Warehouse(owner, system)
		}
	}

	private companion object {
		const val SABOTEUR = "saboteur"
		const val OWNER = "owner"
		const val SYSTEM = "system"
		const val KIND = "kind"
		const val SHIP_NAME = "ship"
		const val GOODS = "goods"
		const val BLAMED = "blamed"
		const val COST = "cost"
		const val EFFECT_ROLL = "effectRoll"
		const val EFFECT_MODIFIER = "effectModifier"
		const val REPUTATION_ROLL = "reputationRoll"
		const val REPUTATION_MODIFIER = "reputationModifier"
		const val FOILED_BY = "foiledBy"
		const val LOST = "lost"
		const val SETTLED = "settled"
		const val SHIP = "SHIP"
		const val WAREHOUSE = "WAREHOUSE"
		const val FACTORY = "FACTORY"
	}
}
