package dev.adriangrzebyk.trader.game.adapter.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The game's fixed rules for the Guide tab and the labels built from them (playtest, 2026-11-20). Public, like `/api/options`. */
@RestController
@RequestMapping("/api")
class RulesController {

	@GetMapping("/rules")
	fun rules(): RulesResponseDto = RulesResponseDto()
}
