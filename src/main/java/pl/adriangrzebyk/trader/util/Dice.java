package pl.adriangrzebyk.trader.util;

import java.util.Random;

public class Dice {

	public static int roll2D() {
		int upperBound = 6;
		Random random = new Random();
		int first = random.nextInt(upperBound) + 1;
		int second = random.nextInt(upperBound) + 1;
		return first + second;
	}
}
