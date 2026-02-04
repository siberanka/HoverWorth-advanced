package com.jolly.hoverworth.commands;

import com.jolly.hoverworth.HoverWorth;
import com.jolly.hoverworth.integrations.EconomyShopGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Reload implements CommandExecutor {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final HoverWorth plugin;
    public Reload(HoverWorth plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String s, String [] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("hoverworth.reload")) {
            sender.sendMessage(mm.deserialize("<red>You do not have permission to use this command."));
            return true;
        }
        plugin.reloadConfig();
        plugin.getWorthFile().reload();
        if (plugin.getConfig().getString("settings.integration").equalsIgnoreCase("EconomyShopGUI")) {
            plugin.getEconomyShopGUI().loadESGUI();
        }
        player.sendMessage(mm.deserialize("<green>Successfully reloaded the HoverWorth configuration."));
        return true;
    }
}
