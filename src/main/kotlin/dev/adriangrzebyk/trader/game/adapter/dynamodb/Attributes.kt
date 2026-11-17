package dev.adriangrzebyk.trader.game.adapter.dynamodb

import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal fun number(value: Number): AttributeValue = AttributeValue.fromN(value.toString())

internal fun strings(values: List<String>): AttributeValue = AttributeValue.fromL(values.map { AttributeValue.fromS(it) })

internal fun Map<String, AttributeValue>.attribute(name: String): AttributeValue =
	this[name] ?: throw IllegalStateException("Stored game item has no '$name' attribute")
