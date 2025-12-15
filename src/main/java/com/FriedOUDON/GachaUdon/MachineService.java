package com.FriedOUDON.GachaUdon;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class MachineService {
    private final GachaUdonPlugin plugin;
    private final Map<String, GachaMachine> machines = new LinkedHashMap<>();
    private final Random random = new Random();

    public MachineService(GachaUdonPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        machines.clear();
        File folder = machineFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create machine folder at " + folder.getAbsolutePath());
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No gacha machines found in " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadMachine(file);
        }
    }

    private void loadMachine(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String rawId = cfg.getString("id");
        if (rawId == null || rawId.isBlank()) {
            rawId = file.getName().replaceFirst("\\.yml$", "");
        }
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        if (machines.containsKey(id)) {
            plugin.getLogger().warning("Duplicate machine id '" + id + "' in " + file.getName() + "; skipping.");
            return;
        }

        String displayName = resolveMachineDisplayName(cfg, rawId);
        double price = Math.max(0.0, cfg.getDouble("price", 0.0));

        List<Map<?, ?>> rawItems = cfg.getMapList("items");
        List<GachaPrize> prizes = new ArrayList<>();
        for (Map<?, ?> raw : rawItems) {
            Object itemObj = raw.get("item");
            if (itemObj == null) itemObj = raw.get("type");
            String itemId = Objects.toString(itemObj, "").trim();
            if (itemId.isEmpty()) {
                continue;
            }
            Material mat = Material.matchMaterial(itemId);
            if (mat == null) {
                plugin.getLogger().warning("Unknown item id '" + itemId + "' in " + file.getName());
                continue;
            }

            int amount = 1;
            Object amountObj = raw.get("amount");
            if (amountObj instanceof Number num) {
                amount = Math.max(1, num.intValue());
            } else if (amountObj instanceof String s && !s.isBlank()) {
                try {
                    amount = Math.max(1, Integer.parseInt(s));
                } catch (NumberFormatException ignored) {
                }
            }

            double chance = readDouble(raw.get("chance"));
            if (chance <= 0.0) {
                plugin.getLogger().warning("Chance must be > 0 for item '" + itemId + "' in " + file.getName());
                continue;
            }

            ItemStack stack = new ItemStack(mat, amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String itemDisplayNameRaw = asString(raw.get("displayName"));
                if (!itemDisplayNameRaw.isBlank()) {
                    meta.setDisplayName(color(itemDisplayNameRaw));
                }

                List<String> loreLines = asStringList(raw.get("lore"));
                if (!loreLines.isEmpty()) {
                    List<String> colored = new ArrayList<>();
                    for (String line : loreLines) colored.add(color(line));
                    meta.setLore(colored);
                }

                Integer cmd = asInt(raw.get("customModelData"));
                if (cmd != null) meta.setCustomModelData(cmd);

                applyEnchants(meta, raw.get("enchants"));
                applyItemFlags(meta, raw.get("flags"));

                stack.setItemMeta(meta);
            }

            String displayItemName = stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName()
                    : friendlyName(mat);

            prizes.add(new GachaPrize(itemId, stack, chance, displayItemName));
        }

        if (prizes.isEmpty()) {
            plugin.getLogger().warning("Machine '" + id + "' has no valid prizes; skipping.");
            return;
        }

        machines.put(id, new GachaMachine(id, displayName, price, prizes));
    }

    private double readDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    public GachaMachine get(String id) {
        if (id == null) return null;
        return machines.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, GachaMachine> all() {
        return machines;
    }

    public Random random() {
        return random;
    }

    public File machineFolder() {
        String folderName = plugin.getConfig().getString("machineFolder", "Machine");
        if (folderName == null || folderName.isBlank()) folderName = "Machine";
        return new File(plugin.getDataFolder(), folderName);
    }

    private String resolveMachineDisplayName(YamlConfiguration cfg, String rawId) {
        String displayNameRaw = cfg.getString("displayName");
        if (displayNameRaw == null || displayNameRaw.isBlank()) {
            displayNameRaw = cfg.getString("displayname");
        }
        if (displayNameRaw == null || displayNameRaw.isBlank()) {
            displayNameRaw = cfg.getString("name");
        }
        if (displayNameRaw == null || displayNameRaw.isBlank()) {
            displayNameRaw = rawId;
        }
        return ChatColor.translateAlternateColorCodes('&', displayNameRaw);
    }

    private String friendlyName(Material mat) {
        String base = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = base.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            parts[i] = parts[i].substring(0, 1).toUpperCase(Locale.ROOT) + parts[i].substring(1);
        }
        return String.join(" ", parts);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                result.add(o.toString());
            }
            return result;
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private Integer asInt(Object value) {
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private void applyEnchants(ItemMeta meta, Object rawEnchants) {
        if (!(rawEnchants instanceof Map<?,?> map)) return;
        for (var e : map.entrySet()) {
            String key = Objects.toString(e.getKey(), "");
            if (key.isBlank()) continue;
            Enchantment enchant = resolveEnchant(key);
            if (enchant == null) {
                plugin.getLogger().warning("Unknown enchant '" + key + "' on item meta");
                continue;
            }
            int level = 1;
            Object v = e.getValue();
            if (v instanceof Number num) level = Math.max(1, num.intValue());
            else if (v instanceof String s && !s.isBlank()) {
                try {
                    level = Math.max(1, Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            }
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, level, true);
            } else {
                meta.addEnchant(enchant, level, true);
            }
        }
    }

    private void applyItemFlags(ItemMeta meta, Object rawFlags) {
        if (!(rawFlags instanceof List<?> list)) return;
        for (Object o : list) {
            if (o == null) continue;
            String name = o.toString().trim().toUpperCase(Locale.ROOT);
            try {
                ItemFlag flag = ItemFlag.valueOf(name);
                meta.addItemFlags(flag);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown item flag '" + name + "' on item meta");
            }
        }
    }

    private Enchantment resolveEnchant(String key) {
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return null;

        Enchantment byName = Enchantment.getByName(trimmed.toUpperCase(Locale.ROOT));
        if (byName != null) return byName;

        NamespacedKey ns = NamespacedKey.fromString(trimmed, plugin);
        if (ns == null) ns = NamespacedKey.minecraft(trimmed.toLowerCase(Locale.ROOT));
        if (ns != null) {
            Enchantment byKey = Enchantment.getByKey(ns);
            if (byKey != null) return byKey;
        }
        return null;
    }
}
