package com.FriedOUDON.GachaUdon;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PityService {
    public record PityOutcome(GachaPrize prize, PityRule rule) {
        public boolean pityHit() {
            return rule != null;
        }
    }

    private final GachaUdonPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    public PityService(GachaUdonPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "pity-data.yml");
        reload();
    }

    public void reload() {
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                dataFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        if (data == null) return;
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pity data: " + ex.getMessage());
        }
    }

    public PityOutcome roll(UUID playerId, GachaMachine machine, java.util.Random random) {
        if (machine == null || random == null) return new PityOutcome(null, null);
        if (data == null) reload();
        List<PityRule> rules = machine.pityRules();
        if (rules.isEmpty()) {
            return new PityOutcome(machine.roll(random), null);
        }

        int[] counters = counters(playerId, machine);
        int forcedRuleIndex = triggeredRuleIndex(counters, rules);
        PityRule forcedRule = forcedRuleIndex >= 0 ? rules.get(forcedRuleIndex) : null;

        GachaPrize prize = null;
        if (forcedRule != null) {
            prize = machine.rollWithFilter(random, forcedRule::matches);
            if (prize == null) {
                plugin.getLogger().warning("Pity rule triggered for machine '" + machine.id() + "', but no prize matched rarity '"
                        + forcedRule.minRarityName() + "'. Rolling normally.");
            }
        }
        if (prize == null) {
            prize = machine.roll(random);
        }

        updateCounters(counters, rules, prize);
        storeCounters(playerId, machine, counters);
        save();

        return new PityOutcome(prize, forcedRule);
    }

    private int[] counters(UUID playerId, GachaMachine machine) {
        String path = path(playerId, machine);
        List<Integer> stored = data.getIntegerList(path);
        int size = machine.pityRules().size();
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            if (i < stored.size()) {
                arr[i] = Math.max(0, stored.get(i));
            } else {
                arr[i] = 0;
            }
        }
        return arr;
    }

    private void storeCounters(UUID playerId, GachaMachine machine, int[] counters) {
        List<Integer> list = new ArrayList<>(counters.length);
        for (int counter : counters) list.add(counter);
        data.set(path(playerId, machine), list);
    }

    private String path(UUID playerId, GachaMachine machine) {
        return "players." + playerId.toString().toLowerCase(Locale.ROOT) + "." + machine.id();
    }

    private int triggeredRuleIndex(int[] counters, List<PityRule> rules) {
        int result = -1;
        int bestThreshold = -1;
        for (int i = 0; i < rules.size() && i < counters.length; i++) {
            int nextCount = counters[i] + 1;
            PityRule rule = rules.get(i);
            if (nextCount >= rule.threshold() && rule.threshold() >= bestThreshold) {
                result = i;
                bestThreshold = rule.threshold();
            }
        }
        return result;
    }

    private void updateCounters(int[] counters, List<PityRule> rules, GachaPrize prize) {
        for (int i = 0; i < counters.length && i < rules.size(); i++) {
            PityRule rule = rules.get(i);
            if (rule.matches(prize)) {
                counters[i] = 0;
            } else {
                counters[i] = counters[i] + 1;
            }
        }
    }
}
