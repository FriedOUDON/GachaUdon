package com.FriedOUDON.GachaUdon;

import org.bukkit.inventory.ItemStack;

public class GachaPrize {
    private final String itemId;
    private final ItemStack template;
    private final double chancePercent;
    private final String displayName;

    public GachaPrize(String itemId, ItemStack template, double chancePercent, String displayName) {
        this.itemId = itemId;
        this.template = template;
        this.chancePercent = chancePercent;
        this.displayName = displayName;
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
}
