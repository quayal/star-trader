package dev.adriangrzebyk.trader.domain;


import java.util.*;
import java.util.function.Supplier;

import static dev.adriangrzebyk.trader.domain.Goods.*;

public enum StarSystem {
	GAMMA_LEPORIS(
			"Gamma Leporis",
			StarSystem::gammaLeporisPrices,
			StarSystem::gammaLeporisIndicators
			),
	TAU_CETI("Tau Ceti",
			StarSystem::tauCetiPrices,
			StarSystem::tauCetiIndicators
	),
	EPSILON_ERIDANI("Epsilon Eridani",
			StarSystem::epsilonEridaniPrices,
			StarSystem::epsilonEridaniIndicators),
	BETA_HYDRI("Beta Hydri",
			StarSystem::betaHydriPrices,
			StarSystem::betaHydriIndicators),
	MU_HERCULIS("Mu Herculis",
			StarSystem::muHerculisPrices,
			StarSystem::muHerculisIndicators),
	SIGMA_DRACONIS("Sigma Draconis",
			StarSystem::sigmaDraconisPrices,
			StarSystem::sigmaDraconisIndicators);

	private final String name;
	private final Map<Goods, Integer> goodsPrices;
	private final Map<Goods, Integer> indicators;


	StarSystem(String name, Supplier<Map<Goods, Integer>> goodsSupplier, Supplier<Map<Goods, Integer>> indicatorsSupplier) {
		this.name = name;
		this.goodsPrices = goodsSupplier.get();
		this.indicators = indicatorsSupplier.get();
	}

	private static HashMap<Goods, Integer> gammaLeporisPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 2);
		return map;
	}

	private static HashMap<Goods, Integer> tauCetiPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 6);
		map.put(ISO, 4);
		return map;
	}

	private static HashMap<Goods, Integer> epsilonEridaniPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 5);
		map.put(ISO, 10);
		map.put(FOOD, 14);
		map.put(CHIPS, 17);
		return map;
	}

	private static HashMap<Goods, Integer> betaHydriPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 6);
		map.put(ISO, 10);
		map.put(FOOD, 15);
		map.put(CHIPS, 18);
		return map;
	}

	private static HashMap<Goods, Integer> muHerculisPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 8);
		map.put(ISO, 11);
		map.put(CHIPS, -12);
		return map;
	}

	private static HashMap<Goods, Integer> sigmaDraconisPrices() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 8);
		map.put(ISO, 4);
		map.put(FOOD, 10);
		map.put(CHIPS, 14);
		return map;
	}



	private static HashMap<Goods, Integer> gammaLeporisIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, 1);
		return map;
	}

	private static HashMap<Goods, Integer> tauCetiIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, -10);
		map.put(ISO, -9);
		return map;
	}

	private static HashMap<Goods, Integer> epsilonEridaniIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, -7);
		map.put(ISO, -7);
		map.put(FOOD, -8);
		map.put(CHIPS, -9);
		return map;
	}

	private static HashMap<Goods, Integer> betaHydriIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, -8);
		map.put(ISO, -8);
		map.put(FOOD, -9);
		map.put(CHIPS, -10);
		return map;
	}

	private static HashMap<Goods, Integer> muHerculisIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, -9);
		map.put(ISO, 1);
		map.put(CHIPS, -4);
		return map;
	}

	private static HashMap<Goods, Integer> sigmaDraconisIndicators() {
		HashMap<Goods, Integer> map = new HashMap<>();
		map.put(POLY, -5);
		map.put(ISO, -1);
		map.put(FOOD, -3);
		map.put(CHIPS, -8);
		return map;
	}

	public Integer getIndicator(Goods goods) {
		return indicators.get(goods);
	}

	public Integer getPrice(Goods goods) {
		return goodsPrices.get(goods);
	}
}