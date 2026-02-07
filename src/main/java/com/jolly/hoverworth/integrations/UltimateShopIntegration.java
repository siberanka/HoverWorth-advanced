package com.jolly.hoverworth.integrations;

import com.jolly.hoverworth.HoverWorth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

public class UltimateShopIntegration {
    private final HoverWorth plugin;

    private Method getSellPricesMethod;
    private Method getResultMapMethod;
    private Field thingTypeField;
    private boolean initialized;
    private boolean available;

    public UltimateShopIntegration(HoverWorth plugin) {
        this.plugin = plugin;
    }

    public void init() {
        initialized = true;
        available = false;
        getSellPricesMethod = null;
        getResultMapMethod = null;
        thingTypeField = null;

        Plugin us = Bukkit.getPluginManager().getPlugin("UltimateShop");
        if (us == null || !us.isEnabled()) {
            plugin.getLogger().warning("UltimateShop integration selected, but UltimateShop is not enabled.");
            return;
        }

        try {
            Class<?> shopHelperClass = Class.forName("cn.superiormc.ultimateshop.api.ShopHelper");
            Class<?> giveResultClass = Class.forName("cn.superiormc.ultimateshop.objects.items.GiveResult");
            Class<?> singleThingClass = Class.forName("cn.superiormc.ultimateshop.objects.items.AbstractSingleThing");

            getSellPricesMethod = shopHelperClass.getMethod("getSellPrices", ItemStack[].class, Player.class, int.class);
            getResultMapMethod = giveResultClass.getMethod("getResultMap");
            thingTypeField = singleThingClass.getField("type");

            available = true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook UltimateShop API: " + e.getMessage());
        }
    }

    public Double getSellWorth(Player player, ItemStack itemStack) {
        if (!initialized) {
            init();
        }
        if (!available || player == null || itemStack == null) {
            return null;
        }

        try {
            ItemStack copy = itemStack.clone();
            copy.setAmount(1);

            Object result = getSellPricesMethod.invoke(null, (Object) new ItemStack[] { copy }, player, 1);
            if (result == null) {
                return null;
            }

            Object rawMap = getResultMapMethod.invoke(result);
            if (!(rawMap instanceof Map<?, ?> resultMap) || resultMap.isEmpty()) {
                return null;
            }

            double economyTotal = 0.0;
            boolean foundEconomy = false;
            Double fallback = null;

            for (Map.Entry<?, ?> entry : resultMap.entrySet()) {
                Object singleThing = entry.getKey();
                Object amountObj = entry.getValue();
                if (!(amountObj instanceof BigDecimal amount)) {
                    continue;
                }

                double numericValue = amount.doubleValue();
                if (fallback == null) {
                    fallback = numericValue;
                }

                if (isEconomyThing(singleThing)) {
                    economyTotal += numericValue;
                    foundEconomy = true;
                }
            }

            if (foundEconomy) {
                return economyTotal;
            }
            return fallback;
        } catch (Exception e) {
            if (plugin.debug) {
                plugin.getLogger().warning("UltimateShop lookup failed: " + e.getMessage());
            }
            return null;
        }
    }

    private boolean isEconomyThing(Object singleThing) {
        if (singleThing == null || thingTypeField == null) {
            return false;
        }
        try {
            Object type = thingTypeField.get(singleThing);
            if (type == null) {
                return false;
            }
            String name = type.toString();
            return "HOOK_ECONOMY".equals(name) || "VANILLA_ECONOMY".equals(name);
        } catch (Exception ignored) {
            return false;
        }
    }
}
