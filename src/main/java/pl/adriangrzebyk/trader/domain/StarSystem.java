package pl.adriangrzebyk.trader.domain;

import java.util.*;

public enum StarSystem {
	GAMMA_LEPORIS("Gamma Leporis", Collections.singletonList(Goods.POLY)),
	TAU_CETI("Tau Ceti", Arrays.asList(Goods.POLY, Goods.ISO)),
	EPSILON_ERIDANI("Epsilon Eridani", Arrays.asList(Goods.values())),
	BETA_HYDRI("Beta Hydri", Arrays.asList(Goods.values())),
	MU_HERCULIS("Mu Herculis", Arrays.asList(Goods.POLY, Goods.ISO, Goods.CHIPS)),
	SIGMA_DRACONIS("Sigma Draconis", Arrays.asList(Goods.values()));

	private final String name;
	private final Set<Goods> goods;


	StarSystem(String name, Collection<Goods> goods) {
		this.name = name;
		this.goods = new HashSet<>(goods);
	}
}

