package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.OfferType;
import dev.adriangrzebyk.trader.domain.Player;
import dev.adriangrzebyk.trader.domain.StarSystem;

public record TradeOffer(
		Player player,
		StarSystem system,
		Goods goods,
		OfferType offerType,
		int price,
		int initiative) { }