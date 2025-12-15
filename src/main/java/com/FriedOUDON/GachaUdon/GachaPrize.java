package com.FriedOUDON.GachaUdon;

import org.bukkit.inventory.ItemStack;

public class GachaPrize {
    private final String itemId;
    private final ItemStack template;
    private final double chancePercent;
    private final String displayName;
    private final String rarity;
    private final int rarityRank;

    public GachaPrize(String itemId, ItemStack template, double chancePercent, String displayName, String rarity, int rarityRank) {
        this.itemId = itemId;
        this.template = template;
        this.chancePercent = chancePercent;
        this.displayName = displayName;
        this.rarity = rarity;
        this.rarityRank = rarityRank;
    }

    public String itemId() {
        return itemId;
    }

    public ItemStack createStack() {
        return template.clone();
    }

    public int amount() {
        return template.getAmount();
    }

    public double chancePercent() {
        return chancePercent;
    }

    public String displayName() {
        return displayName;
    }

    public String rarity() {
        return rarity;
    }

    public int rarityRank() {
        return rarityRank;
    }
}
