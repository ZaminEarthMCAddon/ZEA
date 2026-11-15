# Map Subsystem — Phase 0 Research Report

Status: **complete**. Research preceded any implementation, as required.
Scope: world map, minimap, world imagery, claims, players, map customization
only (profiles/search/history/shops/waypoints are out of scope and were not
researched beyond what borders on map rendering).

Everything below is verified against the actual sources listed in §7-§9.
Live endpoints were probed from this machine on 2026-11-15; repository state
was read from local clones (`research/emc-map-addon` @ branch `1.21.11`,
`research/neu` @ master, `zea-repo` @ main).

---

## 1. EarthMC Map Addon — important source files and responsibilities

Branch `1.21.11`, package `net.townymap`, 58 Java files. Map-relevant ones:

| File | Responsibility |
|---|---|
| `TownyMapMod.java` | 4,400-line orchestrator: world-key resolution, server gate, claim-command pipeline, caches for town/player details, data-freshness status, camera state |
| `TownyMapConfig.java` | Gson config (`townymapaddon.json`): toggles, alphas, refresh intervals, endpoint base URLs, `sanitize()` migration/clamping, `PRESERVED_ON_RESET` |
| `api/SquaremapApiClient.java` | Claims + players: `markers.json` per world, player feed, ETag/If-Modified-Since, gzip, Cache-Control floors, idle backoff, geometry overrides, per-world memoised player filter |
| `api/EarthMcApiClient.java` | EarthMC API v4 client: per-town claims (`coordinates.townBlocks`), bulk town/nation/player details, key-auth policy |
| `render/SquaremapTileRenderer.java` | Imagery: tile pyramid fetch/render, LRU texture cache, upload queue with budgets, parent-tile fallback, prefetch, circle clip |
| `render/WorldMapRenderer.java` | World-map overlay inside Xaero's map screen: town fills/outlines (direct + baked tile paths), player dots/heads, nation stars, culling |
| `render/TownyMinimapOverlay.java` | Minimap overlay: world-space matrix transform, cached chunk mask (`MaskSnapshot`), merged edge runs, player dots, town names, squaremap background |
| `render/PlayerHeadRenderer.java`, `PlayerHeadImages.java` | Player head textures from skins |
| `util/ChunkRings.java` | Chunk set → polygon rings (outer-first, holes after) |
| `model/TownData.java` | Town record: name, outline/fill RGB, rings, bbox, `withRings`/`withColors` |
| `model/PlayerMarker.java` | Player record: name, uuid, x, z, yaw, world + `inWorld()` |
| `model/OptimisticClaimChunk.java` | Single optimistic claim: chunk, town, colors, TTL, world |
| `mixin/MixinGuiMap.java` | Injects into Xaero's world-map screen (camera, callbacks, input) |
| `mixin/MixinMinimapModuleRenderer.java`, `MixinCommonMinimapRenderer.java`, `MixinWaypointMapRenderer.java`, `MixinInfoDisplayRenderer.java` | Inject into Xaero's minimap render pipeline |
| `input/TownyMapKeybinds.java` | Keybinds (screenshot P, refresh R, stats J) + tick hooks |
| `gui/*` | Settings screen, hover/search/info overlays (search/info out of scope) |

Xaero's mods are **closed source**; the addon reaches them only through the
mixin surface above. There is no Xaero commit history to read — their API
surface is exactly what these six mixins touch.

## 2. Map imagery source and data format

Verified live:

- Base: `https://map.earthmc.net` (squaremap; Cloudflare-fronted).
- World list: `GET /tiles/settings.json` →
  `{"worlds":[{"name":"earthmc_moon","display_name":"Moon","type":"custom","order":1},
  {"name":"minecraft_overworld","display_name":"Terra Nostra","type":"normal","order":0}], "ui":{...}}`.
- Imagery: `GET /tiles/<world>/<zoom>/<x>_<y>.png` — 512×512 PNG tiles,
  standard squaremap pyramid. `pixelsPerBlock(zoom) = 2^(zoom − maxZoom)`,
  `maxZoom = 5` (clamped 0-8 in config) → zoom 0 = 1/32 px/block overview,
  zoom 5 = 1:1. Verified: `0_0.png` = 149 KB, `Cache-Control: public,
  max-age=7200, stale-while-revalidate=600`, strong ETag, `accept-ranges`.
