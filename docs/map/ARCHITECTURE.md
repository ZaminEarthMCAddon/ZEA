# Map Subsystem — Architecture

Design for the native EarthMC world map + minimap in ZEA (MC 26.3, Fabric,
Mojang mappings, client-only). Derived from
[RESEARCH.md](RESEARCH.md); every borrowed decision cites the commit or
source that motivates it.

## 1. Principles

1. **World identity is a dimension of every key.** `MapWorld` (earth |
   moon) participates in every cache key, tile key, geometry key, marker
   collection, camera state and GPU resource name (`1fc868b6`, `22a56c4`,
   `eb0c3ba`). A town is identified by `(world, townKey)` — names collide
   across worlds by construction.
2. **One projection, one frame naming.** All EarthMC data is in
   world-block coordinates of its own world; the player's position is in
   the dimension they stand in. A single `MapProjection` converts; callers
   name their frame (`playerWorld` vs `viewedWorld`) (`8ce45767`).
3. **Two data levels, one renderer stack.** Quick Map (zoom-0 pyramid
   layer + vector overlays) under Detailed tiles, swapped only on full
   coverage, sticky (`8ce45767`).
4. **Static imagery and live data are separate layers.** Terrain tiles
   (squaremap now, ZEA-REPO community tiles as they land) never carry
   claims or players; claims/markers refresh independently (Part 5.5).
5. **Network is scheduled, never frame-driven.** Refresh timers +
   server-published `max-age` floors + ETag/If-Modified-Since + gzip +
   idle backoff + bounded per-world concurrency (`295ed059`, `d2b6a024`,
   `bc5881c`).
6. **No Nether/cave anything.** Unsupported dimensions get a disabled
   minimap state; no coordinate scaling features (EarthMC policy).
7. **Failure is visible.** Data freshness distinguishes last-success,
   last-attempt, failing, imagery version; 404 is normal, refusals
   surface with a reason (`19bd14a4`, freshness machinery).

## 2. Package layout (`net.zaminmc.zea.map`)

Part 8's ten components, as packages:

| Package | Responsibility |
|---|---|
| `map` | Subsystem facade + wiring only (`MapSubsystem`) |
| `map.model` | Immutable data: `TownClaim`, `ClaimGeometry`, `PlayerMarker`, `MapWorld`, `TileId`, `MapCamera`, `Freshness` |
| `map.source.squaremap` | Markers/player-feed/tile HTTP clients (ETag, gzip, max-age floors) |
| `map.source.earthmcapi` | v4 API client for per-town claim pulls (reuse patterns from `docs/EARTHMC_API.md`; separate from squaremap) |
| `map.source.repo` | ZEA-REPO manifest + tile download client |
| `map.cache` | `TileCache` (bounded LRU, per-world budgets, states, backoff), `ClaimStore` (per-world claim sets + geometry overrides), `PlayerStore` (per-world markers, memoised) |
| `map.geometry` | `ChunkRings` (own implementation), even-odd ring model, geometry signatures |
| `map.projection` | `MapProjection`, `MapCamera` math, screen<->world transforms |
| `map.render.tile` | Tile texture pipeline: upload queue (budgeted), `TileRenderer` (pyramid draw, parent fallback, coverage tracking) |
| `map.render.overlay` | Claims fill/outline (merged runs), player markers + interpolation, labels, culling |
| `map.render.worldmap` | `WorldMapScreen` (full-screen, input isolation) |
| `map.render.minimap` | `MinimapHud` (square/circle, rotation), `MapEditorScreen` |
| `map.config` | `MapConfig` MoulConfig category (nested groups) |
| `map.command` | `/zeamap` client command |
| `map.lifecycle` | Tick hooks, world-change detection (main thread), activity tracking for idle backoff |

Mixins (if any) live in `net.zaminmc.zea.mixin` per house rules; the
goal is zero mixins — Fabric's `HudRenderCallback` (or 26.3 equivalent
`RenderedHudLayer` registration) draws the minimap, and the world map is
our own `Screen`, so no third-party surface is touched.

## 3. World identity

```java
enum MapWorld {
    EARTH("minecraft_overworld", "Terra Nostra"),
    MOON("earthmc_moon", "Moon");
    final String squaremapKey;   // verified live
    static MapWorld ofDimension(ResourceKey<Level> dim);  // earthmc:* -> MOON, minecraft:* -> EARTH
    static MapWorld fromSquaremapKey(String key);          // settings.json validated
}
```

