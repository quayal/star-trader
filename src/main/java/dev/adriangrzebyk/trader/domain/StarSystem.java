package dev.adriangrzebyk.trader.domain;


import java.util.*;
import java.util.function.Supplier;

import static dev.adriangrzebyk.trader.domain.Goods.*;

public enum StarSystem {
	GAMMA_LEPORIS(
			"Gamma Leporis",
			Collections.singletonList(POLY),
			StarSystem::gammaLeporisIndicators
			),
	TAU_CETI("Tau Ceti",
			 List.of(POLY, ISO),
			StarSystem::tauCetiIndicators
	),
	EPSILON_ERIDANI("Epsilon Eridani",
			List.of(Goods.values()),
			StarSystem::epsilonEridaniIndicators),
	BETA_HYDRI("Beta Hydri",
			List.of(Goods.values()),
			StarSystem::betaHydriIndicators),
	MU_HERCULIS("Mu Herculis",
			List.of(POLY, ISO, CHIPS),
			StarSystem::muHerculisIndicators),
	SIGMA_DRACONIS("Sigma Draconis",
			List.of(Goods.values()),
			StarSystem::sigmaDraconisIndicators);

	private final String name;
	private final Set<Goods> goods;
	private final Map<Goods, Integer> indicators;


	StarSystem(String name, Collection<Goods> goods, Supplier<Map<Goods, Integer>> indicatorsSupplier) {
		this.name = name;
		this.goods = new HashSet<>(goods);
		this.indicators = indicatorsSupplier.get();
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
		map.put(POLY, -7);
		map.put(ISO, -7);
		map.put(FOOD, -8);
		map.put(CHIPS, -9);
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
}