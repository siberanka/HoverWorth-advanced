package com.jolly.hoverworth.listeners;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.jolly.hoverworth.HoverWorth;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WindowListener implements PacketListener, Listener {
    private final HoverWorth plugin;
    private final String symbol;
    MiniMessage mm = MiniMessage.miniMessage();

    public WindowListener(HoverWorth plugin) {
        this.plugin = plugin;
        this.symbol = plugin.getConfig().getString("settings.currency-symbol", "$");
    }



    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = wrapper.getItems();
            for (int i = 0; i < items.size(); i++) {
                items.set(i, addLore(items.get(i)));
            }
            wrapper.setItems(items);
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot wrapper =
                    new WrapperPlayServerSetSlot(event);

            ItemStack item = wrapper.getItem();
            if (item != null && !item.isEmpty()) {
                wrapper.setItem(addLore(item));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        org.bukkit.inventory.ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.AIR) return;
        //org.bukkit.inventory.ItemStack itemClone = event.getCurrentItem().clone();
        //todo, add dynamic lore based in item amount
        event.setCurrentItem(addLoreBukkit(item));
    }

    private ItemStack addLore(ItemStack packetItem) {
        if (packetItem == null || packetItem.isEmpty())
            return packetItem;

        org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(packetItem);
        ItemMeta meta = bukkitItem.getItemMeta();
        if (meta == null) return packetItem;

        Material item = bukkitItem.getType();

        // Only add lore if item exists in worth.yml
        if (!plugin.getWorthFile().get().contains(item.name())) {
            return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        }
        List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
        boolean hasWorth = lore.stream()
                .anyMatch(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Worth:"));
        if (!hasWorth) {
            String symbol = plugin.getConfig().getString("settings.currency-symbol", "$");
            int worth = plugin.getWorthFile().get().getInt(item.name().toUpperCase());
            Component worthLine = mm.deserialize(plugin.getConfig().getString("settings.lore-message",
                                    "<white>Worth: <gold>{currency-symbol}{worth}<white>/pc")
                            .replace("{currency-symbol}", symbol)
                            .replace("{worth}", String.valueOf(worth)))
                    .decoration(TextDecoration.ITALIC, false);

            lore.add(0, worthLine); // add at top
            meta.lore(lore);
            bukkitItem.setItemMeta(meta);
        }

        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    private org.bukkit.inventory.ItemStack addLoreBukkit(org.bukkit.inventory.ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType() == Material.AIR)
            return bukkitItem;
        ItemMeta meta = bukkitItem.getItemMeta();
        if (meta == null)
            return bukkitItem;
        Material item = bukkitItem.getType();
        if (!plugin.getWorthFile().get().contains(item.name()))
            return bukkitItem;
        List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
        int worth = plugin.getWorthFile().get().getInt(item.name().toUpperCase());
        Component worthLine = mm.deserialize(plugin.getConfig().getString("settings.lore-message",
                                "<white>Worth: <gold>{currency-symbol}{worth}<white>/pc")
                        .replace("{currency-symbol}", symbol)
                        .replace("{worth}", String.valueOf(worth)))
                .decoration(TextDecoration.ITALIC, false);
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Worth:"));

        lore.add(0, worthLine); meta.lore(lore); bukkitItem.setItemMeta(meta); return bukkitItem;
    }

}