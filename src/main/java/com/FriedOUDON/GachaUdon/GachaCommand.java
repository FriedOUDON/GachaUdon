package com.FriedOUDON.GachaUdon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GachaCommand implements CommandExecutor {
    private final GachaUdonPlugin plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public GachaCommand(GachaUdonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gachaudon.use")) {
            sender.sendMessage(msg(sender, "no-permission", null));
            return true;
        }

        if (args.length == 0) {
            showList(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("gachaudon.admin")) {
                    sender.sendMessage(msg(sender, "no-permission", null));
                    return true;
                }
                plugin.reloadConfig();
                plugin.messages().reload();
                plugin.machines().reload();
                plugin.pity().reload();
                plugin.applyCommandAliases();
                plugin.discord().reload();
                sender.sendMessage(msg(sender, "reloaded", null));
            }
            case "list" -> showList(sender);
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg(sender, "errors.usage-info", Map.of("label", label)));
                    return true;
                }
                showInfo(sender, args[1]);
            }
            case "roll", "spin", "pull" -> roll(sender, label, args);
            default -> sender.sendMessage(msg(sender, "unknown-subcommand", null));
        }
        return true;
    }

    private void showList(CommandSender sender) {
        Map<String, GachaMachine> all = plugin.machines().all();
        if (all.isEmpty()) {
            sender.sendMessage(msg(sender, "errors.no-machines", null));
            return;
        }
        sender.sendMessage(msg(sender, "list.header", Map.of("count", String.valueOf(all.size()))));
        for (GachaMachine machine : all.values()) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("id", machine.id());
            vars.put("name", machine.displayName());
            vars.put("cost", formatCurrency(machine.price()));
            sender.sendMessage(msg(sender, "list.entry", vars));
        }
    }

    private void showInfo(CommandSender sender, String machineId) {
        GachaMachine machine = plugin.machines().get(machineId);
        if (machine == null) {
            sender.sendMessage(msg(sender, "errors.unknown-machine", Map.of("id", machineId)));
            return;
        }
        if (!machine.hasPrizes()) {
            sender.sendMessage(msg(sender, "errors.empty-machine", Map.of("id", machine.id())));
            return;
        }
        Locale locale = effectiveItemLocale(sender);
        Map<String, String> headerVars = new LinkedHashMap<>();
        headerVars.put("id", machine.id());
        headerVars.put("name", machine.displayName());
        headerVars.put("cost", formatCurrency(machine.price()));
        sender.sendMessage(msg(sender, "info.header", headerVars));
        if (!machine.pityRules().isEmpty()) {
            sender.sendMessage(msg(sender, "info.pity-header", null));
            for (PityRule rule : machine.pityRules()) {
                Map<String, String> vars = new LinkedHashMap<>();
                vars.put("pulls", String.valueOf(rule.threshold()));
                vars.put("rarity", rule.minRarityName());
                sender.sendMessage(msg(sender, "info.pity-entry", vars));
            }
        }
        for (GachaPrize prize : machine.prizes()) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("item", infoItemName(prize, locale));
            vars.put("amount", String.valueOf(prize.createStack().getAmount()));
            vars.put("chance", String.format(Locale.US, "%.2f", prize.chancePercent()));
            sender.sendMessage(msg(sender, "info.entry", vars));
        }
    }

    private void roll(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg(sender, "errors.players-only", null));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(msg(sender, "errors.usage-roll", Map.of("label", label)));
            return;
        }

        if (isWorldDisabled(player.getWorld().getName())) {
            sender.sendMessage(msg(sender, "errors.disabled-world", Map.of("world", player.getWorld().getName())));
            return;
        }

        GachaMachine machine = plugin.machines().get(args[1]);
        if (machine == null) {
            sender.sendMessage(msg(sender, "errors.unknown-machine", Map.of("id", args[1])));
            return;
        }
        if (!machine.hasPrizes()) {
            sender.sendMessage(msg(sender, "errors.empty-machine", Map.of("id", machine.id())));
            return;
        }

        int count = 1;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(msg(sender, "errors.invalid-count", Map.of(
                        "max", String.valueOf(maxRolls())
                )));
                return;
            }
        }
        if (count < 1 || count > maxRolls()) {
            sender.sendMessage(msg(sender, "errors.invalid-count", Map.of(
                    "max", String.valueOf(maxRolls())
            )));
            return;
        }

        double totalCost = machine.price() * count;
        if (totalCost > 0.0) {
            if (!plugin.hasEconomy()) {
                sender.sendMessage(msg(sender, "payments.no-economy", null));
                return;
            }
            Economy econ = plugin.economy();
            if (!econ.has(player, totalCost)) {
                sender.sendMessage(msg(sender, "payments.insufficient", Map.of(
                        "cost", econ.format(totalCost)
                )));
                return;
            }
            econ.withdrawPlayer(player, totalCost);
            sender.sendMessage(msg(sender, "payments.charged", Map.of(
                    "cost", econ.format(totalCost),
                    "count", String.valueOf(count)
            )));
        }

        List<ItemStack> awarded = new ArrayList<>();
        Locale locale = effectiveItemLocale(player);
        for (int i = 0; i < count; i++) {
            PityService.PityOutcome outcome = plugin.pity().roll(player.getUniqueId(), machine, plugin.machines().random());
            GachaPrize prize = outcome.prize();
            if (prize == null) {
                sender.sendMessage(msg(sender, "errors.empty-machine", Map.of("id", machine.id())));
                return;
            }
            if (outcome.pityHit()) {
                Map<String, String> pityVars = new LinkedHashMap<>();
                pityVars.put("rarity", outcome.rule().minRarityName());
                pityVars.put("pulls", String.valueOf(outcome.rule().threshold()));
                pityVars.put("machine", machine.displayName());
                String customMessage = outcome.rule().message();
                if (customMessage != null && !customMessage.isBlank()) {
                    String rendered = customMessage
                            .replace("%rarity%", pityVars.get("rarity"))
                            .replace("%pulls%", pityVars.get("pulls"))
                            .replace("%machine%", pityVars.get("machine"));
                    sender.sendMessage(colorize(rendered));
                } else {
                    sender.sendMessage(msg(sender, "roll.pity-hit", pityVars));
                }
            }

            ItemStack stack = prize.createStack();
            var leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            }
            awarded.add(stack.clone());
            if (count == 1) {
                Map<String, String> vars = new LinkedHashMap<>();
                vars.put("machine", machine.displayName());
                vars.put("item", localizedItemName(prize, locale));
                vars.put("amount", String.valueOf(stack.getAmount()));
                sender.sendMessage(msg(sender, "roll.result-single", vars));
                broadcastSingle(player, machine.displayName(), prize);
            }
        }

        if (count > 1) {
            List<String> parts = buildResults(awarded, locale);
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("machine", machine.displayName());
            vars.put("results", parts.stream().collect(Collectors.joining(", ")));
            sender.sendMessage(msg(sender, "roll.result-summary", vars));
            broadcastSummary(player, machine.displayName(), awarded, count);
        }
    }

    private int maxRolls() {
        return Math.max(1, plugin.getConfig().getInt("maxRollsPerCommand", 10));
    }

    private String msg(CommandSender sender, String key, Map<String, String> vars) {
        if (sender instanceof Player player) return plugin.messages().get(player, key, vars);
        return plugin.messages().get(plugin.messages().defaultLocale(), key, vars);
    }

    private String formatCurrency(double amount) {
        Economy econ = plugin.economy();
        if (econ != null) return econ.format(amount);
        return String.format(Locale.US, "%.2f", amount);
    }

    private void broadcastSingle(Player roller, String machineName, GachaPrize prize) {
        if (roller == null || prize == null) return;
        Locale locale = effectiveItemLocale(roller);
        Map<String, String> varsTemplate = new LinkedHashMap<>();
        varsTemplate.put("player", roller.getName());
        varsTemplate.put("machine", machineName);
        varsTemplate.put("amount", String.valueOf(prize.createStack().getAmount()));
        varsTemplate.put("item", localizedItemName(prize, locale));

        String message = plugin.messages().get(roller, "broadcast.result-single", varsTemplate);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("gachaudon.broadcast")) continue;
            online.sendMessage(message);
        }

        Bukkit.getConsoleSender().sendMessage(message);
        if (plugin.discord() != null) plugin.discord().sendRollMessage(message);
    }

    private void broadcastSummary(Player roller, String machineName, List<ItemStack> awarded, int count) {
        if (roller == null || awarded.isEmpty()) return;
        Locale locale = effectiveItemLocale(roller);
        Map<String, String> varsTemplate = new LinkedHashMap<>();
        varsTemplate.put("player", roller.getName());
        varsTemplate.put("machine", machineName);
        varsTemplate.put("count", String.valueOf(count));

        List<String> parts = buildResults(awarded, locale);
        Map<String, String> messageVars = new LinkedHashMap<>(varsTemplate);
        messageVars.put("results", String.join(", ", parts));
        String message = plugin.messages().get(roller, "broadcast.result-summary", messageVars);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("gachaudon.broadcast")) continue;
            online.sendMessage(message);
        }

        Bukkit.getConsoleSender().sendMessage(message);
        if (plugin.discord() != null) plugin.discord().sendRollMessage(message);
    }

    private List<String> buildResults(List<ItemStack> awarded, Locale locale) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : awarded) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            String name = localizedItemName(stack, locale);
            totals.merge(name, stack.getAmount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (var entry : totals.entrySet()) {
            parts.add(entry.getKey() + " x" + entry.getValue());
        }
        return parts;
    }

    private Locale localeOf(CommandSender sender) {
        if (sender instanceof Player p) {
            try {
                Locale l = p.locale();
                if (l != null) return l;
            } catch (NoSuchMethodError ignored) {}
            String legacy = null;
            try {
                legacy = p.getLocale();
            } catch (NoSuchMethodError ignored) {}
            if (legacy != null && !legacy.isBlank()) {
                return localeFromString(legacy);
            }
        }
        return localeFromString(plugin.messages().defaultLocale());
    }

    private Locale effectiveItemLocale(CommandSender sender) {
        String override = plugin.getConfig().getString("itemNameLocaleOverride", "").trim();
        if (!override.isEmpty()) {
            return localeFromString(override);
        }
        return localeOf(sender);
    }

    private Locale localeFromString(String code) {
        if (code == null || code.isBlank()) return Locale.getDefault();
        String normalized = code.replace('_', '-');
        return Locale.forLanguageTag(normalized);
    }

    // Attempt to load vanilla Minecraft translation files for locales where GlobalTranslator isn't populated (e.g., Spigot)
    private static final Map<String, Map<String, String>> VANILLA_CACHE = new HashMap<>();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    private String lookupVanillaTranslation(String translationKey, Locale locale) {
        String code = minecraftLangCode(locale);
        Map<String, String> map = loadVanillaLang(code);
        String translated = map.getOrDefault(translationKey, "");
        if (!translated.isBlank()) return translated;

        if (!"en_us".equals(code)) {
            Map<String, String> fallback = loadVanillaLang("en_us");
            translated = fallback.getOrDefault(translationKey, "");
            if (!translated.isBlank()) return translated;
        }
        return "";
    }

    private Map<String, String> loadVanillaLang(String code) {
        return VANILLA_CACHE.computeIfAbsent(code, c -> {
            String path = "assets/minecraft/lang/" + c + ".json";
            InputStream in = null;
            try {
                // 1) external override under plugins/GachaUdon/vanilla-lang/<code>.json
                java.io.File ext = new java.io.File(plugin.getDataFolder(), "vanilla-lang/" + c + ".json");
                if (ext.exists()) {
                    try (InputStreamReader reader = new InputStreamReader(new java.io.FileInputStream(ext), StandardCharsets.UTF_8)) {
                        Map<String, String> parsed = new Gson().fromJson(reader, MAP_TYPE);
                        if (parsed != null) return parsed;
                    } catch (Exception ignored) {}
                }

                ClassLoader[] loaders = new ClassLoader[] {
                        plugin.getClass().getClassLoader(),
                        plugin.getServer().getClass().getClassLoader(),
                        Thread.currentThread().getContextClassLoader(),
                        ClassLoader.getSystemClassLoader(),
                        Material.class.getClassLoader()
                };
                for (ClassLoader cl : loaders) {
                    if (cl == null) continue;
                    in = cl.getResourceAsStream(path);
                    if (in != null) break;
                }
                if (in == null) return Map.of();
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> parsed = new Gson().fromJson(reader, MAP_TYPE);
                    if (parsed != null) return parsed;
                }
            } catch (Exception ignored) {
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
            }
            return Map.of();
        });
    }

    private String minecraftLangCode(Locale locale) {
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if (country != null && !country.isBlank()) {
            return (lang + "_" + country).toLowerCase(Locale.ROOT);
        }
        return lang.toLowerCase(Locale.ROOT);
    }

    private String localizedItemName(GachaPrize prize, Locale locale) {
        ItemStack stack = prize.createStack();
        if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        String localized = localizedMaterialName(stack.getType(), locale);
        if (!localized.isBlank()) return localized;
        String i18n = tryI18nDisplayName(stack);
        if (!i18n.isBlank()) return i18n;
        String fallback = prize.displayName();
        if (fallback != null && !fallback.isBlank()) return fallback;
        return friendlyName(stack.getType());
    }

    private String infoItemName(GachaPrize prize, Locale locale) {
        ItemStack stack = prize.createStack();
        boolean hasCustom = stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName();
        String vanilla = localizedMaterialName(stack.getType(), locale);
        if (vanilla.isBlank()) vanilla = friendlyName(stack.getType());

        String display = hasCustom ? stack.getItemMeta().getDisplayName() : vanilla;
        String format = plugin.getConfig().getString("info.itemDisplayFormat", "%displayName% (%vanilla%)");
        String combined = format
                .replace("%displayName%", display != null ? display : "")
                .replace("%vanilla%", vanilla != null ? vanilla : "");
        return colorize(combined);
    }

    private String localizedItemName(ItemStack stack, Locale locale) {
        if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        String localized = localizedMaterialName(stack.getType(), locale);
        if (!localized.isBlank()) return localized;
        String i18n = tryI18nDisplayName(stack);
        if (!i18n.isBlank()) return i18n;
        return friendlyName(stack.getType());
    }

    private String localizedMaterialName(Material mat, Locale locale) {
        // Prefer vanilla lang lookup for non-English locales or when override is set
        if (!isEnglish(locale) || hasItemLocaleOverride()) {
            String vanilla = lookupVanillaTranslation(mat.translationKey(), locale);
            if (!vanilla.isBlank()) return vanilla;
        }
        try {
            Component rendered = GlobalTranslator.render(Component.translatable(mat.translationKey()), locale);
            String legacy = LegacyComponentSerializer.legacySection().serialize(rendered);
            if (!legacy.isBlank()) {
                // If locale is non-English and translation matches en_us, try vanilla before accepting
                if (!isEnglish(locale) && !hasItemLocaleOverride()) {
                    String en = lookupVanillaTranslation(mat.translationKey(), Locale.forLanguageTag("en-US"));
                    if (!en.isBlank() && en.equals(legacy)) {
                        String vanilla = lookupVanillaTranslation(mat.translationKey(), locale);
                        if (!vanilla.isBlank()) return vanilla;
                    }
                }
                return legacy;
            }
        } catch (Exception ignored) {}
        String vanilla = lookupVanillaTranslation(mat.translationKey(), locale);
        if (!vanilla.isBlank()) return vanilla;
        return friendlyName(mat);
    }

    private String colorize(String text) {
        if (text == null) return null;
        text = translateHexColors(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String translateHexColors(String input) {
        if (input == null || input.indexOf('&') == -1) return input;
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isEnglish(Locale locale) {
        if (locale == null) return false;
        return locale.getLanguage().equalsIgnoreCase("en");
    }

    private boolean isWorldDisabled(String worldName) {
        if (worldName == null) return false;
        List<String> disabled = plugin.getConfig().getStringList("disableWorlds");
        for (String w : disabled) {
            if (w == null) continue;
            if (worldName.equalsIgnoreCase(w.trim())) return true;
        }
        return false;
    }

    private boolean hasItemLocaleOverride() {
        String override = plugin.getConfig().getString("itemNameLocaleOverride", "");
        return override != null && !override.trim().isEmpty();
    }

    private String tryI18nDisplayName(ItemStack stack) {
        try {
            String v = stack.getI18NDisplayName();
            if (v != null) return v;
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }
        return "";
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
}
