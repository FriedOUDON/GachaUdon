package com.FriedOUDON.GachaUdon;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class GachaMachine {
    private final String id;
    private final String displayName;
    private final double price;
    private final List<GachaPrize> prizes;
    private final List<PityRule> pityRules;
    private final double totalWeight;

    public GachaMachine(String id, String displayName, double price, List<GachaPrize> prizes, List<PityRule> pityRules) {
        this.id = id;
        this.displayName = displayName;
        this.price = price;
        this.prizes = Collections.unmodifiableList(prizes);
        this.pityRules = Collections.unmodifiableList(pityRules == null ? List.of() : pityRules);
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

    public List<PityRule> pityRules() {
        return pityRules;
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

    public GachaPrize rollWithFilter(Random random, Predicate<GachaPrize> filter) {
        if (!hasPrizes()) return null;
        double filteredWeight = 0.0;
        for (GachaPrize prize : prizes) {
            if (filter.test(prize)) filteredWeight += prize.chancePercent();
        }
        if (filteredWeight <= 0.0) return null;
        double r = random.nextDouble() * filteredWeight;
        double acc = 0.0;
        for (GachaPrize prize : prizes) {
            if (!filter.test(prize)) continue;
            acc += prize.chancePercent();
            if (r <= acc) return prize;
        }
        return prizes.stream().filter(filter).reduce((first, second) -> second).orElse(prizes.get(prizes.size() - 1));
    }
}
