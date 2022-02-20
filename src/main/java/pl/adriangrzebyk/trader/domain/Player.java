package pl.adriangrzebyk.trader.domain;

import java.util.Set;

public class Player {
	String name;
	int money;
	int reputation;
	private Loan loan;
	private Set<MarketPosition> positions;

	public Player(String name) {
		this.name = name;
	}
}
