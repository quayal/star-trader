package dev.adriangrzebyk.trader.board

/**
 * R31: a system's three locations and their statistics — the Port's class and legal system, Space's police and
 * the Planet's security, corrected by the creator on 2026-11-20 (R31). Planet security decides landings (R116); police and
 * the legal system have no mechanic yet.
 */
data class LocationStats(val portClass: Int, val legalSystem: Int, val police: Int, val security: Int)
