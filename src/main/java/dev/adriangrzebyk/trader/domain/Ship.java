package dev.adriangrzebyk.trader.domain;

import java.util.ArrayList;
import java.util.List;

public class Ship {

    private final String name;

    public Ship(String name) {
        this.name = name;
        modules = new ArrayList<>();
    }

    List<ShipModule> modules;

}
