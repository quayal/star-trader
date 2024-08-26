package dev.adriangrzebyk.trader.domain;

import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.abs;

@NoArgsConstructor
public class SDCalculator {

    private final Map<Integer, Integer> indicatorByModifier = new HashMap<>();

    {
        indicatorByModifier.put(-18, 7);
        indicatorByModifier.put(-17, 6);
        indicatorByModifier.put(-16, 6);
        indicatorByModifier.put(-15, 6);
        indicatorByModifier.put(-14, 5);
        indicatorByModifier.put(-13, 5);
        indicatorByModifier.put(-12, 5);
        indicatorByModifier.put(-11, 4);
        indicatorByModifier.put(-10, 4);
        indicatorByModifier.put(-9, 4);
        indicatorByModifier.put(-8, 3);
        indicatorByModifier.put(-7, 3);
        indicatorByModifier.put(-6, 3);
        indicatorByModifier.put(-5, 2);
        indicatorByModifier.put(-4, 2);
        indicatorByModifier.put(-3, 1);
        indicatorByModifier.put(-2, 1);
        indicatorByModifier.put(-1, 0);
        indicatorByModifier.put(0, 0);
        indicatorByModifier.put(1, 0);
        indicatorByModifier.put(2, -1);
        indicatorByModifier.put(3, -1);
        indicatorByModifier.put(4, -1);
        indicatorByModifier.put(5, -2);
        indicatorByModifier.put(6, -2);
        indicatorByModifier.put(7, -2);
        indicatorByModifier.put(8, -3);
        indicatorByModifier.put(9, -3);
        indicatorByModifier.put(10, -3);
        indicatorByModifier.put(11, -4);
        indicatorByModifier.put(12, -4);
        indicatorByModifier.put(13, -4);
        indicatorByModifier.put(14, -5);
        indicatorByModifier.put(15, -5);
        indicatorByModifier.put(16, -5);
        indicatorByModifier.put(17, -5);
        indicatorByModifier.put(18, -6);
    }


    int getPriceModifier(int sDIndicator) {
        return indicatorByModifier.get(sDIndicator);
    }

    public int getHighestAbsoluteSDIndicatorByModifier(int priceModifier) {
        Set<Integer> indicators = new HashSet<>();
        indicatorByModifier.forEach((indicator, modifier) -> {
            if (modifier == priceModifier)
                indicators.add(indicator);
        });
        return indicators
                .stream()
                .max((lower, higher) -> {
                    if (lower.equals(higher)) return 0;
                    if (abs(lower) < abs(higher)) return -1;
                    return 1;
                }).orElseThrow(() -> new RuntimeException("Can't find indicator for the given price modifier: " + priceModifier));
    }
}
