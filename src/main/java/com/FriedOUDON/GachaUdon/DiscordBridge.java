package com.FriedOUDON.GachaUdon;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

public class DiscordBridge {
    private final JavaPlugin plugin;
    private boolean enabled;
    private boolean allowMentions;
    private String messageTypeKey;
    private Object discordService;
    private Object messageTypeInstance;
    private Method sendMessageMethod;
    private Method isRegisteredMethod;

    public DiscordBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("discord.sendRollResults", false);
        allowMentions = plugin.getConfig().getBoolean("discord.allowMentions", false);
        messageTypeKey = normalizeKey(plugin.getConfig().getString("discord.messageType", "gachaudon"));
        clear();

        if (!enabled) return;

        try {
            Class<?> serviceClass = Class.forName("net.essentialsx.api.v2.services.discord.DiscordService");
            Class<?> messageTypeClass = Class.forName("net.essentialsx.api.v2.services.discord.MessageType");

            if (!plugin.getServer().getPluginManager().isPluginEnabled("EssentialsDiscord")) {
                plugin.getLogger().info("EssentialsX Discord is not enabled; Discord broadcasts are disabled.");
                return;
            }

            RegisteredServiceProvider<?> provider = plugin.getServer()
                    .getServicesManager()
                    .getRegistration(serviceClass);
            if (provider == null || provider.getProvider() == null) {
                plugin.getLogger().info("EssentialsX Discord service not available; Discord broadcasts are disabled.");
                return;
            }

            discordService = provider.getProvider();

            Constructor<?> ctor = messageTypeClass.getConstructor(String.class);
            messageTypeInstance = ctor.newInstance(messageTypeKey);

            isRegisteredMethod = serviceClass.getMethod("isRegistered", String.class);
            Method registerMethod = serviceClass.getMethod("registerMessageType", Plugin.class, messageTypeClass);
            sendMessageMethod = serviceClass.getMethod("sendMessage", messageTypeClass, String.class, boolean.class);

            boolean alreadyRegistered = false;
            try {
                Object registered = isRegisteredMethod.invoke(discordService, messageTypeKey);
                if (registered instanceof Boolean b) alreadyRegistered = b;
            } catch (Exception ignored) {
                // If this check fails, just try to register below.
            }

            if (!alreadyRegistered) {
                registerMethod.invoke(discordService, plugin, messageTypeInstance);
            }
            plugin.getLogger().info("EssentialsX Discord hooked for gacha broadcasts (message type: " + messageTypeKey + ").");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().info("EssentialsX Discord is not installed; Discord broadcasts are disabled.");
            clear();
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to hook EssentialsX Discord: " + ex.getMessage());
            clear();
        }
    }

    public boolean isReady() {
        return enabled && discordService != null && messageTypeInstance != null && sendMessageMethod != null;
    }

    public void sendRollMessage(String message) {
        if (!isReady()) return;
        if (message == null || message.isBlank()) return;

        String plain = ChatColor.stripColor(message);
        try {
            sendMessageMethod.invoke(discordService, messageTypeInstance, plain, allowMentions);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send gacha result to Discord: " + ex.getMessage());
        }
    }

    public String messageTypeKey() {
        return messageTypeKey;
    }

    private void clear() {
        discordService = null;
        messageTypeInstance = null;
        sendMessageMethod = null;
        isRegisteredMethod = null;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) return "gachaudon";
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
