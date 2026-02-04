package com.jolly.hoverworth;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class WorthFile {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;

    public WorthFile(JavaPlugin plugin) {
        this.plugin = plugin;
        create();
    }

    private void create() {
        file = new File(plugin.getDataFolder(), "worth.yml");

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("worth.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get() {
        return config;
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save worth.yml!");
            e.printStackTrace();
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }
}
