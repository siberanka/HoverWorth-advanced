package com.jolly.hoverworth.commands;

import com.jolly.hoverworth.HoverWorth;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Reload implements CommandExecutor {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final HoverWorth plugin;
    public Reload(HoverWorth plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String s, String [] args) {
        if (!sender.hasPermission("hoverworth.reload")) {
            sender.sendMessage(mm.deserialize("<red>You do not have permission to use this command."));
            return true;
        }
        plugin.reloadConfig();
        plugin.getWorthFile().reload();
        String integration = plugin.getConfig().getString("settings.integration", "none");
        if (integration.equalsIgnoreCase("EconomyShopGUI")) {
            plugin.getEconomyShopGUI().loadESGUI();
        } else if (integration.equalsIgnoreCase("UltimateShop")) {
            plugin.getUltimateShopIntegration().init();
        }
        sender.sendMessage(mm.deserialize("<green>Successfully reloaded the HoverWorth configuration."));
        return true;
    }
}
