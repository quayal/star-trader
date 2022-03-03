package pl.adriangrzebyk.trader.util;

import java.util.Random;

public class Dice {

	public static int roll2D() {
		return roll1D() + roll1D();
	}

	public static int roll3D() {
		return roll2D() + roll1D();
	}

	public static int roll1D() {
		return new Random().nextInt(6) + 1;
	}
}
