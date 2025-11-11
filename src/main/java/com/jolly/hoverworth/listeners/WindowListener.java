package com.jolly.hoverworth.listeners;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.jolly.hoverworth.HoverWorth;
import com.jolly.hoverworth.Scheduler;
import com.jolly.hoverworth.integrations.EconomyShopGUI;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WindowListener implements PacketListener, Listener {
    private final HoverWorth plugin;
    private final String symbol;
    private final Scheduler scheduler;
    private static final NamespacedKey WORTH_NBT_KEY = new NamespacedKey("hoverworth", "worth");
    private final ThreadLocal<Boolean> fixingInventory = ThreadLocal.withInitial(() -> false);

    MiniMessage mm = MiniMessage.miniMessage();
    public WindowListener(HoverWorth plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.symbol = plugin.getConfig().getString("settings.currency-symbol", "$");
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            int windowId = wrapper.getWindowId();

            if (!isVanillaInventory(windowId, event)) return;

            List<ItemStack> items = wrapper.getItems();
            if (items == null) return;

            for (int i = 0; i < items.size(); i++) {
                ItemStack packetItem = items.get(i);
                if (packetItem == null || packetItem.isEmpty()) continue;

                Material mat = SpigotConversionUtil.toBukkitItemMaterial(packetItem.getType());
                if (plugin.getEconomyShopGUI() == null) return;
                Map<Material, List<EconomyShopGUI.ESGUIItem>> esgui = plugin.getEconomyShopGUI().esguiWorths;
                if (esgui == null || !esgui.containsKey(mat)) continue;

                items.set(i, addLore(packetItem));
            }

            wrapper.setItems(items);
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            int windowId = wrapper.getWindowId();

            if (!isVanillaInventory(windowId, event)) return;

            ItemStack packetItem = wrapper.getItem();
            if (packetItem == null || packetItem.isEmpty()) return;
            if (plugin.getEconomyShopGUI() == null) return;

            Material mat = SpigotConversionUtil.toBukkitItemMaterial(packetItem.getType());
            Map<Material, List<EconomyShopGUI.ESGUIItem>> esgui = plugin.getEconomyShopGUI().esguiWorths;
            if (esgui == null || !esgui.containsKey(mat)) return;

            wrapper.setItem(addLore(packetItem));

            if (windowId == 0) {
                Player player = (Player) event.getPlayer();
                scheduler.runLater(() -> fixInventory(player), 2L);
                fixingInventory.set(false);
            }
        }
    }

    public void fixInventory(Player player) {
        if (fixingInventory.get()) return;

        fixingInventory.set(true);
        try {
            scheduler.runLater(() -> {
                org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                boolean changed = false;

                for (int i = 0; i < inv.getSize(); i++) {
                    org.bukkit.inventory.ItemStack item = inv.getItem(i);
                    if (item == null || item.getType() == Material.AIR) continue;

                    ItemMeta meta = item.getItemMeta();
                    if (meta == null) continue;

                    if (!meta.getPersistentDataContainer().has(WORTH_NBT_KEY, PersistentDataType.DOUBLE)) {
                        inv.setItem(i, addLoreBukkit(item));
                        changed = true;
                    }
                }

                if (changed) player.updateInventory();
            }, 10L);
        } catch (Exception e) {
            fixingInventory.set(false);
            throw e;
        }
    }


    private boolean isVanillaInventory(int windowId, PacketSendEvent event) {
        Player player = (Player) event.getPlayer();

        if (windowId == 0) return true;

        InventoryView view = player.getOpenInventory();
        if (view == null) return false;

        InventoryType type = view.getType();
        String title = ChatColor.stripColor(view.getTitle()).toLowerCase();

        if (isCustomTitle(title)) return false;

        return switch (type) {
            case CHEST, ENDER_CHEST, BARREL, HOPPER, FURNACE, BLAST_FURNACE, SMOKER,
                 ANVIL, BEACON, BREWING, DISPENSER, DROPPER, SHULKER_BOX -> true;
            default -> false;
        };
    }

    private boolean isCustomTitle(String title) {
        if (title == null || title.isEmpty()) return true;

        String[] vanillaTitles = {
                "chest", "large chest", "ender chest", "furnace", "blast furnace", "smoker",
                "anvil", "hopper", "beacon", "brewing stand", "dispenser", "dropper",
                "shulker box", "barrel", "crafting"
        };

        /*List<String> whitelist = plugin.getConfig().getStringList("settings.gui-whitelist");
        for (String guiName : whitelist) {
            String strippedGui = ChatColor.stripColor(guiName)
                    .trim()
                    .toLowerCase();
            titles.add(strippedGui);
        }*/

        for (String v : vanillaTitles) {
            if (title.equals(v)) return false;
        }

        return true;
    }



    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(PlayerAttemptPickupItemEvent event) {
        if (event.isCancelled()) return;
        org.bukkit.inventory.ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Double existing = meta.getPersistentDataContainer().get(WORTH_NBT_KEY, PersistentDataType.DOUBLE);
            if (existing != null) return;
        }

        org.bukkit.inventory.ItemStack modified = addLoreBukkit(item);
        event.getItem().setItemStack(modified);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        org.bukkit.inventory.ItemStack item = event.getEntity().getItemStack();
        org.bukkit.inventory.ItemStack modified = addLoreBukkit(item);
        event.getEntity().setItemStack(modified);
    }

    /*@EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        org.bukkit.inventory.ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        event.setCurrentItem(addLoreBukkit(item).asOne());
    }*/

    private ItemStack addLore(ItemStack packetItem) {
        if (packetItem == null || packetItem.isEmpty())
            return packetItem;

        org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(packetItem);
        org.bukkit.inventory.ItemStack updated = addLoreBukkit(bukkitItem);
        return SpigotConversionUtil.fromBukkitItemStack(updated);
    }

    private org.bukkit.inventory.ItemStack addLoreBukkit(org.bukkit.inventory.ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType() == Material.AIR) return bukkitItem;

        org.bukkit.inventory.ItemStack clone = bukkitItem.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) return clone;

        Material item = clone.getType();
        String itemKey = item.name().toUpperCase();
        Double worth = null;

        if ("EconomyShopGUI".equalsIgnoreCase(plugin.getConfig().getString("settings.integration", "none"))
                && plugin.getEconomyShopGUI() != null) {
            worth = plugin.getEconomyShopGUI().getESGUIItemWorth(item, meta);
        }
        if (worth == null && plugin.getWorthFile().get().contains(itemKey)) {
            worth = plugin.getWorthFile().get().getDouble(itemKey + ".worth");
        }

        if (worth == null) return clone;

        Double existing = meta.getPersistentDataContainer().get(WORTH_NBT_KEY, PersistentDataType.DOUBLE);
        if (existing != null && Math.abs(existing - worth) < 0.0001) {
            return clone;
        }

        meta.getPersistentDataContainer().set(WORTH_NBT_KEY, PersistentDataType.DOUBLE, worth);

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore = updateWorthLine(lore, worth, symbol);
        meta.lore(lore);
        addDescriptionLine(meta, lore, itemKey);
        clone.setItemMeta(meta);

        return clone;
    }



    private List<Component> updateWorthLine(List<Component> lore, double worth, String symbol) {
        String template = plugin.getConfig().getString(
                "settings.lore-message",
                "<white>Worth: <gold>{currency-symbol}{worth}<white>/pc"
        );

        Component worthLine = mm.deserialize(
                template.replace("{currency-symbol}", symbol)
                        .replace("{worth}", String.valueOf(worth))
        ).decoration(TextDecoration.ITALIC, false);

        String[] segments = template.split("\\{[^}]+}");

        List<String> literals = Arrays.stream(segments)
                .map(s -> s.replaceAll("<[^>]+>", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();

        lore.removeIf(line -> {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            return literals.stream().anyMatch(plain::contains);
        });

        lore.addFirst(worthLine);
        return lore;
    }

    private void addDescriptionLine(ItemMeta meta, List<Component> lore, String itemKey) {
        String desc = plugin.getWorthFile().get().getString(itemKey + ".description", "");
        if (desc.isEmpty()) return;

        String plainDesc = PlainTextComponentSerializer.plainText()
                .serialize(mm.deserialize(desc)).trim();

        boolean hasDesc = lore.stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line).trim())
                .anyMatch(lineText -> lineText.equalsIgnoreCase(plainDesc));

        if (hasDesc) return;

        Component descLine = mm.deserialize(desc);
        if (!desc.contains("<italic>") && !desc.contains("<i>")) {
            descLine = descLine.decoration(TextDecoration.ITALIC, false);
        }

        lore.addLast(descLine);
        meta.lore(lore);
    }
}