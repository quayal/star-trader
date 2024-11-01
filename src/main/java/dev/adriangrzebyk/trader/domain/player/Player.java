package dev.adriangrzebyk.trader.domain.player;

import dev.adriangrzebyk.trader.domain.Loan;
import dev.adriangrzebyk.trader.domain.MarketPosition;
import dev.adriangrzebyk.trader.domain.Ship;
import dev.adriangrzebyk.trader.domain.Warehouse;

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
