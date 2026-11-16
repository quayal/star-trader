package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class HullTest {

	@Test
	fun shouldHoldTheThirteenHullsOfTheCreatorsTable() {
		assertThat(Hull.entries.map { it.displayName }).containsExactly(
			"Dagger", "Corco Gamma", "Flute", "Sword", "Corco Zeta", "Clarinet", "Spear", "Corco Iota", "Phoenix",
			"Corco Mu", "Monarch", "Leviathan", "Piccolo"
		)
	}

	@Test
	fun shouldCarryEveryNumberOfAHullRow() {
		val spear = Hull.SPEAR

		assertThat(listOf(spear.moduleCapacity, spear.intercept, spear.crew, spear.weapons, spear.shields, spear.builtInCargo))
			.containsExactly(5, 1, 2, 5, 4, 1)
		assertThat(listOf(spear.price, spear.repairCost)).containsExactly(600, 100)
		assertThat(spear.military).isTrue()
		assertThat(spear.aerodynamic).isFalse()
	}

	@Test
	fun shouldReadAnNInTheCargoColumnAsNoBuiltInCargo() {
		assertThat(Hull.entries.filter { it.builtInCargo == 0 }).containsExactly(
			Hull.DAGGER,
			Hull.CORCO_GAMMA,
			Hull.FLUTE,
			Hull.SWORD,
			Hull.CORCO_ZETA,
			Hull.CLARINET,
			Hull.PICCOLO
		)
	}

	@Test
	fun shouldFlagMilitaryAndAerodynamicHullsAsTheTableDoes() {
		assertThat(Hull.entries.filter { it.military }).containsExactly(Hull.DAGGER, Hull.SWORD, Hull.SPEAR)
		assertThat(Hull.entries.filter { it.aerodynamic }).containsExactly(Hull.DAGGER, Hull.CORCO_GAMMA, Hull.FLUTE, Hull.PICCOLO)
	}

	@Test
	fun shouldAcceptAsManyModulesAsItsCapacity() {
		Hull.CLARINET.checkFits(List(5) { Module.CARGO })
	}

	@Test
	fun shouldRefuseMoreModulesThanItsCapacityNamingTheHullAndItsCapacity() {
		assertThatThrownBy { Hull.CLARINET.checkFits(List(6) { Module.CARGO }) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("A Clarinet holds 5 modules, not 6.")
	}

	@Test
	fun shouldNotCountSafeJumpAgainstItsCapacity() {
		Hull.CLARINET.checkFits(List(5) { Module.CARGO } + Module.SAFE_JUMP)
	}

	@Test
	fun shouldRefuseTwoSafeJumpModules() {
		assertThatThrownBy { Hull.CLARINET.checkFits(listOf(Module.SAFE_JUMP, Module.SAFE_JUMP)) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("A ship has at most one Safe jump module.")
	}

	@Test
	fun shouldTakeNoModulesWhenItsCapacityIsZero() {
		Hull.PICCOLO.checkFits(listOf(Module.SAFE_JUMP))
		assertThatThrownBy { Hull.PICCOLO.checkFits(listOf(Module.CARGO)) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("A Piccolo holds 0 modules, not 1.")
	}
}