- Resolution happens **once per client tick on the render thread**
  (`playerWorld`); async code only ever reads the published value
  (`1fc868b6`: "clearing the tile cache from a fetch thread could close a
  NativeImage another thread is still decoding").
- `viewedWorld` (world map) = auto (follows `playerWorld`) or pinned;
  `WorldMapScreen` exposes the toggle; the minimap always draws
  `playerWorld`.
- World switch: bump a generation counter; in-flight tile responses
  stamped with the old generation are dropped; claims are *filed per
  world* so a late markers response is kept for its own world; player
  markers are cleared and repopulated by the next feed (the new feed's
  `world` field makes this exact); per-world camera state untouched.
- Two worlds are held simultaneously (map pinned ≠ player world):
  `ClaimStore` keeps both claim sets; `PlayerStore` memoises per-world
  lists against the feed identity (`7ea2bb8d`).

## 4. Coordinate system

- All map data: world blocks (integers for claims, doubles for players).
- `MapCamera { double centerX, centerZ; double pixelsPerBlock; }` —
  `pixelsPerBlock` is the single zoom scalar; zoom steps are powers of
  two so tile zoom = `floor(log2(ppb))` with bias +2 (sharper than
  needed, `QUALITY_ZOOM_BIAS`).
- `MapProjection`: `screenX = width/2 + (worldX − cam.centerX) * ppb`
  (and inverse for mouse picking). Rotation applied as a 2×2 on the
  delta for the rotating minimap; icons/labels counter-rotate
  (`orientIcons`, NEU `dmOrientCheck`).
- No dimension scaling anywhere: EarthMC's map worlds are 1:1. The
  Nether's 8× is irrelevant because we never draw there.
- Earth bounds: ±64,512 (world width 129,024) used only for clamping
  the camera and validating tile indices; **no antimeridian wrapping**
  (attempted and reverted upstream, `cb592917`); Moon bounds 35k × 17.5k
  from docs.

## 5. Tile pipeline (Quick Map / Detailed Map)

**Identity**: `TileId(world, source, zoom, x, z, dataVersion)` where
source ∈ {squaremap, repo} and dataVersion = the imagery generation
(repo manifest version / squaremap = per-tile ETag). No bare-coordinate
keys (Part 4.4).

**Rendering levels**:
- Quick Map = pyramid zoom 0 (whole world in ≤ a handful of 512px tiles)
  plus vector overlays. Always drawable; pinned in cache (`OVERVIEW_PIN`).
- Detailed Map = chosen zoom via `chooseTileZoom(ppb)`; request budget
  28/frame idle, 2 while panning; upload budget 5/frame & 1.5 ms (1 &
  750 µs moving); per-world concurrent load caps.
- Draw order per frame: zoom-0 layer → current-zoom tiles (parent-region
  fallback for missing ones) → overlays. Layer swap to the fine zoom only
  at **full visible coverage**, sticky once established (`8ce45767`).
- Prefetch: ±1 adjacent zooms when idle; pan-direction lead 0.75
  viewports when moving; margin 1 tile.

**Cache** (`TileCache`):
- Bounded LRU (1024 GPU textures), access-order; states: ABSENT,
  LOADING (dedup set), QUEUED (bytes+NativeImage pending upload),
  READY (GPU), FAILED (retry backoff 60 s; 429/5xx exponential
  0.4→3.2 s ×5 as in the API client discipline).
- Pinned: zoom ≤ 2. Reserved: 128 entries for `playerWorld` (minimap).
- Ownership: render thread claims via `queue.remove(id, entry)` then
  uploads; `close()` only after removal (**remove-then-close**,
  `aa92179f`). Generation stamps drop stale responses on world switch.
- ETags per tile (`If-None-Match`); 304 resets the 20-min age clock;
  evicting a texture evicts its ETag (`d2b6a024`).
- HTTP: gzip for JSON always; PNG tiles already-compressed. 404 normal;
  refusals surfaced once per status + last-refused reason.

**ZEA-REPO tiles** (community layer):
- `RepoMapClient` reads `manifest.json` (raw URL, HTTP-cached), diffs
  sha256 against the local cache dir
  (`…/zea/map-tiles/<world>/<zoom>/<x>_<z>.png`), downloads deltas
  asynchronously with bounded concurrency, verifies hash post-download,
  supports commit-pinned zip for first sync, ETag conditional on the
  manifest, full offline mode.
- Source priority: repo tile if present and fresher, else squaremap
  tile. Both may serve the same `TileId` slot at different
  `dataVersion`s; the renderer treats repo tiles as another LOD source
  with the same coverage/stickiness rules.
- Contribution tooling (explicit, user-initiated) is a separate later
  step; format and validation in §7.

## 6. Claims pipeline

- Steady state: `markers.json` per world, 60 s timer, floored by
  `max-age=120`, ETag (same-encoding!) + gzip, last-success tracked
  separately from last-attempt.
- Parse → `TownClaim(world, key, name, outlineRgb, fillRgb, rings, bbox)`
  list per world; unchanged towns reuse instances (identity-equality
  fast path); geometry signature (hash of rings) drives render-cache
  invalidation (`a5570c6f`).
- Live updates (own commands only, chat-gated like the addon):
  `/t claim|unclaim` send/success/failure detection → `PendingClaim`
  (12 s TTL) → optimistic chunk (20 s TTL) → v4 API pull at 700 +
  3000 ms (`coordinates.townBlocks`, **Earth-only** — a Moon claim is
  never written from API data, `eb0c3ba`) → `ChunkRings.trace()` →
  world-scoped geometry override replacing the town's rings, with
  180 s expiry and drop-when-satisfied (published bbox ⊇ override bbox
  ∧ equal chunk count).
- Unclaim: caught at send; a refreshed geometry can remove chunks.
- Full rebuild (drop outline caches + tile cache) = manual recovery +
  auto-promotion every 10 min (`b00d56e1`).

## 7. ZEA-REPO distribution format

```
datasets/map/
  tiles.json            # format "zea-repo/map-tiles", version 1
                        # worlds: [earth, moon]; per-world block bounds;
                        # zoom range; tile px (512); sha256 whitelist of
                        # "empty" tiles allowed to repeat
  earth/lod0/zoom0/0_0.png      # LOD = repo vs squaremap provenance tier
  earth/lod1/zoom5/123_-45.png
  moon/lod0/zoom0/0_0.png
```

- Manifest: existing `path → sha256` model; schema change relaxes
  `propertyNames` to `^datasets/.+\.(json|png)$` (version bump to
  manifest v2).
- `validate.py` additions: discover `*.png`; tile path pattern
  `datasets/map/<world>/lod<digit>/zoom<digit>/<int>_<int>.png`; world ∈
  enum; zoom/x/z within bounds from `tiles.json`; PNG signature; ≤256 KiB
  per tile; duplicate-hash rejection unless whitelisted; no executable
  or non-PNG payloads. CI: regenerate manifest **and validate against
  the fresh one on PRs** (fixes the current stale-hash gate), serialize
  bot pushes with a concurrency group.
- Client contract: manifest → hash diff → delta download → verify →
  cache; interrupted downloads retried with backoff; never per-frame;
  fully usable offline from cache.

## 8. Player markers

- Source: `/map/v1/players` (feed's own `world` field respected;
  markers only ever drawn on their world). Poll ~1 s while the world
  map shows them, ~3 s minimap-only, idle-scaled ×5/×15 after 2/10 min.
- `Authorization: Bearer <key>` when the user configures one; on
  401/403: hide dots, keep claims, freshness line says why; the key is
  only ever sent to `api.earthmc.net` (fixed URL constant — `PLAYERS_URL`
  discipline).
- Rendering: self marker (accent color) + others; optional heads (skin
  atlas, cached, cap concurrent fetches); interpolation between feed
  ticks with NEU's guards (only if jump < half a screen-span; wrap-aware
  angle lerp); labels zoom-gated with screen-space decluttering and a
  cap; per-world memoised lists rebuilt only when the feed identity
  changes.

