package com.FriedOUDON.GachaUdon;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class GachaTab implements TabCompleter {
    private final GachaUdonPlugin plugin;

    public GachaTab(GachaUdonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = List.of("roll", "info", "list", "reload");
            return options.stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("roll") || sub.equals("info") || sub.equals("pull") || sub.equals("spin")) {
                return plugin.machines().all().keySet().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("roll") || sub.equals("pull") || sub.equals("spin")) {
                List<String> options = new ArrayList<>();
                int max = Math.max(1, plugin.getConfig().getInt("maxRollsPerCommand", 10));
                int limit = max;
                for (int i = 1; i <= limit; i++) options.add(String.valueOf(i));
                return options.stream()
                        .filter(opt -> opt.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
