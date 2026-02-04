# HoverWorth 💎

**HoverWorth** is a high-performance, visual-only item worth display plugin designed for modern Minecraft servers. It provides players with instant feedback on item values through hover lore, without ever modifying the actual item data.

Built with **Folia** and **Paper** compatibility in mind, it uses high-efficiency packet interception to ensure zero impact on your server's performance and data integrity.

## ✨ Features

*   **⚡ Visual-Only Lore**: Lore is injected directly into network packets. No permanent changes are made to items, making it 100% dupe-proof and safe for economy balance.
*   **☁️ Folia & Paper Support**: Fully compatible with Folia's regional threading model.
*   **🛒 Shop Integration**: Seamlessly integrates with **EconomyShopGUI** to fetch real-time sell prices.
*   **📦 Stack Price Calculation**: Optionally displays the total value of an item stack (e.g., "Worth: $64" for a stack of 64).
*   **🛡️ Smart GUI Filtering**:
    *   **Split-View**: Automatically hides worth lore in custom plugin menus (to keep them clean) while keeping lore visible in the player's own inventory.
    *   **Vanilla Whitelist**: Automatically shows lore in standard chests, barrels, and shulkers.
    *   **Keyword Whitelist**: Enable lore in specific custom menus using title keywords (e.g., "Storage").
*   **📝 Custom Descriptions**: Add flavor text or additional info to items via `worth.yml`.
*   **🔄 Dynamic Reload**: Update prices and settings on the fly with `/hwreload`.

## 🛠️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/hwreload` | Reloads the configuration and worth files | `hoverworth.reload` |

## ⚙️ Configuration

```yaml
debug: false

settings:
  currency-symbol: "$"
  # Message for single items
  lore-message: "<white>Worth: <gold>{worth}{currency-symbol}"
  
  # Available integrations: EconomyShopGUI, none
  integration: "none"
  
  # Calculate total price for stacks (e.g. 64x items)
  calculate-stack-price: true
  # Message format for stack prices
  stack-lore-message: "<white>Worth: <gold>{worth}{currency-symbol}"
  
  # --- GUI Filtering ---
  # Hide worth lore in custom plugin GUIs
  filter-guis: true
  # Show worth in standard Minecraft containers (Chest, Barrel, etc.)
  worth-vanilla-storages: true
  # Whitelist specific GUI titles (Case-sensitive)
  worth-guis:
    - "Ender Chest"
    - "Storage"
```

## 📖 Installation

1.  Download the latest `HoverWorth.jar`.
2.  Place it in your server's `plugins` folder.
3.  (Optional) Install **EconomyShopGUI** for automatic price fetching.
4.  Restart your server.
5.  Configure your prices in `worth.yml` or via your shop plugin.

## 🏗️ For Developers

HoverWorth uses **PacketEvents** to handle item modification at the protocol level. This ensures that the server-side `ItemStack` remains "clean". When an item is moved, shift-clicked, or dragged, the plugin forces a visual refresh to keep the lore consistent without causing desync.

---
*Developed with ❤️ for high-performance Minecraft servers.*
