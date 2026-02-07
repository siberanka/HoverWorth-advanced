package com.jolly.hoverworth.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.jolly.hoverworth.HoverWorth;
import com.jolly.hoverworth.Scheduler;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WindowListener implements PacketListener, Listener {
    private final HoverWorth plugin;
    private final Scheduler scheduler;

    // Track which players currently have a "worth-allowed" TOP inventory
    private final Set<UUID> allowedTopInventories = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Track the size of the open top inventory to know where the player inventory
    // starts
    private final Map<UUID, Integer> openContainerSizes = new ConcurrentHashMap<>();
    // Coalesce refresh operations to avoid updateInventory spam
    private final Set<UUID> pendingInventorySync = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final MiniMessage mm = MiniMessage.miniMessage();

    public WindowListener(HoverWorth plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    // -------------------------------------------------------------------------
    // Packet Handling (Lore Injection)
    // -------------------------------------------------------------------------

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            // Process Window Items
            if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
                Player player = (Player) event.getPlayer();
                if (player == null)
                    return;

                int windowId = wrapper.getWindowId();
                List<ItemStack> items = wrapper.getItems();
                if (items == null)
                    return;

                UUID uuid = player.getUniqueId();
                int topSize = openContainerSizes.getOrDefault(uuid, 0);
                boolean topAllowed = windowId == 0 || allowedTopInventories.contains(uuid);

                for (int i = 0; i < items.size(); i++) {
                    ItemStack packetItem = items.get(i);
                    if (packetItem == null || packetItem.isEmpty())
                        continue;

                    // Logic:
                    // 1. If Window ID is 0 (Stand-alone Inventory), always show.
                    // 2. If slot index 'i' is >= topSize, it's the player's inventory part of the
                    // GUI -> Always show.
                    // 3. Otherwise, only show if the top inventory is whitelisted.
                    if (windowId == 0 || i >= topSize || topAllowed) {
                        items.set(i, createVisualItem(packetItem, player));
                    }
                }
                wrapper.setItems(items);
            }

            // Process Set Slot
            if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
                Player player = (Player) event.getPlayer();
                if (player == null)
                    return;

                int windowId = wrapper.getWindowId();
                int slot = wrapper.getSlot();
                ItemStack packetItem = wrapper.getItem();
                if (packetItem == null || packetItem.isEmpty())
                    return;

                UUID uuid = player.getUniqueId();
                int topSize = openContainerSizes.getOrDefault(uuid, 0);
                boolean topAllowed = windowId == 0 || allowedTopInventories.contains(uuid);

                // Slot -1 is the cursor, usually allowed.
                if (windowId == 0 || slot == -1 || slot >= topSize || topAllowed) {
                    wrapper.setItem(createVisualItem(packetItem, player));
                }
            }
        } catch (Exception e) {
            if (plugin.debug)
                plugin.getLogger().severe("Packet error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Bukkit Event Handling (GUI Detection & Refresh)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        org.bukkit.inventory.Inventory inv = event.getInventory();
        openContainerSizes.put(uuid, inv.getSize());

        if (!plugin.getConfig().getBoolean("settings.filter-guis", true)) {
            allowedTopInventories.add(uuid);
            return;
        }

        Component titleComp = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(titleComp);
        InventoryType type = inv.getType();

        boolean showInVanillaCheck = plugin.getConfig().getBoolean("settings.worth-vanilla-storages", true);
        boolean isVanilla = false;

        if (titleComp instanceof TranslatableComponent trans) {
            if (trans.key().startsWith("container.")) {
                isVanilla = isVanillaStorage(type);
            }
        } else if (plainTitle.equals("Chest") || plainTitle.equals("Large Chest") || plainTitle.equals("Ender Chest")) {
            isVanilla = isVanillaStorage(type);
        }

        List<String> whitelist = plugin.getConfig().getStringList("settings.worth-guis");
        boolean hasKeyword = false;
        for (String word : whitelist) {
            if (plainTitle.contains(word)) {
                hasKeyword = true;
                break;
            }
        }

        if ((showInVanillaCheck && isVanilla) || hasKeyword) {
            allowedTopInventories.add(uuid);
            if (plugin.debug)
                plugin.getLogger().info("DEBUG: Allowed top GUI worth: " + plainTitle);
        } else {
            allowedTopInventories.remove(uuid);
            if (plugin.debug)
                plugin.getLogger().info("DEBUG: Hidden top GUI worth: " + plainTitle);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        allowedTopInventories.remove(uuid);
        openContainerSizes.remove(uuid);
        pendingInventorySync.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        allowedTopInventories.remove(uuid);
        openContainerSizes.remove(uuid);
        pendingInventorySync.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            requestInventorySync(player);
        }
    }

    private void requestInventorySync(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingInventorySync.add(uuid)) {
            return;
        }

        // Small delay to batch rapid click/drag bursts into one sync
        scheduler.runLater(player, () -> {
            try {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            } finally {
                pendingInventorySync.remove(uuid);
            }
        }, 2L);
    }

    // -------------------------------------------------------------------------
    // Item Visual Logic
    // -------------------------------------------------------------------------

    private boolean isVanillaStorage(InventoryType type) {
        return type == InventoryType.CHEST ||
                type == InventoryType.ENDER_CHEST ||
                type == InventoryType.BARREL ||
                type == InventoryType.SHULKER_BOX ||
                type == InventoryType.HOPPER ||
                type == InventoryType.DISPENSER ||
                type == InventoryType.DROPPER;
    }

    private ItemStack createVisualItem(ItemStack packetItem, Player player) {
        try {
            org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(packetItem);
            if (bukkitItem == null || bukkitItem.getType() == Material.AIR)
                return packetItem;

            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                return packetItem;
            }

            org.bukkit.inventory.ItemStack clone = bukkitItem.clone();
            ItemMeta meta = clone.getItemMeta();
            if (meta == null)
                return packetItem;

            Material itemType = clone.getType();
            String itemKey = itemType.name().toUpperCase();
            Double unitWorth = null;
            String integration = plugin.getConfig().getString("settings.integration", "none");

            if ("EconomyShopGUI".equalsIgnoreCase(integration)
                    && plugin.getEconomyShopGUI() != null) {
                unitWorth = plugin.getEconomyShopGUI().getESGUIItemWorth(itemType, meta);
            } else if ("UltimateShop".equalsIgnoreCase(integration)
                    && plugin.getUltimateShopIntegration() != null) {
                unitWorth = plugin.getUltimateShopIntegration().getSellWorth(player, clone);
            }

            if (unitWorth == null) {
                if (plugin.getWorthFile().get().contains(itemKey)) {
                    unitWorth = plugin.getWorthFile().get().getDouble(itemKey + ".worth");
                } else if (plugin.getWorthFile().get().contains(itemKey.toLowerCase())) {
                    unitWorth = plugin.getWorthFile().get().getDouble(itemKey.toLowerCase() + ".worth");
                }
            }

            if (unitWorth == null)
                return packetItem;

            boolean calculateStackPrice = plugin.getConfig().getBoolean("settings.calculate-stack-price", false);
            String symbol = plugin.getConfig().getString("settings.currency-symbol", "💎");
            String stackLoreMessage = plugin.getConfig().getString("settings.stack-lore-message",
                    "<white>Değer: <gold>{worth}{currency-symbol}");
            String defaultLoreMessage = plugin.getConfig().getString("settings.lore-message",
                    "<white>Değer: <gold>{worth}{currency-symbol}");

            double displayWorth = unitWorth;
            int amount = clone.getAmount();
            boolean isStackTotal = false;

            if (calculateStackPrice && amount > 1) {
                displayWorth = unitWorth * amount;
                isStackTotal = true;
            }

            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            String template = isStackTotal ? stackLoreMessage : defaultLoreMessage;

            removeExistingWorthLines(lore, defaultLoreMessage, stackLoreMessage);

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat df = new DecimalFormat("#.##", symbols);
            String formattedWorth = df.format(displayWorth);

            Component worthLine = mm.deserialize(
                    template.replace("{currency-symbol}", symbol)
                            .replace("{worth}", formattedWorth))
                    .decoration(TextDecoration.ITALIC, false);

            lore.add(worthLine);
            meta.lore(lore);

            addDescriptionLine(meta, lore, itemKey);

            clone.setItemMeta(meta);
            return SpigotConversionUtil.fromBukkitItemStack(clone);
        } catch (Exception e) {
            return packetItem;
        }
    }

    private void removeExistingWorthLines(List<Component> lore, String... templates) {
        for (String template : templates) {
            if (template == null)
                continue;
            String cleanTemplate = mm.serialize(mm.deserialize(template)).replaceAll("<[^>]+>", "").split("\\{")[0]
                    .trim();
            if (cleanTemplate.isEmpty())
                continue;
            lore.removeIf(line -> {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                return plain.startsWith(cleanTemplate);
            });
        }
    }

    private void addDescriptionLine(ItemMeta meta, List<Component> lore, String itemKey) {
        String desc = plugin.getWorthFile().get().getString(itemKey + ".description", "");
        if (desc.isEmpty())
            return;

        String plainDesc = PlainTextComponentSerializer.plainText().serialize(mm.deserialize(desc)).trim();
        boolean hasDesc = lore.stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line).trim())
                .anyMatch(lineText -> lineText.equalsIgnoreCase(plainDesc));

        if (hasDesc)
            return;

        Component descLine = mm.deserialize(desc);
        if (!desc.contains("<italic>") && !desc.contains("<i>")) {
            descLine = descLine.decoration(TextDecoration.ITALIC, false);
        }

        lore.add(descLine);
        meta.lore(lore);
    }
}
