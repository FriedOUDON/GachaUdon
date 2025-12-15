package com.FriedOUDON.GachaUdon;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GachaMachine {
    private final String id;
    private final String displayName;
    private final double price;
    private final List<GachaPrize> prizes;
    private final double totalWeight;

    public GachaMachine(String id, String displayName, double price, List<GachaPrize> prizes) {
        this.id = id;
        this.displayName = displayName;
        this.price = price;
        this.prizes = Collections.unmodifiableList(prizes);
        this.totalWeight = prizes.stream().mapToDouble(GachaPrize::chancePercent).sum();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double price() {
        return price;
    }

    public List<GachaPrize> prizes() {
        return prizes;
    }

    public boolean hasPrizes() {
        return !prizes.isEmpty() && totalWeight > 0.0;
    }

    public GachaPrize roll(Random random) {
        if (!hasPrizes()) return null;
        double r = random.nextDouble() * totalWeight;
        double acc = 0.0;
        for (GachaPrize prize : prizes) {
            acc += prize.chancePercent();
            if (r <= acc) return prize;
        }
        return prizes.get(prizes.size() - 1);
    }
}