- Claims: `GET /tiles/<world>/markers.json` — squaremap marker document,
  ~10.7 MB raw / ~1.4 MB gzipped; verified headers: `Cache-Control: public,
  max-age=120`, `ETag: W/"…-gzip"` (**the ETag is per content-encoding** —
  a conditional request must reuse the same `Accept-Encoding` or it misses
  silently), `Vary: Accept-Encoding`, `Last-Modified` present.
- Players: `GET https://api.earthmc.net/map/v1/players` (moved off squaremap
  2026-11-03; API-key required from 2026-11-19 — verified working keyless
  2026-11-15). Body: `{"max":800,"current":327,"players":[{"world":
  "minecraft_overworld","name":…,"x":…,"y":64,"z":…,"uuid":…,"yaw":…,
  "premium":true|absent}, …]}`. Headers: `Cache-Control: public, max-age=1,
  must-revalidate` (polling at ~1s matches the feed's own cadence; no ETag,
  If-Modified-Since only). ~2.9 KB gzipped. **The `world` field is new with
  this endpoint** — cross-world player isolation is now data-supported.

## 3. Town polygon representation

Two representations, reconciled by the override system:

1. **Published**: parsed from `markers.json` — squaremap gives each town a
   polygon (the addon parses marker popups with regexes for
   residents/mayor/nation). `TownData(name, rgbColor, fillRgbColor,
   polygonRings, minX, maxX, minZ, maxZ)`; rings are `int[][]` of
   `[worldX, worldZ]` block coords, GeoJSON convention (ring 0 = outer
   boundary, rest = holes). Outline and fill colors differ for ~62% of towns
   (nation `dynmapColour` / `dynmapOutline` from the API when refreshed).
2. **API-derived**: `EarthMcApiClient.fetchTownClaims()` →
   `coordinates.townBlocks: [[chunkX, chunkZ], …]` → `ChunkRings.trace()`:

   - each claimed chunk emits boundary edges on sides facing unclaimed
     neighbors (long-packed `point → edges` map);
   - edges chain head-to-tail into closed rings; at 4-way diagonal
     junctions `pick()` prefers continuing straight (keeps loops from
     self-intersecting);
   - collinear points simplified away; largest-|area| ring sorted first
     (outer), remainder are holes;
   - even-odd fill semantics: a ring enclosed by another reads as an
     unclaimed pocket.

   ~135 lines, O(n), no allocation beyond the walk. Reproduces what
   squaremap itself publishes.

## 4. Player marker source and format

Source: the `/map/v1/players` feed (§2) — NOT the v4 `/players` endpoint
(which has no positions, only `status.isOnline`). Format per §2; the addon's
`PlayerMarker(name, uuid, x, z, yaw, key, world)` maps 1:1. Visibility rules
(server-enforced upstream, so the feed is already clean): underground,
sneaking, invisibility-potion players and API opt-outs are absent from the
feed; the addon never infers hidden positions. Markers whose `world` is
empty are treated as belonging to any world (legacy squaremap shape);
with the new feed the world is always present.

## 5. How Earth and Moon are distinguished

- squaremap world keys, verified live: `minecraft_overworld` (Terra Nostra)
  and `earthmc_moon` (Moon).
- Dimension → world resolution (`playerMapWorld()`, client thread only):
  exact `namespace_path` match against the fetched world list first; then
  `minecraft_*` → overworld; then **`earthmc_*` → Moon** (EarthMC has two
  lunar dimensions — `earthmc:space` and `earthmc:moon` — both served by
  the single `earthmc_moon` squaremap world; deliberately not conditional
  on the fetched list so stepping onto the Moon before the list lands still
  resolves correctly); unknown namespace → the single non-overworld world
  if exactly one exists, else refuse to guess and fall back to Earth.
- The map's shown world (`activeWorldKey()`, mode: auto/pinned-Earth/
  pinned-Moon) is resolved once per client tick; fetch threads read only
  the published value.
- **Identity rule**: a town name is NOT an identity. Every Moon town is an
  outpost of an Earth town and shares its name (all 374 of them at the time
  of `eb0c3ba`). Everything world-scoped is keyed by `(world, key)`:
  geometry overrides (`world + '\0' + townKey`), town lookups, tile keys,
  texture names (`squaremap/<world>/<zoom>/<x>_<y>`), ETag maps.
