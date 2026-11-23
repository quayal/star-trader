package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.sabotage.InvalidSabotageException
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R127: a ship in a Parking Hangar is protected from sabotage for the turn it is paid for.
class ShipHangarTest {

	private val inPort = Ship.emptyAt("Meridian", TAU_CETI, ShipLocation.PORT)

	@Test
	fun shouldHaveAHangarInEverySystemButSigmaAndMu() {
		assertThat(listOf(GAMMA_LEPORIS, TAU_CETI).all { it.hasHangar() }).isTrue()
		assertThat(listOf(SIGMA_DRACONIS, MU_HERCULIS).none { it.hasHangar() }).isTrue()
	}

	@Test
	fun shouldBeProtectedOnlyInTheTurnItWasPaidFor() {
		val parked = inPort.parkedOn(4)

		assertThat(parked.isParkedOn(4)).isTrue()
		assertThat(parked.isParkedOn(5)).isFalse()
		assertThat(parked.isInHangar()).isTrue()
	}

	@Test
	fun shouldBeDueForRenewalTheTurnAfterItWasParked() {
		val parked = inPort.parkedOn(4)

		assertThat(parked.isDueHangarRenewal(4)).isFalse()
		assertThat(parked.isDueHangarRenewal(5)).isTrue()
		assertThat(parked.parkedOn(5).isDueHangarRenewal(5)).isFalse()
	}

	@Test
	fun shouldLeaveTheHangarWhenItJumps() {
		assertThat(inPort.parkedOn(4).jumpTo(GAMMA_LEPORIS).isInHangar()).isFalse()
		assertThat(inPort.parkedOn(4).jumpTo(TAU_CETI).isInHangar()).isFalse()
	}

	@Test
	fun shouldOnlyBeAskedToParkInAPortWithAHangarAndNotWhileParked() {
		assertThat(inPort.canBeParked()).isTrue()
		assertThat(inPort.parkedOn(4).canBeParked()).isFalse()
		assertThat(Ship.emptyAt("Meridian", MU_HERCULIS, ShipLocation.PORT).canBeParked()).isFalse()
		assertThat(Ship.emptyAt("Meridian", TAU_CETI, ShipLocation.SPACE).canBeParked()).isFalse()
	}

	@Test
	fun shouldRefuseParkingWhereThereIsNoHangar() {
		assertThatThrownBy { Ship.emptyAt("Meridian", SIGMA_DRACONIS, ShipLocation.PORT).parkedOn(1) }
			.isInstanceOf(InvalidHangarException::class.java)
		assertThatThrownBy { Ship.emptyAt("Meridian", TAU_CETI, ShipLocation.PLANET).parkedOn(1) }
			.isInstanceOf(InvalidHangarException::class.java)
	}

	@Test
	fun shouldPayTheFeeToPark() {
		val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines").startWith(100, listOf(inPort), 0)

		val parked = grace.parkShip("Meridian", 2)

		assertThat(parked.money).isEqualTo(95)
		assertThat(parked.ship("Meridian")!!.isParkedOn(2)).isTrue()
		assertThat(parked.leaveHangar("Meridian").ship("Meridian")!!.isInHangar()).isFalse()
	}

	@Test
	fun shouldRefuseParkingWithoutTheFee() {
		val broke = Player.newcomer("grace@example.com", "Grace", "Vega Lines").startWith(4, listOf(inPort), 0)

		assertThat(broke.canAffordHangar()).isFalse()
		assertThatThrownBy { broke.parkShip("Meridian", 2) }.isInstanceOf(InvalidHangarException::class.java)
	}

	@Test
	fun shouldListTheShipsDueForRenewal() {
		val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
			.startWith(100, listOf(inPort, Ship.emptyAt("Halcyon", TAU_CETI, ShipLocation.PORT)), 0)
			.parkShip("Meridian", 2)

		assertThat(grace.hangarRenewalsDue(2)).isEmpty()
		assertThat(grace.hangarRenewalsDue(3)).containsExactly("Meridian")
	}

	@Test
	fun shouldRefuseSabotageOnAShipParkedThisTurn() {
		val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines").startWith(100, listOf(inPort), 0).parkShip("Meridian", 2)
		val target = SabotageTarget.Ship("grace@example.com", TAU_CETI, "Meridian")

		assertThatThrownBy { grace.checkSabotageTarget(target, 2) }.isInstanceOf(InvalidSabotageException::class.java)
		grace.checkSabotageTarget(target, 3)
	}
}
