package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E12: political, business and criminal connections, 0 to 10 each (R81–R84).
class ConnectionsTest {

	@Test
	fun shouldPlaceStartingPointsAsWholeNumbersFromZeroToTenAddingUpToThePoints() {
		// R82, E12-S01-2
		assertThat(Connections.placed(2, 3, 0, 5)).isEqualTo(Connections(2, 3, 0))
		assertThat(Connections.placed(0, 0, 6, 6)).isEqualTo(Connections(0, 0, 6))
	}

	@Test
	fun shouldRefuseAPlacementThatDoesNotAddUpOrIsOutOfRange() {
		assertThatThrownBy { Connections.placed(2, 2, 0, 5) }
			.isInstanceOf(InvalidConnectionsException::class.java)
			.hasMessage("Your connections must add up to 5 points.")
		for ((political, business, criminal) in listOf(Triple(-1, 6, 0), Triple(11, 0, 0), Triple(null, 5, 0))) {
			assertThatThrownBy { Connections.placed(political, business, criminal, 5) }
				.isInstanceOf(InvalidConnectionsException::class.java)
				.hasMessage("Enter a whole number from 0 to 10 for each connection.")
		}
	}

	@Test
	fun shouldCostTheNewLevelTimesTenToRaiseAndNothingAtTen() {
		// R83, E12-S02-4: 3 → 4 costs 40 HT
		val connections = Connections(3, 0, 10)

		assertThat(connections.raiseCost(ConnectionKind.POLITICAL)).isEqualTo(40)
		assertThat(connections.raiseCost(ConnectionKind.BUSINESS)).isEqualTo(10)
		assertThat(connections.raiseCost(ConnectionKind.CRIMINAL)).isNull()
		assertThat(connections.raised(ConnectionKind.POLITICAL)).isEqualTo(Connections(4, 0, 10))
	}

	@Test
	fun shouldAddABusinessPointOnAMarketRiseUpToTen() {
		// R83, E12-S02-6
		assertThat(Connections(1, 4, 0).afterMarketRise()).isEqualTo(Connections(1, 5, 0))
		assertThat(Connections(1, 10, 0).afterMarketRise()).isEqualTo(Connections(1, 10, 0))
	}

	@Test
	fun shouldCapFactoriesAtPoliticalPlusBusinessWordedAsTheCreatorDid() {
		// R84, E12-S03-7
		val connections = Connections(3, 4, 6)

		assertThat(connections.factoryCap()).isEqualTo(7)
		assertThat(connections.factoryCapText()).isEqualTo("You can only own 7 units of factory (POL: 3, BUSS: 4).")
	}

	@Test
	fun shouldNeverHoldALevelOutsideZeroToTen() {
		assertThatThrownBy { Connections(11, 0, 0) }.isInstanceOf(IllegalArgumentException::class.java)
		assertThatThrownBy { Connections(0, -1, 0) }.isInstanceOf(IllegalArgumentException::class.java)
	}
}
