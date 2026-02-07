package com.jolly.hoverworth.integrations;

import com.jolly.hoverworth.HoverWorth;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class EconomyShopGUI {
    public Map<Material, List<ESGUIItem>> esguiWorths = new HashMap<>();
    private static HoverWorth plugin;

    public EconomyShopGUI(HoverWorth plugin) {
        EconomyShopGUI.plugin = plugin;
    }

    public void loadESGUI() {
        esguiWorths.clear();
        Plugin esgui = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
        if (esgui == null || !esgui.isEnabled()) return;
        File shopFolder = new File(esgui.getDataFolder(), "shops");
        if (!shopFolder.exists()) return;

        for (File file : Objects.requireNonNull(shopFolder.listFiles((dir, name) -> name.endsWith(".yml")))) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection pages = yml.getConfigurationSection("pages");
            if (pages == null) continue;

            for (String pageKey : pages.getKeys(false)) {
                ConfigurationSection items = pages.getConfigurationSection(pageKey + ".items");
                if (items == null) continue;

                for (String slotKey : items.getKeys(false)) {
                    String matStr = yml.getString("pages." + pageKey + ".items." + slotKey + ".material");
                    if (matStr == null) continue;

                    Material material = Material.matchMaterial(matStr);
                    if (material == null) continue;

                    String sellPath = "pages." + pageKey + ".items." + slotKey + ".sell";
                    if (!yml.contains(sellPath)) continue;

                    double sell = yml.getDouble(sellPath, 0.0);
                    if (sell <= 0.0) continue;

                    Map<String, Integer> enchants = new HashMap<>();
                    String enchPath = "pages." + pageKey + ".items." + slotKey + ".enchantments";
                    if (yml.contains(enchPath)) {
                        for (String e : yml.getStringList(enchPath)) {
                            String[] parts = e.split(":");
                            if (parts.length == 2) {
                                try {
                                    enchants.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }

                    int ominousStrength = yml.getInt("pages." + pageKey + ".items." + slotKey + ".ominous-strength", 0);
                    List<String> potionTypes = yml.getStringList("pages." + pageKey + ".items." + slotKey + ".potiontypes");
                    ESGUIItem esguiItem = new ESGUIItem(material, sell, enchants, ominousStrength, potionTypes);

                    esguiWorths.computeIfAbsent(material, k -> new ArrayList<>()).add(esguiItem);

                    for (List<ESGUIItem> list : esguiWorths.values()) {
                        list.sort((a, b) -> Integer.compare(b.enchantments.size(), a.enchantments.size()));
                    }
                }
            }
        }
    }


    public static class ESGUIItem {
        public Material material;
        public double sell;
        public Map<String, Integer> enchantments;
        public int ominousStrength;
        public List<String> potionTypes;

        public ESGUIItem(Material material, double sell, Map<String, Integer> enchantments, int ominousStrength, List<String> potionTypes) {
            this.material = material;
            this.sell = sell;
            this.enchantments = enchantments != null ? enchantments : new HashMap<>();
            this.ominousStrength = ominousStrength;
            this.potionTypes = potionTypes != null ? potionTypes : new ArrayList<>();
        }
    }

    public Double getESGUIItemWorth(Material item, ItemMeta meta) {
        List<EconomyShopGUI.ESGUIItem> itemList = esguiWorths.get(item);
        if (itemList == null) return null;

        Map<Enchantment, Integer> itemEnchants;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            itemEnchants = storageMeta.getStoredEnchants();
        } else {
            itemEnchants = meta.getEnchants();
        }

        for (EconomyShopGUI.ESGUIItem esguiItem : itemList) {
            boolean matches = true;

            if (!esguiItem.enchantments.isEmpty()) {
                for (Map.Entry<String, Integer> required : esguiItem.enchantments.entrySet()) {
                    boolean hasEnch = itemEnchants.entrySet().stream().anyMatch(e ->
                            e.getKey().getKey().getKey().equalsIgnoreCase(required.getKey())
                                    && e.getValue() == required.getValue()
                    );
                    if (!hasEnch) {
                        matches = false;
                        break;
                    }
                }
                if (matches && itemEnchants.size() != esguiItem.enchantments.size()) {
                    matches = false;
                }
            }

            if (matches && item == Material.OMINOUS_BOTTLE && esguiItem.ominousStrength > 0) {
                int actualStrength = -1;
                try {
                    Method getStrength = meta.getClass().getDeclaredMethod("getAmplifier");
                    getStrength.setAccessible(true);
                    Object result = getStrength.invoke(meta);
                    if (result instanceof Integer i) actualStrength = i;
                } catch (InvocationTargetException e) {
                    actualStrength = 0;
                } catch (Exception ignored) {}
                if (actualStrength + 1 != esguiItem.ominousStrength) matches = false;
            }

            if (matches && (item == Material.POTION || item == Material.SPLASH_POTION ||
                    item == Material.LINGERING_POTION || item == Material.TIPPED_ARROW)) {

                if (!esguiItem.potionTypes.isEmpty() && meta instanceof PotionMeta potionMeta) {
                    List<String> potionTypes = esguiItem.potionTypes;

                    boolean potionMatch = false;

                    String key = Objects.requireNonNull(potionMeta.getBasePotionType()).name().toUpperCase();

                    for (PotionEffect effect : potionMeta.getBasePotionType().getPotionEffects()) {
                        int amp = effect.getAmplifier();
                        int dur = effect.getDuration();
                        if (plugin.debug) plugin.getLogger().info("Name: " + key + " Amplifier: " + amp + " Duration: " + dur);

                        if (potionTypes.stream().anyMatch(t -> {
                            if (plugin.debug) plugin.getLogger().info("Type: " + t + " Match: " + key);
                            return t.equalsIgnoreCase(key);
                        }
                        )) {
                            potionMatch = true;
                            break;
                        }
                    }

                    if (!potionMatch) matches = false;
                }
            }


            if (matches) {
                if (plugin.debug) plugin.getLogger().info(esguiItem.material + " -> " + esguiItem.sell);
                return esguiItem.sell;
            }
        }

        return null;
    }

}

