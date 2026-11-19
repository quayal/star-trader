package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.Interception

/**
 * What one member of a game, [player], may see beyond its public state, already decided by [Game.viewFor] (R17, R29, A05,
 * engineering standards §10), so an adapter only maps it. Each field is the viewer's own or null when it isn't theirs to
 * see: their own [offers] and [bid]; [tradeMax] while the counter waits for their offer (E05-S04); [wholesaleAsk], the
 * resolution, while it asks them to trade wholesale (R37); [factoriesToCollect] while they have output to place (E06-S03);
 * [investment] while they may buy (E06-S04); [interception] when they are asked (R70); [battle] when they have a ship in
 * it or hits to place (R74–R77); [battleReport] the salvos that concern them (R78). [prices] are the game's.
 */
data class PlayerView(
	val player: Player,
	val offers: List<Offer>,
	val bid: InitiativeBid?,
	val tradeMax: Int?,
	val wholesaleAsk: Resolution?,
	val factoriesToCollect: List<Factory>?,
	val investment: InvestmentView?,
	val interception: Interception?,
	val battle: Battle?,
	val battleReport: BattleReport?,
	val prices: MarketPrices
)

/**
 * What the viewer's Investment offers them while they may buy (E06-S04): the suggested names for a new ship
 * ([Game.freeShipNames], R62), and whether they already raised a connection (R83) or bought reputation (R87) this turn.
 */
data class InvestmentView(val freeShipNames: List<String>, val raisedConnection: Boolean, val boughtReputation: Boolean)