- Both worlds are held in memory simultaneously (map pinned to one, player
  standing in the other): `townsByWorld` map + per-world memoised player
  filters. Terra Nostra ~5,500 towns vs Moon ~47 — the second world is
  nearly free.
- `coordinates.townBlocks` from the v4 API is the town's **Earth** claim
  only (verified: Amsterdam's 569 chunks match its Terra Nostra outline
  exactly; its Moon outpost is absent). Moon outposts come from
  `markers.json` of `earthmc_moon`.
- Moon facts (docs verified): 35,000 × 17,500 blocks; one outpost per town
  (`/t claim outpost`, 256 gold; `/t claim` expansion 16 gold each); real
  1:1 coordinates (no scaling); player markers hidden while viewing a world
  the player is not in.

## 6. How claims are refreshed and reconciled

Steady state: `markers.json` per world on a 60s timer (floored by the
server's `max-age=120`), ETag + gzip; player feed ~1s while the world map
is open, ~3s minimap-only, idle-scaled ×5 after 2 min / ×15 after 10 min
unless the nation-proximity alert wants it live.

Claim-command pipeline (the interesting part):

1. `/t claim` sent → `PendingClaim(chunkX, chunkZ, town?, 12s TTL)`.
2. Chat parsed for success ("successfully claimed") or a curated failure
   list ("already been claimed", "too close", …) → on success an
   `OptimisticClaimChunk` (20s TTL, world-tagged) draws immediately.
3. Unclaim is caught **at send time** (before the server acts, while the
   chunk still answers to the town).
4. Refresh ladder after a confirmed claim/unclaim: v4 API at **700 ms +
   3000 ms** (the server may not have applied the change yet — "the map
   being exactly one claim behind"); squaremap markers forced at
   **150/750/2500/6500/12000 ms**.
5. API result = authoritative chunk set → `ChunkRings.trace()` →
   `overrideTownGeometry(world, town.withRings(rings))`: **replaces** the
   town's outline in the published list (same visual language, colors
   kept) rather than overlaying boxes.
6. Overrides are world-scoped, expire after **180 s** ("an override that
   has outlived any plausible publish lag is more likely to be a stale
   read than the map being slow"), and are dropped as soon as the
   published town covers the override's bbox with equal chunk count
   (`dropSatisfiedOverrides`). A refreshed complete geometry can add AND
   remove claims.
7. Manual refresh: light by default (re-read markers; signature-keyed
   rebuilds redraw only changed towns, imagery stays); full rebuild
   (drop caches) is opt-in **and auto-promoted every 10 minutes** as
   corruption insurance.
8. Incremental invalidation: town render caches key on a geometry
   signature, so unchanged towns are never re-tessellated.

## 7. How tiles are loaded, cached and evicted

- Identity: `TileKey(world, zoom, x, y)` — world included after the
  cross-world poisoning bugs; texture `Identifier` includes world too.
- State machine per tile: absent → loading (dedup via `loading` set) →
  completed (NativeImage in `completedTiles` queue) → uploaded (GPU
  texture in LRU) / failed (`failedAt`, 60 s retry backoff). HTTP 404 is
  normal (ungenerated region); only real refusals (403/429/5xx) surface
  via `lastRefusedStatus` → "why is imagery blank".
- Uploads: render thread drains `completedTiles` under budget — 5/frame,
  1.5 ms (1/frame, 750 µs while panning), priority = zoomPenalty×10,000 +
  distance-from-center. **Remove-then-close** ownership rule
  (`aa92179f`): a NativeImage is closed only after its queue entry is
  removed, so the render thread either gets a live image or never sees
  the entry — the race uploaded freed memory as permanent black tiles.
- Cache: 1024-entry access-order LRU with (a) zoom ≤ 2 tiles pinned
  (overview never re-fetches), (b) a 128-tile reserve for the minimap's
  world (the world map floods the cache while open; closing it must not
  leave the minimap imagery evicted), (c) eviction also deletes the
  tile's ETag. Concurrency budget is **per world** (40 world map / 8
  minimap) so one world's burst cannot starve the other.
- Staleness: per-tile ETag `If-None-Match` on refresh after 20 min; a 304
  resets the age. `worldGeneration` stamp: responses landing after a
  world switch are dropped (per-world filing makes late marker responses
  keepable, but tile images still validate against the stamp).
- Fallback: zoom-0 under-layer always drawn; per-tile parent fallback
  walks up the pyramid and blits the parent's subregion; the
  fine-layer→coarse-layer swap waits for **full coverage** and is sticky
  (established zoom keeps drawing through later eviction — no
  oscillation, no flash).
- Prefetch: pan-direction-lead (EMA-smoothed direction × 0.75 viewport),
  adjacent zooms ±1 when idle; budgets 28 new/frame idle, 2 while moving.

## 8. Historical bugs and how they were fixed

All 17 required commits read in full (chronological order), plus ~25
surveyed and 8 read in depth. Highlights — full per-commit detail in the
working notes:

| Commit | Bug → fix |
|---|---|
| `1fc868b6` | Moon added; hardcoded overworld → derived world keys, total per-world cache invalidation ("coordinates overlap numerically, so nothing may be reused"), markers hidden cross-world |
| `8ce45767` | Nether 8× offsets (four mixed coordinate frames) → single `dimensionCoordinateScale()` helper reading the game's own `coordinateScale()`; zoom-flash → LOD swap waits for full coverage, sticky zoom |
| `19bd14a4` | Fake claimed chunks (hole-closing pass invented claims) → deleted, mask reflects data exactly; hollow towns filled solid → one even-odd path over all rings; rotation seams → band-core+crescent blits |
| `038eab0c` | API claims drawn as chunk-box grid → rebuild the town's own rings, swap geometry in place |
| `c41b0185` | Whole-town optimistic overlay → filter to the delta the map hasn't published |
| `8655c53c` | Publish lag → per-town v4 API pull (`townBlocks`) |
| `62c1f9a7` | API answered pre-application → delayed double-ask 700/3000 ms; overrides age out (180 s) |
| `a5570c6f` | Refresh rebuilt everything → light refresh + signature-keyed incremental redraw |
| `b00d56e1` | Bad caches needed manual discovery → 10-minute auto-promotion of a full rebuild |
| `295ed059` | 640 MB/hr markers polling → `Accept-Encoding: gzip` (84 MB/hr) |
| `d2b6a024` | 180 KB re-download per stale tile → per-tile ETag, 304 resets age |
| `aa92179f` | Freed NativeImage uploaded to GPU (permanent black tiles) → remove-then-close + manual clear-imagery recovery |
| `3c757cd4` | Minimap's world never refreshed imagery → refresh allowed on minimap policy, prefetch still not |
| `e72c0a75` | 27 MB prebaked border atlas, blurry past its zoom cap → vector rendering, screen-space decimation (1.2 MB jar) |
| `021b9000` | Raster snapshot soft at GUI scale 2 → rasterise at physical pixel density; trade margin before density |
| `cb592917` | Antimeridian wrap — **attempted and reverted 5 minutes later, never re-landed**; world width 129,024 blocks; per-copy rendering doubled the tile working set and starved the LRU |
| `7ea2bb8d` | Half-converted per-world split (frame-mixing lookups), per-frame allocations → every lookup names its frame; memoised per-world views; allocation-free steady state |

Additional significant ones: `909fd05` (per-world load budgets +
minimap reserve), `22a56c4` (world in texture name — checkerboard bug),
`eb0c3ba` (world+town override keys), `3fdb1df` (ETag per encoding),
`a4d0616` (chunk mask off render thread, immutable snapshot), `c0347f6`
(merged outline runs, 3-5× fewer quads), `802f022` (zoom is a divisor),
`b92d190` (edge cap mid-scan = half-drawn towns), `10118a2` (hybrid
vector/raster at far zoom), `c2d8488` (both worlds held), `bf551bd`
(generation stamps), `fcab8dd` (separate flash version counter),
`e0bdb52` (cull selections), `bc5881c`/`a17907d`/`22adbce`
(server-directed polling, idle backoff).

## 9. Worth adapting vs. dependent on Xaero

**Adapt directly** (data/infra, no Xaero coupling):
- All URLs, formats, headers, cadences, and conditional-request behavior
  (§2, §6, §7) — including the per-encoding ETag trap and the
  server-published `max-age` floors.
- `ChunkRings` algorithm and the even-odd ring convention.
- The claim pipeline: pending → chat confirm → optimistic TTL → API
  double-ask → geometry override with satisfaction + expiry.
- World-key derivation and the `(world, key)` discipline everywhere.
- Tile cache design: per-world budgets, pinned overview zoom, minimap
  reserve, remove-then-close, upload budgets, parent fallback, full
  coverage + sticky LOD.
- Freshness UX: last-success vs last-attempt vs failing separately;
  404-normal vs refused; "claims 4m ago (refresh failed)".
- Config hygiene: `sanitize()` migrations, clamps, preserved-on-reset
  user data; idle-scaled polling; virtual threads + exponential backoff.

**Depends on Xaero → redesign for ZEA**:
- The entire mixin surface (`MixinGuiMap`, minimap renderers) exists only
  to draw inside Xaero's screens and read its camera/session state. ZEA
  owns the whole screen instead: no hooks, no session classes, no
  waypoint suppression/redraw workarounds (`542f21b`/`7a5d683`), no
  `blocksAcross = size / zoom` inversion (we define our own zoom
  semantics), no GUI-scale fights with another mod's atlas.
- Xaero's baked-outline tile layers and its render pipeline ordering.
- Nether "overworld coords" overlay mode is **not ported** (EarthMC policy:
  no Nether mapping; unsupported dimensions get a disabled state).
- The info-display/sidebar integration (out of scope).

## NEU Dungeon Map (customization reference)

All five required files read (`DungeonMap.java` 1,714 lines,
`GuiDungeonMapEditor.java` 863, `DungeonMapConfig.java` 161,
`Dungeons.java` 304, `MapCommand.java` via `5dd063fb^` + its Kotlin
successor), the 3 named commits, plus 8 extra bugfix commits.

What ZEA takes from NEU:
- **16-option config surface** (exact NEU names → ZEA analogs):
  `dmEnable`→`enable`; `dmPosition`→`position` (anchored, clamped);
  `dmPlayerHeads`→`playerIconStyle` (arrow/head/head+border);
  `dmIconScale`→`iconScale`; `dmCenterPlayer`→`centerPlayer`;
  `dmRotatePlayer`→`rotateWithPlayer` (+`orientIcons` so labels stay
  upright); `dmPlayerInterp`→`interpolatePlayers` (with NEU's small-jump
  guard + wrap-aware angle lerp); `dmBackgroundColour`/`dmBorderColour`→
  ARGB colors; `dmChromaBorder`→ dropped (or later); `dmBackgroundBlur`→
  blur factor with the `0.1 < f < 100` else-off guard; `dmCompat`→
  collapsed (26.3 guarantees the modern path; keep a scissor fallback
  only); border styles resource-pack-overridable under our own namespace
  with `missingTexture` skip.
- **Editor UX**: live preview of the real renderer against a bundled
  demo snapshot; cycle buttons with state labels; sliders with
  shift-snap; inline-validated numeric fields; color picker with alpha;
  a focused position-editor screen (drag box, arrows ±1/±10, R-to-reset);
  instant apply; editor background blur.
- **Rendering lessons**: off-screen render at N× then blit (crisp icons,
  trivial circle mask as one shader constant); forced internal UI scale
  so layout is GUI-scale independent; try/catch + framebuffer rebind so
  one bad frame cannot black-screen; full GL state restore; resolve
  entities by cached id, not list scans.
- **Dropped**: all dungeon logic (room scanning, decoration permutation
  matching, scoreboard player discovery, bow/boss-room detection,
  checkmark options, pixel-dump dev commands) and the O(n!) matcher
  (we have real positions with names attached).

NEU commit lessons that generalize: stale-cache HUDs are the #1 overlay
bug class (`7f703f86`: clear caches when context ends); options that
exist but aren't wired are worse than absent ones (`5ea3130e`);
context detection should combine weak signals ANDed with cache validity
(`18e15a2a`); asset paths are implicit resource-pack API — namespace
editor textures (`7b79c6bb`); clamp-vs-skip for out-of-range markers is
a semantic choice — skip, don't teleport to the edge (`38bfc81a`).

## ZEA-REPO (distribution skeleton)

Inspected in full (17 files): `datasets/` (badges, channels, empty
nations/towns), 5 JSON Schemas, `scripts/validate.py` (179 lines),
`validate.yml` CI (validate on PR/push; manifest regenerated and
committed by the bot on merge to main), `manifest.json`
(`format: zea-repo/manifest`, datasets → {path: sha256}).

Extension design for map tiles (Part 5) is specified in
[ARCHITECTURE.md §7](ARCHITECTURE.md): tiles under
`datasets/map/<world>/…` with a `tiles.json` metadata index; validate.py
gains tile rules (path shape, world enum, zoom/x/z bounds from
`tiles.json`, PNG signature, size cap, sha256, duplicate detection
against whitelisted empty-tile hashes); the manifest schema's
`propertyNames` pattern relaxes to `.(json|png)`; known wrinkle — today
a PR that changes dataset files without regenerating the manifest fails
on "stale hash", so CI must regenerate-then-validate on PRs too.
Consumption: raw GitHub per-file with the manifest hash diffing (the
existing model), commit-pinned zip for initial bulk sync, ETag
conditional requests; never a per-frame source.

## EarthMC documentation verification (Part 2)

- **API**: v4 verified live (previous session + this one): base
  `https://api.earthmc.net/v4`, documented 180 req/min but 429s at ~4
  concurrent in practice; `POST /location` `{"query":[[x,z]]}`;
  `POST /towns|nations|players` ≤100 names with `template` field
  filtering (100 towns: 82 KB → 2 KB); `coordinates.townBlocks` =
  `[[chunkX,chunkZ],…]`; `dynmapColour`/`dynmapOutline` nation colors;
  no player positions anywhere in v4 — only `/map/v1/players`.
  ZEA's own `docs/EARTHMC_API.md` already documents the surface
  including `/map/v1` versioning.
- **Map**: visibility rule verbatim — "Players underground, sneaking, or
  using invisibility potions are hidden from the map"; nation colors via
  `/emcmap nationcolor <HEX>` (premium); Earth/Moon toggle in map
  settings; globe and flat views; 3D archive at 3d.earthmc.net.
- **Moon**: 35k × 17.5k blocks; one outpost per town; `/t claim outpost`
  256 gold, expansion 16 gold; oxygen inside claims; PvP always on; no
  ender chests; death → Earth respawn. Map: "Toggle between earth and
  moon in the top right corner."
- **Policy**: no Cave Mode (server rule; the addon also bails when
  Xaero's cave mode is displayed). ZEA hides the minimap and shows a
  disabled state in the Nether; no underground data ever.

## Inspection log (what was actually read)

- **Addon sources** (read in full or in verified part): `TownyMapMod`,
  `TownyMapConfig`, `SquaremapApiClient` (996 lines), `SquaremapTileRenderer`
  (771), `TownyMinimapOverlay` (1,997, first ~550 + structure),
  `WorldMapRenderer` (via structured extraction), `ChunkRings`,
  `TownData`, `PlayerMarker`, `TownyMapKeybinds`, mixins json + file
  list, `fabric.mod.json`. File inventory of all 58 classes.
- **Addon commits**: all 17 required SHAs read in full diff; ~25 more
  surveyed, 8 read in depth (via a research subagent whose report was
  checked against the repo; SHAs verified present locally).
- **NEU**: the 5 required files + `Position.java`,
  `GuiPositionEditorButForTheDungeonMap.java`, shader sources,
  `Utils.pushGuiScale`; 3 named commits + 8 extra.
- **ZEA-REPO**: every file.
- **Live endpoints probed 2026-11-15**: `settings.json`, markers.json
  headers, tile headers + size, `/map/v1/players` body + headers.
- **ZEA codebase**: `ZeaClient`, `ZeaConfig`/`ZeaConfigStore`,
  `ServerIdentity`/`EarthMcDetector`, `ZeaKeys`, `ARCHITECTURE.md`,
  `docs/EARTHMC_API.md`, build deps (MoulConfig bundled), test tree
  (81 test files).

Not inspected (and not claimed): Xaero's internals (closed source);
`DetailPages`/`TownSearchOverlay` internals beyond their map relevance;
NEU's non-map subsystems.
