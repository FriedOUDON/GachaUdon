package com.FriedOUDON.GachaUdon;

import org.bukkit.ChatColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Map;

public class GachaSignListener implements Listener {
    private final GachaUdonPlugin plugin;

    public GachaSignListener(GachaUdonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        BlockState state = event.getClickedBlock() != null ? event.getClickedBlock().getState() : null;
        if (!(state instanceof Sign sign)) return;

        String[] lines = sign.getLines();
        if (lines.length == 0 || !isGachaMarker(lines[0])) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("gachaudon.use")) {
            player.sendMessage(plugin.messages().get(player, "no-permission", null));
            return;
        }

        String machineId = clean(lines, 1);
        if (machineId.isEmpty()) {
            player.sendMessage(plugin.messages().get(player, "sign.missing-machine", null));
            return;
        }

        String actionOrCount = clean(lines, 2);
        boolean info = "info".equalsIgnoreCase(actionOrCount);

        int count = 1;
        if (!info && !actionOrCount.isEmpty()) {
            try {
                count = Integer.parseInt(actionOrCount);
            } catch (NumberFormatException ignored) {
                sendInvalidLine(player, actionOrCount);
                return;
            }
        }

        if (info) {
            player.performCommand("gacha info " + machineId);
        } else {
            if (count < 1 || count > maxRolls()) {
                sendInvalidCount(player);
                return;
            }
            player.performCommand("gacha roll " + machineId + " " + count);
        }
    }

    private boolean isGachaMarker(String line) {
        if (line == null) return false;
        String stripped = ChatColor.stripColor(line);
        if (stripped == null) stripped = "";
        return "[gachaudon]".equalsIgnoreCase(stripped.trim());
    }

    private String clean(String[] lines, int index) {
        if (index >= lines.length || lines[index] == null) return "";
        String stripped = ChatColor.stripColor(lines[index]);
        if (stripped == null) stripped = "";
        return stripped.trim();
    }

    private void sendInvalidCount(Player player) {
        player.sendMessage(plugin.messages().get(player, "errors.invalid-count", Map.of(
                "max", String.valueOf(maxRolls())
        )));
    }

    private void sendInvalidLine(Player player, String raw) {
        player.sendMessage(plugin.messages().get(player, "sign.invalid-action", Map.of(
                "action", raw
        )));
    }

    private int maxRolls() {
        return Math.max(1, plugin.getConfig().getInt("maxRollsPerCommand", 10));
    }
}
