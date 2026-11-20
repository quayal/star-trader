package dev.adriangrzebyk.trader.game

/** A landing without a valid location (R30), or into a Port while Ports are closed (E10), so the ship wasn't placed. */
class InvalidLandingException(message: String = "Choose where the ship ends up.") : RuntimeException(message)