## 9. World map screen (`WorldMapScreen`)

Own `Screen` (no Xaero). `/zeamap` + keybind (default unbound-ish per
house style: `zea.key.open_map`, default `M`?) — decide: default `M`
collides with common socials; use `GLFW_KEY_M` but document. Actually:
default unbound, consistent with ZEA's other binds.

- Camera per world, persisted per session (`MapCamera` keyed by world);
  initialized from saved camera or player position; **never reset per
  frame**; minimap state never touches it.
- Input: wheel zoom (toward cursor), drag pan, follow-toggle, recenter,
  world switch button, coordinates readout, scale indicator, freshness
  line (clickable = refresh), settings button. Screen consumes all input
  (standard `Screen` behavior) — gameplay keybinds cannot leak.
- Overlays: claim fills (even-odd, alpha config), outlines (merged runs,
  screen-px width), town labels (zoom-gated, mode 0-3 analog), player
  markers, self marker, optional repo/squaremap imagery layers.

## 10. Minimap HUD

- Rendered from the same `ClaimStore`/`PlayerStore`/`TileCache` via the
  same `MapProjection`; its own camera (player-centered or offset).
- Shape: square or circle — circle via render-to-texture at N× +
  blit with a radius mask (NEU's approach; one constant, no per-tile
  strip clipping) with a scissor-rect fallback.
