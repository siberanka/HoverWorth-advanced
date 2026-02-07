# HoverWorth v2.0.3

HoverWorth is a high-performance, visual-only item worth display plugin for modern Minecraft servers.
It shows item values in hover lore without modifying real item data.

Built for Paper/Folia with PacketEvents-based packet injection.

## Features

- Visual-only lore injection (no permanent ItemStack changes)
- Paper and Folia support
- Shop integrations:
  - EconomyShopGUI: reads configured sell prices
  - UltimateShop: reads sell prices from UltimateShop API
    - if dynamic price sell is enabled, dynamic sell value is used
    - if dynamic price sell is disabled, normal sell value is used
- Stack total calculation support
- GUI filtering (vanilla storage + whitelist-based custom GUIs)
- Item descriptions from `worth.yml`
- Live reload with `/hwreload`

## Commands and Permissions

| Command | Description | Permission |
| --- | --- | --- |
| `/hwreload` | Reloads config and worth data | `hoverworth.reload` |

## Configuration

```yaml
debug: false

settings:
  currency-symbol: "$"
  lore-message: "<white>Worth: <gold>{worth}{currency-symbol}"

  # Available options: EconomyShopGUI, UltimateShop, none
  integration: "ultimateshop"

  calculate-stack-price: true
  stack-lore-message: "<white>Worth: <gold>{worth}{currency-symbol}"

  filter-guis: true
  worth-vanilla-storages: true
  worth-guis:
    - "Ender Chest"
```

## Installation

1. Download the latest `HoverWorth.jar`.
2. Place it in your server `plugins` folder.
3. Install optional dependencies as needed:
   - `EconomyShopGUI` for EconomyShopGUI integration
   - `UltimateShop` for UltimateShop integration
4. Start or restart the server.
5. Configure `settings.integration` and your price source.

## Notes for Developers

HoverWorth uses PacketEvents to modify display data at protocol level only.
Server-side items stay unchanged; lore is re-injected on inventory updates to prevent desync.
