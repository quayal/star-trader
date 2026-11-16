package dev.adriangrzebyk.trader.game.adapter.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TradeRequestDtoTest {

	@Test
	fun shouldReadNoWarehouseUnitsAsZero() {
		// R49: a request from before warehouses has no field
		assertThat(TradeRequestDto(BigDecimal(4)).warehouseUnits()).isZero()
	}

	@Test
	fun shouldReadAWholeNumberOfWarehouseUnits() {
		assertThat(TradeRequestDto(BigDecimal(4), null, BigDecimal(3)).warehouseUnits()).isEqualTo(3)
		assertThat(TradeRequestDto(BigDecimal(4), null, BigDecimal("3.0")).warehouseUnits()).isEqualTo(3)
	}

	@Test
	fun shouldReadAFractionOfAUnitAsNotWhole() {
		assertThat(TradeRequestDto(BigDecimal(4), null, BigDecimal("2.5")).warehouseUnits()).isNull()
	}

	@Test
	fun shouldGiveAWholesaleRequestsWarehouseUnitsToItsTrade() {
		assertThat(WholesaleRequestDto("BUY", BigDecimal(2), emptyList(), BigDecimal(2)).trade().warehouseUnits()).isEqualTo(2)
	}
}
