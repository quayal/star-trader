package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * The hull's modules of the playtest's starting ship: 4 Cargo, 1 Light weapons, 1 Safe jump, 8 cargo spaces. Most tests were
 * written against it and need a ship with room, not the real starting ship, which has 6 spaces and a Passenger module (R132,
 * [Ship.STARTING_MODULES]; GameStartTest covers it).
 */
val PLAYTEST_MODULES: List<Module> = List(4) { Module.CARGO } + Module.LIGHT_WEAPONS + Module.SAFE_JUMP

/** A class B Clarinet fitted with [PLAYTEST_MODULES], carrying nothing: the ship most tests start from. */
fun emptyShip(name: String, system: StarSystem, location: ShipLocation): Ship =
	Ship(name, system, location, emptyMap(), Ship.STARTING_HULL, PLAYTEST_MODULES, Ship.STARTING_CREW_CLASS, null, 0, emptyList())
