package dev.adriangrzebyk.trader.login

class OneTimeCodeDeliveryException(cause: Throwable) : RuntimeException("The one-time code could not be sent", cause)
