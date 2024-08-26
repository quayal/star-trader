package dev.adriangrzebyk.trader.domain;

import java.util.Set;

public class Player {
	String name;
	int money;
	int reputation;
	private Loan loan;
	private Set<MarketPosition> positions;

	private Set<Ship> ships;

	private Set<Warehouse> warehouses;



	public Player(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
