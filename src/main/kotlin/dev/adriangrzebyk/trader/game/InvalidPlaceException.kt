package dev.adriangrzebyk.trader.game

/** A turn-order pick for a place that isn't free (R26), so it wasn't made. */
class InvalidPlaceException : RuntimeException("Choose a free place in the turn order.")
