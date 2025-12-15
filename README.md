# GachaUdon

Paper/Spigot plugin that lets players pay via Vault to roll configurable gacha machines.

## Features
- Multiple machines loaded from `plugins/GachaUdon/Machine/*.yml`.
- Each machine has a price and prizes with an item id, amount, and decimal-percent chance (e.g., `12.5`).
- Prizes can define meta via YAML (displayName, lore, enchants, customModelData, flags) and are given with that ItemMeta.
- Vault economy support for charging per roll; zero-price machines work without Vault.
- Localization (English/Japanese) with external overrides under `plugins/GachaUdon/lang/`.
- Command aliases configurable in `config.yml`.
- Per-machine pity rules (guaranteed rarity after N pulls), e.g., “1 rare or better every 30 pulls” / “legendary guaranteed at 100 pulls.”

## Commands
- `/gacha list` - show available machines.
- `/gacha info <machine>` - show price and prize rates.
- `/gacha roll <machine> [count]` - pay the machine price and roll 1..N times.
- `/gacha reload` - reload config, messages, and machine definitions.

## Sign triggers
Right-click a sign with the following lines to run a gacha without typing a command:
1. `[gachaudon]`
2. Machine id
3. Roll count or `info` (blank = roll once)
4. Any memo text (ignored)

## Permissions
- `gachaudon.use` - use the `/gacha` commands (default: true).
- `gachaudon.admin` - reload/configure aliases (default: op).

## Configuration
`plugins/GachaUdon/config.yml`
- `commandAliases` - extra aliases for `/gacha`.
- `maxRollsPerCommand` - limit batch rolls (default 10).
- `machineFolder` - folder name for machine YAMLs (default `Machine`).
- `defaultLocale` - blank to follow server locale.
- `rarityOrder` - list rarities from lowest to highest; used by pity checks.
- `defaultRarity` - fallback rarity when a prize omits `rarity`.
- `discord.*` - enable EssentialsX Discord relay of roll results.

### EssentialsX Discord example
`plugins/GachaUdon/config.yml`:
```yaml
discord:
  sendRollResults: true
  messageType: "gachaudon"
  allowMentions: false
```
`plugins/EssentialsDiscord/config.yml` (or the equivalent for your setup):
```yaml
message-types:
  gachaudon: primary  # set the channel name/key you want to use
```

## Machine format
Place files under `plugins/GachaUdon/Machine/`:

```yaml
id: starter
displayName: "&bStarter Machine"
price: 250.0
items:
  - item: "minecraft:diamond"
    amount: 1
    chance: 0.5   # decimal percent
    displayName: "&bShiny Diamond"
    lore: ["&7A sparkling gem"]
  - item: "minecraft:enchanted_book"
    amount: 1
    chance: 3.0
    displayName: "&dSharpness Tome"
    lore:
      - "&7Contains Sharpness V"
    enchants:
      sharpness: 5
  - item: "minecraft:iron_ingot"
    amount: 8
    chance: 12.5
    rarity: common
  - item: "minecraft:gold_ingot"
    amount: 4
    chance: 4.5
    rarity: rare
pity:
  - pulls: 30
    minRarity: rare
    message: "&6Guaranteed rare+ every 30 pulls"
  - pulls: 100
    minRarity: legendary
    message: "&dLegendary guaranteed at 100 pulls"
```

If no machines are present, a sample `sample.yml` is created automatically.
