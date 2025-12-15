package com.FriedOUDON.GachaUdon;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GachaUdonPlugin extends JavaPlugin {
    private MessageService messages;
    private MachineService machines;
    private Economy economy;
    private DiscordBridge discord;
    private PityService pity;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/en_US.yml", false);
        saveResource("lang/ja_JP.yml", false);
        ensureMachineFolder();
        ensureVanillaLangFolder();

        messages = new MessageService(this);
        machines = new MachineService(this);
        machines.reload();
        pity = new PityService(this);
        setupEconomy();
        applyCommandAliases();
        discord = new DiscordBridge(this);

        GachaCommand command = new GachaCommand(this);
        GachaTab tab = new GachaTab(this);
        getServer().getPluginManager().registerEvents(new GachaSignListener(this), this);
        PluginCommand cmd = getCommand("gacha");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(tab);
        } else {
            getLogger().warning("Command /gacha is missing from plugin.yml");
        }

        getLogger().info("GachaUdon enabled");
    }

    public MessageService messages() {
        return messages;
    }

    public MachineService machines() {
        return machines;
    }

    public Economy economy() {
        return economy;
    }

    public DiscordBridge discord() {
        return discord;
    }

    public PityService pity() {
        return pity;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    private void ensureMachineFolder() {
        String folderName = getConfig().getString("machineFolder", "Machine");
        if (folderName == null || folderName.isBlank()) folderName = "Machine";
        File folder = new File(getDataFolder(), folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create machine folder: " + folder.getAbsolutePath());
            return;
        }
        File[] existing = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existing == null || existing.length == 0) {
            File target = new File(folder, "sample.yml");
            try (InputStream in = getResource("Machine/sample.yml")) {
                if (in != null) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("Copied sample machine to " + target.getAbsolutePath());
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to copy sample machine: " + ex.getMessage());
            }
        }
    }

    private void ensureVanillaLangFolder() {
        File folder = new File(getDataFolder(), "vanilla-lang");
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create vanilla-lang folder: " + folder.getAbsolutePath());
            return;
        }
        copyVanillaLangIfMissing(folder, "ja_jp.json");
    }

    private void copyVanillaLangIfMissing(File folder, String name) {
        File target = new File(folder, name);
        if (target.exists()) return;
        String path = "vanilla-lang/" + name;
        try (InputStream in = getResource(path)) {
            if (in == null) {
                getLogger().warning("Bundled vanilla-lang resource not found: " + path);
                return;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Copied vanilla-lang file to " + target.getAbsolutePath());
        } catch (Exception ex) {
            getLogger().warning("Failed to copy vanilla-lang file " + name + ": " + ex.getMessage());
        }
    }

    public void applyCommandAliases() {
        PluginCommand cmd = getCommand("gacha");
        if (cmd == null) {
            getLogger().warning("Could not find /gacha command to apply aliases");
            return;
        }

        List<String> configured = getConfig().getStringList("commandAliases");
        Set<String> cleaned = new LinkedHashSet<>();
        for (String alias : configured) {
            if (alias == null) continue;
            String normalized = alias.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            if (normalized.equals("gacha")) continue;
            cleaned.add(normalized);
        }
        cmd.setAliases(new ArrayList<>(cleaned));

        CommandMap commandMap = findCommandMap();
        if (commandMap == null) {
            getLogger().warning("Could not access command map to register /gacha aliases");
            return;
        }
        cmd.unregister(commandMap);
        Map<String, org.bukkit.command.Command> known = commandMap.getKnownCommands();
        List<String> staleKeys = new ArrayList<>();
        for (Map.Entry<String, org.bukkit.command.Command> entry : known.entrySet()) {
            if (entry.getValue() == cmd) staleKeys.add(entry.getKey());
        }
        for (String key : staleKeys) {
            try {
                known.remove(key);
            } catch (UnsupportedOperationException ex) {
                break;
            }
        }
        cmd.setLabel(cmd.getName());

        String label = cmd.getName().toLowerCase(Locale.ROOT);
        String fallback = getDescription().getName().toLowerCase(Locale.ROOT);
        boolean registered = commandMap.register(label, fallback, cmd);
        if (!registered) {
            getLogger().warning("Failed to register /gacha command aliases (conflict?)");
        }
    }

    private CommandMap findCommandMap() {
        try {
            Method getter = getServer().getClass().getMethod("getCommandMap");
            Object map = getter.invoke(getServer());
            if (map instanceof CommandMap commandMap) return commandMap;
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private void setupEconomy() {
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException e) {
            getLogger().info("Vault not found; gacha payments disabled");
            return;
        }
        var rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            if (economy != null) {
                getLogger().info("Hooked into Vault economy: " + economy.getName());
            }
        }
        if (economy == null) {
            getLogger().info("No Vault economy provider found; gacha payments disabled");
        }
    }

    @Override
    public void onDisable() {
        if (pity != null) pity.save();
        getLogger().info("GachaUdon disabled");
    }
}
