package com.jolly.hoverworth;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.jolly.hoverworth.commands.Reload;
import com.jolly.hoverworth.integrations.EconomyShopGUI;
import com.jolly.hoverworth.listeners.WindowListener;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HoverWorth extends JavaPlugin {
    private WorthFile worthFile;
    private EconomyShopGUI economyShopGUI;
    private Scheduler scheduler;
    public boolean debug = false;

    @Override
    public void onLoad() {
        scheduler = new Scheduler(this);
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.debug = getConfig().getBoolean("debug", false);
        int pluginId = 27893;
        Metrics metrics = new Metrics(this, pluginId);
        economyShopGUI = new EconomyShopGUI(this);
        PacketEvents.getAPI().init();
        worthFile = new WorthFile(this);

        WindowListener windowListener = new WindowListener(this, scheduler);
        PacketEvents.getAPI().getEventManager().registerListener(windowListener,
                PacketListenerPriority.HIGHEST);

        if (this.getCommand("hwreload") != null) {
            this.getCommand("hwreload").setExecutor(new Reload(this));
        } else {
            getLogger().severe("Command 'hwreload' not found in plugin.yml! reloading will not work.");
        }

        Bukkit.getPluginManager().registerEvents(windowListener, this);
        getLogger().info("HoverWorth enabled ✅ (Debug: " + debug + ")");
        if (getConfig().getString("settings.integration").equalsIgnoreCase("EconomyShopGUI")) {
            economyShopGUI.loadESGUI();
            getLogger().info("EconomyShopGUI integration enabled ✅");

            if (debug) {
                Map<Material, List<EconomyShopGUI.ESGUIItem>> materials = economyShopGUI.esguiWorths;
                for (Map.Entry<Material, List<EconomyShopGUI.ESGUIItem>> entry : materials.entrySet()) {
                    Material mat = entry.getKey();
                    List<EconomyShopGUI.ESGUIItem> items = entry.getValue();

                    for (EconomyShopGUI.ESGUIItem item : items) {
                        if (mat == Material.OMINOUS_BOTTLE) {
                            getLogger().info(mat + " -> sell: " + item.sell + ", strength: " + item.ominousStrength);
                            continue;
                        }

                        if (mat == Material.POTION || mat == Material.SPLASH_POTION ||
                                mat == Material.LINGERING_POTION || mat == Material.TIPPED_ARROW) {
                            getLogger().info(mat + " -> sell: " + item.sell +
                                    (item.potionTypes.isEmpty() ? "" : ", potion type(s): " + item.potionTypes));
                            continue;
                        }

                        if (!item.enchantments.isEmpty()) {
                            getLogger().info(mat + " -> sell: " + item.sell + ", enchantments: " + item.enchantments);
                            continue;
                        }

                        getLogger().info(mat + " -> sell: " + item.sell);
                    }
                }
            }
            return;
        }
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }

    public WorthFile getWorthFile() {
        return worthFile;
    }

    public EconomyShopGUI getEconomyShopGUI() {
        return economyShopGUI;
    }
}
