package com.jolly.hoverworth;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Scheduler {

    private final Plugin plugin;
    private final boolean isFolia;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = tryFolia();
    }

    private boolean tryFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void runLater(Runnable runnable, long delay) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> runnable.run(), delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    public void runLater(Player player, Runnable runnable, long delay) {
        if (isFolia) {
            player.getScheduler().runDelayed(plugin, (task) -> runnable.run(), null, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    public void runAsync(Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }
}
