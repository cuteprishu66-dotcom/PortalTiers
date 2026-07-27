<img width="1600" height="720" alt="Screenshot_20260627_112158" src="https://github.com/user-attachments/assets/4712b90b-f37f-4b2a-8e8b-9cd0d7cad87e" />
# Pojav Tier Tagger

A client-side Fabric mod for **Minecraft 1.21.1** that displays players' **Pojav**
PvP tiers (`HT1`–`LT5`) with gamemode emoji icons — above their heads, in the
tab list, and in chat. It pulls live data from the Pojav rankings API.

It is a feature replica of the two most popular tier-tagger mods (uku3lig
**TierTagger** and PvPTiers **Tiers**), rebuilt as a single self-contained mod
wired to the Pojav API.

## Features

- ⭐ **Nametag tags** — coloured `[icon][tier]` badge above every player's head.
- 📋 **Tab list tags** — tier badge prefixed in the player list.
- 💬 **Chat tags** — tier badge injected before player names in chat.
- 🎮 **8 gamemodes** with emoji icons copied from the reference mods:
  Sword, Mace, SMP, Pot, Vanilla, NethOP, UHC, Axe.
- 🎨 **Per-tier colours** (HT1 gold → LT5 dark gray), fully configurable.
- 🔁 **Cycle gamemode** keybind (unbound by default) + overlay feedback.
- 🏆 **Highest-tier mode** — show the player's best tier when they have none in
  the selected gamemode (`Never` / `If none` / `Always`).
- ⏱️ **Auto refresh** of the rankings cache (configurable interval).
- 🛠️ **Config screen** (vanilla widgets, opens via Mod Menu or `/pojavtier config`).

## Commands

| Command | Action |
|---|---|
| `/pojavtier` | Toggle the tags on/off |
| `/pojavtier refresh` | Force re-fetch the rankings |
| `/pojavtier gamemode <mode>` | Set the displayed gamemode |
| `/pojavtier player <name>` | Print a player's full tier list |
| `/pojavtier config` | Open the config screen |

## API

Data source: `Still working on`

The endpoint returns a JSON array of players. Lookup is by `minecraftUsername`:

```json
{
  "discordId": "1155804161418461205",
  "minecraftUsername": "sanatanisam",
  "region": "AS",
  "ranks": { "Sword": "HT3", "NethOP": "HT3", "Mace": "HT3" },
  "overallPoints": 44,
  "overallTier": "Novice"
}


# Project layout
```
src/main/java/com/pojavtiers/tagger/
  PojavTierTaggerClient.java   # entrypoint: keybind, commands, refresh ticks
  PojavTierManager.java        # API fetch, cache, badge/text building
  model/GameMode.java          # the 8 gamemodes + icon chars/colours
  model/PlayerRanking.java      # API JSON model
  config/PojavConfig.java      # JSON config + tier colours
  gui/ConfigScreen.java         # vanilla-widget config screen
  gui/ModMenuIntegration.java   # Mod Menu hook
  mixin/PlayerEntityMixin.java  # nametag above head
  mixin/PlayerListHudMixin.java # tab list
  mixin/ChatHudMixin.java       # chat
src/main/resources/assets/pojavtiertagger/
  font/icons.json               # bitmap font: \uE000-\uE007 -> gamemode PNGs
  textures/gamemodes/*.png      # the 8 gamemode emojis
```

## Credits
Gamemode *emoji* textures and *rendering* approach adapted from
uku3lig/TierTagger (MPL-2.0) and Pojav
