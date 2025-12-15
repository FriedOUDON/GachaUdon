package com.FriedOUDON.GachaUdon;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MessageService {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> bundles = new HashMap<>();
    private final Map<UUID, String> overrides = new HashMap<>();
    private String defaultLocale;
    private boolean placeholderApi;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        bundles.clear();
        this.defaultLocale = resolveDefaultLocale();
        this.placeholderApi = detectPlaceholderApi();
        load("en_US");
        load("ja_JP");
        if (!bundles.containsKey(defaultLocale)) {
            load(defaultLocale);
        }
    }

    public void setPlayerLocaleOverride(UUID uuid, String code) {
        overrides.put(uuid, code);
    }

    public String get(Player player, String key, Map<String, String> vars) {
        String result = get(localeOf(player), key, vars);
        if (placeholderApi && player != null) {
            try {
                result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
            } catch (NoClassDefFoundError ignored) {
                // PlaceholderAPI not present at runtime; leave text as-is
            }
        }
        return colorize(result);
    }

    public String get(String locale, String key, Map<String, String> vars) {
        YamlConfiguration yml = bundles.getOrDefault(locale, bundles.get("en_US"));
        String s = yml != null ? yml.getString(key) : null;
        if (s == null && !"en_US".equalsIgnoreCase(locale)) {
            YamlConfiguration fallback = bundles.get("en_US");
            if (fallback != null) s = fallback.getString(key);
        }
        if (s == null) s = key;
        if (vars != null) {
            for (var e : vars.entrySet()) {
                s = s.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return colorize(s);
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    private void load(String code) {
        File external = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (external.exists()) {
            bundles.put(code, YamlConfiguration.loadConfiguration(external));
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("lang/" + code + ".yml")),
                StandardCharsets.UTF_8
        )) {
            bundles.put(code, YamlConfiguration.loadConfiguration(reader));
        } catch (Exception ignored) {
        }
    }

    private String localeOf(Player p) {
        String override = overrides.get(p.getUniqueId());
        if (override != null && bundles.containsKey(override)) return override;
        return bundles.containsKey(defaultLocale) ? defaultLocale : "en_US";
    }

    private String resolveDefaultLocale() {
        String cfg = plugin.getConfig().getString("defaultLocale", "");
        if (cfg != null && !cfg.isBlank()) {
            return cfg.replace('-', '_');
        }
        Locale jvm = Locale.getDefault();
        String code = jvm.toString();
        if (code == null || code.isBlank()) return "en_US";
        return code.replace('-', '_');
    }

    private boolean detectPlaceholderApi() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private String colorize(String text) {
        if (text == null) return null;
        text = translateHexColors(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // Support &#RRGGBB style colors before legacy translation
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
}
