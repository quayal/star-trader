package dev.adriangrzebyk.trader.game

/** A landing without a valid location (R30), so the ship wasn't placed. */
class InvalidLandingException : RuntimeException("Choose where the ship ends up.")
