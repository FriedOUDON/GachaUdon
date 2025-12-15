package com.FriedOUDON.GachaUdon;

public class PityRule {
    private final int threshold;
    private final String minRarityName;
    private final int minRarityRank;
    private final String message;

    public PityRule(int threshold, String minRarityName, int minRarityRank, String message) {
        this.threshold = threshold;
        this.minRarityName = minRarityName;
        this.minRarityRank = minRarityRank;
        this.message = message;
    }

    public int threshold() {
        return threshold;
    }

    public String minRarityName() {
        return minRarityName;
    }

    public int minRarityRank() {
        return minRarityRank;
    }

    public String message() {
        return message;
    }

    public boolean matches(GachaPrize prize) {
        if (prize == null) return false;
        return prize.rarityRank() >= minRarityRank;
    }
}
