# ZEA — Zamin's EarthMC Addon

A client-side Fabric mod for [EarthMC](https://earthmc.net). ZEA is an
all-in-one EarthMC addon: a modern chat experience with EarthMC-aware message
classification, feeds and notifications, an item browser beside the inventory
that indexes every physical shop you observe, a native EarthMC map, player
information, persistent chat history, inventory buttons and configuration
sharing.

ZEA is exclusively client-side. It does not require (or support) installation
on the server, and it never sends anything on your behalf unless you explicitly
trigger an action.

## Status

Early development. See [ROADMAP.md](ROADMAP.md) for the staged plan and
[WORKLOG.md](WORKLOG.md) for actual progress.

Automated dev builds are published to
[releases](https://github.com/ZaminEarthMCAddon/ZEA/releases) on every push to
`develop`.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 26.3 |
| Loader | Fabric (loader 0.20.1+) |
| Java | 25+ |

## Installation

1. Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Drop the ZEA jar and the matching
   [Fabric API](https://modrinth.com/mod/fabric-api) build into your `mods`
   folder.
3. Launch the game and connect to `earthmc.net`. ZEA's features activate on
   EarthMC; on other servers the mod stays idle.

Dev builds are published on every push to `develop` — pick the newest release
tag from the
[releases page](https://github.com/ZaminEarthMCAddon/ZEA/releases). These are
development builds: expect rough edges, and report what you find.

## Features

- **Chat** — the vanilla chat rebuilt on EarthMC-aware classification: tabs
  per channel, private-message and mention notifications, persistent
  searchable history, trade-channel pacing, advertisement filters, and a
  live-request surface for trade threads.
- **Item browser** — the NEU-style pane beside any container GUI: every exact
  item variant observed in EarthMC shops, one tile per variant, each variant's
  real shops behind it. Search, pagination, ordering and direction filters
  follow NEU's item-pane model; selecting a tile keeps you inside the pane and
  shows that variant's BUY FROM and SELL TO offers with owners, prices, stock,
  town context and observation age.
- **Shop observation** — inspecting a shop sign records the shop and its
  response: the exact item identity, the transaction direction, prices, stock
  and the sign's coordinates. Observations persist locally, merge with the
  community dataset, and resolve to towns and nations through the map data.
- **Map** — a native EarthMC map: full-screen world map, minimap, town claim
  overlays and player markers, with shop navigation to observed stores.
- **Inventory utilities** — freely placed inventory buttons with an in-game
  editor, the search bar with an inline calculator, slot binding and slot
  locking.
- **Rarity** — item rarity for tools and armor from the item's real
  components and enchantments, with RPG-style tooltip statistics.
- **Sharing** — export configurations and button layouts as paste links;
  import them back with `/zea import`.

## Commands

| Command | What it does |
|---|---|
| `/zeamap` | Opens the full-screen world map (also bound to `M`). |
| `/zeahud` | Opens the HUD manager for the map surfaces. |
| `/zea capture on\|off` | Records EarthMC's raw server messages for a bug report. |
| `/zea capture status` | Shows whether a capture is running. |
| `/zea share` | Exports configurations or button layouts as paste links. |
| `/zea import <link>` | Imports a shared paste after confirming it. |

Additional key bindings live in Controls → ZEA: chat search, inventory-button
editing and slot locking. The built-in shortcuts (right-click to copy a chat
line, Ctrl+F for the chat search) work regardless.

## Configuration

ZEA's config screen opens through Mod Menu (or `/zea share status`'s
companion UI). Settings are grouped per subsystem — Chat, Inventory utilities,
Rarity, Map, Market — and every group documents what its options do. Nothing
toggles another player's experience: ZEA is a client mod.

## Data collection and privacy

ZEA records what your own client observes on EarthMC: shop responses, sign
contents, chat messages, map data from the public EarthMC API. Everything is
stored locally in your game folder. Nothing leaves your machine unless you
share a configuration paste yourself or submit an observation to
[ZEA-REPO](https://github.com/ZaminEarthMCAddon/ZEA-REPO) through the
validated submission queue. ZEA never sends chat, purchases or commands on
your behalf without an explicit action from you.

## Repository data

Community-maintained EarthMC data consumed by ZEA lives in
[ZEA-REPO](https://github.com/ZaminEarthMCAddon/ZEA-REPO): curated datasets,
their JSON schemas and the CI that validates every contribution.

## Building

```
./gradlew build
```

The build requires a Java 25 toolchain (Minecraft 26.3 runs on Java 25).

## Contributing

Bug reports and feature ideas go to
[issues](https://github.com/ZaminEarthMCAddon/ZEA/issues). Data contributions
(sign observations, town and nation descriptions) go to
[ZEA-REPO](https://github.com/ZaminEarthMCAddon/ZEA-REPO) — its
[contribution guide](https://github.com/ZaminEarthMCAddon/ZEA-REPO/blob/main/CONTRIBUTING.md)
covers the validation flow. Code changes follow
[ARCHITECTURE.md](ARCHITECTURE.md)'s conventions.

## License

ZEA is licensed under [LGPL-3.0-or-later](LICENSE).
