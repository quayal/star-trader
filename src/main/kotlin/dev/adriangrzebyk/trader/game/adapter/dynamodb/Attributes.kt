package dev.adriangrzebyk.trader.game.adapter.dynamodb

import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal fun number(value: Number): AttributeValue = AttributeValue.fromN(value.toString())

internal fun Map<String, AttributeValue>.attribute(name: String): AttributeValue =
	this[name] ?: throw IllegalStateException("Stored game item has no '$name' attribute")