- Rotation: north-up default; optional rotate-with-player; icons and
  labels counter-rotated.
- Position: anchored `Position` (9-anchor model, clamped to screen,
  resolution/GUI-scale safe); edited in `MapEditorScreen` (drag, arrows
  ±1/±10, R reset, live preview with a bundled demo snapshot).
- Disabled state (Nether/unsupported): dimmed panel with a short label,
  never fake imagery. Hidden entirely if configured.

## 11. Configuration (`MapConfig` MoulConfig category)

One top-level `Map` category, nested subcategories (MoulConfig renders
`@Category` fields one level deep — house-verified):

- **General**: enable, enableWorldMap, enableMinimap, earthMcOnly
  (gated on `EarthMcDetector`, the existing reliable check), default
  world (auto/earth/moon), apiKey (text, optional), showFreshness,
  hideInUnsupportedDimensions.
- **Minimap**: shape, size, zoom, position (opens editor), opacity,
  background/border color, rotateWithPlayer, centerPlayer,
  interpolatePlayers, playerIconStyle, playerIconScale, showClaims,
  showTownNames, showPlayers, showPlayerNames, showNationColors,
  showCoordinates, showScale.
- **World map**: defaultZoom, followMode, rotationMode, showPlayers,
  showPlayerLabels, showTownFills, showTownOutlines, showNationColors,
  claimOpacity, outlineThickness, playerIconScale, townLabelScale,
  imagery source toggles.
- **Shared** (used by both unless overridden): marker visibility/style,
  name visibility, claim fill alpha, outline thickness, label/icon
  scale, imagery layer enable + darken.
- **Editor**: button → `MapEditorScreen` (drag/scale/preview/reset).
- **Data**: refresh intervals (advanced), apiOptOut note, repo layer
  enable, clear-caches button (recovery).

All persisted through `ZeaConfigStore` safe-write; range-checked
accessors; user data (positions, favorites-if-any) preserved on reset.

## 12. Threading model

- Render thread: camera math, draw calls, texture uploads (budgeted),
  world resolution, input.
- `MapScheduler` (single daemon thread): refresh timers, backoff,
  delayed claim re-asks.
- Virtual-thread-per-task executors for HTTP (two clients:
  squaremap-host, api-host — keys never cross).
- Worker (single thread) for chunk-mask/geometry builds → immutable
  snapshots published via volatile swap (`a4d0616`).
- Ownership rules: bytes → NativeImage produced on the fetch thread;
  NativeImage → GPU texture only on the render thread via the claimed
  queue entry; images closed only by their current owner after
  removal. Eviction/world-switch/resource-reload follow the generation
  discipline.

## 13. Testing

- **Unit** (JUnit, no client): `ChunkRings` (holes, diagonals,
  disconnected, enclosed wilderness, empty, single chunk, ring order,
  collinear simplification); projection round-trips; tile index math
  (negative coords, zoom bounds); LRU behavior (pin, reserve, ETag
  eviction); claim-state machines (pending → confirm → override →
  satisfy/expire); freshness accounting; config clamps/migrations;
  repo manifest diffing (hash mismatch, partial download, offline).
- **Processor tests** (existing `config` test driver pattern):
  MoulConfig category loads/saves.
- **Manual visual checklist** (documented, requires the server): the
  Part 11 lists — LOD transitions under rapid zoom, Earth↔Moon switch
  under load, circular clip at GUI scales 1-4, claim/unclaim live
  updates, 429 behavior, offline mode, freshness line under outage.
  Verified with real screenshots before calling any visual behavior
  done (house rule: code inspection is not visual verification).

## 14. Explicitly deferred

Player/town/nation profile viewers, search, history/archives, market,
shops, waypoints, planning tools, cave/Nether/dungeon mapping, Xaero or
Xiro interop of any kind.
