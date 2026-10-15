package dev.adriangrzebyk.trader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StarTraderApplication

fun main(args: Array<String>) {
	runApplication<StarTraderApplication>(*args)
}
