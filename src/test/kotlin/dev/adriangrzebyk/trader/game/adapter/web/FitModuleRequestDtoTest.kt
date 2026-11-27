package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The fit request (R65): one module as `module`, or several at once as `modules` (creator, 2026-11-26). */
class FitModuleRequestDtoTest {

	@Test
	fun shouldReadOneModuleOrSeveralAndRefuseAnyUnknownOne() {
		assertThat(FitModuleRequestDto(module = "CARGO").moduleConstants()).containsExactly(Module.CARGO)
		assertThat(FitModuleRequestDto(modules = listOf("CARGO", "CARGO", "PASSENGER")).moduleConstants())
			.containsExactly(Module.CARGO, Module.CARGO, Module.PASSENGER)
		assertThat(FitModuleRequestDto(modules = listOf("CARGO", "WARP")).moduleConstants()).isNull()
		assertThat(FitModuleRequestDto().moduleConstants()).isNull()
	}
}
