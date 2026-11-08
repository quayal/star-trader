package dev.adriangrzebyk.trader.game

/** How many of a trade's [units] the trader's ship [shipName] loads (a buy) or unloads (a sell) (R42, E05-S04-3). */
data class ShipLoad(val shipName: String, val units: Int)
