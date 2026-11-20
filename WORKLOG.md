# ZEA Worklog

Chronological record of actual work: research, findings, decisions, files
changed, tests performed, results, outstanding problems. Newest entries last.

---

## 2026-11-08 — Stage 0: research, documentation, licensing, ZEA-REPO design

### Repository state

- `ZEA` repository was **empty** (no branches, no commits). No existing work
  to preserve. Project starts from scratch: Java + Fabric, per spec.
- `ZEA-REPO` existed with one commit on `main` (MIT `LICENSE` +
  one-line `README.md`).
- Work happens on `develop` (ZEA). `main` is never touched.

### Research performed and findings

**LuckPerms engineering practices** (target standard for commits):

- Commit subjects: imperative mood, capitalized verb, no conventional-commit
  prefixes, no bodies (0 of the last 80 commits had one). Typical verbs:
  Add / Update / Fix / Improve / Use.
- Size: median 4 files / 37 lines; 41% of commits ≤ 20 lines. Large commits
  exist but are bulk refactors or platform ports, never mixed concerns.
- Regression tests ship **in the same commit** as the fix.
- Refactors are isolated, behavior-preserving, often net-negative in lines.
- Dependency bumps get their own commit ("Update Gradle to v9.4").

**Reference mods:**

- *Polyfrost/Chatting 3.2.5* — verified to support MC 26.3 (Stonecutter
  multi-version). Useful machinery to extract conceptually: tab model with
  prefix-based outgoing routing, shift-click multi-select, versioned JSON
  config migration chain, quartic ease-out smooth-chat translation, custom
  chat width + separate focused/unfocused heights, mixin surface
  (`ChatComponent#render/addMessage/scrollChat/getWidth/getHeight/...`).
  Its config framework is OneConfig — **not usable here** (project
  constraint); its default tab regexes are Hypixel-specific — not portable.
- *JR1258/EarthMC-Recruitment-Addon* — targets 1.21.11 only, but the API
  access pattern is verified and current: `https://api.earthmc.net/v4`,
  `POST /players {"query":[...]}`, `java.net.http.HttpClient` on virtual
  threads, 60s profile TTL, batch cap 50, single-flight fetch flag.
- *NotEnoughUpdates* — inventory buttons: model `{x, y, playerInvOnly,
  anchorRight, anchorBottom, backgroundIndex, command, icon}`; rendering via
  post-draw hooks; editor without drag repositioning; clipboard
  import/export. The valuable part is **`ConfigUtil.saveConfig`**: temp
  file → re-parse verify → `ATOMIC_MOVE` → `.corrupted` backup on failure.
  ZEA adopts this protocol for all stores.
- *afranz29/Inventory-Buttons* — Fabric port mixin approach verified
  (`AbstractContainerScreen` render + cancellable `mouseClicked`), drag
  editor with grid snap. Its persistence is a plain `Files.newBufferedWriter`
  write — exactly the corruption risk ZEA must avoid.

**MoulConfig (configuration framework):**

- Canonical repo: `NotEnoughUpdates/MoulConfig` (Moulberry's original is a
  404). License LGPL-3.0-or-later (matches ZEA's own licensing).
- **4.8.0 ships `modern-26.3`** at `https://maven.notenoughupdates.org/releases/`
  (`org.notenoughupdates.moulconfig:modern-26.3:4.8.0`). Verified published.
  Fabric-only for modern versions. Distinct from Lattice (GUI-only, MIT) and
  from OneConfig (Polyfrost; excluded by constraint).
- Integration decision: **jar-in-jar (`include`)** — MoulConfig stays a
  separate unmodified Fabric mod jar. Cleanest LGPL compliance and idiomatic
  Fabric. Fallback if JiB breaks the build: official shadow+relocate recipe.
  MoulConfig has no built-in HUD editor; ZEA's chat dimensions editing will
  be a native implementation.

**EarthMC API (docs + live verification):**

- Current version **v4**, base `https://api.earthmc.net/v4`. Public core
  endpoints (towns/nations/players/quarters/location/nearby/online) need no
  key. `POST` endpoints take `{"query": [names|uuids], "template": {...}}`.
- The newer authorization model is verified: `/api key create|delete|copy`
  creates personal API keys in-game; `/api auth` opens a per-player shop
  authorization editor; `/api opt-out` opens a granular opt-out editor.
  Keys unlock SSE, own resident data, shops, mcMMO, pursuits.
- Rate limit 180 req/min (429 + `Retry-After`), per-endpoint cooldowns.
  Live coordinates only via `https://api.earthmc.net/map/v1/players`.
- Full details recorded in [docs/EARTHMC_API.md](docs/EARTHMC_API.md).

**Toolchain (all verified):**

- Minecraft 26.3 (released 2026-09-15) requires **Java 25**.
- Fabric: loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19-SNAPSHOT,
  Gradle 9.8.2 wrapper — per the official `fabric-example-mod` default
  branch (now `26.3`).
- **Yarn mappings end at 1.21.11.** MC 26.3 uses official Mojang mappings
  (the example mod declares no mappings at all; Loom defaults to official).

**NEU-REPO analysis** (for ZEA-REPO design): flat ID-keyed files, external
schemas, commit-sha-pinned zip downloads, changed-file-only CI. Weaknesses to
avoid: 9k-file flat dirs, triple-stored display text, loose typing, no
manifest. Full report lives in ZEA-REPO `docs/DESIGN.md`.

**HelpChat paste service:** protocol recorded from spec. The hostname does
not resolve from this development environment (NXDOMAIN), so live protocol
verification is pending; Stage 8 tests will use an embedded mock HTTP server
regardless.

### Decisions

1. **Single source set, `environment: client`** — ZEA is 100% client code;
   a split common/client layout would be an empty shell.
2. **Official Mojang mappings** — Yarn is dead past 1.21.11; matches
   MoulConfig modern-26.3.
3. **MoulConfig via jar-in-jar** — see above; OneConfig excluded.
4. **LGPL-3.0-or-later for ZEA** (MoulConfig is LGPL; mod + config framework
   licensing aligned). **MIT for ZEA-REPO** (data repo, maximally permissive).
5. **ZEA-REPO is curated/supplementary data only** — volatile live data
   (residents, balances, coordinates) always comes from the official API at
   runtime; the repo stores what the API cannot: badge meanings, channel
   definitions, curated nation/town descriptions, history. Keyed by slug +
   UUID, manifest-driven selective download, CI-validated + CI-regenerated
   manifest.
6. **NEU safe-save protocol adopted** for every persistent store.
7. Commit discipline mirrors LuckPerms (see research above).

### Files changed (this session)

ZEA (all on `develop`):

- Documentation: `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `WORKLOG.md`,
  `docs/{DEPENDENCIES,EARTHMC_API,EARTHMC_MESSAGE_FORMATS,TESTING,PASTE_SERVICE}.md`.
- Licensing: `LICENSE` (LGPL-3.0-or-later, full text).
- Build: Gradle 9.8.2 wrapper, Loom 1.19 setup, single source set,
  `fabric.mod.json` (environment client), MoulConfig 4.8.0 via jar-in-jar.
- Foundation (Stage 1, partial): `ZeaClient` wiring,
  `lifecycle/ClientLifecycle` (join/leave/stopping events),
  `earthmc/EarthMcDetector` (address-based detection),
  `persistence/SafeFileWriter` (atomic replacement),
  `persistence/JsonStore` (round-trip-verified saves, corruption recovery).
- Tests: `EarthMcDetectorTest`, `SafeFileWriterTest`, `JsonStoreTest`.

ZEA-REPO (on `main`):

- `docs/DESIGN.md` (the assessment above), `README.md`, `CONTRIBUTING.md`,
  `manifest.json`, `schemas/` (manifest/badges/channels/nation/town),
  `datasets/{badges,channels}.json` (seeded with verified observations),
  `scripts/validate.py`, `.github/workflows/validate.yml`, `.vscode/`.

### Tests performed

- Build: `./gradlew build` — **successful** (Java 25, Minecraft 26.3,
  Loom 1.19). Jar verified to contain `ZeaClient`, expanded
  `fabric.mod.json` version, `LICENSE_zea`, and the bundled
  `META-INF/jars/modern-26.3-4.8.0.jar` (MoulConfig).
- Unit tests: **21 tests, 0 failures** (EarthMcDetector 13,
  SafeFileWriter 4, JsonStore 4) via JUnit 6.1.3.
- ZEA-REPO validation: `scripts/validate.py` passes on the seeded data;
  negative tests confirmed it rejects bad schema versions, missing
  required fields, stray file locations and stale manifest hashes.
- In-game testing: **none performed this session.** No claims are made
  about in-game behavior, MoulConfig runtime initialization, or the
  EarthMC connection flow beyond what compiles and passes unit tests.

### Outstanding problems

- `paste.helpcha.at` unreachable from this environment — live protocol
  verification pending (mock-server tests planned for Stage 8).
- Nickname → real username resolution source unresolved (Stage 4
  investigation).
- Server identity key for history isolation unresolved (Stage 7).
- In-game verification of the full writable channel list pending; the
  ZEA-REPO `channels` dataset allows corrections without mod releases.

---

## 2026-11-08 (session 2) — Stage 1 complete, Stage 2 message classification

### Research performed

- Verified the 26.3 chat integration surface before writing capture code:
  Fabric API ships `ClientReceiveMessageEvents` (GAME + CHAT) in
  `fabric-message-api-v1` 7.0.10 for 26.3 — official hooks, so **no custom
  mixins are needed for capture**.
- Verified the 26.3 component API: `ClickEvent`/`HoverEvent` are now
  sealed interfaces with record implementations (`OpenUrl.uri()`,
  `RunCommand.command()`, `ShowText.value()`); `MutableComponent` builders
  unchanged. Minecraft `Component` classes load in plain JUnit tests, so
  classifiers are testable without a client.

### Work done

- **Stage 1 completed** with `chat.capture.MessageCapture`: registers the
  Fabric events, skips overlay (action bar) text, gates on the EarthMC
  connection, forwards immutable captures. Wired in `ZeaClient`.
- **Stage 2 implemented**:
  - `chat.model`: `CapturedMessage` (original component + flattened text +
    timestamp), `MessageCategory`, sealed `MessagePayload` (one record per
    family + `None`), `Badge`, `Affiliation`, `ChannelChat`,
    `PrivateMessage`, `VoteMessage`, `CrateWinMessage`, `DeathMessage`,
    `SystemMessage`, `WritingChannelReport`, `DiscordMessage`,
    `ClassifiedMessage`.
  - `chat.classify`: `MessageClassifier` (binary confidence: anchored match
    or decline), `ClassificationPipeline` (fixed order, first match wins,
    guaranteed UNCLASSIFIED fallback), per-family classifiers, `KnownBadges`
    (mirrors ZEA-REPO ids), `BadgeExtractor` (component-first sibling walk
    preserving hover/click, text fallback for flat messages, unknown
    symbols never assigned meanings).
- **Tests**: 14 fixture families in `fixtures/messages.json` run through
  the full pipeline; per-classifier tests for component-structure behavior
  (badge events preserved, single-segment affiliations, nickname display
  names, non-PM arrows, variable crate amounts, optional `/vote`,
  death hover preservation, Discord-before-global ordering).

### Tests performed

- `./gradlew build` successful. `./gradlew test`: **83 tests, 0 failures**
  (14 new fixture-pipeline assertions ×2, 27 classifier tests, 2 model
  tests, plus the existing 21).
- In-game testing: **none** — capture/classification runtime behavior on
  live EarthMC is unverified; only compile + unit results are claimed.

### Decisions

1. **Fabric events over mixins** for capture (official integration point).
2. **Binary confidence** — anchored patterns are always confident; the
   guaranteed UNCLASSIFIED fallback is the safety property that matters.
3. **Bracket required for global classification** — `Name: content` alone
   is too weak an anchor; bracket-less messages stay unclassified rather
   than risk misclassification.
4. **Single-segment affiliations kept raw** — nation-vs-town is not
   guessable from text.
5. **KnownBadges in code for now**, mirroring ZEA-REPO ids; ZEA-REPO
   becomes the source when the repository client exists.

### Outstanding problems

- Local/trade/nation/town message formats: **no observed samples** — no
  classifiers written (documented in CHAT_CLASSIFICATION.md). Noted risk:
  bracket-leading channel markers (`[Town] Name: msg`) would currently be
  claimed by the global classifier; ZEA-REPO channels dataset is the
  planned data-driven fix.
- Component-tree structure of EarthMC messages unverified (text fixtures
  only) — needs in-game capture.
- Nickname → real username resolution unresolved (Stage 4).

---

## 2026-11-08 (session 2, continued) — Stage 3 core: feeds and routing

### Work done

- `chat.feeds`: `Feed` (+ `Feeds` registry: 7 default tabs, 6 extra feeds,
  All aggregates everything including unclassified), `FeedSelection`,
  `SendTargets`, `ChannelState` (server writing channel — updated only
  from server reports, cleared on disconnect), `WriteChannel`/`Channels`
  (verified per-message command table), `OutgoingRouter`,
  `MultiChannelDispatcher` (staggered dispatch, 1000 ms default interval,
  failure cancels remaining, batch replacement respects the interval).
- `chat.ChatSubsystem`: wiring — capture → classify → channel state;
  dispatcher ticked on client tick, cancelled on disconnect, sends via
  `ClientPacketListener#sendCommand`.
- `docs/CHAT_CHANNELS.md`: the three-state model, channel table, dispatch
  policy, what is deliberately not modeled (switch commands).

### Design decisions

1. **Per-message routing, no switch commands.** ZEA sends `/g <message>`
   style commands; it never runs `/global` itself. The server writing
   channel therefore only ever changes through the player's own actions,
   tracked from server reports — no transition to get wrong.
2. **Three separate state classes** (`ChannelState`, `FeedSelection`,
   `SendTargets`) so the spec's "never one overloaded variable" rule is
   enforced by the type system.
3. **Uniform dispatch timing** (1000 ms) — conservative against spam
   limits, never randomized to look human.

### Tests performed

- `./gradlew test`: **110 tests, 0 failures** (27 new: feed filtering and
  aggregation, router routing/order/rejections, dispatcher interval/
  cancel/failure/replacement behavior, channel state report/clear).
- In-game testing: **none.** Runtime behavior (tick wiring, sendCommand)
  is compile-verified only.

### Outstanding problems

- Server writing-channel report on join unverified — `ChannelState`
  starts unknown until a report arrives.
- Dispatch interval and default tabs not yet configurable (MoulConfig
  options with the Stage 4 UI).
- Tab rendering, send UI, "All is read-only as a selector" UI enforcement:
  Stage 4.

---

## 2026-11-09 (session 3) — Configuration, chat tab bar, feed filtering

### Work done

- **MoulConfig configuration** (`config` package): `ZeaConfig` with two
  categories — Chat (multi-channel sending off by default, dispatch
  interval slider 500–5000 ms) and Tabs (per-feed visibility for the six
  extra feeds). `ZeaConfigStore` persists it through the safe-write
  protocol (round-trip verified, atomic replace, corrupt files preserved
  as `*.corrupted` with defaults restored). `saveNow` is routed through
  the store so MoulConfig editor saves are safe writes. `configVersion`
  gates migrations (none needed at v1).
- **Wiring**: `ChatSubsystem` reads the dispatch interval and gates
  multi-target sends behind the config flag (`MultiSendResult`);
  `Feeds.visibleTabs` resolves the tab strip from enabled extra feeds;
  `ZeaClient` loads the config at init.
- **Chat tab bar** (`chat.render.ChatTabBar` + `mixin.ChatScreenMixin`):
  a flat, borderless tab strip above the chat input, EarthMC-only. Plain
  click switches the display feed; rendering is a translucent fill and a
  label, nothing else.
- **Feed filtering**: applies the selected feed as the vanilla chat's
  official `setVisibleMessageFilter` — visual only, every message stays
  in history (All restores vanilla behavior by clearing the filter).
  `ClassificationCache` maps displayed components back to classifications
  by identity, bounded at 512 entries, single classification per message
  shared between capture and filtering. Display state resets to All on
  disconnect so other servers see untouched chat.

### Research performed

- Verified the 26.3 chat integration surface by decompiling the mapped
  jar: `ChatComponent.setVisibleMessageFilter(Predicate<GuiMessage>)` is
  an official hook that immediately refreshes trimmed messages;
  `ChatScreen` draws input background → chat panel → widgets (widgets on
  top, so a widget tab bar works).
- **Minecraft 26.3 ships unobfuscated**: its version manifest has no
  `client_mappings`/`server_mappings` downloads. Official Mojang names
  are runtime names, so ZEA's jar needs no intermediary remap and its
  mixins need no refmap — documented in ARCHITECTURE.md.
- MoulConfig 4.8.0 annotation surface verified from the jar
  (`@Category`, `@ConfigOption`, `@ConfigEditorBoolean/Slider`, runtime
  reflection processing); the MoulConfig base class loads in plain JUnit.

### Tests performed

- `./gradlew build` successful; jar contains the mixin, `zea.mixins.json`
  and the bundled MoulConfig jar.
- `./gradlew test`: **135 tests, 0 failures** (22 new: config defaults/
  clamping/round-trip/corruption/migration, annotation-contract
  structure, visible-tabs resolution, cache identity/bounds, feed
  filter predicates).
- In-game testing: **none.** The tab bar's rendering, hitboxes and the
  filter's live behavior are compile- and unit-verified only; first
  in-game verification is pending.

### Design decisions

1. **Official filter hook over a rendering mixin** — filtering happens
   inside vanilla's own pipeline; ZEA adds no message-rendering mixin.
2. **`null` filter for All** — aggregates skip the predicate entirely;
   vanilla shows everything, including anything ZEA never classified.
3. **Identity-keyed classification cache** — one classification per
   message, shared by capture and display filtering; misses re-classify
   lazily, so cache eviction can never hide a message.
4. **Tabs anchored above the input strip** — exact, scale-independent
   geometry (`height - 14 - TAB_HEIGHT`); panel-top anchoring would
   depend on scaled chat geometry.


### Late addition (same session) — message copying

- **`util.LegacyText`**: Component → legacy `§`-code serializer (26.3 ships
  none). Style runs emit `§r` + full code set on change, mirroring how
  EarthMC emits segments; inherited child styles produce no extra codes;
  non-legacy colors are skipped.
- **`chat.render.ChatGeometry` + `ChatHitTester`**: the vanilla line
  geometry mirrored from ChatComponent's bytecode (`chatBottom =
  floor((guiHeight - 40) / scale)`, entry metrics, text-region slot math),
  plus accessor reads (`ChatComponentAccessor`) of the live line list and
  scroll position.
- **`ChatScreenMixin`**: right-click copies the message under the cursor,
  alt + right-click copies the formatted variant. Clicks not over a
  message fall through to vanilla.

Tests: **152 total, 0 failures** (LegacyText emission rules including the
observed EarthMC segment style; geometry boundaries and slot stacking).
In-game hit-test accuracy remains **unverified** — the math mirrors
vanilla's own formulas, but no in-game check has happened yet.

### Outstanding problems

- Nickname → real username resolution unresolved (Stage 4).
- Smooth chat, search, dimensions: Stage 4 remainder.
- Tab bar width uses the unscaled chat width; users with chat scale
  below 1 see a strip wider than the panel (cosmetic, tracked).
- Writing-channel report on join still unverified in-game.

---

## 2026-11-09 (session 4) — Copy wiring completed, confirmation toast

### Problem found and fixed

- Commit 432f3a6 ("Add chat message copying") had landed **incomplete**:
  the support classes (geometry, hit-tester, legacy serializer, accessor)
  and their tests existed, but the `mouseClicked` injection itself was
  missing — the mixin carried only its imports and constants. Copying
  compiled but did nothing. Rebuilt the wiring properly.

### Work done

- **`chat.render.ChatCopier`**: the copy policy — hit-test the cursor,
  pick plain or legacy-formatted serialization, write the clipboard.
  Returns `PLAIN` / `FORMATTED` / `NO_MESSAGE` so callers can leave
  missed clicks to vanilla. `textFor` is unit-testable without a client.
- **`chat.render.CopyToast`**: the "message copied" confirmation, shown
  through the vanilla system toast with ZEA's own
  `SystemToastId` token (never replaces a vanilla notification). First
  localized strings: `assets/zea/lang/en_us.json`.
- **`ChatScreenMixin`**: right-click copies the message under the cursor,
  alt + right-click the formatted variant; the click is consumed only
  when a message was hit, otherwise vanilla proceeds untouched. Verified
  against the 26.3 signatures by javap: `ChatScreen.mouseClicked(
  MouseButtonEvent, boolean)`, `MouseButtonEvent.{x,y,button,modifiers}`,
  `GuiMessage.content()`, `Gui.gui.hud.getChat()`, `Gui.toastManager()`.
- Cleaned the dead imports the incomplete commit had left behind.

### Tests performed

- `./gradlew build` successful; jar contains the new classes and the
  lang file.
- `./gradlew test`: **156 tests, 0 failures** (4 new: plain flattening,
  formatted emission incl. the no-leading-reset rule, both-modes parity,
  EarthMC segment round-trip).
- In-game testing: **none.** Hit-test accuracy on a live client is still
  the outstanding verification for the copying feature as a whole.

### Outstanding problems

- Unchanged from the previous session, plus: toast text is
  English-only until more languages are contributed.

---

## 2026-11-09 (session 4, continued) — Chat search, smooth chat

### Work done

- **Chat search** (`Ctrl+F`): `FeedFilter` holds the query — the single
  source of truth — and composes it with the selected feed's predicate
  (a search narrows even on All; a blank open query narrows nothing;
  `null` means closed). `ChatSearchBar` is a borderless vanilla
  `EditBox` with the flat chat background. `ChatScreenMixin` wires it:
  Ctrl+F toggles (macOS: Cmd+F via the vanilla control quirk), live
  filtering per keystroke, Escape closes the search before it closes
  the screen, Enter while focused returns focus to the input (so the
  input's text can never be sent accidentally mid-search), and
  `removed()` closes the search when the screen goes away — the
  visible-message filter also affects the HUD, which must never stay
  narrowed invisibly. The query survives window resize (state lives in
  the filter, the widget is rebuilt by init) and resets on disconnect.
- **Smooth chat**: `SmoothChat` keeps one vertical offset (unscaled
  chat units) that starts at a scroll discontinuity and eases to zero
  (quartic ease-out; mid-flight discontinuities accumulate from the
  remaining offset). `ChatComponentMixin` records the anchor around
  `scrollChat`, `resetChatScroll` and `addMessageToDisplayQueue`
  (message line count captured by wrapping `GuiMessage.splitLines`,
  because the trimmed list's 100-entry cap makes growth under-report
  on busy channels; the add-path's internal pin scroll is skipped via
  a flag), and translates the chat's pushed pose by the eased
  remainder at render time (chat units × chat scale = gui pixels;
  `pushMatrix` wrapped so the package-private `DisplayMode` parameter
  never has to be referenced). EarthMC-gated, configurable, on by
  default (200 ms).
- **Configuration**: new "Chat interface" category — smooth chat
  toggle + duration slider (50–500 ms, clamped accessor). Additive
  fields; older files keep defaults, no migration needed.
- Verified from the mapped jar before writing code: 26.3 focus
  cycling skips invisible widgets (`isActive` = visible ∧ active), Tab
  focus cannot land on the hidden search field; `KeyEvent` carries
  `shortcutKey`/`isEscape`/`isConfirmation`/`hasControlDownWithQuirk`;
  `resetChatScroll` fires from `ChatScreen.removed`.
- **Chat dimensions dropped as a work item**: vanilla 26.3 ships
  `chatWidth`, `chatHeightFocused` and `chatHeightUnfocused` options
  itself. Re-implementing them would duplicate vanilla settings and
  fight its own options screen.

### Design decisions

1. **Query state in `FeedFilter`, not the screen** — one writer of
   `setVisibleMessageFilter`, resize-safe, resettable on disconnect.
2. **Search composes rather than replaces the feed filter** — the tab
   you are viewing stays the search domain.
3. **Trigger hooks around anchor changes, render hook applies the
   offset** — the animation is a pure function of (anchor history,
   clock), so the two halves stay independently testable.
4. **`@WrapOperation` on `pushMatrix`** for the render hook: the
   handler needs no target-method parameters, dodging the
   package-private `DisplayMode`, and composes with other mods'
   wraps of the same call.

### Tests performed

- `./gradlew build` successful; jar contains the new classes and mixin
  registration.
- `./gradlew test`: **171 tests, 0 failures** (15 new: 6 search
   composition in FeedFilterTest — blank query, All narrowing,
   case-insensitivity, feed composition, styled flatten, close
   restore; 8 SmoothChat — full start, negative start, exact-zero end,
   quartic shape at t=¼ and t=½, mid-flight accumulation,
   zero-duration collapse, zero-shift no-op, finished-state reset;
   1 config clamp).
- Mixin descriptors re-audited against `javap` output (method
  signatures, the `splitLines`/`pushMatrix` targets, the public
  `extractRenderState` descriptor).
- In-game testing: **none.** Both features are compile- and
  unit-verified; the pose translate, easing feel, search focus flow
  and copy hit-testing need a live client.

### Outstanding problems

- Nickname → real username resolution unresolved (Stage 4 remainder).
- Player hover presentation, configurable controls: Stage 4 remainder.
- Smooth chat: hover/click hit regions follow the settled anchor, so
  they can lag the visuals by up to one animation (~200 ms); clicks in
  practice land after the glide. Tab bar width with chat scale below 1
  (cosmetic) and writing-channel report on join — unchanged.

---

## 2026-11-09 (session 4, wrap-up) — Key binding, nickname investigation

### Work done

- **Rebindable "toggle chat search" key binding** (`keybind.ZeaKeys`):
  registered through Fabric's `fabric-key-mapping-api-v1`
  (`KeyMappingHelper.registerKeyMapping`), under a ZEA category in the
  vanilla controls screen, unbound by default. `ChatScreenMixin`
  accepts it alongside Ctrl+F. Verified from the mapped jar: 26.3
  renamed `InputConstants.Type.KEYSYM` → `KEYBOARD`, `KeyMapping` takes
  an int key code, `KeyMapping.matches(KeyEvent)` exists, and
  `KeyMapping.Category.register(Identifier)` is public (label key:
  `key.category.<namespace>.<path>`).
- **Why the built-in shortcuts stay fixed**: vanilla key mappings are a
  single key without modifiers — they cannot express right-click,
  alt+right-click or Ctrl+F. Rebinding those would need a custom
  capture UI; recorded as a future option if users ask.

### Research performed

- **Nickname → username resolution, settled against the API** (live
  probes, 2026-11-09): player objects carry no nickname field; lookup
  by nickname 404s while the real name resolves (observed sample:
  `Coriolanus` → 404, `pengpy` → found). The API indexes real names
  only, so resolution cannot be built on it. Remaining candidate: the
  chat component tree (hover/click events on the displayed name) —
  needs in-game captured components. Full player object shape (title,
  surname, formattedName, about, town, nation, timestamps, status,
  stats, perms, ranks) recorded for the future player-card work.
- The simple `GET /v4/players` response is an online-player
  `{name, uuid}` index (query parameter ignored) — noted for the
  future API client.

### Tests performed

- `./gradlew build` successful; **171 tests, 0 failures** (no behavior
  change to test — the binding is registration + a `matches` check).
- In-game testing: **none.** The controls-screen entry and the
  keybind-triggered toggle need a live client.

### Outstanding problems

- Stage 4 leftovers (player hover, real-username display) are blocked
  on in-game component capture; next unblocked work is Stage 5
  (notifications).


---

## 2026-11-09 (session 5) — Stage 5: notifications, mentions

### Work done

- **Notification coordinator** (`notify`): the single owner of the action
  bar, title and subtitle. PM-over-mention priority (a PM preempts a
  showing mention; a mention never preempts a PM), equal-priority
  queueing bounded at 4 (oldest dropped first), temporary notifications
  (mentions, 4 s), persistent ones (PM notices) that resolve by key —
  the sender, so replying dismisses them — or expire at a hard 5-minute
  cap, and a `clear()` for the disconnect path. All display goes through
  a `NotificationSurface` interface; time is injected, so the policy is
  fully unit-tested without a client.
- **PM notifications**: incoming PMs post a persistent notice ("PM from
  <sender>: <content>", 60-char preview); any outgoing PM to that sender
  resolves it. Config: enable, sound, placement (action bar default,
  title optional — both re-show every 2.5 s while a persistent notice is
  active, just inside vanilla's ~3 s overlay fade).
- **Mention detection** (`mention`): case-insensitive, word-boundary
  matching of the local player's name in chat-like categories only (PM,
  system, vote, crate and death lines never ping). Channel chat scans the
  parsed content and excludes the speaker's own echo; Discord and
  unclassified lines only match behind a `Name: content` separator —
  lines that cannot be attributed never ping.
- **Mention presentation**: gold tint (alpha 0.30, blended with the
  line's own alpha) behind mentioned chat lines; notification with
  40-char preview and configurable placement; experience-orb cue (pitch
  1.0 mentions, 0.6 PMs). Highlight state is a bounded identity-keyed
  set — the render mixin looks up `GuiMessage.Line#parent#content`,
  the same instance capture classified, so no component is ever
  modified.
- **Opt-in auto-response**: off by default and inert until a response
  text is configured; cooldown (10–300 s slider, default 30); hard
  session cap (20 per connection, reset on disconnect); loop guard (an
  incoming mention already containing the response text is never
  answered, so two ZEA users cannot ping-pong); only verified writable
  channels (global/local/trade/nation/town via the Channels table);
  dispatches through the existing conservative router + dispatcher.

### Research performed and findings

- **26.3 HUD split**: titles and the action bar moved from `Gui` to the
  new `Hud` (`Minecraft.getInstance().gui.hud`): `setOverlayMessage`
  (60-tick display), `setTitle`/`setSubtitle`/`setTimes`/`clearTitles`.
  Verified from the mapped jar via javap and Vineflower decompilation.
- **Action bar lifetime**: `overlayMessageTime = 60` ticks (~3 s),
  decremented in `Hud.tick` — measured to pick the 2.5 s refresh.
- **Line background rendering**: `extractRenderState` draws each visible
  line's background inside `lambda$extractRenderState$1` — one
  `ChatGraphicsAccess.fill(IIIII)V` call per line with the lambda's
  captured params (including the `GuiMessage.Line` and its alpha)
  available to a `@WrapOperation` handler. The three
  `ChatGraphicsAccess` implementations (focused, background,
  click-capture) make the wrap safe in every mode — the click-capture
  one's `fill` is a no-op.
- **MoulConfig dropdowns accept enum-typed fields** (decompiled
  `GuiOptionEditorDropdown`: `Enum.class.isAssignableFrom(clazz)` path
  sets the constant, displays `toString()`), so placements are
  `Notification.Destination` enums — type-safe in code, serialized by
  name, displayed via label toString. The config structure test now
  allows String or enum dropdowns, and pins text fields to String with
  exactly one editor annotation per option.
- **`SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP,
  pitch)`** for the cue (UI path, no world attenuation).
- `Minecraft.getInstance().getUser().getName()` supplies the local name
  — fetched lazily per message, since the session does not exist at mod
  init.

### Design decisions

1. **Render-time tint over component wrapping.** Wrapping the displayed
   component in a styled prefix would break the classifiers' anchored
   parsing on re-classification and diverge from the identity-keyed
   cache; drawing over vanilla's own per-line background rect changes
   color only — hit-testing, search, copying and hover stay untouched.
2. **`Name: content` attribution for unstructured lines.** EarthMC's
   local/trade/nation/town classifiers are pending fixtures, so those
   lines arrive unclassified; requiring the separator keeps join and
   system lines from pinging while still catching real mentions there.
3. **PM notices persist, mentions do not.** A mention is a 4-second
   attention cue; a PM waits for the reply that usually ends it, with
   the 5-minute cap as the escape hatch.
4. **Auto-response sends through the same dispatcher as manual
   multi-send** — one message at a time, spaced by the configured
   interval, cancelled on disconnect — instead of a private send path.
5. **Loop guard by text containment**: cheap, and it breaks the exact
   ping-pong shape (each side's message contains the shared response
   text) without heuristics about message timing.

### Tests performed

- `./gradlew build` successful; jar contains all new classes and the
  mixin registration.
- `./gradlew test`: **254 tests, 0 failures** (83 new: 18 coordinator —
  preemption, queueing, capacity drop, persistence, refresh window,
  hard expiry, resolve paths, disconnect clear, title times; 12
  notifications service — PM post/resolve/preview, config gates,
  placement; 28 mention detector — boundaries, case, self-echo,
  category gating, unstructured attribution; 9 mentions service — fanout
  and per-option gating; 6 highlight set — identity, eviction, clear;
  10 auto-responder — guards, channels, session cap, reset). Config
  structure test now also enforces single-editor and text/dropdown
  types.
- In-game testing: **none.** The action bar/title behavior, tint
  visibility, sound and the controls-screen entries (new Notifications
  and Mentions categories) need a live client; the mixin target was
  verified against 26.3 bytecode but not against a running game.

### Outstanding problems

- Nickname-based mention matching remains blocked on EarthMC data (API
  has no nicknames); nickname self-echo can false-ping until then.
- `lambda$extractRenderState$1` is pinned to 26.3's compiled shape —
  re-verify on any Minecraft version bump.
- Persistent notices on the title placement re-show every 2.5 s, which
  is assertive by design; if users report it as annoying, gate title
  refresh behind a follow-up option.
- Stage 4 leftovers (player hover, real-username display) stay blocked
  on in-game component capture. Next unblocked work is Stage 6
  (inventory buttons).

## 2026-11-09 (session 6) — Release pipeline, Stage 6: inventory buttons

### Work done

- **Release pipeline** (`.github/workflows/release.yml`): every push to
  `develop` cuts the next numbered dev build automatically — compute N
  over the `vMAJOR.MINOR.PATCH-dev.N` tag ledger, stamp `mod_version` in
  `gradle.properties`, sync the bump back to develop as an Abasing
  `[skip ci]` commit before building (loop-breaker), `./gradlew build`
  (tests included), tag, publish `zea-<version>.jar` as a prerelease
  with auto-generated notes. Failure safety and escape hatches mirror
  the ZaminTorch pipeline: no tag/release on build failure, orphan-tag
  repair path, `[skip ci]` postpones, re-running a published run is a
  no-op. The `ABASING_TOKEN` repo secret was set so every
  pipeline-authored commit, tag and release is Abasing, never
  github-actions[bot]. Verified live: `v0.1.0-dev.1` through `dev.4`
  published.
- **Stage 6a — model and store** (`buttons` package): `InventoryButton`
  (immutable record, self-sanitizing constructor), `ButtonAnchor`
  (four container corners), `ButtonGeometry` (pure anchor math, hit
  tests, nearest-corner re-anchoring), `InventoryButtonStore`
  (versioned `{"version":1,"buttons":[…]}` file through the safe-write
  protocol; tolerant `ButtonEntry` wire record because Gson rejects
  explicit nulls on primitive record components — one damaged entry must
  not quarantine the file; unusable entries dropped with a warning,
  duplicate ids re-issued).
- **Stage 6b — rendering**: `ButtonRenderer` (slot-like box: 1px border
  over dark fill), `ButtonIcons` (cached id→ItemStack, barrier
  fallback), `ButtonTooltips` (name, command, unknown-icon warning).
  `ContainerScreenMixin` on `AbstractContainerScreen`: buttons draw at
  TAIL of `extractContents` (pose popped → screen coordinates), a
  hovered button cancels `extractTooltip` (HEAD) so slot tooltips never
  stack. Config category *Inventory buttons* (master toggle, on by
  default — inert with no buttons). Gated on the EarthMC connection.
- **Stage 6c — execution**: `ButtonActivator` is the only path from a
  click to a sent command: re-validates the command (non-blank, ≤256,
  no control chars — a hand-edited file cannot smuggle a malformed
  command past the click), 500 ms per-button cooldown (double-click =
  one command), failed sends don't arm the cooldown, resets on
  disconnect. Sends via `ClientPacketListener.sendCommand` — the same
  path as the chat dispatcher. Mouse releases over buttons are consumed
  so vanilla never treats the area as "outside the GUI" (which would
  drop a carried item); mid-quick-craft releases pass through.
- **Stage 6d — editing**: `ButtonDragSession` (grab → preview →
  re-anchor at nearest corner; 3px jitter threshold so a plain press
  never repositions), `ButtonEditScreen` (vanilla LinearLayout form:
  name/command/icon fields, anchor cycler, enabled toggle, Done with
  inline validation, two-click delete), edit mode toggled by the
  rebindable *Toggle button editing* key while a container screen is
  open (Esc leaves edit mode first), a **+** ghost beside the container
  creates buttons, `InventoryButtons` CRUD (upsert/delete persist
  immediately through the store).
- **Docs**: `docs/INVENTORY_BUTTONS.md` (behavior, anchoring, store
  policy, the no-execution guarantees, mixin target table, limitations).

### Research performed and findings

- **26.3 container screen internals** (decompiled from the mapped jar):
  `extractRenderState` → `extractContents` (background + slots, pose
  translated by leftPos/topPos and popped at the end) →
  `extractCarriedItem` (own stratum) → `extractTooltip`. Injecting at
  TAIL of `extractContents` draws in screen coordinates below carried
  items and tooltips.
- **`mouseClicked`/`mouseReleased` semantics**: the container screen
  consumes nearly all clicks; releases outside the GUI with a carried
  item drop it (`slotClicked(null, -999, …, PICKUP)`). Hence the
  release-consume. `mouseReleased` resets `isQuickCrafting` at its end —
  cancelling it mid-quick-craft would strand the state, so those
  releases pass through (shadowed `isQuickCrafting` to check).
- **`ClientPacketListener.sendCommand`** sends `ServerboundChatCommandPacket`
  directly (no parse-error popups; `commands.parse` runs only to find
  signable arguments). `sendUnattendedCommand` exists but opens
  confirmation windows for parse/permission issues — wrong UX for
  buttons, which send plugin commands the client tree may not know.
- **`menu.removed(player)` is a client-side no-op** (server-only logic),
  so an edit screen can open over a container and return with
  `gui.setScreen(parent)` without closing the container — the same
  pattern vanilla's `sendUnattendedCommand` confirmation flow uses.
- **Gson 2.14 record behavior** (probed): a missing key leaves the
  component default; an explicit `null` on a primitive component throws
  `JsonParseException` — which would quarantine the whole buttons file.
  The `ButtonEntry` wire record (all-nullable boxed fields) isolates
  this: one weird entry degrades to a dropped button, not a lost set.
- **26.3 API notes**: `Registry.get(Identifier)` now returns
  `Optional<Holder.Reference<T>>` (use `getOptional`); `Identifier.tryParse`
  for null-safe parsing; widgets render through
  `extractWidgetRenderState`; screens build layouts with
  `LinearLayout`/`FrameLayout` + `visitWidgets(addRenderableWidget)`
  (pattern taken from `ConfirmScreen`); `Button.Builder` defaults
  150×20; `CommonComponents.OPTION_ON`/`optionStatus(boolean)`.

### Design decisions

1. **Anchors relative to the container, not the screen** — buttons keep
   their place beside the GUI across container sizes (inventory vs
   chest), matching how NEU positions its buttons conceptually without
   copying its Hypixel-oriented machinery.
2. **Offset (0,0) is the flush corner inside the GUI; new buttons use
   (22, 2) beside it** — pure math stays trivially invertible for drag
   re-anchoring, and the default still lands buttons outside the GUI.
3. **Self-sanitizing record + `isUsable()` at the boundary** — partial
   data is representable (the edit form builds incomplete buttons);
   the store boundary decides what persists. This keeps one validation
   ruleset (`InventoryButton.validCommand`/`validIcon`) used by the
   form, the store and the activator.
4. **The activator as the single execution path** — loading, importing
   and editing are inert by construction (no sender reference), which
   is the strongest form of the "no command execution on import" rule.
5. **Edit mode is screen-scoped and keybind-toggled, not a config
   toggle** — a persisted edit flag would outlive its purpose and
   confuse players who forget it on.
6. **Two-click delete instead of a confirmation screen** — the same
   restraint pattern as the copy toast: no modal for a recoverable-by-
   recreation action.
7. **Unknown-but-well-formed icon ids are accepted** — they render as
   barrier with a tooltip warning, so a typo is visible and fixable
   instead of silently dropping a user's button.

### Tests performed

- `./gradlew build` green; **307 tests, 0 failures** (53 new: model
  normalization/validity 8, geometry 8, store round-trip/corruption/
  per-entry tolerance/duplicate ids 8, tooltips 2, hit testing 4,
  activator 6, drag session 8, coordinator CRUD/edit mode 5, plus the
  extended config structure suite covering the new category).
- The mixin targets (`extractContents`, `extractTooltip`,
  `mouseClicked(MouseButtonEvent, boolean)`, `mouseDragged`,
  `mouseReleased`, `removed`) were verified against the 26.3 mapped
  bytecode before writing the injections; compile + jar contents
  checked.
- In-game testing: **none.** Rendering, hover, tooltips, the edit
  screen and drag interaction need a live client.

### Outstanding problems

- Stage 4 leftovers (player hover cards, real-username display) remain
  blocked on in-game component capture.
- The edit screen has no automated tests (pure widget assembly); its
  validation logic lives in `InventoryButton`/`Identifier.tryParse`,
  which are covered.
- Share/import of button sets (`inven-` share type) is deferred to
  Stage 8 as planned; the store's per-entry policy is the foundation.

## 2026-11-09 (session 7) — Fix dev.5 launch crash: inherited @Shadow

### Incident

User's instance (EarthSMP profile, Modrinth App, MC 26.3 + loader 0.20.1)
crashed at launch with `dev.5` installed. The crash fired while the
*recruitmentaddon* mod's entrypoint loaded `ChatScreen`, but the failing
component was ZEA's `ChatScreenMixin`:

```
InvalidMixinException: @Shadow method
addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)
Lnet/minecraft/client/gui/components/events/GuiEventListener;
in zea.mixins.json:ChatScreenMixin from mod zea
was not located in the target class net.minecraft.client.gui.screens.ChatScreen.
```

### Root cause

Mixin resolves `@Shadow` (and injection) members only against the target
class's **own declared** members — never inherited ones.
`addRenderableWidget` is `protected` on `Screen`, not declared on
`ChatScreen` (verified by `javap` on the 26.3 mapped jar: exact generic
signature exists on `Screen`). The shadow therefore failed during mixin
apply, which killed the `ChatScreen` transformation, which aborted the
`client` entrypoint stage — for whichever mod loaded `ChatScreen` first.
Any later class load of `ChatScreen` would have crashed the game just the
same; recruitmentaddon merely got there first. No ZEA behavior was at
fault; the entrypoint failure attribution was collateral.

### Work done

- **Fix**: new `ScreenMixin` (targets `Screen`, where the method is
  declared) bridges `addRenderableWidget` through the
  `ScreenWidgetAccess` duck interface; `ChatScreenMixin` drops the
  unresolvable shadow and calls
  `((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(...)`.
  JVM superclass loading guarantees the augmented `Screen` is in place
  before any subclass code runs. The bridge delegates to the vanilla
  method unchanged, so widget registration keeps 26.3 semantics.
- **Regression test**: `MixinSurfaceTest` pins every mixin's target
  surface (all `@Shadow` fields/methods, all injection points, both
  `@WrapOperation` call targets) to members actually declared by the
  target classes — the test that would have caught this bug class at
  build time. Convention: a mixin change pins its new targets in the
  same commit.

### Research performed and findings

- Full re-audit of the other four mixins against the 26.3 mapped jar
  (javap): `ChatScreen` declares `input`/`init`/`keyPressed(KeyEvent)`/
  `removed`/`mouseClicked(MouseButtonEvent, boolean)`;
  `ChatComponent` declares `chatScrollbarPos`/`trimmedMessages`/
  `getScale`/`getWidth`/`addMessageToDisplayQueue`/`scrollChat`/
  `resetChatScroll`/`extractRenderState(…;DisplayMode;Z)V`/
  `lambda$extractRenderState$1` and `ChatGraphicsAccess.fill(IIIII)V`
  exists; `AbstractContainerScreen` declares every field and method
  `ContainerScreenMixin` touches. No other inherited-member lookups
  exist in ZEA's mixins — this was the only instance.
- `ButtonEditScreen` (a real `Screen` subclass) already used
  `this::addRenderableWidget` — compiling fine, because subclass code
  *may* call inherited protected members. The mixin author applied the
  same pattern where the lookup rules differ; that asymmetry was the
  trap.

### Tests performed

- `./gradlew build` green; **312 tests, 0 failures** (5 new
  `MixinSurfaceTest` methods).
- In-game testing: none available in this environment; the mixin surface
  is now statically pinned by the new test instead.

### Outstanding problems

- Unchanged from session 6 (Stage 4 leftovers, edit-screen test gap,
  Stage 8 share/import).

## 2026-11-09 (session 8) — Stage 7: persistent chat history

### Work done

Built out in five commits, each independently verifiable:

- `HistoryEntry` + `ServerIdentity` — the immutable recorded row
  (timestamp, category, plain text, formatted text) and the stable
  per-server key. Identity is the address the client actually dialed
  (lower-cased host, default port stripped), not the MOTD brand —
  `earthmc.net` and a direct node address key different files.
- History configuration — `history.enabled`, `history.size`
  (entries, default 5000), `history.retention` (ONE_DAY … FOREVER,
  default 30 days) grouped under `history` in the config screen.
- `ChatHistory` — the bounded in-memory buffer (lock-guarded, immutable
  snapshots) that already served Stage 4 search; session-only or
  persistent is purely a matter of who seeds and flushes it.
- `HistoryStore` — per-server JSON through the existing safe-write
  protocol (serialize in memory, round-trip verify, atomic replace,
  corrupt file quarantined as `*.corrupted`). Schema `{"version": 1,
  "entries": [{"t", "c", "p", "f"}]}`; per-entry tolerance mirrors
  inventory buttons — one hand-edited row never quarantines the file.
- `HistoryRecorder` — the session coordinator: seeds on join
  (asynchronous load), batches dirty writes at most every 30 s onto a
  bounded single-thread executor, re-enforces retention every 5 min
  (before the flush, so cleanup persists in the same write), flushes on
  disconnect and on client shutdown. Writes coalesce while one is in
  flight; a rejected submission is retried on a later tick.
  `ZeaClient` owns the executor (daemon thread, queue of 4) and drains
  it with a 5 s bounded wait on CLIENT_STOPPING.

Search needed no new work: Stage 4's `FeedFilter` already searches the
`ChatHistory` buffer, which persistence now seeds on join — so chat
search transparently covers previous sessions' messages.

### Threading decisions

- Recorder state (`dirty`, `writing`, `store`) is confined to the
  client thread; the executor only does file IO and marshals results
  back through `Minecraft::execute`.
- The load is submitted before any save can be (executor is
  sequential), so the first save always observes the file the load
  read — no clobber window.
- Configuration is sampled per session; toggling persistence or
  retention applies on the next join, never retroactively.

### Tests performed

- `./gradlew build` green; 356 tests, 0 failures (10 new
  `HistoryRecorderTest` methods: seeding, disabled persistence,
  flush interval, clean-history no-writes, in-flight coalescing,
  disconnect flush, retention schedule, rejected-write retry,
  per-server isolation, rejected load).
- Fixed three test bugs from the interrupted authoring session before
  landing: a self-contradictory assertion (pre-created file asserted
  absent — now asserts its content unchanged), a rejection list that
  rejected the join load instead of the save, and a deferring executor
  whose queued load/save tasks were never drained.
- In-game testing: none available in this environment. Mixin surface
  unchanged this stage, so no new bytecode risk.

### Outstanding problems

- Stage 4 leftovers and the edit-screen test gap are unchanged.
- Stage 8 (HelpChat sharing) is the last Phase 1 stage.

## 2026-11-09 (session 9) — Stage 8: HelpChat configuration sharing

### Work done

Five commits, each landing with its tests:

- `ShareType`/`ShareDocument` — the `{format, type, version, data}`
  envelope. Strict identity: the format marker must match, the type must
  be known, versions this build does not understand are rejected, never
  guessed; unknown envelope fields are ignored for forward compatibility.
- `PasteKey` — accepts a bare key or a paste URL, but only
  `https://paste.helpcha.at`, only one key segment (with optional
  presentation extension like `.java`), no queries or fragments. The
  key charset `[A-Za-z0-9_-]` keeps every traversal shape out before
  any URL is built.
- `PasteService` — HTTP client on an injected bounded executor.
  POST `text/plain; charset=utf-8` to `/documents`, validate the
  returned key; GET `/raw/{key}` with a 1 MB cap (bytes buffered, then
  decoded once — chunk-wise UTF-8 decoding can split a character).
  Redirects are never followed. Every failure is a player-readable
  `ShareException`.
- Payload codecs — `InventoryButtonsPayload` (wire shape mirrors the
  button store's file, but imports validate strictly: unusable entry,
  duplicated identity or >100 buttons rejects the whole import) and
  `GeneralConfigPayload` (allowlist of behavior settings; history and
  internals excluded by construction; absent fields keep the current
  value; validate-everything-then-apply).
- Wiring — `ShareCoordinator` (exports round-trip their own document
  before uploading; imports hold as pending until confirmed; futures
  marshal to the client thread), `/zea share|import` commands, the
  `ZEA-Share` executor closed on shutdown, and `applyImport` on the
  buttons facade.

### Research performed and findings

- fabric-command-api-v2 3.1.2 (the version fabric-api 0.165.2 resolves)
  has no `ClientCommandManager`; the entry points are
  `ClientCommands.literal/argument` and the callback's second parameter
  is a `CommandBuildContext`. Pinned by compiling against them.
- Brigadier's `getRelevantNodes` prefers an exactly-matching literal
  child over a sibling argument, so `/zea import confirm` coexists with
  the greedy paste argument; a paste key that literally spells "confirm"
  is still importable by pasting the full URL.
- MC 26.3 feedback surface: `Hud.getChat()` → `ChatComponent
  .addClientSystemMessage` exists, but `FabricClientCommandSource
  .sendFeedback/sendError` is the idiomatic path and needs no direct
  chat access — used instead.

### Tests performed

- `./gradlew build` green; 443 tests, 0 failures. New: 15 envelope, 18
  paste key, 15 paste client (mock HTTP server — no test touches
  paste.helpcha.at), 14 button payload, 12 general payload (including
  the allowlist exclusion test: an export with history configured must
  not contain the word "history"), 14 coordinator (fake transport).
- Test bugs fixed before landing: a self-contradictory URL assertion,
  fixtures missing their icon field, and a text block whose JSON escapes
  were processed at the Java level instead of the JSON level.
- In-game testing: none available in this environment. Live protocol
  verification against the real paste service remains blocked by the
  NXDOMAIN from here — mock-tested only, as documented.

### Outstanding problems

- Live HelpChat verification (needs a network that resolves
  paste.helpcha.at, ideally an in-game pass).
- Stage 4 leftovers (nickname username display depends on data EarthMC's
  API does not expose) and the inventory edit-screen test gap remain.
- Phase 1 stages are now all implemented to the unit-tested level; the
  remaining Phase 1 work is in-game verification of the whole build.

## 2026-11-09 (session 10) — Tagged channel classifiers; edit form extraction

### Work done

Three commits:

**Add local, trade, nation and town chat classifiers** — closing the last
Stage 2 parser gap:

- `TaggedChannelChat` — payload for player chat on the tagged channels:
  bracket label, the sender's town (nation chat only — observed
  `[Nation Chat|Sosnovka]`, where the nation is implicit because only
  nation members can write there), display name, content.
- `TaggedChannelChatClassifier` — exact bracket label opening the
  message: `Local`, `Trade`, `Town Chat`, and `Nation Chat|<town>`
  (town required, non-blank, bounded). Case-sensitive; declined shapes:
  non-leading brackets, missing space, missing `": "`, bracketed names,
  the unobserved town-less nation form. Runs before the global
  classifier, which would otherwise read the bracket as an affiliation —
  the exact misroute risk this session's doc previously tracked.
- `MentionDetector` — structured attribution for the new payload, same
  path as global chat; the unstructured fallback stays for messages the
  classifiers do not recognize.
- Fixtures: local, trade, nation, town entries; docs updated
  (EARTHMC_MESSAGE_FORMATS.md records the observed legacy codes,
  CHAT_CLASSIFICATION.md the parse and the residual ambiguity).

**Extract the button edit form's decisions** — closing the edit-screen
test gap from session 6:

- `ButtonEditForm` — the edit form's logic without any Screen
  machinery: applying field values, validation in a fixed order
  (command before icon, first failure wins, one inline error at a
  time), the anchor/enabled mutations of the working copy, and the
  two-click delete confirmation. A refused save leaves the working
  copy untouched — the text fields stay the source of truth.
- `ButtonEditScreen` — now widget assembly and wiring only,
  delegating every decision to the form. The refusal reason carries
  its translation-key suffix explicitly (`Rejection.key()`), so the
  lang keys and the enum cannot drift apart silently.

**Reindent remaining files to tabs** — finishing the migration the
earlier `Reindent ZeaClient to tabs` commit started. 63 files, no
content changes: the conversion is text-block-aware (leading whitespace
inside a text block is string data and was left byte-identical — the
share-document tests that compare exact JSON confirm it).

### Research performed and findings

- The observed samples for all four channels were in the project brief
  all along (legacy-coded in-game logs) — the "no observed samples"
  blocker was a bookkeeping gap, not a data gap. The nation-chat bracket
  carries the sender's town, not the nation; trade's label is `Trade`
  (not "Trade Chat", which the brief's prose typo suggested) and town's
  is `Town Chat`, per the legacy strings.
- Residual ambiguity accepted and documented: a nation or town actually
  named "Local"/"Trade"/"Town Chat" would be claimed by the tag
  classifier before global. EarthMC names are geographic; the tags are
  data in ZEA-REPO so a wrong tag is correctable without a mod release.

### Tests performed

- `./gradlew build` green after each commit; 478 tests, 0 failures
  (was 443) — the count unchanged through the reindent, which is the
  verification that no string content moved. New: 15 classifier tests
  (observed shapes, town handling, declines, pipeline ordering), 8
  fixture assertions (4 families ×
  category + original retention), 2 mention tests (structured
  attribution, self-echo), 10 edit-form tests (validation order,
  refusal reasons, untouched working copy after refusal, slash
  stripping, anchor/enabled mutations, two-click delete, key/enum
  agreement).
- In-game testing: none available in this environment. Text-anchored
  parsing only; component-level structure remains unverified pending
  in-game capture. The edit screen's widget assembly remains
  untested; its decisions no longer are.

### Outstanding problems

- Component-level fixtures for every family (unchanged).
- Nation chat without a town is unobserved; if it exists it currently
  falls through to the global classifier's single-segment handling.
- Stage 4 leftovers (nickname username display) remain. Live HelpChat
  verification still blocked (paste.helpcha.at does not resolve from
  this environment).

## 2026-11-09 (session 11) — Chat component capture

### Work done

Two commits:

**Remove a dead duplicate of the shutdown path** — `ZeaClient` carried
both `shutdownBackgroundWork` (registered on CLIENT_STOPPING) and an
older private `shutdownHistory` that nothing called; the dead one goes.

**Add chat component capture** — the tool every "pending in-game
capture" item was waiting for:

- `ComponentJson` — a component tree to its vanilla JSON form through
  the shared component codec with plain JSON ops (no registry access).
  Wire form: no client-only style insertion; item/entity hovers cannot
  encode without registries and surface as errors instead of crashes.
- `CaptureRecorder` — explicit-request, bounded sessions (100 entries,
  auto-stop with chat feedback), timestamped files under
  `zea/capture/`, intermediate flushes every 25 entries through the
  safe-write protocol, final write always attempted, disconnect only
  flushes (a session spans rejoins), client shutdown ends it. Same
  threading contract as the history recorder: client-thread state, IO
  on a sequential executor, completion marshaled back.
- `/zea capture on|off|status` — `on` refuses off EarthMC; bare
  `/zea capture` is `status`. The `/zea` root is now assembled once in
  the entrypoint (`ShareCommands.register(root, …)` /
  `CaptureCommands.register(root, …)` contribute branches) instead of
  each subsystem registering its own root — this also fixes a trap the
  refactor exposed: the original tree kept `import` a sibling of
  `share`, which a naive branch extraction would have nested.
- File schema: `zea-capture` envelope (format + version, like the
  share documents), entries carrying timestamp, category, plain text,
  legacy codes, the component JSON, or the serialization error.
- `CaptureFixtures` (test side) loads capture files back into live
  components through the same codec — an in-game capture drops
  straight into a regression test.
- `VanillaBootstrap` (test side) — the bare test JVM must run
  `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` before
  anything touches `BuiltInRegistries`; the component codec's static
  chain (Style → ClickEvent → SHOW_DIALOG → Dialog → registries) fails
  without it. Found by the round-trip tests, pinned by them.

### Research performed and findings

- `ComponentSerialization.CODEC` is a plain `Codec<Component>` usable
  with `JsonOps.INSTANCE` — no `HolderLookup.Provider` needed for
  text/style/hover-text/click components. Verified by javap against the
  mapped 26.3 jar before writing code, then empirically by the tests.
- Brigadier merges same-name roots at the dispatcher level, but two
  registration callbacks each owning `zea` would be structural
  ambiguity all the same; one root owner is the honest shape.

### Tests performed

- `./gradlew build` green; 488 tests, 0 failures (was 478). New: 2
  component round-trips (literal; styled with hover SHOW_TEXT, click
  RUN_COMMAND, colored sibling), 7 recorder tests (inactive by default,
  stamped-file recording, blank skipping, cap auto-stop with feedback
  and post-stop silence, error-keeping entries, same-second file
  separation, interval flushes mid-session), 1 fixture loop test
  (record → stop → load → component, style, hover and click intact).
- In-game testing: none available in this environment. The capture
  command paths and file writes are unit-tested; the first real
  capture happens on EarthMC.

### Outstanding problems

- The first real capture session (needs EarthMC) — then component
  fixtures, badge hover verification and the nation-chat-without-town
  question all become answerable from data.
- Stage 4 leftovers (nickname username display) and live HelpChat
  verification remain blocked as before.

## 2026-11-09 — dev.16 launch crash: duck interface inside the mixin package

### What happened

Live test report from the player: dev.16 crashes during startup, before
the title screen. Log signature (three identical uploads, one session):

```
net.fabricmc.loader.impl.FormattedException:
  java.lang.RuntimeException: Mixin transformation of
  net.zaminmc.zea.mixin.ScreenWidgetAccess failed
Caused by: org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError:
  net.zaminmc.zea.mixin.ScreenWidgetAccess is in a defined mixin package
  net.zaminmc.zea.mixin.* owned by zea.mixins.json and cannot be referenced directly
```

The first failure fires while populating the crash report itself
(`YumiEvents.<clinit>` → classloading cascade), the fatal one at
`Main.main:251` — every path resolves to the same missing class.

### Root cause

`ScreenWidgetAccess` — the duck interface `ScreenMixin` adds to `Screen`
so screen-targeting mixins can call `addRenderableWidget` (built after
the dev.5 crash) — lived in the mixin package. Mixin semantics, verified
by disassembling `sponge-mixin-0.17.5+mixin.0.8.7` (Fabric's fork):

- `MixinProcessor.applyMixins` owns every class under a config's
  package. A classload of any of them either gets the accessor
  carve-out or throws `IllegalClassLoadError`.
- The carve-out lives in `MixinCoprocessorAccessor`: it passes through
  **registered accessor mixins only** (`@Mixin` interfaces whose members
  are all `@Accessor`/`@Invoker`, like `ChatComponentAccessor`).
- `ScreenWidgetAccess` is a plain duck interface: unregistered, not
  `@Mixin`. It is merged into the transformed `Screen`'s interface list,
  and the JVM resolves a class's superinterfaces eagerly at define time.
  So the first load of `Screen` — deep in startup — tried to load the
  interface, Mixin refused, game over. Every build carrying it was
  unlaunchable in production; nothing before dev.15 shipped it to a
  real client.

Why the dev environment never saw it: the dev classpath loads mod
classes through a different code path, and the unit tests never define
the transformed `Screen`.

### Fix

- `ScreenWidgetAccess` moved to the new `net.zaminmc.zea.duck` package,
  outside Mixin's ownership. `ScreenMixin` (implements) and
  `ChatScreenMixin` (two casts) now import it from there. The duck
  interface's javadoc states the placement rule and points at the
  hygiene test.
- `MixinPackageHygieneTest` scans the compiled production classes with
  ASM and turns both loadability rules into build failures:
  1. every class under `net/zaminmc/zea/mixin/` must be registered in
     `zea.mixins.json` (stray classes — including nested ones — are
     unloadable in production);
  2. any bytecode reference to a mixin-package class must target a
     registered accessor mixin (interface, `@Mixin`, members all
     `@Accessor`/`@Invoker`).
  Detail that cost a debugging round: Mixin's annotations have CLASS
  retention, so ASM reports them as *invisible* annotations
  (`RetentionPolicy.CLASS`), not visible ones.

### Tests performed

- `./gradlew test` green; 490 tests, 0 failures (was 488). The two new
  hygiene tests pass against the fixed tree; rule 2 was observed firing
  (correctly flagging the shape) during development before the fix
  landed.
- Jar verification: `net/zaminmc/zea/duck/ScreenWidgetAccess.class`
  present, `mixin/ScreenWidgetAccess` gone, `ScreenMixin.class`
  references the new location.
- In-game testing: none available in this environment. The fix is a
  bytecode-level constraint, so the launch crash is structurally
  impossible now; the player's next dev-build run confirms it.

### Outstanding problems

- The untracked multi-send interaction layer (`MultiSendInteraction`
  policy class) remains to be wired into the tab bar and chat screen —
  continuing next.

## 2026-11-09 — Stage 3 completion: multi-send interaction layer

### Context

The multi-send backend existed end to end since Stage 3's core work —
`SendTargets`, `OutgoingRouter`, the staggered `MultiChannelDispatcher`,
`sendToTargets` on `ChatSubsystem` — but had no callers: no way to select
targets, no way for a typed message to become a dispatch. This closes
that gap; it is the actual last code item of Stage 3's chat-surface plan.

### Files changed

- `MultiSendInteraction` (chat/feeds, new) — the pure interaction
  policy: which feeds can become targets (channel-backed only), when a
  chat send becomes a multi-target dispatch (plain text, non-blank,
  enabled, targets selected — slash commands stay vanilla because a
  player typing a command means the command), and which tabs render as
  targeted.
- `ChatSubsystem` — owns the session's `SendTargets` (cleared on
  disconnect next to the other display resets); the dispatch listener
  became `DispatchFeedback`: failures toast in addition to logging.
- `ChatTabBar` — shift-click toggles a tab's channel as a send target;
  targeted tabs render an aqua label with a thin marker strip so
  selected-and-targeted stays unambiguous. Narration marks targeted
  tabs.
- `MultiSendToast` (chat/render, new) — all multi-send feedback is
  toast-based: the chat HUD is feed-filtered, so a client system
  message would be invisible exactly when a channel tab is selected —
  the multi-send situation.
- `ChatScreenMixin` — wires the tab bar to the policy and intercepts
  `handleChatInput` at HEAD: normalized text enters up-arrow recall,
  the batch dispatches, and cancelling preserves vanilla's
  close-on-submit/clear behavior (verified against the 26.3 bytecode:
  the caller performs both after `handleChatInput` returns).

### Tests performed

- `./gradlew build` green; 498 tests, 0 failures (was 490). New:
  `MultiSendInteractionTest` (8 tests) — channel-feed to write-channel
  mapping across the whole `Channels` table, read-only feeds refusing
  without disturbing the selection, disabled-flag refusal, add/remove
  toggling, interception gating (blank, null, slash, disabled, empty),
  targeted rendering state.
- In-game testing: none available in this environment; the round is
  specified in TESTING.md for the next dev-build run.

### Outstanding problems

- In-game verification of the whole chat surface (tabs, search, copy,
  multi-send) still pending the player's next session — dev.17 carries
  the launch-crash fix, so a run is finally possible again.
## 2026-11-09 — dev.18 launch crash: the mention-highlight wrap handler layout

### What happened

Live test report from the player: dev.18 crashes during startup, before
the title screen — this time past the point where dev.16 died (`ZEA
loading` appears; the duck-interface fix held). Log signature:

```
Mixin apply for mod zea failed zea.mixins.json:MentionHighlightMixin
  -> net.minecraft.client.gui.components.ChatComponent:
  InvalidInjectionException @WrapOperation operation wrapper method
  ChatComponent::zea$tintMentionedLine has an invalid signature.
  Found unexpected argument type int at index 0, expected
  ChatComponent$ChatGraphicsAccess.
```

MixinExtras printed both descriptors: the built handler put the
enclosing lambda's arguments first and `Operation` last; the expected
layout is the opposite. `ChatComponentMixin`'s two wraps applied cleanly
in the same log — both take only receiver + wrapped arguments, no
enclosing capture — which isolated the failure to exactly that layout
question.

### Root cause

Researched before touching anything, from three primary sources that
all agree:

1. **The MixinExtras wiki** — the handler receives the wrapped
   operation's receiver, then its arguments, then the `Operation`, then
   *optionally* a prefix of the enclosing method's arguments at the
   end.
2. **The shipped MixinExtras 0.5.5, decompiled**
   (`WrapOperationInjector#invokeHandler`) — it pushes
   `methodArgs[0..originalArgs.length]` (the receiver at index 0 for a
   non-static target, per `getEffectiveArgTypes`), then the generated
   `Operation`, then `target.arguments[0..captureTargetArgs]` as a
   captured prefix. And `OperationUtils#generateSyntheticBridge` runs
   `checkArgumentCount(args, argTypes.length, …)` — the receiver counts
   as a call argument — before re-executing the original invocation.
3. **The mapped 26.3 jar** — the line-background lambda compiles as
   `private static void lambda$extractRenderState$1(int, int,
   ChatGraphicsAccess, int, float, GuiMessage.Line, int, float)`, and
   its `fill` call site passes `aload_2` (the lambda's
   `ChatGraphicsAccess` parameter) as the receiver.

So the old handler had two defects, one visible and one latent: the
argument order was wrong (rejected at apply time), and
`original.call(x0, y0, x1, y1, color)` omitted the receiver — which the
generated bridge would have rejected at runtime with
`IncorrectArgumentCountException` even if the apply had succeeded.

Why no test caught it: MixinExtras validates handler signatures only
when the mixin *applies* to a target class, and unit builds never apply
mixins. The compiled-classes checks (`MixinSurfaceTest`,
`MixinPackageHygieneTest`) verify targets and registration, not the
handler contract.

### Fix

- `MentionHighlightMixin` — the handler now reads
  `(graphics, x0, y0, x1, y1, color, Operation, chatBottom,
  entryHeight, lineGraphics, maxWidth, backgroundOpacity, line,
  lineIndex, alpha)` and calls `original.call(graphics, x0, y0, x1, y1,
  color)`. The compiled descriptor was verified byte-for-byte against
  the one MixinExtras printed in the crash log. Behavior is unchanged:
  tint on top of vanilla's fill, keyed on the lambda's `GuiMessage.Line`
  — the same instance the mention detector marked.
- `WrapOperationSignatureTest` (new) — scans the compiled mixin classes
  with ASM, finds every `@WrapOperation` handler, and judges each
  against the mapped jar: the receiver + wrapped arguments + `Operation`
  prefix, the enclosing-method prefix behind it, static-ness, return
  type, and that the `original.call` array receives exactly the receiver
  plus the wrapped arguments (counted through the `ANewArray`/
  `AAStore` sequence). A wrong layout now fails `./gradlew build`
  instead of the player's game.

While in there, the remaining mixin targets (`Screen`, `ChatScreen`,
`AbstractContainerScreen`) were re-audited against the same 26.3
bytecode — targets and shadows all match; this handler was the only
defect in the mixin surface.

### Tests performed

- `./gradlew build` green; 499 tests, 0 failures (was 498). The new
  signature test passes against the fixed tree; during development it
  correctly flagged a handler-shape question (full-descriptor method
  specs like `extractRenderState(...)V` needed the return type kept in
  the enclosing-method resolution) before going green.
- `javap` on the built class: the `zea$tintMentionedLine` descriptor is
  character-identical to the crash log's expected descriptor.
- In-game testing: none available in this environment. The mixin now
  satisfies the exact contract the runtime enforces at apply time — the
  player's next dev-build run confirms it end to end.

### Outstanding problems

- In-game verification of the whole chat surface (tabs, search, copy,
  multi-send, mention tint) still pending the player's next session —
  dev.19 carries this fix.
- The 239-mod environment co-targets `ChatComponent` (chatting,
  OneConfig's chat events). MixinExtras chains wraps through the
  `Operation` interface, so coexistence should be structural; the
  player's log will tell.

## 2026-11-09 — Paste service: live verification and the real hostname

### What happened

The share backend had never touched the real service — the development
sandbox could not resolve the hostname (NXDOMAIN, recorded in
docs/PASTE_SERVICE.md as pending verification). A manual
`live checks` workflow (`.github/workflows/live-checks.yml`, dispatch
only) was added to run environment-blocked verifications from GitHub's
runners — and it failed too: `paste.helpcha.at` does not resolve from
public resolvers (Google, Cloudflare) or CI runners either.

The spec's hostname was wrong, not the network. HelpChat's domain is
the short form `helpch.at`; the paste service lives at
`paste.helpch.at`, and it answers the exact protocol ZEA implements:

- `POST /documents` with `Content-Type: text/plain; charset=utf-8` and
  the document as the body → `200` with `{"key":"..."}`; keys observed
  (10 chars, alphanumeric) match the `[A-Za-z0-9_-]+` whitelist.
- `GET /raw/{key}` → direct `200` (no redirect),
  `text/plain; charset=UTF-8`, byte-identical round-trip including
  multibyte (`café ☃ — ünïcode ✓` in a real `zea-share` envelope).
- Presentation URL `/{key}` and the `.java` extension form both answer
  `200` — the extension really is presentation-only.

All verified by hand from the sandbox (the domain resolves fine here —
only the old spelling didn't) and independently by the live-checks
workflow's green run.

### Fix

`PasteKey.HOST` is now `paste.helpch.at` (BASE_URL derives), with the
hostname spelled out in code, tests, docs and the workflow. The worklog
entries mentioning the old spelling stand as history.

### Outstanding problems

- None for the protocol. In-game sharing still wants one live pass
  (upload from the game, import on another client) with the next
  dev-build run.

## 2026-11-09 — The tab strip stays inside the chat panel

### Context

Tracked cosmetic defect since the tab bar landed: the strip could
extend past the chat panel's right edge — read, at the time, as "uses
the unscaled chat width; visible when chat scale is below 1".

### Root cause — and a correction

The 26.3 bytecode settles the actual geometry, and the old note had it
backwards. `ChatComponent.getWidth()` is
`floor(chatWidthOption * 280 + 40)` and the renderer works in scaled
coordinates (`pose.scale(scale)`, layout widths `ceil(getWidth() /
scale)`), so the panel's **on-screen** width is `getWidth()` —
independent of chat scale. Smaller chat scale shrinks the glyphs and
re-wraps the lines, not the panel box. The strip, meanwhile, was
bounded only by the screen width, so it overhung the panel whenever the
enabled tabs were wider than the player's chat width setting (default
panel 320 px; all extra feeds enabled ≈ 500 px — reachable at any
scale).

### Fix

- `TabLayout` (new, `chat/render`) — the strip's layout policy as a
  pure computation: natural fit → padding compressed toward a 1 px
  floor → labels shortened one character at a time, widest tab first,
  with an ellipsis, never below one character. If even that cannot
  fit, the minimum layout overflows rather than dropping tabs: drawn
  extent always equals hit-test extent, so no tab can ever become dead
  pixels.
- `ChatTabBar` — consumes the layout; a shortened label gets the full
  one as a hover tooltip (narration already carried it).
- `ChatScreenMixin` — the strip's max width is
  `min(screen.width - 4, ChatComponent.getWidth(chatWidthOption))`,
  vanilla's own public static, so the strip is clamped to the panel's
  actual on-screen width by construction. The search field stays
  full-width deliberately: it is a text-entry affordance, matching the
  vanilla input strip below it.

### Tests performed

- `./gradlew build` green; 508 tests, 0 failures (was 499).
  `TabLayoutTest` (9 tests) pins the ladder: natural fit, padding
  compression before shortening (including Java's truncating division
  at the floor), longest-first shortening with the ellipsis cost, the
  one-character floor, single-character labels never shortened, empty
  and single-tab edges, and the documented overflow.
- In-game verification pending the next dev-build run (a cosmetic
  render fix; the logic is pure and pinned).

### Outstanding problems

- In-game verification of the whole chat surface (tabs, search, copy,
  multi-send, mention tint) still pending the player's next session —
  dev.21 carries this and the paste hostname fix.

## 2026-11-10 — The rejoin disconnect, the import ambiguity, and the /zea menu

### What happened

Three live reports from the player on dev.19+:

1. Joining EarthMC disconnects immediately with "Network Protocol
   Error". Log signature: `Connected to EarthMC; EarthMC features
   enabled`, then while handling the first `ClientboundSystemChatPacket`
   — `Cannot invoke "java.util.function.Predicate.test(Object)"
   because "this.visibleMessageFilter" is null` at
   `ChatComponent.addMessage:295`.
2. Brigadier warnings on every registration:
   `Ambiguity: [zea, import, confirm] vs [zea, import, paste]` (and the
   same for `cancel`).
3. `/zea` "does not open maulconfig menu at all" — reported twice.

All three were investigated to a proven cause before any change.

### Root cause

**The rejoin disconnect** — a ZEA defect since the feed-filtering
commit (dev.1), bytecode-verified against the mapped 26.3 jar:

- Vanilla initializes `visibleMessageFilter` in the `ChatComponent`
  constructor to a non-null always-true lambda; its only other setter
  caller (`LocalPlayer`, fed by `ChatAbilities`) also passes non-null.
  The field is never null in vanilla — and `addMessage` (and the
  setter's internal `refreshTrimmedMessages`) call `.test` on it with
  no null check.
- `FeedFilter.apply()` passed `currentFilter()` straight through, and
  that is null whenever nothing filters (All tab, no search).
- The silent detonation path: at disconnect, vanilla's
  `Hud.onDisconnected()` clears the message list *before* ZEA's
  disconnect reset installs the null, so the refresh loop (which would
  otherwise NPE immediately) iterates nothing — the null lands quietly.
  The `ChatComponent` lives on the `Gui`'s hud, which is created once
  per client launch and survives server switches, so the null persists
  into the next session; EarthMC's first welcome message then throws
  inside the packet handler and the connection drops. Mid-session the
  same defect could fire by clicking the All tab or closing a search
  with messages on screen — NPE inside the click handler.
- Not another mod, not MC: only an explicit
  `setVisibleMessageFilter(null)` can produce the state, and ZEA is the
  only caller. Other mods never enter the picture.

**The import ambiguity** — cosmetic, but real noise. Brigadier's
`findAmbiguities` feeds each sibling's examples to the others'
`isValidInput`; a literal's example ("confirm") is valid input for a
greedy string, hence the warnings. Parse-time was never affected
(`getRelevantNodes` short-circuits to an exactly-matching literal), so
`/zea import confirm` already routed correctly — the scan just didn't
know the literals own those words.

**The /zea menu** — never wired. The root registered only `share`,
`import` and `capture`; no code anywhere opened a MoulConfig GUI (the
config side was ready: annotations pinned by tests, `saveNow()` wired
to the safe-write store — the entrypoint just never asked for a
screen).

### Fix

- `FeedFilter` — `apply()` now installs `installableFilter()`: the
  composed feed+search predicate, or a static always-true predicate
  when nothing filters. Identical to vanilla's own default state; no
  call site changed; `currentFilter()` keeps its null semantics for
  callers that only want to know *whether* something filters.
- `ImportPasteArgument` (new, `share` package) — the paste operand,
  greedy as before, rejecting exactly the reserved words `confirm` and
  `cancel` so the ambiguity scan agrees with the parser. A paste key
  that spelled "confirm" remains reachable via the full URL, exactly
  as the javadoc always documented. `ShareCommands` uses it;
  `beginImport` reads the operand through `context.getArgument`.
- `ConfigScreen` (new, `config` package) — opens the MoulConfig editor
  (`MoulConfigProcessor` → `MoulConfigEditor` → `GuiElementComponent` →
  `GuiContext` → `MoulConfigScreenComponent`), with `minecraft.gui`
  screen accessors per the 26.3 API (the screen moved from
  `Minecraft` to `Gui` this version). The `/zea` root gained the
  executor that calls it; the three subcommands are untouched and bare
  `/zea` now opens the menu. Works on any server; closing returns to
  the previous screen; saves go through the existing safe-write path.

### Tests performed

- `./gradlew build` green; **518 tests, 0 failures** (was 499).
- `FeedFilterTest` — three new tests pin that the *installed* filter is
  never null: All tab, blank search on All (both formerly the crash
  paths), and composed feed+search still filtering when it should.
- `ImportPasteArgumentTest` (new, 7 tests) — parse accepts keys, URLs,
  multi-word text and empty input like a greedy string; rejects
  exactly `confirm`/`cancel` and nothing else; examples are a key and
  a URL; the *real* `ShareCommands` tree scans clean of ambiguities;
  `zea import confirm`/`cancel` still route to the literals and
  everything else still reaches the paste operand.
- Counterfactual, run manually during development: reverting the tree
  to `StringArgumentType.greedyString()` makes
  `theImportTreeScansCleanOfAmbiguities` fail with exactly the
  player's reported pairs — the regression test has teeth.
- In-game testing: none available in this environment. The filter fix
  is exercised only by the next live session; the MoulConfig screen is
  likewise compile-verified against the bundled 4.8.0 jar but has
  never rendered in this environment.

### Outstanding problems

- Live verification of all three fixes pending the player's next
  session — dev.23 carries them. The rejoin sequence to test: join
  EarthMC, play, disconnect, rejoin from the multiplayer screen
  without restarting the client (the exact path that crashed), plus
  tab switches and search open/close mid-chat.
- If the 239-mod pack contains another `setVisibleMessageFilter`
  caller, it and ZEA will overwrite each other's filter on their own
  triggers — pre-existing interplay, unchanged by this fix, but worth
  remembering if tab filtering ever looks wrong after a config save.

## 2026-11-10 (session 2) — The first live tab test: null cache, the ingest gate, and the rebuild glitches

### What happened

The player's first live test of the tab system on dev.23 (the tab UI
itself had never been exercised in-game before — every prior release
note said "pending the next session"):

1. Chat tabs appear correctly on join.
2. Switching tabs causes visual glitches.
3. Switching tabs can disconnect the player ("Network Protocol
   Error").
4. The chat filtering system does not work as intended.

Two stack traces from the live log:

- Click path: `ChatTabBar.onClick` → the ChatScreenMixin select
  lambda → `FeedFilter.apply` → `ChatComponent.setVisibleMessageFilter`
  → `refreshTrimmedMessages` → `FeedFilter.lambda$feedPredicate$0:98` →
  NPE: `this.cache` is null in the feed predicate.
- Packet path: `ClientPacketListener.handleSystemChat:1057` →
  `ChatListener.handleSystemMessage:242` → `addServerSystemMessage:284`
  → `ChatComponent.addMessage:295` → the same NPE — the disconnect.

### Root cause

Three defects, one shared trigger, each proven against the mapped 26.3
bytecode before any change was made.

**The null cache** — a construction-order bug, present since the
feed-filtering commit, deterministic on every launch:

- `ChatSubsystem` declared
  `private final FeedFilter feedFilter = new FeedFilter(classifications(), feedSelection);`
  as a *field initializer*, while `classifications` was assigned only
  in the *constructor body*. Java runs field initializers before the
  constructor body, so `classifications()` returned null, and the
  singleton `FeedFilter` was built with a null cache — permanently, not
  as a race or a reload artifact (init is single-threaded; the field is
  final; nothing recreates it).
- The crash mechanics, from the bytecode: the predicate *lambda* merely
  captures the null `cache`; nothing dereferences it until vanilla
  tests the filter. `setVisibleMessageFilter` assigns the field *then*
  calls `refreshTrimmedMessages` (line 105 → 106), so the broken
  predicate is already installed when the first test throws.
- Not another mod: no line of the stack leaves ZEA, and only ZEA
  constructs this filter. The 239-mod pack (OneConfig, MoreChatHistory,
  Chatting) is not in the path.

**The ingest gate** — the architecture rested on a false assumption
about vanilla semantics:

- `FeedFilter`'s contract said "filtering is visual only — every
  message stays in the chat history, so switching back to All shows
  them again." Vanilla 26.3's `addMessage` makes that impossible as
  written: it tests the filter at line 295 and *returns* on reject at
  line 300 — before `logChatMessage`, before
  `addMessageToDisplayQueue` (the display list), and crucially before
  `addMessageToQueue` (the `allMessages` history). A rejected message
  is not hidden; it is *destroyed*.
- Consequence with a *working* cache: every message arriving while a
  channel tab (or a search) is active would be dropped from the chat
  forever — switching back to All could not restore what was never
  stored. This is live report #4, independent of the crash.

**The rebuild glitches** — two separate mechanisms, both verified:

- `refreshTrimmedMessages` clears `trimmedMessages` (line 389) *then*
  tests each stored message (line 391). With the null cache the first
  test throws, so the click left the visible chat **empty** — the
  "visual glitch" — while the broken predicate stayed installed for
  the next packet to trip over (the disconnect).
- Even with the NPE fixed, two vanilla behaviors made tab switching
  visually wrong, because a bulk rebuild is not an append:
  `addMessageToDisplayQueue` compensates the scroll anchor one notch
  *per line* while the chat is open and scrolled up — across a whole
  rebuild the anchor inflates until it clamps against the new
  (shorter) list, jumping the view toward the oldest messages; and the
  smooth-chat hooks (`zea$animateNewMessage`, `zea$animateScroll`)
  treated every rebuilt message as *new*, so the chat would glide on
  every tab switch (smooth chat is on by default).

### Fix

Lifecycle ownership, not null checks:

- `ChatSubsystem` — the filter is constructed in the constructor body,
  after the cache exists. `FeedFilter` — the constructor now fails fast
  on a null cache or selection (`Objects.requireNonNull`), so a future
  wiring mistake dies at startup, where it is debuggable, instead of
  inside a packet handler. The fail-fast follows the same convention as
  `ClassificationCache`'s own constructor.
- `FeedFilter.Installed` (new, nested) — everything ZEA installs as
  the vanilla filter is now a delegating wrapper recognizably ZEA's
  own. Behavior is unchanged; the marker exists so the retention wrap
  can distinguish ZEA's filters from vanilla's chat-trust filter and
  other mods'.
- `ChatComponentMixin` — three additions:
  - A `@WrapOperation` on the `Predicate.test` call inside `addMessage`:
    when the installed filter is ZEA's and rejects a message, the
    handler stores it through the shadowed `addMessageToQueue` before
    vanilla drops it — hidden from the display list now, retained in
    `allMessages` for every other feed and the All aggregate. Any other
    filter is a pure passthrough (vanilla and other mods keep their
    drop semantics byte-for-byte). This restores the documented tab
    contract — and equally fixes search, which had the same latent
    data loss.
  - A `refreshTrimmedMessages` bracket: remember the scroll anchor at
    HEAD; at TAIL restore it and re-clamp through vanilla's own
    `scrollChat(0)` (which also clears `newMessageSinceScroll` when it
    clamps to zero). EarthMC-gated: off EarthMC, vanilla's own rare
    filter toggles keep their behavior untouched.
  - A `zea$inRefresh` flag guarding the smooth-chat hooks, so a
    rebuilt message never counts as new or as scrolling. The flag is
    also cleared by `resetChatScroll` (which never runs inside a
    rebuild) so a third-party filter that throws mid-rebuild can only
    disable animations until the next successful refresh or screen
    close — never correctness.

### Tests performed

- `./gradlew build` green; **522 tests, 0 failures** (was 518).
- `ChatSubsystemWiringTest` (new) — reads the *compiled* constructor
  with ASM (ChatSubsystem cannot be constructed in a test JVM: it
  registers Fabric events and binds Minecraft) and pins that no read of
  the classification cache — direct or through the accessor — precedes
  its assignment. Counterfactual, run manually during development:
  restoring the dev.23 field initializer makes this test fail with
  exactly the diagnosis above. The bug class is now a build failure,
  not a live disconnect.
- `FeedFilterTest` — three new tests: the constructor rejects null
  cache and null selection; every filter state (All, blank search,
  feed selected, feed+search) installs an `Installed` predicate; the
  wrapper delegates to the composed filter (feed+search semantics
  through `installableFilter()`).
- `WrapOperationSignatureTest` — automatically picked up the new
  retention handler and validated its signature against the mapped jar;
  it caught a malformed `@At` target during development (a stray colon
  in the member spec — field syntax in a method target) before it
  could reach a game. The existing tests kept their coverage of the
  two older wraps.
- `MixinSurfaceTest` — pins the newly targeted members
  (`addMessage`, `addMessageToQueue`, `refreshTrimmedMessages`,
  `newMessageSinceScroll`) as declared by `ChatComponent` in 26.3, per
  the file's own rule that mixin changes pin their targets in the same
  commit.
- Threading, verified from the bytecode rather than assumed:
  `handleSystemChat` opens with `PacketUtils.ensureRunningOnSameThread`,
  so the entire path — Fabric capture event, classification, `addMessage`,
  the retention wrap, the refresh — runs on the game thread. The
  cache's unsynchronized `IdentityHashMap` is safe by confinement, as
  its javadoc claims.

### Outstanding problems

- Live verification pending the player's next session — dev.24
  carries everything. The sequences to test: repeated tab switching
  (both while at the bottom and scrolled up), switching to a channel
  tab, letting messages arrive, then switching back to All (they must
  all be there), search open/narrow/close on each tab, and the dev.23
  rejoin sequence.
- `allMessages` still holds only vanilla's 100-entry window (or
  whatever MoreChatHistory raises it to); retention keeps messages
  *within* that window, and ZEA's own persistent history remains the
  only full record.
- Vanilla's `logChatMessage` is skipped for retained messages (they
  never hit the client's `[CHAT]` log line) — the same as any
  filter-rejected message, and they remain in ZEA's history store.
- The pre-existing interplay note from the dev.23 entry (another
  `setVisibleMessageFilter` caller overwriting ZEA's filter) still
  stands, and now cuts both ways: a foreign filter that replaces ZEA's
  stops retention until ZEA re-applies, because the wrap only retains
  for `Installed` predicates.

## 2026-11-14 (session 3) — The second live report: switching lag, the stolen caret, and the /zea that never opened

### What happened

The player's verdict on the tab system as it stood: "the way you
implemented chat filters channels etc absolutely suck. They lag, kick
out of game, and freeze" — with Polyfrost/Chatting named as the
seamless-switching standard to match. Three more findings arrived with
it: the caret vanishing mid-typing (the player had to click the chat
bar to keep typing), `/zea` never opening the MoulConfig menu, and a
request for ModMenu support. The kick is the dev.23 null-cache crash,
already fixed in the dev.24 commits; everything else was new
investigation, all against the mapped 26.3 bytecode and the bundled
4.8.0 jar before any change was made.

### Root causes

**The switching lag and freezes** — two compounding costs inside every
`refreshTrimmedMessages`, which runs on every tab click, every search
keystroke, and every filter re-apply:

- Vanilla re-wraps every stored message per rebuild:
  `addMessageToDisplayQueue` calls `GuiMessage.splitLines`, which runs
  the font-width wrap each time — no memoization anywhere. With the
  pack's MoreChatHistory widening `allMessages` far past vanilla's 100
  messages, every switch re-measured and re-allocated the entire
  history's line wrapping.
- `ClassificationCache(512)` was sized for vanilla's window. With a
  ~1000-message store, FIFO eviction guaranteed hundreds of misses per
  switch, and each miss re-ran the whole classifier pipeline
  synchronously inside the rebuild loop — regex over hundreds of
  components, again on every switch.

**The stolen caret** — 26.3's `Screen.keyPressed` navigates focus with
Tab and all four arrow keys. Vanilla chat is immune only because its
input is the single widget on the screen; the navigation finds no
other target. ZEA's tab strip and search bar, added as ordinary
focusable children, became real targets: an up-arrow (history recall —
a normal thing to press while typing) moved screen focus onto the
strip, and every following keystroke went nowhere. Tab had its own
trap: finding no target, the navigation still calls `clearFocus`,
which nulls the screen's focused widget — typing dies until the player
clicks the input again.

**The dead Ctrl+F** — `KeyEvent.shortcutKey()` reports letters as
lowercase ASCII (vanilla's narrator hotkey compares `== 98`, 'b'), but
the mixin compared it against `InputConstants.KEY_F`, the HID usage
code (9). `102 == 9` never fires, so the advertised search hotkey could
not open anything.

**The /zea that opened nothing** — two independent defects, either of
which alone kills the menu:

- `ConfigScreen` built the editor over a processor that had no editor
  factories registered (`withDefaults` was never called) and had never
  been driven (`ConfigProcessorDriver.processConfig` was never run):
  zero categories, zero options, finalized-check warning — an empty
  husk.
- MoulConfig 4.8.0 is written in Kotlin and its jar declares no Kotlin
  dependency; its slider editor loads `SliderWithTextComponent` (Kotlin)
  on first use, which needs `kotlin.jvm.internal.Intrinsics`. Without
  another mod shipping a Kotlin runtime, `/zea` died with
  `NoClassDefFoundError` — reproduced in the test JVM, which is how it
  was found. The live pack evidently carries no Kotlin.

**ModMenu** — never wired: no `modmenu` entrypoint, no API dependency.

### Fix

Performance, Chatting-style instant switching within ZEA's own
architecture (no renderer replacement, no new mixins beyond one inert
inject):

- `SplitLineCache` (new, `chat.render`) — memoizes each message's wrap,
  stamped with font and width so a resource reload or chat-width change
  re-wraps. Wired into the existing `splitLines` wrap in
  `ChatComponentMixin`, EarthMC-gated so vanilla behavior elsewhere is
  byte-for-byte. Bounded FIFO at 2048, cleared when `clearMessages`
  runs (new TAIL inject — pinned in MixinSurfaceTest).
- `ChatSubsystem` — the classification cache window widens 512 → 2048
  to cover the widened store; still FIFO-bounded.
- The retention wrap now classifies at ingest: every message passing a
  ZEA-installed filter is classified once as it arrives (messages the
  capture event already classified hit the cache; MoreChatHistory
  restores and All-tab accepts get classified here instead of inside
  the next switch's rebuild). Later rebuilds are lookups.

Focus:

- `ChatTabBar` and `ChatSearchBar` override `nextFocusPath` to return
  null — mouse-driven widgets, never keyboard-navigation targets. The
  search bar still receives focus programmatically from the Ctrl+F
  toggle and returns it on close.
- The key handler consumes Tab outright on EarthMC (it has no chat
  meaning worth the focus-clearing trap). Up/Down now fall through to
  vanilla's history recall exactly as before, because the navigation
  finds no target anymore.
- Opening the search moves focus for real: the chat input's
  `canLoseFocus(false)` is lifted for the duration of the
  `setFocused` call, so the input's focused flag actually clears
  instead of leaving two carets and confused IME state.

The search hotkey compares against `'f'` (the ASCII code), documented
against vanilla's own narrator/copy/paste comparisons.

The config menu:

- `ConfigScreen.process` — `MoulConfigProcessor.withDefaults` +
  `ConfigProcessorDriver(checkExpose = false).processConfig` (the
  `@Expose` convention belongs to MoulConfig's Gson persistence; ZEA
  persists through its own store). `create(config, previous)` returns
  the screen for ModMenu; `open` keeps the `/zea` behavior.
- Fabric Language Kotlin 1.14.2+kotlin.2.4.21 is now jar-in-jar'd —
  the canonical vehicle for the Kotlin runtime MoulConfig silently
  needs; the loader deduplicates it against any copy the pack already
  carries. (MoulConfig's own manifest is mis-merged upstream — its
  root `fabric.mod.json` identifies as `libninepatch` — noted here in
  case it ever matters; it loads and works regardless.)
- `ModMenuIntegration` implements `ModMenuApi` and hands ModMenu the
  same `ConfigScreen.create`; declared under the `modmenu` entrypoint.
  ModMenu 21.0.1 (targets `minecraft >= 26.3`) is a dev-run dependency
  only — no hard `depends`, nothing bundled: ZEA works without it.

### Tests performed

- `./gradlew build` green; **539 tests, 0 failures** (was 522).
- `SplitLineCacheTest` (new, 10) — hit/miss, per-message independence,
  width invalidation, stamp overwrite, FIFO eviction at capacity,
  clear, constructor bounds.
- `ConfigScreenTest` (new, 3) — the processor is finalized after
  processing, all seven categories survive, every category carries its
  options. Counterfactual, run manually: restoring the old
  no-recipe `process()` fails all three — the dev.24 defect is now a
  build failure.
- `ChatWidgetsFocusTest` (new, 1) — reads the compiled bytecode of
  both widgets (a real `Font` cannot exist in a test JVM) and pins
  that `nextFocusPath` is overridden and can only answer null.
- `ModMenuWiringTest` (new, 3) — the `modmenu` entrypoint is declared
  and points at the integration; the integration implements
  `ModMenuApi`; modmenu stays absent from `depends` and `jars`.
- `MixinSurfaceTest` — pins `clearMessages(boolean)` as declared by
  `ChatComponent` in 26.3, per the file's own rule.
- The 522 existing tests, including `ChatSubsystemWiringTest`'s
  construction-order pin and `FeedFilterTest`'s 29 filter-semantics
  cases, all still green.

### Outstanding problems

- Live verification pending the next session — dev.24 carries
  everything. The sequences to test: rapid repeated tab switching with
  a history mod installed (the lag path), typing + up-arrow history
  recall (the caret path), Ctrl+F open/narrow/close (the hotkey path),
  `/zea` and ModMenu's config button (the menu path, now with Kotlin
  bundled).
- The first switch after a join can still pay a one-time cost for
  messages that predate any installed filter (classification happens
  lazily in the predicate for those); every later switch is memoized.
- MoulConfig's upstream manifest mis-merge (`libninepatch` id) is
  cosmetic for now but worth re-checking on their next release.
- The EarthMC API client (hover cards) remains parked behind this
  repair work, per the session plan.

## 2026-11-14 (session 4) — Chatting's action buttons: search and clear history

### What happened

The player added one more pointer to Polyfrost/Chatting: its search and
clear-history are "already correctly thoughtful" — a real button in a
real place, not a hotkey to memorize. The ask was to adapt that for
ZEA. The Chatting source (cloned and read, not guessed from memory)
puts a small 9×9 button row at the right end of the input's row —
search toggle, clear history, screenshot — with a search field beside
it, hover tooltips above each button, and a search that closes itself
when its box loses focus. Every element was studied against the 26.3
mappings before anything was written.

### What was adapted, and what deliberately was not

- **The button row** (new `ChatButtonBar`, `chat.render`): search
  toggle and clear-history as one quiet flat row at the right end of
  the tab strip's row, flush with the input's right edge — the same
  input-adjacent placement Chatting uses, sharing ZEA's existing tab
  row instead of Chatting's search field row (ZEA's search bar keeps
  its own full-width row above, which composes with the tab strip).
  9×9 buttons, 2px gaps, Chatting's grid; icons drawn as chunky
  1-pixel fills (magnifier, cross) rather than Chatting's texture
  blits — no textures to bundle, and it matches the tab strip's flat
  language. The search button shows an active state while a search is
  open, like a selected tab.
- **Discoverability**: the search button does exactly what Ctrl+F
  does (shared `zea$toggleSearch`); both are configurable ("Search
  button", "Clear history button", Chat interface category, default
  on). The tab strip's maximum width now reserves the button row, so
  the two can never overlap on narrow windows.
- **The clear button** (Chatting: `chat.clearMessages(false)`, live
  chat only) adapted across ZEA's persistence: one click empties the
  visible chat *and* this server's saved history — `clearMessages(false)`
  keeps the up-arrow recall (the 26.3 boolean resets it to raw command
  history), `HistoryRecorder.clear()` empties the buffer and persists
  the emptiness, and a toast says what went, because "cleared" that
  silently resurrects on rejoin is not cleared.
- **The focus-loss close** (Chatting polls it every frame from the
  render path to avoid reentrancy in the focus machinery — kept
  exactly): ZEA's poll closes the search only when focus lands on the
  chat input specifically. Chatting can afford "any focus loss"
  because its tabs are hand-drawn, not widgets; ZEA's tabs are
  widgets, and clicking one mid-search must keep the search open — a
  search composes with the selected feed, and checking another feed
  mid-search is a normal move.
- **Enter now closes** the search (it used to just return focus to the
  input): with the focus-loss rule, handing the caret back would close
  it a frame later anyway, so the key does it outright.
- **Search bar polish from Chatting**: query ceiling raised to 100
  characters (vanilla EditBox defaults to 32 — it runs out mid-name),
  and the ghost-text hint ("Search messages…") finally wired to the
  lang key that was reserved for it.
- **A resize no longer strands the search caret**: a rebuild with a
  search open now moves focus to the restored bar. Before, typing
  silently went to the chat input while the bar showed its old query.

### A race the clear path exposed

`HistoryRecorder.start` seeds the buffer from an async load. A clear
issued before that load lands would be overwritten by the load's
`prependAll` callback — the old messages re-seeded right over the
clear, then written back by the next flush. Fixed with a session
generation (`start` and `clear` bump it; the load callback applies
only when the generation still matches), plus the in-flight-save case
(the sequential executor means the empty write can never land before
an earlier one; the dirty flag guarantees the next flush after it
carries the emptiness). Both are pinned by tests.

### Tests performed

- `./gradlew build` green; **554 tests, 0 failures** (was 539).
- `ChatButtonBarTest` (new, 10) — row width per configuration, hit
  routing to the right callback, the gap as dead space, clicks outside
  the row, left-button-only activation (right stays free for copying),
  single-button rows, hit-testing the buttons and not the widget box,
  null-callback rejection, narration naming only the shown buttons.
- `HistoryRecorderTest` (4 new, 14 total) — clear persists emptiness;
  clear with persistence off touches no file; clear lands after an
  in-flight save; **clear drops a join load still in flight** (the
  race); clear survives disconnect without further ticks.
- `ChatWidgetsFocusTest` — the new widget is pinned out of keyboard
  focus navigation like its siblings (the dev.24 caret rule).
- `MixinSurfaceTest` — `ChatScreen.extractRenderState` (the focus-loss
  poll's target) is pinned as declared, per the file's own rule.

### Outstanding problems

- Live verification pending the next session — the sequences to test:
  the button row appearing at the tab row's right end, search
  open/narrow/close by button and hotkey, Enter/Esc/click-input
  closing the search, tab clicks keeping it open, the clear button
  emptying chat + history with the file staying empty across rejoin.
- The icons are 1-pixel fills; at odd GUI scales they may read thin.
  Chatting ships textures — swapping in drawn sprites later is a
  drop-in inside `drawMagnifier`/`drawCross` if the pixel look tires.
- Chatting's third button (screenshot chat) and its per-line delete
  were not adapted; neither was requested and both are separate
  features, not parts of the search/clear ask.
- The EarthMC API client (hover cards) stays parked.

## 2026-11-14 (session 5) — The third live report: /zea, sends by tab, General, the scroll drift, and the menu rewrite

### What happened

Five live reports landed together, all on the action-row build:

1. `/zea` does absolutely nothing; the menu is reachable only through
   ModMenu.
2. A message typed while a channel tab is selected should go to that
   channel (`/global <msg>` on Global, `/local`, `/nc`, `/tc`,
   `/tradechat`).
3. The "All" tab should be "General" — one tab that shows every channel's
   messages plus everything else.
4. While scrolling, the whole chat element drifts up and down ("type shit
   happening").
5. The config menu title should read "Zamin's EarthMC Addon - Created by
   Abasing" (given as `&`-coded text), and the chat-related config
   categories should nest under Chat instead of standing side by side.
   Plus: channel messages should carry a slight yellow text tint so the
   player can tell where a message arrived; everything else keeps its
   colors ("already red" is the mention highlight's job).

### The /zea root cause, verified in the 26.3 bytecode

`ChatScreen.keyPressed` on Enter calls `handleChatInput` first and THEN,
unconditionally, `gui.setScreen(null)` (closeOnSubmit). A client command
executes inside that first call: Fabric's interception opens the config
screen, and the very next statement of the same key press closes it —
the menu opens and dies inside one call stack, never rendering a frame.
"Absolutely nothing" was literal.

Fix: `ConfigScreen.open` now schedules the open through
`Minecraft.execute`, so it runs after the chat screen has finished
closing. The previous-screen capture happens at task time too, so the
editor's back target is the game, not the closing chat.

### Sends by selected tab

`ChatSubsystem.sendToSelectedChannel(feed, message)` routes through the
existing `OutgoingRouter` + interval-spaced dispatcher; the chat screen's
`handleChatInput` hook tries multi-send first (unchanged), then single-
channel routing for non-command text on a channel feed. General, PMs and
event feeds fall through to vanilla plain chat. Slash commands stay
vanilla in both branches. Global's per-message command is now
`global`, not `g` — both are verified in the ZEA-REPO channels table;
the player asked for the full word. Single-channel routing is
deliberately not gated behind the multi-send setting: it is core tab
behavior, and the dispatch queue still spaces rapid sends.

### General, and the unclassified tab renamed

`Feeds.ALL` keeps its stable `all` id (config and tests reference it)
but its label is now "General". The hidden unclassified feed —
previously also labeled "General" — becomes "Other" (config option
"Other tab"), so the aggregate owns the name.

### The scroll drift

The smooth-chat glide was hooked into `scrollChat` and
`resetChatScroll` too: every wheel notch shifted the whole chat pose by
the eased remainder, and consecutive notches accumulated (each shift
adds its remainder and restarts the clock), so fast scrolling
rubber-banded the entire panel — lines and their backgrounds are drawn
inside the pose the render hook translates. Scrolling is now instant,
like vanilla; the glide survives exactly where it reads as polish — new
messages arriving at the bottom. `SmoothChat` itself is untouched; the
scroll/reset hooks are gone from `ChatComponentMixin`.

### The menu title and the nested categories

- `Config.getTitle()` (MoulConfig's default is literally "Config GUI")
  is overridden with the coded title, parsed into MoulConfig
  `StructuredText` runs (colors via `TextColor.fromLegacyFormat` — the
  26.3 `ChatFormatting` no longer carries RGB itself). The Screen title
  component parses the same string through the new
  `LegacyText.parse` (`&` and `§` forms, formats and resets, unknown
  markers stay literal).
- The config layout is now two top-level categories: **Chat** (multi-
  send, dispatch interval, channel tint) with the **Tabs**, **Chat
  interface**, **Notifications**, **Mentions** and **Chat history**
  subcategories nested inside it, and **Inventory buttons**. MoulConfig
  4.8.0 supports one nesting level: a `@Category` field inside a
  category class becomes a subcategory (verified against the bundled
  processor driver bytecode — `processCategoryMeta` recurses through the
  collected bound fields and calls `setCategoryParent`).
- Schema version 2: Gson cannot move whole objects at the value level
  (unknown fields drop silently), so `JsonStore` gained a tree-level
  migration hook — the v1 file's five chat category objects are hoisted
  under `chat` before deserialization, values untouched, and the file
  is rewritten immediately. A current-version file passes through
  byte-identical. The share payload's wire format is untouched (it is
  its own format; only the config field paths inside `of`/`applyTo`
  moved).

### The slight yellow

`ChannelTint` (new, `chat.render`): a message classified into one of the
five writable channels gets a tinted copy of its component for display
wrapping — every run's *effective* color blended 35% toward warm yellow
(`0xFFE878`; colorless runs blend from white). Styles are resolved the
way `Component.visit` resolves them (own style over the parent's
effective, siblings inherit) and baked per node, so bold/italic/hover/
click carry over and inherited colors land correctly. The wrap happens
inside the split-lines memoization hook: the tinted lines are computed
once and memoized under the original message — the stored message is
never modified, so copying, mention identity (which keys on component
identity) and history all see the original. Behind a "Channel tint"
option (Chat, default on). Known trade-off: formatted copy of a tinted
line emits the blended RGBs, which have no legacy codes — the plain
copy is unaffected.

### Tests performed

- `./gradlew build` green; **574 tests, 0 failures** (was 554).
- `ChannelTintTest` (new, 8) — white and colored blends, blend-is-not-a-
  repaint, input never modified, per-sibling colors, inherited colors
  resolve before blending, non-color styles survive, determinism.
- `LegacyTextTest` (+6) — the coded title splits into three colored
  runs, `§` form, format flags and reset, unknown marker stays literal,
  empty/null, uppercase codes.
- `ZeaConfigTest` (+2, rewritten 2) — v1 file's categories hoist under
  `chat` with every value carried over and the flat layout gone after
  the rewrite; a current-version file is not rewritten; v0 migration
  updated for the new version constant.
- `ConfigScreenTest` (+2) — the processor records the parent link for
  all five subcategories; Inventory buttons stays top-level.
- `ZeaConfigStructureTest` — rewritten for the nesting: every Chat field
  is exactly one of option or subcategory, the five subcategories are
  not top-level, options checked recursively through nested categories.
- `OutgoingRouterTest` — global routes through `global`.
- `MentionAutoResponderTest` — auto-responses on Global go through
  `global` too.

### Outstanding problems

- Live verification pending the next session: `/zea` opening (and the
  ModMenu path unchanged), sends landing on the selected tab's channel,
  the General tab label, instant scrolling with the arrival glide kept,
  the title/colors, the nested menu, and the tint's visibility against
  EarthMC's actual line colors.
- Formatted copy of tinted lines loses legacy codes (blended RGBs);
  rounding blended colors to the nearest legacy code would fix it but
  changes LegacyText for every color — left out of this pass.
- The scroll glide's removal is a judgment call: if the player only
  disliked the *accumulation*, a capped scroll glide could come back.

## 2026-11-14 (session 6) — Item rarity, the missing-enchantment line, and native scrollable tooltips

### Research performed and findings

**NEU (1.8.9, Hypixel SkyBlock)** — the assigned reference. Read, not guessed:

- Rarity is *server-provided* there (a `rarity` field in the item JSON of
  NEU-REPO), never calculated — so the useful references are the color
  mapping (`Utils.rarityArrC`: white, green, dark_purple, gold, all bold
  for the lore line), `ItemRarityHalo` (a framebuffer+shader item halo —
  heavy, 1.8.9-bound, deliberately not copied) and `ChromaColour`
  (`specialToChromaRGB`: a plain HSB hue rotation from
  `System.currentTimeMillis()`).
- Enchant styling (`EnchantStyleCustomizer` + `EnchantMatcher`) matches
  tooltip lines by regex on rendered text; the matching idea survives,
  the string regex does not (SkyBlock-only enchant names).
- ZEA adaptation: same four colors for the four levels, the bold rarity
  lore line, chroma as an animated hue — applied per line instead of per
  glyph so no custom font renderer is needed, and computed at tooltip
  build time (tooltips rebuild every frame while hovered, so the
  animation costs nothing).

**Minecraft 26.3 registries** (verified against the loom-cached named jar,
`javap` and the embedded vanilla data, not from memory):

- Enchantments: `Enchantment` is a record; `isSupportedItem(stack)`
  (tag-driven `supported_items`), `areCompatible(Holder, Holder)`
  (the `exclusive_set` data), `getMaxLevel()`, and
  `getFullname(Holder, int)` (the exact vanilla display text,
  roman numerals included). The registry is **data-driven** in 26.3 —
  `BuiltInRegistries.ENCHANTMENT` no longer exists; enchantments come
  from the level's own `RegistryAccess`.
- Materials: tool classes (SwordItem & co.) are gone; tools are plain
  items built from `ToolMaterial` records (WOOD, GOLD, STONE, COPPER,
  IRON, DIAMOND, NETHERITE — copper equipment is new). No material field
  survives on the item, but each material writes a unique
  (durability, enchantability) pair into the item components, so the
  pair recovers the material exactly; the lookup is built from the
  records themselves. Armor carries an `Equippable` component whose
  `assetId()` matches the `EquipmentAssets` constants — registry-key
  identity, no substring matching anywhere.
- Item kinds: the vanilla tags `#minecraft:swords/axes/pickaxes/shovels/
  hoes` and `#minecraft:head/chest/leg/foot_armor` decide supported
  kinds (the `enchantable/*` tags also list shears, skulls and elytra —
  not used, they are out of scope).
- Rendering: 26.3 extracts draw commands. `AbstractContainerScreen`
  exposes `extractSlot` (per-slot, uses `slot.x/y` locals — vanilla's own
  hover highlight fills the same rectangle), `extractTooltip` and
  `getTooltipFromContainerItem` (the per-frame tooltip list source),
  with a protected `hoveredSlot`. Tooltips are *deferred*
  (`setTooltipForNextFrame`) and drawn in `GuiGraphicsExtractor.tooltip`,
  which resolves the position through one
  `ClientTooltipPositioner.positionTooltip` call and then draws
  background, text and images at explicit coordinates — no matrix
  translate to hook.
- Input: `MouseHandler.onScroll(long, double, double)` dispatches into
  `Screen.mouseScrolled(DDDD)` at exactly one site; shift state comes
  from `Minecraft.getInstance().hasShiftDown()`.

**ScrollableTooltipsPort (Sk1erLLC)** — read in full. Its mechanics:
reset on hovered-slot change, translate the tooltip matrix, clamp scroll
to screen margins (6 px), wheel step 10, shift = horizontal, start-at-top
reset. Its hooks (1.8.9–1.21.x `drawTooltip*` matrix juggling with
MixinExtras `@Local`) do not exist on 26.3; the mechanics were rebuilt on
the position-resolution redirect instead. One scrollable-tooltips mixin
redirects `positionTooltip` in `GuiGraphicsExtractor.tooltip` — background
and content all derive from that one result, so nothing can drift apart.

### The rarity model (documented in full in `RarityCalculator`'s javadoc)

- Material tier 0..6: tools wood, gold, stone, copper, iron, diamond,
  netherite; armor leather, gold, chainmail, copper, iron, diamond,
  turtle, netherite. Unknown material (modded/edited) → tier 0, never a
  wrong material.
- Enchantment fill F = Σ level/maxLevel over the item's compatible,
  non-neutral enchantments. The budget M = the fill of the best
  legitimate maxed setup for the item's class (exclusive groups count
  once — greedy selection, exact for vanilla's clique conflicts), cached
  per item class and invalidated on disconnect (the registry is
  data-driven per level).
- Progress P = F/M measures hypermaxed-ness *within the class*, so a
  maxed pickaxe (4.0) and a maxed sword (6.0) compare fairly and a bow
  with Infinity is not punished for lacking Mending.
- Rarity = clamp(max(material rarity, enchant rarity), ceiling):
  material rarity `clamp(T-4, 0, 2)` (iron and below Common, diamond
  Uncommon, netherite Epic); enchant rarity at P ≥ 0.30 / 0.55 /
  0.85(+F ≥ 3.0); ceiling 1 for tiers 0–1, 2 for 2–4, 3 for 5–6.
  Worked examples live in the javadoc and the test matrix; every guard
  is explained there.

### Neutral and preference enchantments

`EnchantPolicy`: Knockback, Thorns, Soul Speed, Swift Sneak, Frost Walker
and both curses are neutral — zero contribution, never suggested, never
required for Legendary. Identified by registry key path (resource packs
cannot move them). Conflict-group leaders for suggestions: Sharpness,
Protection, Mending, Silk Touch, Loyalty, Multishot, Depth Strider;
unranked groups fall back to the alphabetical key. Groups are detected
from the registry (`areCompatible`), not hardcoded.

### Missing enchantments (Shift hover)

`MissingUpgrades`: present-below-max → the next level ("Sharpness IV" →
"Sharpness V"); absent → the maximum level (the meaningful target);
conflicts with *held* enchantments (including held neutrals — a Frost
Walker boot never gets Depth Strider suggested) are impossible and
skipped; absent group members collapse to the leader; neutrals never
appear (they are filtered at the budget). Output sorted by display name.
Rendering: a dark-gray "Missing enchantments:" header plus the gray list
joined with ", ", wrapped at comma boundaries to
min(240, screen − 40) glyph units — a single over-long entry is placed
alone rather than broken or dropped, and the empty list renders nothing.
The width budget keeps the tooltip box (~256 px worst case) inside even
the smallest vanilla GUI width (320), so the line can never stretch to
the screen edge.

### Files

- New: `rarity/{ItemRarity, MaterialTier, EnchantPolicy, EnchantBudget,
  RarityCalculator, MissingUpgrades, RarityTooltipEditor, RarityEngine}`,
  `misc/TooltipScroller`,
  `mixin/{CreativeScreenMixin, GuiGraphicsExtractorMixin,
  MouseHandlerMixin}`, `duck/HoveredSlotAccess`.
- Extended: `mixin/ContainerScreenMixin` (slot tint at `extractSlot`
  HEAD, tooltip edit at `getTooltipFromContainerItem` TAIL, scroller
  tracking at `extractTooltip` HEAD, scroller reset on `removed`),
  `config/ZeaConfig` (Rarity category with the Appearance and
  Enchantment display subcategories, a Miscellaneous category, clamped
  accessors), `ZeaClient` (cache invalidation on disconnect),
  `zea.mixins.json` (+3 mixins).
- Rendering touches nothing but the per-frame tooltip list vanilla
  builds and a 16×16 background fill; stacks, components and stored lore
  are never written.

### Tests performed

- `./gradlew build` green; **630 tests, 0 failures** (was 574).
- `RarityCalculatorTest` (+16) — the full threshold matrix from the
  spec's verification list: the six unenchanted materials, identical
  setups on different materials, maxed low/mid/high tiers, partial high
  tier vs maxed low tier, the 0.85+3.0 Legendary gates, determinism.
- `MissingUpgradesTest` (+14) — Sharpness IV → only Sharpness V; absent →
  max level; maxed → silent; Smite blocks Sharpness; a maxed group
  member blocks the group; absent groups collapse to the leader; held
  Frost Walker blocks Depth Strider; neutrals absent from suggestions;
  comma wrapping (short, long, single over-long entry, 30-entry list).
- `EnchantPolicyTest` (+13) — the neutral set, all leader tables,
  alphabetical fallback, exclusivity contracts, transitive group shape.
- `MaterialTierTest` (+7) — fingerprint uniqueness across the seven
  real materials, the tool ladder, the armor ladder through the real
  `EquipmentAssets` constants, unknown-pair fallback, non-player assets.
- `TooltipScrollerTest` (+5) — the clamped bounds math: overflow ranges,
  bottom margin, pinned content, horizontal semantics, pass-through.
- `ZeaConfigStructureTest` (+2) — Rarity nests Appearance + Enchantment
  display; Miscellaneous holds scrollable tooltips.
- Bootstrap note: `VanillaBootstrap` stays at `Bootstrap.bootStrap()` —
  26.3 binds item default components only during the data-load flow,
  which needs datapack tags a test JVM never loads. The tier resolvers
  expose pure seams precisely so the real registry data stays testable;
  constructing an `Enchantment` before bootstrapping poisons the test
  JVM's registries ("Not bootstrapped"), so the fixtures bootstrap
  first.

### Outstanding problems

- Live verification pending the next session: the tint's visibility at
  25 % against real inventory textures, the shift list's wrap against
  real enchantment name lengths, chroma speed (1.8 s cycle) taste, and
  scrollable-tooltips behavior against MoulConfig's own tooltips (they
  share `GuiGraphicsExtractor.tooltip`; the scroller only engages for
  container screens with a hovered slot).
- The enchantment budget is cached per item class per session; a server
  whose datapack changes enchantment data mid-session (join → same
  session, different registries) would keep the old budget until the
  next disconnect — acceptable, recorded.
- Copper tools/armor sit at tier 3 (between stone and iron, matching
  their durability band) — a judgment call recorded in `MaterialTier`.

## 2026-11-15 (session 7) — Inventory buttons v2, the search bar, slot binding and slot locking

### Repository state before work

- Branch `develop`, up to date with origin; the rarity/scrollable-tooltips
  work from session 6 present as uncommitted changes — all preserved, no
  resets, no commits made.

### NEU research performed (source + history, not READMEs)

Cloned `NotEnoughUpdates/NotEnoughUpdates` (blobless, full history,
1550 commits) and read the actual implementations:

- `RenderListener.iterateButtons/renderButtons/onGuiScreenDrawPost` —
  button rendering, the hover tooltip delay, the click dispatch:
  `ClientCommandHandler.executeCommand` first, server chat as fallback,
  click swallowed (not run) while an item is carried.
- `GuiInvButtonEditor` (991 lines) — the editor: a drawn player-inventory
  canvas, per-button command field, background/icon-type selectors, a
  searchable scrollable icon grid, presets from `presets.json`,
  base64 clipboard share. Presets carry **blank commands** (NEU's
  default set is 49 positioned placeholders).
- `NEUOverlay` + `MixinRenderItem`/`MixinGuiContainer` — the search bar:
  double-click (300 ms) toggles the gray-out mode; matching items get a
  light overlay under the item, non-matching a dark overlay over it,
  empty slots darken too; `NeuSearchCalculator.format` appends
  `§e = §a<result>`; `Calculator` is lexer + shunting-yard + RPN over
  BigDecimal with a variable hook.
- `SlotLocking.java` (~830 lines) — `LockedSlot{locked, boundTo}` per
  profile, per-dimension (Rift) separation; lock key toggles + starts a
  pairing; click a hotbar slot while held binds; right-click a bound
  slot performs a synthetic `windowClick(from, to, 2)` swap; hover
  highlight recolored red; locked slots excluded from `checkHotbarKeys`.

The listed historical commits, each read as a full diff:

- Inventory buttons: `990d552` (accessory-icon overlap: fixed by nudging
  x), `0f3cfc2` (wardrobe overlap), `3047979` (rift warp menu
  visibility), `f41be3f` (Buttons Everywhere preset, 1012 buttons),
  `ba051e5` (third-party `NEUApi.disableInventoryButtons`),
  `7dd1543` (quick enable/disable toggle), `1ccc013` (hide in dungeon
  menus), `dc4cd12` (the starts-with menu set fix).
- Slot locking: `9f2ae5b` (drop race: 1.8.9 could drop the slot the
  server still had selected after a fast scroll-away; fixed with
  slot-change timestamps), `8f01042` (keybinds dead over locked slots:
  caused by nulling `theSlot`; fixed by keeping a "real slot" copy),
  `fb08aa9` (ninth hotbar slot: excluded from locking entirely),
  `f402113` (Rift/Overworld separation), `2a3536b` + `ca875b3`
  (storage/backpack lock disabling).
- Slot binding: `036d00a` (armor slots: swap index must be the menu slot
  id 5-8, not the inventory index 36-39), `30fea5f` (chest GUIs:
  `idChest = id + (size - 45)` converts inventory index to menu id).

### Design decisions (NEU lessons → ZEA)

- **Regions, not pixel offsets.** NEU's overlap history (accessory icons,
  wardrobe, menu titles) all came from free offsets. ZEA v2 buttons own
  a region (TOP/BOTTOM/UNDER_SEARCH/LEFT/RIGHT, capacities 8/8/5/8/8)
  and an order; `ButtonGeometry` computes every rectangle from live
  screen values. Overflow hides instead of colliding; the recipe book
  claims the left strip while open.
- **Preset templates are inert.** Like NEU's default set, preset buttons
  carry blank commands — nothing invented for EarthMC. The store keeps
  blank-command buttons (inert data); the share import still rejects
  them.
- **Close-on-execute** closes only after `sendCommand` returned and only
  if the screen is still the same instance (a client command that opened
  a GUI keeps it).
- **Binding via the live menu's slot index.** The swap is synthesized
  with `MultiPlayerGameMode.handleContainerInput(containerId,
  slot.index, target, SWAP, player)` — the exact call vanilla's number
  key makes — which structurally absorbs NEU's armor-slot and chest-GUI
  fixes.
- **One interception point.** Every click path funnels through
  `slotClicked(Slot,int,int,ContainerInput)`: one HEAD-inject decides
  BLOCK/SWAP/PASS; `shouldAddSlotToQuickCraft` excludes locked slots
  from drags; the drop key without a hovered slot is blocked in
  `MultiPlayerGameMode.dropItem`. Modern `dropItem` sends the
  carried-item sync before the drop packet, so NEU's scroll-race guard
  is unnecessary.
- **No hover-state nulling.** NEU's dead-keybinds bug cannot recur by
  construction; the padlock indicator replaces the red hover.
- **Ninth slot lockable.** MC 26.3's player-inventory numbering (0-8,
  9-35, 36-39, 40) is clean; NEU's exclusion was a 1.8.9 workaround.
- **Per-server lock contexts** (connect hostname — the same key history
  uses), the modern reading of NEU's per-profile/per-dimension split.
- **Search resets on screen change** (query and mode), fixing NEU's
  stale-global-search behavior; per-slot search text cached against the
  stack and revalidated with `ItemStack.matches`.
- Buttons are no longer EarthMC-gated (the spec requires them to work on
  other servers); chat keeps its gate.

### Files

- New: `buttons/{ButtonRegion, ButtonPresets, ButtonListScreen}`,
  `search/{Calculator, SearchText, InventorySearch, SearchOverlay,
  SearchBarWidget}`, `slots/{SlotLocks, SlotLockStore, SlotGuard,
  SlotIndicators}`, `mixin/{RecipeBookScreenMixin,
  MultiPlayerGameModeMixin}`, `duck/RecipeBookAccess`.
- Rewritten: `buttons/{InventoryButton, ButtonGeometry, ButtonRenderer,
  ButtonDragSession, ButtonEditForm, ButtonEditScreen,
  InventoryButtonStore, InventoryButtons}`, `mixin/ContainerScreenMixin`.
- Extended: `ZeaClient` (subsystems, `/zeabuttons`, lock context on
  join/leave), `keybind/ZeaKeys` (LOCK_SLOT), `config/ZeaConfig` (the
  Inventory utilities category with Button placement / Search bar /
  Slot binding / Slot locking subcategories, the editor launcher
  runnable, accessors), `share/InventoryButtonsPayload` (v2 wire
  shape), `zea.mixins.json` (+2 mixins), lang (+47 keys).
- Also in the same pass: the optional button description (tooltip)
  field through model/store/share/form/editor/tooltip; the icon
  quick-picker row in the edit form (ten vanilla icons, one click
  fills the icon field — no resource-file editing); buttons and the
  search bar stay off the creative screen, whose tabs and search own
  its furniture.
- Schema: buttons.json v1→v2 migration (corner anchors → regions);
  slotlocks.json v1 (per-server contexts, 41-slot arrays).
- Docs: `docs/INVENTORY_UTILITIES.md` (renamed from
  INVENTORY_BUTTONS.md and rewritten), ARCHITECTURE.md module table.

### Tests performed

- `./gradlew build` green; **718 tests, 0 failures** (was 630).
- Rewritten/extended button tests (+~40): region layout (stacking, recipe
  book, capacity, screen clipping, drop targets, hit tests), drag
  session, edit form (region/order/text/close), store v2 round-trip +
  v1 migration (top/bottom/right mapping, dense repack, blank commands
  kept), coordinator moves/presets/restore, tooltips, activator
  close-on-execute semantics.
- New search tests (+~30): calculator (the spec's 39+4=43 / 34+39=73,
  precedence, parens, decimals, unary minus, division by zero,
  incomplete, non-arithmetic, grouping format, repeating division cap),
  text normalization/matching/OR, overlay colors, search state machine.
- New slot tests (+~25): guard matrix (every ContainerInput × locked ×
  hotbar-target × bound-right-click), ninth slot, quick-craft
  exclusion, drop key, store round-trip/padding/corruption, lock
  contexts, persistence, pairing flow.
- MixinSurfaceTest pins the new targets (`init`, `slotClicked`,
  `shouldAddSlotToQuickCraft`, `extractSlot`,
  `getTooltipFromContainerItem`, `menu`, `recipeBookComponent`,
  `dropItem`).

### Outstanding problems

- In-game verification pending (the sandbox cannot run the client):
  button hitboxes vs rendering at each GUI scale, the search bar's
  focus/unfocus feel, matching-mode colors against real container
  textures, lock padlock readability, binding swap in a real chest,
  recipe-book left-column yield, `/zeabuttons` canvas layout, preset
  restore flow. Compiled + unit-tested only; not yet visually verified.
- The double-click collection (PICKUP_ALL) drain-from-locked limitation
  is shared with NEU and documented.
- The recipe-book-open detection uses the component's `isVisible()`; a
  third-party mod embedding its own recipe book UI would need its own
  exclusion (none known on 26.3 yet).
- The four session-6 field tests (tint visibility, shift-list wrap,
  chroma speed, MoulConfig tooltip scrolling) also remain for the
  in-game pass.

## 2026-11-15 (session 8) — Rarity determinism audit: one cross-client hazard found and fixed

### The question

"Are rarity calculations consistent? Won't two players get entirely
different rarity for the same item and enchantments?" Answered by
auditing every input to the tier, not by reassuring:

- **Material tier** — a pure lookup of the item type's
  (durability, enchantability) pair; vanilla data, identical on every
  client.
- **Enchantment budget** — `EnchantBudget.compute` sorts its candidate
  list by registry key before the greedy selection and the maxFill sum,
  and the enchantment registry is server-synced, so every client on the
  same world derives the same budget. Datapack worlds can legitimately
  differ — rarity measures progression under *that world's* rules, the
  same context separation NEU applies between dimensions.
- **Neutral classification / group leaders** — key-path based
  (`minecraft:knockback`), immune to resource packs and translations.
- **The cache** — memoization only; `ItemEnchantments` overrides
  `equals` order-independently, and a flush recomputes the same value.

### The hazard

`RarityEngine.fillOf` summed the floating-point `level/maxLevel` fill
by iterating the component's `entrySet()`. Verified against the
loom-cached 26.3 jar: `ItemEnchantments` is backed by an
`Object2IntOpenHashMap` keyed by `Holder.Reference`, which does **not**
override `hashCode`/`equals` — identity hashing. Iteration order of the
same logical enchantment set therefore differs between JVMs, and since
floating-point addition is not associative, two clients could round
the fill differently. It would take a progress value sitting within an
ulp of a threshold (0.30 / 0.55 / 0.85 / the 3.0 fill floor) to
actually diverge — vanishingly rare, but the calculator's javadoc
promises "same material, same enchantments → same rarity, always", so
the hole is closed rather than excused.

### The fix

`fillOf` now iterates the budget's canonically ordered list (registry
key order) and reads each level through `getLevel` — the component's
map is never walked. The sum order is then a pure function of registry
content, identical on every client, making the fill bit-for-bit
reproducible. (Also removed an unused `ConcurrentHashMap` import.)

### Test

`RarityFieldTest.sameEnchantmentsScoreIdenticallyOnEveryClient` — two
independently built "clients" (separate holder pools, so the component
maps really are keyed by different objects and can order differently)
each sum their own budget in canonical key order; the fills must match
bitwise, never merely within an epsilon, and both must classify the
same partially-enchanted netherite sword as Epic. Writing it surfaced a
fixture subtlety worth remembering: `Holder.Reference` equality is
identity, so a component built from one client's holders cannot be
queried with another client's holders — each client's budget must be
built from its own registry, exactly as in production.

`./gradlew build` green; **719 tests, 0 failures** (was 718).

## 2026-11-15 (session 10) — Map subsystem Phase 0 research + Phase 1 core

### Phase 0: source research (the map spec's mandatory preliminary)

Full reports live in `docs/map/RESEARCH.md` (findings, 10 required
outputs, inspection log) and `docs/map/ARCHITECTURE.md` (design). Both
were written before any map code.

**EarthMC-Map-Addon** (branch `1.21.11`, local clone): all 17 required
commits read in full diff, ~25 more surveyed with 8 deep-reads (via a
research subagent, spot-verified against the clone). Key sources read
directly: `TownyMapMod` (world resolution, claim pipeline),
`TownyMapConfig` (URLs), `SquaremapApiClient` (996 lines: ETag/gzip/
max-age floors/idle backoff/geometry overrides),
`SquaremapTileRenderer` (771: LRU, budgets, parent fallback),
`TownyMinimapOverlay` (mask snapshots, merged runs), `ChunkRings`,
`TownData`, `PlayerMarker`, keybinds, mixins json. Xaero itself is
closed source — only its mixin surface is visible.

**NEU Dungeon Map**: the 5 required files + `Position`,
`GuiPositionEditor…`, shaders; 3 named commits + 8 extra. Editor
philosophy and the 16-option config surface extracted for adaptation.

**ZEA-REPO**: every file (17): schemas, validate.py, CI, manifest —
tile extension plan written into ARCHITECTURE §7.

**Live endpoint verification (2026-11-15)**: `settings.json` (both
world keys), markers.json headers (`max-age=120`, **ETag per
content-encoding** `W/"…-gzip"`), tile headers (`max-age=7200,
stale-while-revalidate=600`, strong ETag), `/map/v1/players` body —
**the new feed carries a `world` field per player** (cross-world
isolation is now data-supported), `{"max":800,"current":327}`, feed
`max-age=1, must-revalidate`. Moon docs: 35k×17.5k, one outpost per
town. Map docs: visibility rules verbatim.

markers.json format verified from a live 10.7 MB capture: top-level
array of layers; `towny` polygons with `points` nesting
**polygons → rings → `{x,z}` vertices**; `chunky` World Border
polyline (x −64512..64511, z −32256..32255).

### Phase 1: core implemented (`net.zaminmc.zea.map`)

- **model**: `MapWorld` (dimension→world rules incl. both lunar
  dimensions→`earthmc_moon`), `TileId` (world+source+zoom+x+z),
  `PlayerMarker` (feed parse + `inWorld`), `TownClaim` ((world,key)
  identity, rings, bbox, covers/withRings/geometrySignature),
  `MapCamera` (zoom-around-anchor, pan, view bounds, clamps),
  `Freshness` (last-success vs last-attempt vs failing vs refused).
- **geometry**: `ChunkRings` — own implementation of the boundary walk
  with two deliberate improvements: sorted iteration (deterministic
  rings, which geometry signatures need) and right-turn preference at
  diagonal pinches (separate rings, valid GeoJSON — the reference could
  produce a figure-eight depending on HashSet order).
- **source**: `MapHttp` (conditional GET: gzip always, ETag+IMS,
  per-encoding validator discipline, Cache-Control floors, bearer-only-
  to-the-feed, typed `HttpRefusal`), `SquaremapClient` (per-world
  validators + freshness bookkeeping; 304 = success keeping previous
  data), `MarkersParser` (live 3-level points shape, per-marker
  try/catch, nation from tooltip, world-border polyline),
  `SquaremapEndpoints` (fixed hosts; key never sent to squaremap).
- **config**: `MapConfig` MoulConfig category wired into `ZeaConfig`
  (Chat-style: gates directly on Map + Minimap/World map/Shared display
  subcategories; ~40 options incl. API key, EarthMC-only default,
  north-up default).

### Bugs found and fixed during development

- `CompletionException` wrapping: `thenApply` wraps source failures, so
  refusal detection must walk the cause chain (one `getCause()` level
  missed the `HttpRefusal`; diagnosed with a scratch future-chain
  probe).
- MoulConfig drops a category whose fields are *all* subcategories —
  an intermediate category needs at least one direct option
  (`everyCategoryCarriesItsOptions` caught it; Chat's layout is the
  house answer).
- Generic inference collapse on `thenApply(...).exceptionallyCompose`
  for `List<PlayerMarker>` — fixed with an explicitly typed local.

### Verification

- Full-document parse: the real 10.7 MB markers.json → **5,814/5,814
  towns**, 119 multi-ring, 5,272 with nation, world border kept,
  Amsterdam ≈588 chunks (Netherlands) in 429 ms — confirming parsing
  must stay off the render thread, as designed.
- `./gradlew build` green; **800 tests, 0 failures** (was 719; +81:
  ChunkRings 11, MapHttp 7, SquaremapClient 7, Freshness 7, MapWorld 9,
  PlayerMarker 6, TileIdAndCamera 15, MarkersParser 9,
  SquaremapEndpoints 4, MapConfigStructure 6). Live-data test fixture:
  `src/test/resources/map/markers-fixture.json` (trimmed from the
  capture: Etna, holed Middelburg, nationless Mount_Baldy, Los_Altos,
  a skippable icon, verbatim border layer).

### Not done yet (next sessions)

Tile cache + GPU upload pipeline, ClaimStore/PlayerStore, world map
screen, minimap HUD, /zeamap + keybinds, map editor, ZEA-REPO client,
claim-command pipeline, visual verification. Nothing committed yet
(the spec says commit only on request).

## 2026-11-15 (session 11) — Map subsystem Phase 2: caches, stores, v4 client

The data core under the rendering: everything pure and unit-tested,
Minecraft still not imported anywhere in `net.zaminmc.zea.map`.

### `map.projection.MapProjection` (new)

One coordinate frame conversion for every surface: world ⇄ screen
under a camera, surface size and rotation; tile pyramid math.
- Rotation expressed once, as the angle north is turned away from
  screen-up; `rotationForYaw(yaw) = yaw + 180°` pins Minecraft's yaw
  convention (0 = south, 90 = west) so rotate-with-player points the
  player's heading up. Round-trips are exact inverses at any angle.
- `chooseTileZoom` follows ARCHITECTURE §4:
  `MAX_ZOOM + floor(log2(ppb)) + QUALITY_ZOOM_BIAS`, clamped. First
  draft computed the floor by doubling — wrong at the zoom-out end
  (it let the +2 bias pick zoom 2 at 1/64 px/block); the architecture's
  formula shrinks the bias past the pyramid floor, which is also the
  resource-sane behavior. `Math.log2` does not exist in Java;
  `Math.getExponent` is the exact `floor(log2)` for normalized doubles
  and immune to the power-of-two rounding a computed log suffers at
  step boundaries.
- Tile math pinned to the live instance: 512 px tiles, zoom 5 = 1:1,
  span 512·2^(5−zoom); floor-division indices (Earth's west border
  −64512 is tile −126, −64513 is −127); `regionWithin` gives the
  parent-fallback UV sub-rect and rejects non-descendants; the
  visible-tile range uses the surface diagonal so it is a correct
  superset under rotation.

### `map.cache.TileCache<T>` (new)

The imagery store: lifecycle (LOADING → QUEUED → READY / FAILED),
access-order LRU (1024), pinned overview (zoom ≤ 2), a 128-image
reserve for the minimap's world, per-entry ETag/IMS that die with the
entry (`d2b6a024`), 404-vs-retryable failure ladders (60 s vs
0.4→3.2 s ×5 then 60 s), generation stamps, and claim-for-upload with
claimed-entry immunity (`aa92179f`'s remove-then-close, expressed as a
claimed flag so a revalidating entry can keep its old image drawable
while the replacement uploads).
- Generic over the decoded image type: production will instantiate
  `TileCache<NativeImage>`, tests run `byte[]`. GPU handles stay
  render-side keyed by `TileId`.
- Every retired image leaves through `drainDisposals()`, drained on
  the render thread — a fetch thread never frees image memory, and a
  rejected (stale-generation) completion hands its image to the same
  channel because the fetcher cannot know which thread would be safe.

### `map.source.squaremap.TileFetcher<T>` (new)

Drives tiles into the cache: per-world semaphore (8), validators from
the cache's ticket, 304 → `revalidated`, 200 → decode on the fetch
thread → `complete`, 404/429/5xx/transport → the matching failure
ladder, decoder seam injected (production: `NativeImage::read`). The
optional API key is unreachable from here by construction — squaremap
never sees it. `SquaremapUrls.tile` added as a default method so the
existing test stubs keep compiling.

### `map.cache.ClaimStore` (new)

The claim state machine with the full bridge from Phase 0: pendings
(12 s) → optimistic single-chunk delta in neutral colors (20 s, only
the chunk the map has not published — `c41b0185`) → v4 API geometry
override replacing the town's rings in place (180 s, dropped when the
published town covers it with equal chunk count — `62c1f9a7`).
Earth-only guard on API chunks (`eb0c3ba`), instance fast path for
unchanged towns (`7ea2bb8d`), unclaims caught at send, and
`TownClaim.coversBlock` (even-odd over all rings, holes excluded) to
retire optimistic chunks the map adopted.

### `map.cache.PlayerStore` (new)

Per-world marker lists split by the feed's own `world` field (blank =
legacy, belongs to any world), rebuilt only when the feed content
changes — identical documents keep every instance. Keeps the previous
snapshot + timestamps for the renderer's interpolation anchors. A
parsed-empty feed clears (an empty server); a failed fetch never
reaches the store at all.

### `map.source.earthmcapi.EarthMcApiClient` (new) + `MapHttp.post`

`POST /v4/towns` with the `name + coordinates` template; parses
`coordinates.townBlocks` into `ChunkRings` keys (malformed pairs
skipped). Unknown towns and 404/400 complete empty; 429/5xx/transport
retry on the 0.4→3.2 s ladder (5 attempts) and only then fail.
Concurrency gated to 4 — the observed 429 threshold, not the
documented 180/min. No key is ever sent: town claims are public data.
`MapHttp.post` reuses the Fetched/refusal/gzip plumbing; retry base
injectable so tests run the ladder instantly.

### Bugs found and fixed during development

- **Evicted-but-queued double free** (`TileCache`): an entry evicted
  while QUEUED stayed in the upload queue, so the render thread would
  claim an already-retired image. Surfaced by writing the
  mass-eviction test before trusting the eviction path; eviction now
  dequeues its victim.
- **Stale-generation poisoning** (`TileCache`): a completion rejected
  by generation left its LOADING entry in the map forever, blocking
  every future load of that tile. Rejection now retires the slot (and
  clears a stuck revalidation flag).
- **Hot revalidation loop** (`TileCache`): a failed revalidation left
  the entry stale, so the scheduler would immediately re-ask — a
  persistently failing server polled at frame rate. Failures now push
  `validatedAtMs` forward by the retryable delay.
- **`clearWorld` claimed-entry leak** (`TileCache`): a claimed LRU node
  kept its image counted after the world clear. Claimed nodes now
  leave the LRU without disposing — the uploader owns that disposal
  through its finish path.
- **Operator precedence** (`ClaimStore`): the instance-reuse condition
  mixed `&&` with `?:` and parsed wrong; extracted to `isUnchanged`.
  Also an NPE when confirming an unclaim with no optimistic map, and
  a hand-rolled chunk-key packing that duplicated `ChunkRings.key`.
- **Tooling incident**: the session's indentation helper halved the
  tabs of any file containing 2-tab lines — including the perfectly
  healthy `TownClaim.java`, which had no git history to restore from.
  Rewritten verbatim from the session's read of it; the script now
  only converts spaces→tabs by default and halving is an explicit
  `--halve` flag. Lesson recorded: never run a lossy whole-file
  transform without a committed copy to diff against.

### Verification

`./gradlew build` green; **864 tests, 0 failures** (was 800; +64:
MapProjection 14, TileCache 18, TileFetcher 7, ClaimStore 12,
PlayerStore 8, EarthMcApiClient 7 — the fetcher and client suites run
against real local HTTP servers, covering 304/404/429 paths, the
ladder, the gate and late-answer retirement end to end).

### Not done yet (next sessions)

Render layer: NativeImage decode + GPU upload queue (budgets 5/frame,
1.5 ms), TileRenderer with zoom-0 under-layer, parent fallback,
coverage-sticky LOD swap. Then WorldMapScreen, MinimapHud,
MapEditorScreen, `/zeamap` + keybinds, lifecycle wiring (schedulers,
world resolution, generation), RepoMapClient, claim-command chat
integration, and the visual checklist on the live server. Still
nothing committed (spec: commit only on request).

## 2026-11-15 (session 12) — Map subsystem Phase 3: the pure render core

The render layer's decision-making half: everything that decides *what
a frame contains*, still free of Minecraft types so every visual policy
is pinned by tests. The only Minecraft-touching part left for later is
the thin adapter that draws the planned quads and uploads decoded
images.

### `map.render.tile.Coverage` (new)

The sticky gate between the Quick Map (zoom-0 under-layer) and the
Detailed layer: activate only at full visible coverage, then stay
active through dips (a pan into fresh territory, an eviction) while
individual gaps fill from parent fallback — the `8ce45767` mosaic
lesson. Stickiness is scoped to one (world, zoom); a pan does not
re-arm, a zoom or world change does, and `rearm()` serves cache
clears.
- Semantics settled while testing: re-arming means full coverage is
  *required again* — it does not delay activation by a frame when the
  new view is already fully covered (that would flash the coarser
  layer for nothing). An empty view (0 slots) never activates on the
  vacuous truth.
- A slot covered by 404 (confirmed absent: no finer imagery exists at
  any level) counts as coverage; a slot merely *backed by an ancestor*
  does not — that is exactly the blurry state the gate exists to keep
  off screen.

### `map.render.tile.TileRenderer<T>` (new)

The frame planner, a pure function of camera + cache state + motion:
- **Frame naming**: quads are emitted in map-frame pixels (view-centre
  relative, world-north-up, unrotated) so the adapter sets up one
  rotated pose per frame instead of one transform per quad.
- **Layers**: zoom-0 overview always planned first (the guarantee that
  something drawable exists), Detailed at `chooseTileZoom` behind the
  coverage gate; missing slots fall back to their nearest drawable
  ancestor above zoom 0 (zoom 0 would duplicate the under-layer).
- **Requests**: 28/frame idle, 2 while moving, centre-out, never
  outside the world's published extent. Idle frames prefetch one zoom
  finer and one coarser plus a margin ring; moving frames spend the
  tiny budget on the pan lead (0.75 viewports ahead) — spending it on
  tiles about to scroll out is the stutter it prevents.
- **Lead semantics pinned by tests**: the lead rect overlaps the
  current view, and READY tiles are never re-requested, so on an empty
  cache the moving budget serves the lead's nearest (in-view) tiles,
  and only a served view lets it reach true out-of-view lead tiles.
  Both behaviours are asserted, not assumed.
- Degenerate views (zero-size surface, non-positive ppb) plan
  `FramePlan.empty()` — previously a 0×0 surface planned 1 slot, and a
  non-positive ppb would send the visible-tile range to infinity.

### `map.render.tile.TileUploads<T>` (new)

The render thread's half of the cache contract: drains the upload
queue within budgets (5 uploads / 1.5 ms idle, 1 / 750 µs moving,
always at least one so a trickle still progresses), sends rejected
uploads down the failed ladder instead of wedging the queue, and frees
retired images (`disposeRetired`) — the fetch thread never frees image
memory (`aa92179f`).

### `map.render.overlay.ClaimTessellator` (new)

Rings → merged fill runs + edge runs: rectilinear scanline between
distinct vertex rows (EarthMC edges sit at chunk corners, so bands are
whole-chunk), runs extended vertically while the span recurs, stale
open-runs purged across empty bands so two bars with a gap never
bridge into one (the half-drawn-town class of bug), even-odd over all
rings so enclosed pockets stay holes, and a whole-town ceiling of
8192 counted runs+edges that drops the town entirely — never a partial
band (`b92d190`). Results cached by geometry signature *and* instance
identity (256 towns, cleared whole), riding ClaimStore's instance
reuse across refreshes (`a5570c6f`).

### `map.render.overlay.ClaimOverlay` / `PlayerOverlay` (new)

Draw-list planners in map-frame pixels:
- Claims: bounding-box culling against the padded view (16 blocks of
  slack), run clipping to it, constant-*pixel* outline thickness
  centred on edges, outline decimation below 2/16 px per block
  (`10118a2`'s noise floor), per-layer alpha switches.
- Players: the glide between feed snapshots over exactly the previous
  feed interval (one period of latency, never extrapolated — dots
  glide instead of stepping at the feed cadence), the teleport guard
  at half the view span (a bigger step snaps; NEU's rule), and the
  live self override replacing the feed's own stale entry.

### MapProjection

`FULL_REGION` constant added (the whole-tile UV rect for direct
quads); no behaviour change.

### Bugs found and fixed during development

All in this session's own new code and tests — the Phase 2 core
needed no changes:
- **Test expectations written against imagined geometry**: the quad-
  position test used a tile outside the visible set with zoom-0 span
  math; the world-bounds test asserted Earth's half-*width* on the Z
  axis too (the real half-height is 32,256, not 64,512).
- **The camera clamp discovery**: `MapCamera` clamps ppb to
  [1/64, 8], so `chooseTileZoom` can never return 0 through a camera
  — the "zoom-0 view" test was silently testing zoom 1. Replaced with
  the reachable boundary: at the camera floor the view chooses zoom 1
  and fetches the zoom-0 under-layer as coarser prefetch.
- **Coverage re-arm misread** (see above): the first draft of the
  tests demanded a one-frame activation delay after a view change
  that the (correct) implementation deliberately doesn't have.
- **Signature collision by construction**: the cache-flush test's
  loop town #0 was geometry-identical to the town under test (same
  key, same ring) and overwrote its cache slot — the loop now offsets
  every town.
- **Edge culling forgotten in expectations**: the layer-switch test
  centred the camera so the town's south and east edges fell outside
  the padded view and were (correctly) culled.
- `TileRenderer.java` had been left in 8-space indentation; retabbed
  to the house tabs before commit.

### Verification

`./gradlew build` green; **907 tests, 0 failures** (was 864; +43:
TileRenderer 14, Coverage 7, ClaimTessellator 10, ClaimOverlay 7,
PlayerOverlay 5 — including 1638-run and 1639-run checkerboards
around the ceiling, and gap-bridging bar pairs around the merge
logic).

### Not done yet (next sessions)

The Minecraft-facing half of the render layer: NativeImage decode +
`GpuTexture` upload through TileUploads' budgets, the quad-drawing
adapter (one rotated pose, `blit` with direct UVs), WorldMapScreen,
MinimapHud (HUD layer registration), MapEditorScreen, `/zeamap` +
keybinds, lifecycle wiring (schedulers, world resolution, generation
bumps), RepoMapClient, claim-command chat integration, and the visual
checklist on the live server.

## 2026-11-15 (session 13) — The chat rebuilt on the Chatting model, and the tooltip crash re-fixed

### The sandbox recycle, and what the rebase revealed

This session's sandbox was recycled and handed back the repo at 79409e7;
the transcript recorded four later commits (the map subsystem, the
tooltip-crash fix, the chat live requests, the inventory buttons
rebuild), and `git fsck` found none of them on disk. This session
rebuilt what was reconstructable — the tooltip crash fix (its two
one-line edits and the regression invariant were fully known) and the
whole Chatting-parity chat rework below — and then the push corrected
the picture: the map subsystem (sessions 10-12 above) had reached
origin from the surviving copy, and the rebase landed it back beneath
this session's commits; the inventory-utilities rebuild (d6b0d00,
buttons v2) had been in the shared history all along. The one genuinely
missing piece is the chat live-request batch's trade pacing (the
five-minute limiter with its toast); it stays on the backlog.

### Part 1: the rarity tooltip crash, re-fixed

`ContainerScreenMixin.zea$rarityTooltip` and
`CreativeScreenMixin.zea$rarityTooltip` both take a
`CallbackInfoReturnable` and write the edited list back with
`cir.setReturnValue(...)` — without `cancellable = true`, which makes
the first frame that runs the hook throw and the whole screen crash
(the "insane crashes" live report). Both handlers now declare the flag.
The invariant is pinned by `InjectionCancellableTest`: every compiled
`@Inject` handler taking a `CallbackInfoReturnable` must declare
`cancellable = true`, checked against the build's class files the way
`ChatWidgetsFocusTest` checks the focus overrides — the bug class fails
the build instead of the game.

### Part 2: the chat, rebuilt 1:1 on Polyfrost's Chatting

The player pointed at Chatting as the reference for the search bar, the
search and clear buttons, and "the chat smoothness thing". Chatting is
GPL-3.0 while ZEA is LGPL-3.0, so nothing was copied — the behavior was
read off its sources (research/chatting) and re-implemented against
ZEA's own seams, with every hook verified against the 26.3 bytecode
(vineflower decompile of the mapped jar).

**Search bar.** Chatting's field: one quarter of the screen wide, 12
pixels tall, on the row above the input (the tab strip's row), its
right edge `n * 11 + 12` pixels in from the screen edge, a vanilla
bordered `EditBox` (no flat restyle, no hint), 100-character ceiling.
It shows when a search opens and its space stays reserved either way,
so opening a search never reflows the tab strip; the strip's maximum
now reserves the box plus the buttons' geometry with two gaps' worth of
clearance.

**Search button functionality.** Chatting's open/close dance exactly:
opening silences vanilla's command suggestions (`setAllowSuggestions(false)`
— not just hiding them, because the async completion callback reshows a
hidden popup), moves the caret from the input (which vanilla builds to
never lose focus) to the search field; closing hands the caret back,
re-arms the suggestions and refreshes them for the input's text. The
search closes when the field loses focus — Chatting's rule, polled per
frame from the render path — and on the screen going away. One
documented deviation: focus landing on ZEA's own row widgets (the tab
strip, the action buttons) does not close the search, because a search
composes with the selected feed; Chatting's tabs are drawn directly on
the screen and never take focus, so its close never sees this case.
Ctrl+F and the search hotkey still work alongside Chatting's button.

**Clear history button.** Chatting's action (`clearMessages(false)`,
keeping the up-arrow recall) was already ZEA's, extended over ZEA's
persistent store; what changed is the look — the button now draws a
trash can (lid, body, slits — own pixels, Chatting's icon in spirit)
and both buttons show Chatting's single yellow-line tooltips
("Search Chat" / "Clear Chat History"). The buttons moved to Chatting's
exact grid: 9×9 on an 11-pixel pitch, rightmost starting at
`width - 12`, vertically centered in the 12-pixel row (offset 2).

**Smooth chat (arrival).** `SmoothChat` was rebuilt on Chatting's
window model: each arriving message opens one animation window — the
whole chat starts one line low and glides up on Chatting's quartic
ease-out while the message's own wrapped lines fade in — replacing the
old shift-remainder model (which glided the push of N lines without a
fade). Back-to-back messages restart the window and drop the first's
fade set, exactly Chatting's behavior. The offset stays zero while the
player is scrolled up (vanilla pins the view; translating it would push
pinned content) but the fade still applies. The fade rides a single
`@ModifyArgs` on `forEachLine`'s per-line `accept` call, which covers
both the background pass and the text pass in one injection — the
consumer interface is private to ChatComponent, so a wrap's handler
could not name it, while the argument shape needs no private type. The
line height comes from `ChatGeometry.entryHeight` (vanilla's own
formula), which is Chatting's constant 9 when the spacing option is at
its default — the honest generalization of the same curve.

**Smooth scrolling.** New: Chatting's `ChatScrolling` model, the piece
ZEA never had. The renderer's scroll position is an animated float
chasing the vanilla anchor on a quadratic ease-out: armed only by real
scroll events (`scrollChat` head, guarded against the add path's
internal pin scroll), gliding only jumps of more than one line (a
single notch snaps, as Chatting ships), riding along with the anchor
shifts a burst of arriving messages produces, and clamped at zero.
Three `@ModifyExpressionValue` injections re-route exactly the render
reads — the line-indexing pair in `forEachLine` and the scrollbar's one
— while every write, the pin scroll and the new-message flag keep the
raw anchor. Because the animation moves content inside the chat's own
clip, nothing outside the chat element moves; the dev.26 drift that
killed the previous pose-translate scroll experiment cannot recur.
The step runs at the private `extractRenderState` head, which both the
visible path and the click-capture path flow through, so hit-testing
reads the position the player sees.

**Config.** "Smooth chat duration" defaults to Chatting's 500 ms;
new "Smooth scrolling" (on) and "Smooth scrolling duration" (150 ms)
options with the same 50–500 slider range; the smooth-chat option text
no longer claims scrolling stays instant.

### Test

`ChatScrollingTest` pins the glide: armed multi-line jumps animate from
the current position, single notches and unarmed jumps snap, the curve
is quadratic ease-out (checked with the stamp's own int-divided
progress), a mid-flight message shift carries the animation and still
lands on the shifted anchor, zero duration snaps everywhere, negative
positions clamp, and one arm animates exactly one jump.
`SmoothChatTest` was rewritten for the window model: the glide starts
one line low and settles exactly, the quartic curve, scrolled-up
stopping the glide but not the fade, only the window's lines fading, a
second message restarting the window and dropping the first's set,
zero duration disabling, and the percent curve itself.
`ZeaConfigTest` pins the new defaults; `MixinSurfaceTest` pins the new
surfaces (`forEachLine`, the private `extractRenderState` overload,
`LineConsumer.accept` — the private interfaces load reflectively — and
the suggestion popup's `commandSuggestions` field and methods).

`./gradlew build` green in the rebased tree; **918 tests, 0 failures**
(session 12 left 907; the chat work adds 11). In-game
verification of the search field's look on the tab row, the scroll
glide's feel at both GUI scales, and the arrival fade's density on
busy channels is the live checklist for the next session.

## 2026-11-16 (session 14) — The chat live requests (rebuilt), and the inventory buttons on the NEU model

### Part 1: the chat live requests, rebuilt and landed

The sandbox recycle had also taken the unread/filter/trade batch (its
commits were invalid objects), so it was rebuilt on the same design and
pushed (`8014296`): **unread tab indicators** (a message arriving on a
non-selected visible tab turns its label yellow until the player selects
it; General lights for everything, as an aggregate should; state
survives screen rebuilds and clears on disconnect), the **General
broadcast filter** (optional hiding of vote, crate-win, death and system
messages in the General tab only — unclassified messages are never
swallowed, and hidden messages stay retrievable through the retention
wrap and their own tabs), and **trade pacing** (EarthMC allows one trade
message every five minutes, so every outgoing `tradechat` send passes a
gate on the one choke point all sends share —
`ClientPacketListener.sendCommand`; outside the window it sends and
starts the window, inside it queues, up to five, for the auto-send drain
or refuses with a toast explaining the wait; a status line above the
chat tabs shows the cooldown and queue, and clicking it toggles
auto-send). Two bugs the first run of tests caught: the gate's blank
check tested the whole command line instead of the message after the
command (a bare `/tradechat` is a channel switch, not a send), and the
send mixin's enum switch compiled into a synthetic class in the mixin
package — the hygiene test rejected it, so the mixin now branches with
plain equality.

### Part 2: the inventory buttons, rebuilt on the Inventory-Buttons model

The player pointed at afranz29/Inventory-Buttons (the LGPLv3 modern
port of NEU's buttons) as what they had meant all along — free-placed
buttons and the NEU editor, not the region rows ZEA had guessed. Both
repos are LGPLv3, so the design was adapted directly from its source
(read in full: the config/store, the 1376-line editor, the container
mixin pair, the command suggestor), skipping the parts the player
excluded (profiles, skull IDs, the Hypixel item getter).

- **Free placement** (`InventoryButton` v3): a stored offset from the
  container corner plus right/bottom edge anchors — a button docked
  beside the GUI stays beside it on every container width — and a
  background style index. Positions are authored on the standard
  inventory canvas (176×166); `ButtonGeometry` resolves them per screen:
  the player's own inventory renders everything as placed, other
  containers hide unanchored buttons parked in the container-slot band
  (they would cover live slots) and shift the inventory band down by the
  container's extra height, exactly like the inventory rows do.
- **Migrations** (`InventoryButtonStore` v3): v1 corner anchors
  translate directly (both models are free offsets — positions survive
  exactly); v2 regions resolve to the exact pixels the old region
  layout computed for those orders, anchored to the edges they hugged,
  so no existing button moves. The store reads every era's fields from
  one permissive entry record.
- **The editor** (`ButtonEditorScreen`, new): the survival inventory
  drawn as a canvas with every button at its stored position; click to
  select, click again to open the hover panel (command field with
  server-backed tab completion through the adapted `CommandSuggestor`,
  name, five background styles, a searchable icon picker with
  All/Items/Blocks/Custom tabs), drag to move (free, with the S key
  toggling snap to the inventory's fixed slots inside and the 20-pixel
  ring grid outside; drops past the canvas edges dock to them; landing
  on another button is refused), right-click empty canvas to create,
  Backspace to delete, Escape to leave. A preset row top-left offers
  the three templates. In-container editing is gone — the keybind now
  opens this screen — which deleted the in-place drag/drop machinery
  from the container mixin.
- **Textures** (new): ZEA's own `buttons.png` — five 18×18 vanilla-style
  bevel faces (stone, dark stone, dirt, gold, obsidian) generated by
  `scripts/gen_button_texture.py` — and ten 16×16 custom icon glyphs
  (cross, checks, question, settings, search, armor, pet, recipe,
  baubles). The in-container renderer blits the styled face instead of
  drawing the flat box the player called ugly.
- **Icon picker** (`IconPicker`, new): pure ranking model — favorites
  first in their own order, prefix hits before substring hits, ties
  alphabetical, filter tabs narrowing the pool.
- **Cleanup**: ButtonRegion/ButtonAnchor/ButtonListScreen/ButtonEditScreen
  deleted; the per-region config toggles are gone (free placement has
  nothing to toggle per edge); the share payload's wire format carries
  the v3 fields; presets build ring positions.

### Verification

`./gradlew cleanTest test` green; **947 tests, 0 failures** (the chat
batch left 945; the buttons batch rewrites the suite for the new model:
geometry 13, store 10 with the v1→v3 and v2→v3 migrations, form 8,
presets 6, picker 7, drag 7, button 10, coordinator 12, editing 5).
The texture sheet verified programmatically: five distinct palette
centers, full opacity, 90×18. In-game verification — the editor flow,
the suggestor against EarthMC's command tree, the styled buttons at
every GUI scale, and foreign-container placement — is the live
checklist for the next session.

## 2026-11-16 (session 15) — The dev.35 chat row: "G…" tabs and the always-open search

### The live report

Two symptoms, one root. Every chat tab rendered as "G…" (unreadable),
and the search field was visible on every fresh chat screen instead of
appearing on Ctrl+F. Both landed in dev.34's Chatting-geometry pass.

### The hunt

The search box, one quarter of the screen wide, is positioned at the
row's right end beside the action buttons — and its space was
**reserved whether or not a search was open** ("so opening a search
never reflows the strip"). The tab strip's maximum is the chat panel's
width (`ChatComponent.getWidth`, floor(option·280+40) — 320 px by
default), so the reserve left the strip 320 − screen/4 − ~35 px: on a
960-px GUI, **45 pixels for every tab**. The layout policy then
shortened every label to one character plus its ellipsis — measured
against the real 26.3 font metrics (`scripts/tab_font_sim.py` reads the
bitmap providers out of the loom-cached jar and recomputes each glyph's
advance exactly as `BitmapProvider` does: seven default labels are 198
px of text, which is why defaults had looked fine in review and any
real screen broke). The second bug was simpler: the bar's `visible` was
only ever set `true` in the restore branch, and `AbstractWidget.visible`
defaults `true` — a fresh screen drew the box with nothing behind it.

### The fix

- **The box is not drawn and its space is not reserved while no search
  is open.** Closed, the strip gets the panel width minus the buttons;
  open, it reflows live to the row that remains left of the box
  (`ChatTabBar.setMaxWidth` recomputes padding, scroll and extent; the
  selected tab stays in view). One documented deviation from Chatting,
  which can afford the reserve because its tabs sit under a wide panel.
- **Labels are never shortened.** `TabLayout` compresses padding toward
  its floor and then hands the overflow to the caller: the strip
  **scrolls by whole tabs** — small 9×9 flat arrows at its right end
  (the action buttons' idiom) shown while actionable, the mouse wheel
  over the strip scrolling it ahead of vanilla's chat scroll, and the
  selected tab always kept in the window. `window`, `maxScrollOffset`
  and `revealOffset` are pure and pinned by tests; the strip clamps
  scrolling so the last tab is always reachable (alone if nothing fits
  beside it).
- The fresh screen hides the search bar (`visible = openQuery != null`);
  labels center in their tabs at any padding; the arrows carry the only
  tooltips (`zea.chat.tabs.scroll_left/right`).

### Verification

`./gradlew build` green; **954 tests, 0 failures** (the layout suite
grew by seven: never-shortened labels, window counts and the single-tab
floor, the last-tab reachability both ways, reveal's three moves).
Font-accurate simulation of the reported setup (960-px GUI, default
tabs, search closed): 295-px budget, all seven tabs full labels at
natural padding; thirteen tabs scroll with eight visible; with a search
open the strip spans the row up to the box. In-game verification — the
arrows' hit boxes, the wheel over the strip versus the chat log, and
the reflow on Ctrl+F — is the live checklist for the next session.

## 2026-11-16 (session 16) — Map subsystem Phase 4a: GPU tile textures and the quad-drawing adapter

The Minecraft-facing half of the render layer begins — the two thin
adapters everything visual will flow through, written against the
actual 26.3 API surface, not remembered from the 1.21.x era. That
surface was verified against the loom-cached jar before a line was
written: `DrawContext` no longer exists (`GuiGraphicsExtractor` is the
drawing context, `pose()` a JOML `Matrix3x2fStack`), the render
back end moved to `com.mojang.renderpearl`, and
`blit(GpuTextureView, GpuSampler, x, y, w, h, u0, v0, u1, v1)` takes
normalized 0..1 UVs — exactly the tile regions the pure core already
emits, so no coordinate conversion exists in the adapter at all.
Vanilla's own `map(MapRenderState)` draws a whole 128×128 texture
through `0, 1, 0, 1`, which is what pinned that last fact.

### `map.render.tile.TileTextures` (new)

The render-side half of the tile cache's ownership contract, and
nothing else: the class *is* the `TileUploads.Sink` and the
`TileCache.Disposer`, so production wiring is
`uploads.run(cache, textures, moving, clock)` plus
`TileUploads.disposeRetired(cache, textures)` with nothing between.
Vanilla's `DynamicTexture` is the create-texture → take-view →
`writeToTexture` pattern (RGBA8, usage copy-dst | texture-binding,
one mip level, texture closed before view — vanilla's own release
order). ZEA's tiles differ from vanilla's dynamic textures in one
respect that shapes the class: a decoded tile is a full megabyte of
pixel memory, so the CPU image is closed the moment its pixels reach
the GPU. The cache keeps the emptied husk as its drawable identity,
and the second `close` when the cache later retires it is the guarded
no-op `NativeImage.close` already is (the `pixels != 0` guard is
visible in the bytecode). Failure is refusal, never death: a null
texture from a GL error, a rejected allocation, or an image that is
not a tile-shaped rectangle returns false and the tile retries on the
cache's failed ladder — the queue cannot wedge on one bad image. The
1024-px edge cap is the poisoned-mirror defense: PNG compresses solid
colour to almost nothing, so a hostile tile host could hide an 8192²
texture inside a few kilobytes, and the upload — not the decode — is
where that would surface. The sampler is clamp-to-edge + linear:
parent-fallback regions sample to the very edge of a tile, and repeat
addressing would filter the tile's opposite side into the seam.

### `map.render.tile.TileFrameDrawer` (new)

The quad-drawing adapter, built exactly around the planner's frame
naming: one rotated pose per frame — translate to the surface centre,
rotate by the projection's angle — and every quad draws axis-aligned
inside it. The pose composition is pinned by a test that transforms a
grid of points through the composed JOML matrix and demands
`MapProjection.screenX/screenY` agree to the float epsilon, across
eight rotations including both sign conventions; the adapter cannot
drift from the projection's own pinned math. The seam defense is the
second pinned policy: vanilla's blit takes whole-pixel rectangles,
so each quad *edge* rounds independently and width always follows from
the snapped edges, never rounds alone — neighbouring quads share their
edge coordinate exactly by construction (asserted against a real
planned frame), so they snap to the same integer edge, and the affine
pose maps a shared edge to one shared coordinate on the far side.
Scissoring deliberately stays with the screen that owns the layout;
the drawer knows only the surface centre and the rotation.

### A circular test, caught and cut

The first full-stack test recovered each quad's world rectangle *from
the quad*, ran it through the projection, and compared the result back
— proving the projection's inverse of itself, nothing more. Deleted
and replaced by the plan-level edge-sharing check above; the honest
chain is the planner's own position pins, the pose-equivalence test,
and the snap edge rule, which together imply the end-to-end property
the fake test claimed to check.

### A replacement race, found re-reading the disposal paths

Landed, then re-read, then fixed in a follow-up commit: a tile id is
reused across image generations — an ETag revalidation hit queues a
*replacement* for a slot that already draws — and the cache's disposal
of the replaced image arrives a frame after the replacement's upload.
Freeing the GPU handle by tile id alone destroyed the freshly
uploaded texture while the cache still reported the tile READY: the
tile would vanish from the map until its next revalidation. Every
handle now remembers the image instance it was uploaded from, and a
disposal only frees the handle that exact image owns — reference
equality, the same instance-identity discipline the claim tessellator
cache rides on ClaimStore. Verified order-independent for the
eviction-after-replacement double disposal.

### Verification

`./gradlew build` green; **959 tests, 0 failures** at landing, **987,
0 failures** after rebasing onto the parallel RPG-tooltip batch that
landed as dev.36–38 in between (was 954; +5 from this session: the
half-pixel bound over a coordinate sweep, adjacency under fractional
offsets, the round-half-toward-positive-infinity tie rule, pose ≡
projection across eight rotations, and plan-level edge sharing).
Bytecode-verified against the 26.3 jar: the blit overload's normalized
UVs, the GUI_TEXTURED pipeline under the view blit, `NativeImage.close`'s
double-free guard, and `DynamicTexture`'s texture-then-view release
order. In-game verification — the first tiles on screen — waits for
the screen that will call this code.

### Not done yet (next sessions)

WorldMapScreen (the first caller of the drawer, with scissor and
input), MinimapHud (HUD layer registration), MapEditorScreen, `/zeamap`
+ keybinds, lifecycle wiring (schedulers, world resolution, generation
bumps), RepoMapClient, claim-command chat integration, and the visual
checklist on the live server.

## 2026-11-15 (session 17) — ZEA Market: the physical-shop marketplace, end to end

The full ZEA Market feature landed as ten commits: models, response
parser, sign correlation, persistence, pricing, the shop screen, the
browser, tooltips, and the submission queue.

### The shape of it

A sign right-click (observed in the game-mode mixin, never modified)
notes a pending use — position, attached chest, the sign's own text.
Shop-shaped lines that follow start or extend a response run attributed
to that use; the run closes on the first unrelated message or a quiet
gap and parses into an inspection. A use that expires without a
response falls back to its sign text. Shop-shaped lines with no pending
use are ignored entirely — the market never records a shop nobody
inspected, and unrelated chat is never touched.

The response parser anchors on the observed line shapes
(`Owner: `, `Stock: `, `Price per 7x ... - 1G`, `This shop is SELLING
items.`) and reads the `[Item Preview]` hover structurally: 26.3's
show_item carries the full item template, and identity fingerprints
that wire data (item id + component patch, count normalized away)
instead of a resolved stack — which also keeps the whole path testable
in a bootstrapped-but-not-data-loaded JVM, where the documented
"Components not bound yet" constraint rules out ItemStack construction.
A missing field degrades the response to partial; nothing is guessed.

### Decisions worth recording

- **Shop identity is server + world + sign position.** The chest is
  data, never identity: a chest resolved on a later visit must not fork
  the shop in two.
- **Direction is named from the shop's point of view** (`SELLING` means
  the player buys) exactly as the server phrases it, and every
  player-facing label derives from it — the two sides cannot silently
  reverse.
- **Prices are BigDecimal end to end**, stored as exact decimal text;
  normalization divides at a fixed documented scale (half-even), totals
  multiply exactly.
- **One offer per shop, from its latest observation.** An old cheaper
  measurement never outranks the shop's current price; history stays
  available for display.
- **No recognized success/failure transaction messages.** Rather than
  guess outcome shapes and claim results the client cannot verify, the
  state machine ends a send in TIMED_OUT — outcome unknown, no retry,
  never a false success. The transitions grow from real captures.
- **The ZEA-REPO submission side is a local validated queue.** The
  repository has no shop dataset/schema/manifest entry; inventing a
  remote endpoint is forbidden by the feature's own contract, so the
  queue stores validated, deduplicated, timestamp-preserving batches
  and documents the missing contract in docs/MARKET.md.

### Verification

`./gradlew test` green — 1113 tests, 0 failures across the feature's
parser fixtures (both example responses from the feature brief, bulk
sizes 7/64, `You can sell 0`, explicit `all`, decimal and comma prices,
malformed variants), correlation (delayed, duplicate, out-of-window,
two-click ownership, sign-only fallback), identity (fingerprint
fidelity, count normalization), pricing (normalization across bulk
sizes, direction separation, per-shop latest, deterministic ties,
incomparable offers listed but unranked), persistence (round-trip,
per-entry tolerance, newer-file refusal, corruption recovery, damaged
prices degrade to none), transactions (bulk and item arithmetic,
server-maximum enforcement, duplicate-confirmation protection, timeout
without retry), submissions (sign-only refusal, dedupe, restart
survival) and the mixin hygiene suite. Compile verified against the
26.3 jar (`useItemOn`, `sendChat`/`sendCommand`, `HoverEvent.ShowItem`
→ `ItemStackTemplate`, `SignText.getMessages`, `EditBox` without
`setFilter`, `AbstractWidget.extractRenderState`).

Not yet verified in-game (no live EarthMC client here): the actual
sign-click → response → screen flow against the real server, the
screens' rendering (the layout is drawn but never seen), and the
server's real component tree for the shop response. The parser and
correlator were built to tolerate all of that being different on first
contact; the field-test capture tool (`/zea capture`) is the ready
instrument for the real session.

## 2026-11-16 (session 18) — The dev.36 QOL batch: Global as the aggregate, server-synced trade pacing, PM replies, the caret, Tab, and the hotbar wash

### The live report

Six asks in one message: remove the General channel and let Global show
everything with per-type switches; stop the PMs tab from crashing and
make it reply instead; pace trade sends by the server's own wait lines
(not a forced client window) with a visible, deletable queue; keep the
chat focused while typing; restore Tab completion; show the rarity wash
in the hotbar; and the tab palette — yellow selected, white unread,
gray unselected-and-read.

### General is gone; Global is the aggregate

`Feeds.ALL` is deleted. `Feeds.GLOBAL` now carries every category
(unclassified included) — the aggregate the player asked for — and what
it hides is the user's per-type choice: a new **Global tab** subcategory
with one switch per kind of message (Local, Trade, Nation, Town, PMs,
Discord, votes, crate wins, deaths, system), all on by default. Two
kinds are deliberately not switchable: Global's own channel, and
UNCLASSIFIED — the "an unknown format must never vanish from the
aggregate" rule survives the redesign (a new server format that
disappears reads as messages being eaten). The choice lives in a new
pure model, `GlobalVisibility`, read live by both the feed filter (so a
settings change applies to the next message without re-selecting
anything) and the unread tracker (a hidden type does not light the
Global tab — a yellow tab with nothing new behind it is a lie). Config
version 3 migrates the old single "hide server broadcasts in General"
switch into the four per-type opt-outs it actually meant; everything
else carries over. Default selection and the disconnect reset now land
on Global.

### The PMs tab replies instead of crashing

The live crash: a plain message typed on the PMs tab fell through ZEA's
routing (PMs has no channel) into vanilla plain chat — which on EarthMC
writes to the writing channel, never what a PMs tab means, and took the
client down in the live session. The tab no longer falls through at
all: a new `PrivateMessaging` model tracks the partner of the last
exchange (an incoming PM sets the sender; the echo of the player's own
PM — sent from the tab or typed as `/msg` — sets the recipient, exactly
the server's `/r` rule), and the PMs tab sends `/r <msg>` through the
interval-spaced dispatcher once a partner exists. Before that the send
is refused with a toast ("No one to reply to yet"), and vanilla plain
chat is never reached from that tab. A manual `/msg <player> <msg>`
keeps working exactly as typed — vanilla command path — and its echo
lands on the PMs tab and becomes the reply target.

### Trade pacing: the server's clock, not the client's guess

The old gate forced a five-minute window from the moment a send went
out. The new `TradeGate` holds no opinion until the server speaks: with
no window known, a trade send goes straight out and is remembered as
unresolved. EarthMC's rejection — `» You must wait another 4 seconds
before sending your next message in Trade.` (minutes-and-seconds form
included) — is now classified (`TradeWaitClassifier`, SYSTEM category,
`TradeWait` payload; added to the sealed `MessagePayload` permits), and
that one line drives everything: the window syncs to the reported
remaining time, the refused message moves into the queue (the player's
words are never lost), and the line itself is hidden from every feed
while pacing is on (retained like anything ZEA hides; it reappears if
pacing is turned off). The status line counts down the server's
numbers.

Resolution closes the loop from both ends: the player's own trade
message echoing back (TaggedChannelChat with the local name) resolves
the oldest unresolved send as accepted, and entries older than fifteen
seconds settle as accepted on their own — display names can be
nicknames, so the echo check must have a net under it. While a window
runs, new trade messages queue (auto-send on) or are refused with the
server-reported wait (off) — the old behavior, but timed by the server.
The drain's sends pass the same choke point, so a re-rejected auto-send
re-syncs and re-queues itself; the loop converges on the server's own
clock.

### The queue is visible and editable

Clicking the status line now opens a `TradeQueuePanel` above it: one
row per held message with a delete button, the auto-send toggle (the
status line's old click action, moved where it has room to explain
itself), and a close button. Rows re-read the queue every frame, so
deletions and the drain reflow live; long messages truncate with an
ellipsis and carry the full text as a tooltip. Left-clicks on the panel
are routed to it ahead of vanilla's clickable-chat pass (vanilla checks
message components before widgets, and a link under the panel must not
win); Escape closes the panel before it closes the screen.

### The caret, and Tab completion — one root, two bugs

26.3's container click handler moves screen focus onto whatever widget
consumed a click (`shouldTakeFocusAfterInteraction`, true by default),
which clears the chat input's focused flag — after one tab click, typed
characters reached no widget until the input was clicked again. The
dev.24 fix (refusing `nextFocusPath`) covered only the keyboard path.
Every mouse-driven chat widget now overrides
`shouldTakeFocusAfterInteraction` to false; the search bar keeps the
default, because clicking into a text field should focus it.

Tab had the opposite problem: the HEAD injection consumed it to spare
the focus cycle, which sat **ahead of vanilla's command-suggestion
completion** — the first act of `ChatScreen.keyPressed` — so completion
never saw the key ("tab-completion doesn't really quite work"). The
consumption moved to an injection at the `Screen.keyPressed` call site:
completion runs first, and only a Tab it did not want is consumed,
before the focus navigation that would clear the screen's focus.

### The rarity wash in the hotbar

The tint was injected into `AbstractContainerScreen.extractSlot` —
containers only. The HUD draws every hotbar and off-hand item through
one private slot pass, `Hud.extractSlot`; a new `HudMixin` draws the
same `RarityEngine` wash at its head, so texture, count, durability bar
and glint stay on top. Gated by the rarity master switch and a new
"Hotbar tint" switch (on by default) beside the slot tint.

### The palette

`ChatTabBar.labelColor`: yellow selected, white unread, gray
unselected-and-read. The multi-send target's thin marker strip stays —
it carries the targeting state the aqua label used to.

### Verification

`./gradlew build` green after rebasing over the dev.36–dev.54 map and
market work: **1,150 tests, 0 failures** — this batch contributed 37
(the trade
gate's server-synced suite, the wait-line classifier's shapes and
near-misses, GlobalVisibility's toggleable set and never-hidden pair,
PrivateMessaging's partner rules, the Global-tab filter and unread
suites, the v3 migrations both ways, and the bytecode pins for
`shouldTakeFocusAfterInteraction` and `Hud.extractSlot`). The live
checklist for the next session: the PMs tab before and after a PM
arrives; a trade send that gets rejected (wait line hidden, message
queued, status line counting the server's time, panel delete working);
typing after clicking a tab; `/tr` + Tab; the hotbar wash in the world.

## 2026-11-16 (session 19) — The RPG tooltips verified in the live 26.3 client, and three bugs they showed

The RPG stat batch (dev.36–40, plus the dev.46 calculation corrections)
was pinned by 1,000+ unit tests but had never been *seen*. This session
closed that gap: the dev client was booted for real — Xvfb for the
display, SDL forced onto EGL, the renderer falling back to lavapipe
Vulkan after the GLX visual probe failed — then driven through world
creation and inventory hovers with synthetic input, every frame
inspected from screenshots. Criterion 12 of the task ("do not claim
visual success without actually checking the rendered output") is now
dischargeable with receipts.

### The driving harness

The client daemonizes with the exact argfile the loom cache builds, so
it survives tool-call teardown; input goes through XTEST (pyautogui
against :99), screenshots through x11grab, and the reads through the
vision model. The non-obvious part was geometry: at this window size
the game runs a *fractional* effective GUI scale (the F3 line reads
"854x480, 1499x… pixel (density)"), so naive "GUI scale 2" arithmetic
lands clicks one slot off. Slot rectangles, button rows and tooltip
lines were therefore measured from the pixels themselves — border-run
detection, saturation clustering, per-row luminance dumps — and every
hover position cross-checked against the game's own cursor sprite
before trusting a read. The survival hotbar row, for what it is worth,
sits at screen y≈478–518 on this window, not the y≈560 the first
estimates produced; the search bar owns that lower band, which is why
early "hotbar hovers" silently showed nothing.

### What the rendered tooltips now show, verified line by line

- **Diamond chestplate (Protection IV):** Armor 8, Toughness 2,
  "Protection IV (most damage): -16%", Durability 528/528 — every
  number identical to vanilla's own attribute listing, which the stat
  block replaces. UNCOMMON label in green.
- **Diamond sword (five maxed enchantments):** "Damage: 10 (7 base +
  3 Sharpness V)", Attack Speed 1.6, Sweeping Damage Ratio 0.75,
  Durability 1561/1561 with "effective ~6244" (Unbreaking III's ×4),
  EPIC label — and this is the item the chroma was watched on.
- **Mace (Density V, Breach IV, Wind Burst III):** the full smash
  presentation — "+6 past 1.5 blocks fallen" with the piecewise band
  breakdown beneath it, Density +2.5/block, Breach -60% armor
  effectiveness, the Wind Burst flag, 500/500 durability.
- **Golden apple:** Nutrition 4, Saturation 9.6, Can Always Eat, and
  the live effect labels "Regeneration II (0:05)" / "Absorption
  (2:00)"; a plain apple shows nothing but nutrition and saturation —
  no weapon statistics on food, no empty sections.
- **Chroma, both states:** with the animation on, the maxed
  enchantment lines rotate through the eight-second hue wheel —
  captured orange→green on the chestplate's Protection IV and
  yellow-green→violet on the sword's five lines seconds apart; with
  it off, the same lines wear the static gold. The name line keeps
  vanilla's aqua (name coloring is off by default), which is exactly
  what made the first chroma measurement measure the wrong line: the
  frozen "cyan" was the item *name*, not the enchantment.

### Three bugs the screenshots caught, all fixed

**Breach never rendered.** The mace read Breach from the generic
attribute-modifier path, but 26.3 ships it as the dedicated
`ARMOR_EFFECTIVENESS` effect component (-0.15 × level); the live
effect read replaced the lookup, and the line appeared on the next
launch.

**The vanilla attribute section survived on armor.** The removal
matched a hardcoded header list last true before 26.3 renamed
"When on Body:" to "When on Chest:" and added the group headers
("When worn:", "When equipped:", "When held:", "When saddled:").
Headers are now matched by their translation key — a translatable
component under `item.modifiers.*` — which covers every current and
future slot group and cannot mistake plain-text lore for a header.

**Two stat lines clipped at the screen edge.** Vanilla tooltips clip
rather than wrap, and the smash attack's inline band breakdown and the
durability line's inline effective maximum both ran past the window's
right edge at this width. Both details now render as grey indented
lines beneath the number they qualify; nothing clips.

### Verification

`./gradlew test` green throughout: **1,062 tests, 0 failures** at
landing (up from 1,059; +1 Breach live read, +2 translation-key
header matching and its lore-lookalike counter-test, and the
presentation tests rewritten against the detail-line layout), and
**1,153, 0 failures** after rebasing onto the parallel dev.36 QOL
batch that landed in between. The
runtime pass covered the sword, mace, chestplate, golden apple and
plain apple tooltips, both chroma states, and the rarity labels —
in-world, in the real 26.3 client, from the rendered pixels.

### Not done yet (next sessions)

The bow/crossbow/spear/pickaxe tooltips carry dev.46's corrected
formulas but have not been re-shot since the corrections landed
(they were verified in the pre-correction client); the shift-held
missing-upgrade block and the Ctrl-held advanced-info mode need the
same live pass, and EarthMC's own server lore should be exercised
against the lore-preservation rules once the addon runs there.

## 2026-11-16 (session 15) — The button zones and the bottom search bar

The chat half of the dev.36 live requests shipped from the parallel
session as dev.54 (`d34afb4`); this session's remaining half — the
search bar and the button zones — lands on top of dev.57, re-based.

- **The container search bar spans the screen's bottom edge.** It used
  to hug the GUI's lower edge, where it read as part of the inventory;
  now it sits at `screenHeight - 12`, GUI-wide and left-aligned with
  the container, with the live re-placement on resize and recipe-book
  toggles.
- **Matching mode cannot take a frame down.** The two crash candidates
  from the live report ("typing crashed", "double-clicking crashed
  instead of highlighting") are defused: an out-of-range slot index
  returns an empty haystack instead of indexing past the menu's slot
  list, and a stack whose tooltip explodes on this client degrades to
  its hover name — both cached like any other text.
- **Buttons live in nine capacity-limited zones** (the "not floating
  in air" spec): 8 on top of the inventory, 8 up each side
  bottom-aligned to the hotbar's level, 8 under the hotbar row, 9 in
  the row directly above the search bar, 1 inside the inventory's
  top-right corner, and 4 just under the 2×2 crafting grid (that row
  and the corner are inventory-only). `InventoryButton` stores zone +
  order — the pixel offsets and anchors are gone; `ButtonGeometry`
  computes every slot from the live screen, the drop magnetically docks
  to the nearest free slot, occupied slots refuse, and zones that
  cannot render (foreign container, recipe book, short screen) hide
  their buttons instead of overlapping.
- **Schema v4** with a geometric migration: every older era (v1 corner
  anchors, v2 regions, v3 free offsets) resolves to the exact pixels
  its formulas produced, and those positions are classified into the
  zones their geometry lived in, orders re-assigned sequentially per
  zone so the migration can never collide. Unknown zone names in a v4
  file are re-filed into the right column with a warning. The share
  wire format carries zone + order.
- **The editor's S-snap is retired** — the zones are the snap. Drag
  previews dock to the target slot, right-click creates in the nearest
  free slot, and the panel edits the working copy as before.

### Verification

`./gradlew build` and the full suite pass on the re-based tree (the
zone math, drop target, migrations, presets and share round-trips are
pinned by the updated button test classes).

## 2026-11-16 (session 18) — The market browser rebuilt: the listings interface the feature wanted

The first browser was a 460x360 shell — thin search, three tabs, an
empty rectangle — the presentation landed before the interface it
presents existed. This session rebuilt the presentation on top of the
(already real) observation pipeline, and added the one model piece the
old screen had papered over.

### What changed

- **The listing model.** One row per physical shop — an EarthMC shop
  trades one item in one direction, so one shop is one listing, and a
  sign that flips direction updates the same listing instead of
  splitting it. The row's advert is the shop's latest server-verified
  observation, falling back to its latest sign-only advert with the
  source type preserved. The grouped search model was removed rather
  than maintained twice.
- **Direction-aware sorting.** Price sorts put the player's best offer
  first — cheapest when buying from, highest payout when selling to —
  with unjudgeable listings (no price, no scope, per-bulk without a
  bulk size) sinking below the judged ones in both directions and ties
  breaking by shop id. The comparator's own tests caught a real bug:
  reversing the whole chain would have floated unjudgeable rows to the
  top on the sell-to side.
- **Town and nation from claim geometry.** A resolver seam over the
  claim store: real point-in-claim tests at the sign position, no text
  guessing. The claim cache is constructed in ZeaClient so the map
  subsystem's pending fetch pipeline can share the instance; until
  claims flow, town and nation are simply unavailable — never guessed.
- **The browser itself.** Adaptive window (~72% of the logical screen,
  capped 1120x720, floors capped by the screen itself so a large GUI
  scale fills instead of overflowing — verified by arithmetic at scale
  1/2/3 and small-window sizes), header with refresh/close, full-width
  search with inline clear, the three views wired to the query
  pipeline, a cycling sort control, and the two-panel main area:
  dense rows (identity icon, direction tag, offer, shop line, stale
  marking) and the full details panel with both timestamps, source
  confidence, and the nation spawn action that exists only when a
  nation is known and dispatches the real command. Selection follows
  shop identity across refreshes; arrows walk the list; wheel scrolls.
  Rendering reads only the in-memory index — no parsing, disk,
  network or world access per frame.

### Verification

`./gradlew test` green — 1183 tests, 0 failures across the whole suite (+18 new this session: listing model
composition, filter sides, search over item/owner/town, sign-only
labelling, town-lookup plumbing, sort orders including the
direction-aware price rules and the unjudgeable sink, tie
determinism, cycle order). Three of those tests initially asserted the
wrong thing and the implementation was right: a flipped sign moves the
shop to the other view; the raw-price sort legitimately ranks a priced
advert that lacks a bulk size; the town lookup receives (x, z) and no
y. Not verified in-game (no live client): rendering itself; the
geometry is verified by calculation, the interactions by unit tests of
the pipeline beneath them.

## 2026-11-17 (session 20) — Inventory UI defects, EarthMC Only, and the map surfaces wired

### The report

Five defects against dev.61: the inventory/container search bar
"extremely thin and visually incorrect" (a reference mock supplied:
404x44 px on a 602x644 screenshot), buttons visible in the editor but
missing in the actual inventory, editor button text overflowing its
boxes, no EarthMC recognition or "EarthMC Only" idle notice, and the
map overlay and minimap not appearing at all.

### The hunt

- **The search bar** was 12 GUI units tall, GUI-wide, left-aligned,
  and still carried its placeholder hint. The mock, measured
  programmatically, is 404x44 physical px at GUI scale 2 with a black
  1-unit frame around a translucent dark body: **202x22 GUI units,
  horizontally centered, five units off the bottom edge**.
- **The buttons'** two render paths disagreed by design: the editor
  drew every button, while the container view required `isUsable()` —
  a valid *command*. Every button the editor creates (a fresh button,
  every preset entry) starts with a blank command, so nothing
  configured through the normal flow ever reached the real inventory.
- **The editor's face** drew `button.text()` at natural font size —
  eight characters is ~48 px across an 18-px button — while the
  container view always scaled text to fit. Two face renderers for
  the same button.
- **EarthMC recognition** existed and was correct (domain-boundary
  matching, tested), but nothing exposed an "EarthMC Only" switch and
  nothing logged the idle notice.
- **The map** had pure pipelines, GPU adapters and input math — all
  tested — but no surface consumed them: no subsystem, no HUD
  registration, no world-map screen, no `/zeamap`, no keybind. The
  last session's own checklist said it: "rendering itself; not
  verified in-game".

### The fix

- **Search bar** (`SearchBarWidget`, `ContainerScreenMixin`,
  `ButtonGeometry`): 202x22 with a one-pixel black frame, bottom
  centered, five units off the edge, width clamped on narrow windows,
  y never leaving the screen; the placeholder hint is gone — field
  and lang key. The matching-mode gold outline now rings the frame.
  `ButtonGeometry`'s bar footprint constants follow (22 + the 5-unit
  margin) so the ABOVE_SEARCH zone hangs above the real bar. A
  borderless EditBox places its text at the widget's top edge
  (`textY = getY()`), so a small `EditBoxMixin` re-centers the text
  vertically — scoped to `SearchBarWidget` instances; every other
  EditBox keeps vanilla placement.
- **Buttons** (`ButtonRenderer`, `ButtonEditorScreen`): the container
  view now skips only unrendered or *disabled* placements — a button
  without a command draws, its tooltip says "Not configured yet",
  and clicking it is refused by the activator exactly as before
  (pinned by the existing `unconfiguredButtonsDoNotRun` test). One
  shared `ButtonRenderer.drawFace` draws every button in both the
  container and the editor: text scaled to fit the 18x18 box, else
  the icon, else a "?" — the editor preview is pixel-exact at every
  GUI scale.
- **EarthMC Only** (`ZeaConfig.Miscellaneous`, `ClientLifecycle`): a
  new mod-wide switch, off by default. On, and connected to a server
  whose connect address is not EarthMC, ZEA logs NEU's notice once
  per connection —
  `[ZEA] Idle on <address>: the address is not recognised as EarthMC
  and "EarthMC Only" is on, so no ZEA functionality has been enabled
  on this server.`
  — from the join handler, never per tick, re-evaluated on every
  join and leave. `ServerIdentity.normalizeHost` also handles
  bracketed `[host]:port` and leaves bare IPv6 literals intact.
- **The map, wired** (`MapSubsystem` + `MinimapHud` +
  `WorldMapScreen` + `OverlayDrawer`): the facade owns the caches,
  the squaremap client, the tile fetcher and one `TileFrameDriver`
  per surface (each with its own sticky-coverage `TileRenderer`);
  every frame runs through the driver — disposals, uploads, plan,
  requests, in its one correct order — and draws through the existing
  `TileFrameDrawer`, with the new `OverlayDrawer` drawing claims and
  markers under the same pose and pixel snapping. Scheduled refresh
  runs on the client tick (markers 60 s; the player feed 1 s while a
  surface is drawn, 15 s idle — `MapRefreshPolicy`, pure and pinned
  by tests), futures marshal back through `Minecraft.execute`, and
  stale-tile ETag revalidation trickles four tiles per tick. The
  minimap registers through Fabric's `HudElementRegistry` (the 26.3
  replacement for HudRenderCallback): square or circle (a generated
  alpha mask — outside opaque, ring stroke, inside clear), claims and
  players, coordinates and a round-number scale bar under it
  (`MinimapLayout`, pure). Unsupported dimensions (the Nether, the
  End) show a disabled panel or hide per the config — never fake
  data. `/zeamap` and the new M keybind open `WorldMapScreen`: drag
  pans and scroll zooms through the pure `MapCameraInput`, +/-
  zoom the center, F follows, W switches Earth and Moon; per-world
  cameras persist in the subsystem, freshness sits in the corner.
  The map's own "Only on EarthMC" (default on) still gates it, and
  the mod-wide EarthMC Only wins over it — the combined rule is
  `MapSubsystem.featureActive`, pinned by tests. The v4-API claim
  geometry overrides stay unwired (squaremap markers drive claims
  this pass); the `ClaimStore` seam for them is untouched.

### Verification

`./gradlew build` green; **1217 tests, 0 failures** (was 1214; the
detector matrix grew subdomains, trailing-dot-with-port, uppercase
and port, attacker-suffix and fake-domain rejections; `ServerIdentity`
gained bracketed-IPv6 cases; map gating, refresh policy, minimap
layout and a construction/camera smoke for `MapSubsystem` itself; the
ABOVE_SEARCH zone expectations moved with the bar's new footprint).
The search bar's dimensions verified against the reference mock
programmatically (pixel-measured, converted to GUI units). The
packaged jar carries the new classes, the registered `EditBoxMixin`
and the lang keys. Not verified in-game (no live client here): the
bar at each GUI scale, buttons from editor to real inventory across
container types, the idle notice on a non-EarthMC server, and both
map surfaces against live EarthMC data — the live checklist.

## 2026-11-17 (session 21) — The RPG tooltip repaired onto the equipment allowlist

The tooltip task came back as a repair order with an acceptance sheet,
and the audit confirmed every regression it listed. The classifier
admitted four categories the spec excludes outright — food, shields,
shears, elytra — so a golden apple carried a nutrition block and a
shield showed a rarity footer. The stat lines explained their own
arithmetic ("Damage: 10 (7 base + 3 Sharpness V)"), sweeping printed
as a raw ratio, durability carried an Unbreaking-inflated "effective
~6244" beside the real maximum, every enchantment consumed a line of
prose, the footer said "EPIC" with no equipment type, custom names
were skipped by the recolor path, and a Ctrl-held diagnostic mode
reimplemented — badly — what F3+H already does. Bows, crossbows,
tridents, maces and spears carried stats but no rarity at all.

### The repair

Three commits. The classifier now answers exactly the spec's
allowlist — the four tool families, the six weapon families, the four
armor slots — and the tooltip editor asks it once, first, so an
unsupported item returns before anything touches its lines: name,
lore, enchantment rendering, advanced diagnostics, all byte-for-byte
vanilla. The elytra resolves to OTHER through the armor-attribute
test that already separated it from a real chestplate.

The stat lines render final values only — "Damage: +10 [5]" with the
contributing enchantment's level in the bracket, "+75%" for the sweep
ratio, seconds for draw and reload times, current/maximum for
durability and nothing else. Enchantments group into one
alphabetized, comma-wrapped section of blue names (red for curses,
the maxed tier color at the cap, grey for foreign command-placed
entries). The footer is "EPIC SWORD", bold, rarity-colored, inserted
before Minecraft's own advanced lines. Custom names keep their exact
text and take the rarity color on a rendering copy; the stack is
never modified. The Ctrl mode is gone. Tridents gained their melee
and thrown numbers from the 26.3 bytecode — flat 8.0 thrown damage,
2.5 blocks per tick, 10-tick charge — and the dedicated weapon
classes now rate (diamond-class tier) so a maxed bow reaches
Legendary.

Two formula corrections came out of the audit itself: the base mining
speed reads the tool's correct-for-drops family rule (8.0 on a
diamond pickaxe) rather than the 1.0 wrong-tool default the same
component carries, and Impaling stays a conditional line rather than
folding into the trident's thrown damage.

### The two bugs only the live client could show

The first F3+H pass rendered the RPG block and the footer but the
native trailer was *gone* — no `minecraft:diamond_sword`, no
component count — while a plain diamond kept both. The bytecode told
the story: 26.3's attribute emitter writes its blank in front of the
"When in Main Hand" header and nothing after the entries, so with
advanced tooltips on the identifier and the count follow the entries
directly, and the removal — which scanned to the next blank —
swallowed them whole. The removal now consumes the emitter's leading
blank with the section, stops at the next blank, another section's
header or any native advanced line, and repeats for the "When worn:"
group family.

The second bug was a constant: the identifier literal is styled
`ChatFormatting.DARK_GRAY`, 0x555555, and the matcher carried
0x3F3F3F — the pixel dump of the rendered tooltip showed (85,85,85),
the identifier never matched, and with it unrecognized the scan
walked straight through it even after the first fix. Both are now
pinned by removal-seam tests against a marker predicate, and the
final layout was read back from the screen: RPG block, "EPIC SWORD",
`minecraft:diamond_sword`, "19 component(s)", in that order.

### Verification, line by line from the pixels

Every supported family hovered and transcribed through the vision
model: sword (enchanted, plain, custom-named), pickaxe, axe, shovel,
hoe, bow, crossbow, trident, mace, spear, and all four armor slots.
The unsupported matrix — golden apple, enchanted book, potion,
shield, elytra, spawn egg, diamond block, plain/named/lore diamonds —
kept pure vanilla tooltips, lore text intact, no footer, no recolor.
The long enchantment list wraps at comma boundaries into two clean
lines; conditional enchantments state their condition (Smite V "(vs
undead): +12.5", Impaling "(vs aquatic mobs)") instead of folding
into the damage. The player died mid-session and dropped the lot, so
the matrix was re-given and re-read — the reads above are from the
rebuilt inventory.

`./gradlew test` green throughout: **1225 tests, 0 failures** on the
rebased tree (+13 over the session: the equipment exclusions, the
compact formats, the grouped section, the trident throw, the
rule-based mining speed, the footer label, and the four
attribute-removal seams).

## 2026-11-17 (session 16, P0 port) — The shop-quantity chat bypass

### The regression

Channel routing rewrote the shop transaction's quantity: EarthMC's
physical shops collect the quantity as the next plain chat message
("Enter in chat, how many you wish to BUY."), and `zea$sendToTargets`
turned that "16" into "/global 16" on the Global tab — a garbled
channel message the shop never reads. This is the release-blocking
defect the live report opened with.

### The fix

`ShopTransactionInput` — a one-shot pending state in the chat subsystem:
the explicit quantity prompt arms it (armed in the capture pipeline
before any display filter can hide the line), the next eligible plain
message consumes it, and `zea$sendToTargets` deliberately does not
intercept that message — vanilla's own `connection.sendChat` sends it
exactly as typed. No prefix, no duplication, no recall of the state;
slash commands and blank text are never consumed and never consume it;
the state expires after 30 s and clears on disconnect; re-arming with a
new prompt refreshes it. The selected channel is never changed.

### Verification

`./gradlew test` green; **1231 tests, 0 failures** (this branch's suite
plus the new `ShopTransactionInputTest`: prompt recognition in both
directions and case/padding variants, quantities and `all` bypassing
exactly as typed, slash commands and blank text leaving the state armed,
expiry, disconnect clearing, re-arm refresh). In-game verification with
both channel selections, BUY and SELL flows, and the normal-message
checklist afterward is the live checklist for the next session.
## 2026-11-17 (session 16) — Shop transactions, the HUD manager, discovery, and the UI corrections

### The live report

A release-blocking regression and a large batch of features arrived
together: shop quantity inputs were being destroyed by ZEA's channel
routing; the inventory search bar was a thin unstyled strip; the button
editor's canvas showed no buttons and its preset labels spilled out of
their boxes; the channel strip's overflow arrows read as a generic
"next" control; and the report asked for `/zeahud` as a universal HUD
manager, four new message-filter categories, click-to-visit spawn
commands, the organized config directory, native discovery, and a
local-first ZEA-REPO integration.

### The shop routing fix (P0)

The root cause: `ChatScreenMixin.zea$sendToTargets` rewrites every
plain message typed on a channel tab into `/global <msg>` — and EarthMC
physical shops collect the transaction quantity as the next plain chat
message, so typing "16" at a shop prompt sent "/global 16" and the shop
never read it. The fix is a one-shot `ShopTransactionInput` state in the
chat subsystem: the explicit prompt ("Enter in chat, how many you wish
to BUY/SELL") arms it, the next eligible plain message consumes it and
the mixin deliberately does not intercept — vanilla's own
`connection.sendChat` sends the raw quantity. Slash commands, blank
text, and multi-send targets are never consumed; the state expires in
30 s and clears on disconnect. Arming happens in the capture pipeline
before any display filter, so a filter can never starve it.

### Search bar, editor preview, label fitting (P1)

The bar was a 12-px borderless `EditBox` with a hint string: no sprite,
no border, text flush against the left edge (vanilla drops the 4 px
padding when `bordered(false)`). It is now a real vanilla text field —
the `widget/text_field` sprites, height 20, no placeholder, a hover
frame, the gold matching-mode outline drawn around the field — positioned
by the shared `SearchBarLayout` (centered on the GUI's bounds, one gap
below), and `ButtonGeometry` reserves that strip for bottom-anchored
buttons so they never overlap it.

The editor's canvas now previews reality: the survival inventory
texture with the vanilla player-model helper and the player inventory
menu's actual stacks; opened from a container, the container's live
dimensions and its menu's real slots through a new `ContainerScreenAccess`
duck; buttons resolve through `ButtonGeometry.placements` with the same
inputs the runtime uses; the search bar preview is the runtime widget
class at the runtime layout math; the drag round-trips through the
runtime band mapping. Label fitting moved into `ButtonRenderer.fitLabel`
(pure, font metrics, scale floor then ellipsis) shared by runtime faces,
the editor canvas, and the preset row that was leaking "Buttons
Everywhere"; truncated labels surface the full text in the tooltip.

### Channel strip (P2)

The overflow arrows lost their button boxes: quiet chevron glyphs at the
strip's right end, shown only while actionable, with the wheel still
scrolling and the selected tab always revealed. The strip stays the
channel selector; no second generic control.

### /zeahud (P3)

A new `hud` package: `HudElementSpec`/`HudRenderer`/`HudElementState`/
`HudLayoutMath`/`HudSubsystem` — one registration call adds an element
with anchor-based positioning, visibility, scale, persistence in
`hud.json` (safe-write, unknown elements' entries carried across), and
one Fabric HUD slot that draws them all. `HudManagerScreen` previews
every element live over a dimmed game, lists them, supports drag
repositioning (the drop converts back to anchor + edge offsets and the
round-trip is pinned by tests), visibility, reset, and a scale slider.
The map engine (`map/MapEngine`) finally wires the pure map stack to
real rendering — tiles fetched through the existing budgeted planner,
uploaded through the cache's claim/disposal discipline, claims and
players through the tested overlay planners — and two elements, Minimap
and Map overlay, register through it.

### Message filters and click-to-visit (P4, P5)

Four structure-anchored classifiers (crate "got lucky" announcements,
Discord recruitment ads, bare-command promo banners, renamed-item
showcase ads) join the existing pipeline before the channel shapes, each
with its own toggle under Chat → Message filters and hiding only on the
General feed through the established `FeedFilter.Installed` retention
mechanism. `SpawnCommandLinks` rebuilds a message from its styled runs
(every run's style verbatim, existing interactions never overwritten)
with `/n spawn`/`/t spawn` click events dispatching the raw command —
gated by the new `Click-to-visit spawn commands` toggle, off by default.

### Config layout, discovery, repository (P6–P8)

`ZeaPaths` moves the main config to `config/zea/config.json` with a
one-time backup-and-move migration (pinned by tests), keeps every
existing store path, and adds `market/` for the discovery stores.
`/zeadiscovery on|off|status` gates its own capture: shop-sign clicks
arm a `ShopTransactionCollector` that parses the server's shop block
(owner, item, direction, stock vs. per-transaction quantity, price,
enchantments) into `ShopObservation` records keyed by physical identity
`host|dimension|signPos` — same-owner-same-item shops stay separate by
construction; navigation observations record executed spawn commands
with before/arrival coordinates and never leave the machine. The
ZEA-REPO client fetches the repository's generated manifest, picks up a
shop dataset when the community adds one, validates and merges through
`ObservationMerge` (local-newer-wins, timestamp-less never overwrites,
invalidations hold) and caches what it saw.

### Verification

`./gradlew clean build` green; **1043 tests, 0 failures** (the session
added ~90: shop bypass, fit policy, layout math, HUD documents, four
classifier suites, spawn-link preservation rules, the live shop block,
identity, the merge cases 1–5, collector association, store persistence,
manifest parsing, and the config migration). In-game verification —
the shop purchase flow at both channel selections, the search bar and
editor previews at several GUI scales, `/zeahud` drag and persistence,
the map elements against live squaremap imagery, the filters against
real chat, and a full restart round-trip — is the live checklist for
the next session. The `JDK 25` requirement (fabric-loom 1.19.4) needs
`-Dorg.gradle.java.home` pointed at a Java 25 runtime on this machine.
## 2026-11-17 (session 16) — Search bar: the typing-interrupt fix, NEC calculator, dimmed empties

### The live report

Three complaints on one screen, worked on the fresh
`fix/search-bar-fixes` branch off develop (6f6455c):

1. Typing **e** into the search bar interrupted typing — the letter
   never landed.
2. The empty inventory/container slot did not darken the way item
   slots do (in matching mode).
3. The calculator "sucked": a bare `2` echoed `= 2`, and the result
   hung at the far end of the bar instead of next to the expression —
   with a pointer to port/adapt Rijzzz' NotEnoughCalculator (the JEI
   search-bar calculator, LGPL-3.0) for the ZEA bar.

### The hunt

- **The E interrupt was ZEA's own "politeness".** The key router
  treated the inventory key specially while the bar was focused:
  defocus instead of close. But the default inventory key *is* E, so
  every attempt to type an "e" yanked focus before the character event
  could land. Vanilla already solved this exact problem for the
  creative search box
  (`CreativeModeInventoryScreen.keyPressed`, decompiled from the
  loom-cached 26.3 jar): the field gets the key first, and a field
  that `capturesInput()` swallows everything else — letters arrive as
  `charTyped`, and `super.keyPressed` (with the E-to-close) is simply
  never reached.
- **The dim gap was deliberate-but-wrong.** The search-dim injection
  carried `!slot.getItem().isEmpty()` — "empty slots are left alone,
  there is nothing to find in them". The NEU research notes in this
  very file say it plainly: *empty slots darken too*. ZEA's deviation
  was the bug.
- **The calculator** was a clean but minimal shunting-yard (`+ - * /`,
  parens, unary minus) that echoed bare numbers and right-aligned the
  result. NEC (cloned to `research/nec`, read in full:
  `CalculatorManager`, `ExpressionTokenizer`, `ExpressionParser`,
  `ExpressionEvaluator`, `ResultFormatter`, `SyntaxHighlighter`,
  `REIOverlayRenderer`) proved both behaviors wrong on paper: NEC's
  `looksLikeCalculation` gate rejects bare numbers ("usually an item
  count search, not math"), and its overlay draws the result at
  `textX + queryWidth` — immediately after the typed text — with a
  floating tooltip box as the overflow fallback.

### The fix

- **`ContainerScreenMixin.zea$handleKeys`** now follows vanilla's
  creative rule: Esc first (matching mode, then focus), then
  `bar.keyPressed(event)` for the editing keys, then
  `bar.capturesInput()` captures every remaining key. The inventory
  key defocus special-case is gone — typing `e` inserts an `e`; Esc
  or a click outside defocuses; E-to-close works again once the bar
  is unfocused; hotbar swaps stay dead mid-typing.
- **The dim injection** drops the `isEmpty` guard: in matching mode
  every non-matching slot dims, empty ones included — NEU parity,
  documented.
- **`Calculator` is rebuilt on NEC's grammar** (lexer → implicit-
  multiplication pass → recursive descent with NEC's precedence
  chain, BigDecimal with a 50-digit context): `^` (right-assoc),
  `!`, glued-`%` percentage vs spaced modulo (`100+10%` = 110),
  bitwise `& | ~ << >>`, `x`/`X` as multiplication, implicit
  multiplication, `0b/0x/0o` literals (radix in → radix out:
  `0x1F` shows `0x1F`), units `k m b t s st stack stacks`, `pi`/`e`,
  `ans`, `$vars` (session-scoped, reserved names refuse), and the
  NEC function set minus the SkyBlock tables/taxes/units — `sqrt abs
  floor ceil round log ln sin cos tan rad deg min max gcd lcm xor
  pct clamp avg hex bin oct fmt`. NEC's gate (`looksLikeCalculation`)
  and its incomplete-expression suppression are the entry point, so
  bare numbers, plain words and half-typed expressions show nothing.
  NEC quirks kept deliberately: `%` after `)` is modulo (so `(1+1)%`
  is incomplete), radix literals propagate. NEC's SkyBlock surface —
  XP tables, bz/ah taxes, the `e/h/sc/dc/eb` units — is dropped as
  meaningless off SkyBlock; `$vars` stay session-scoped instead of
  NEC's config persistence.
- **`SearchBarWidget`** draws the result after the *visible* text
  (an `EditBoxAccessor` mixin exposes `displayPos`/`textX`, because
  the field scrolls long input and vanilla exposes neither), and
  when it no longer fits beside the expression, into NEC's floating
  dark box under the bar (above it when the screen ends first) with
  the same 4-second sine marquee. A `TextFormatter` (26.3's
  `EditBox.addFormatter`) colors the input with NEC's palette —
  runs built with `FormattedCharSequence.forward` + `composite`, from
  a per-character color map computed over the whole value so tokens
  split by the caret still color correctly; matching mode keeps the
  plain white query.
- `zea.mixins.json` registers `EditBoxAccessor`;
  `MixinSurfaceTest` pins `EditBox#displayPos/textX`;
  `MixinPackageHygieneTest` already proves the accessor shape is
  loadable (registered, interface, all-@Accessor).

### Verification

`./gradlew test` green: **975 tests, 0 failures** (the calculator
suite grew from 12 cases to 33: the gate, units, constants, radix,
functions, percentages, bitwise, implicit multiplication, `$vars`,
`ans`, formatting, and the old precedence/paren/negative regression
set — five first-draft expectations were corrected against traced
NEC semantics: comma grouping on `2^10`, radix carry on `0x1F`, and
the paren-percent quirk). In-game verification — typing `e` into the
bar on a container screen, the dimmed empty slots in matching mode,
`2+2 = 4` beside the expression, and the floating box on a long
result — is the live checklist for the next session.

## 2026-11-17 — Branch consolidation: three feature branches into develop

### The report

"Push and then merge branches and then delete old branch. Do not delete
without merging into develop, and do not delete the develop branch."
Three unmerged branches sat beside develop (which had advanced to
dev.63 while they waited): `fix/search-bar-fixes` (the search bar
typing/calculator/dimming fixes), `fix/shop-quantity-chat-bypass` (the
P0 shop quantity routing fix, based on dev.62), and
`session16-parallel-implementation` (the HUD manager, native discovery
with the ZEA-REPO consumer, spawn command links, the advertisement
message filters, ZeaPaths, and its own shop fix and search bar
rebuild, based on dev.35).

### The merges

Oldest-base last, each verified with the full suite before the next:

1. `fix/shop-quantity-chat-bypass` merged clean — its base (dev.62)
   was three commits behind develop's head.
2. `session16-parallel-implementation` needed 17 conflict resolutions.
   Where develop had superseded the parallel session's architecture,
   develop won: the button system stays on the zone model (develop's
   dev.36+ rebuild; session16's free-placement editor changes were
   written against the pre-zone editor), and ChatTabBar keeps
   develop's focus fix and test-pinned label colors. Where session16's
   work was additive, it merged in: the HUD manager, discovery, map
   engine, spawn links, the four ad-category classifiers and filters,
   ZeaPaths, `ButtonRenderer.fitLabel` (the readability floor +
   ellipsis policy, ported into develop's renderer and pinned by
   `ButtonRendererFitTest`), the preset row's fitted labels, and
   `SearchBarLayout`. `FeedFilter` grew a fifth constructor argument —
   the hidden-ad-categories supplier — composing with develop's
   `GlobalVisibility` instead of replacing it (one control per kind).
   The ad categories are hidden from Global through their own toggles,
   so `GlobalVisibility.TOGGLEABLE` stays as develop pinned it and the
   test excludes them with a documented reason. The search bar kept
   develop's zone-friendly vertical (near the screen bottom, so
   UNDER_HOTBAR and ABOVE_SEARCH keep their geometry) and adopted the
   P1 appearance: vanilla text-field sprites, hover frame, no
   placeholder, GUI-relative horizontal centering through the shared
   `SearchBarLayout` (ButtonGeometry's `SEARCH_BAR_HEIGHT` follows the
   sprite field's 20). `MultiPlayerGameModeMixin` keeps both
   observers (market at HEAD, discovery at TAIL); `MessagePayload`
   unions both sides' payloads.
3. `fix/search-bar-fixes` needed three conflict resolutions. The
   container mixin's key routing and empty-slot dimming applied clean
   on top. `SearchBarWidget` was combined by hand: the session16
   foundation (bordered sprite field, no placeholder, shared layout)
   now carries the NEC result overlay — inline after the typed text
   through `EditBoxAccessor`'s scroll state, the floating marquee
   fallback box, and the token-class syntax highlighter. Vanilla 26.3's
   own `EditBox.capturesInput()` backs the "e" rule.

### Verification

`./gradlew build` green after each merge; the suite read **1314 tests,
0 failures** after the session16 merge and **1335 tests, 0 failures**
after the search bar merge (+21: the calculator, layout and label-fit
tests, less the superseded duplicates). All three branch tips verified
as ancestors of develop before any deletion.

### Deletions

`fix/search-bar-fixes`, `fix/shop-quantity-chat-bypass` and
`session16-parallel-implementation` deleted on the remote and locally
after the containment check; `develop` kept, as instructed.

## 2026-11-17 — The editor canvas: the live inventory and the search bar preview

### The live report

Two symptoms on the button editor, straight off the user's screenshot:
the inventory behind the buttons was blank (texture only — the player's
actual items never drew), and the search bar was nowhere in the editor,
so a button parked near it landed blind. The SearchBarLayout javadoc
already promised the editor preview ("any preview (the button editor's
canvas) both go through these functions") — nobody had wired it.

### The fix

- **The canvas is the real inventory.** `ButtonEditorScreen` now draws
  every live slot of the player's menu onto the texture —
  `inventoryMenu.slots` at their vanilla coordinates, item plus
  `itemDecorations` for counts, durability and cooldown — before the
  buttons, so a button renders over what the player is actually
  carrying.
- **The search bar previews in its real footprint.** The editor builds
  a render-only `SearchBarWidget` when the inventory search is enabled
  (calculator flag honored, the per-screen query mirrored, no responder)
  and positions it through the one `SearchBarLayout.apply` call the
  container screens use — never a child widget, so it cannot take
  focus, carets or clicks, and no editor keybinding changes.

### Verification

`./gradlew build` green; **1339 tests, 0 failures**. In-game
verification — the item layer under the buttons, the bar's footprint
against the above-search zone, and the mirrored query — is the live
checklist.

## 2026-11-17 (session 22) — The server-item tooltip polish: six fixes on a branch

The live EarthMC-style items exposed six presentation defects the local
matrix could not show, fixed on `fix/rpg-tooltip-bugs`:

- **Bow labels.** "Projectile Damage" and "Projectile Velocity" lost
  their qualifier on a bow — nothing else on a bow could own the
  numbers, so the lines read "Damage: +15 [5]" and "Velocity: 60
  blocks/s". Tridents and crossbows keep their distinguishing
  qualifiers; the bow's draw time value turned green (§a), the one
  bow number a faster-is-better eye tracks.
- **Names kept their server color.** The recolor only repainted the
  root style, but server custom names carry their color in child
  runs — an aqua "Diamond Sword" whose root is colorless stayed aqua
  while the footer below showed the rarity. The recolor now walks the
  whole component tree (`recolorTree`, built on `copy()` because
  `plainCopy()` resets the style), replacing every explicit color
  with the rarity's while preserving text, translations and non-color
  styling. The anvil-renamed root-styled case is pinned unchanged.
- **Duplicate durability.** The stat block's "Durability: 1354 /
  1561" and F3+H's native `item.durability` line under the footer
  said the same thing twice. The native line now goes whenever the
  stat block presents the pair; the identifier, component count and
  source footer keep their places and order.
- **The enchantment section widened the box.** The wrap budget was
  240 glyphs — wider than any stat line or advanced identifier the
  rest of the tooltip produces (~160), so five bow enchantments
  joined into one 239-glyph line. The budget is now 165: the sword's
  six wrap 2-2-2, the bow's five 4-1, and the box keeps the width
  the other content already demands.
- **Enchantment explanations survived.** Servers attach prose lore
  beside their enchantments ("Increases attack damage.", "The tool
  loses durability slower."). Vanilla 26.3 emits no description lines
  of its own (the bytecode shows `ItemEnchantments.addToTooltip`
  calls only `getFullname`), so these are server lore, and the
  compact section left them orphaned where the vanilla names had
  been. `EnchantmentExplanations` recognizes them two ways — the
  held enchantments' live synced `description()` text, and a bank of
  the observed phrases, matched as whole trimmed lines
  case-insensitively — and only recognized lines go; flavor and
  usage lore keep their place.

### Verification

`./gradlew build` green: **1237 tests, 0 failures** (+12: the bow
label and green draw-time pins, the explanation matcher, the tree
recolor through child runs and grandchildren, the durability dedup
with its plain-text lookalike, and the lore-removal seam with its
no-enchantments counter-case). Live in the 26.3 client: EarthMC-style
items were rebuilt with a datapack function (the chat input caps at
256 characters — the 373-character commands truncated into parser
errors, so the five `/give`s live in
`saves/New World (1)/datapacks/zea_test` and run as
`/function zea:testitems`). The rendered tooltips, read back from
the pixels: the aqua child-run "Diamond Sword" wears EPIC purple
with the §5 footer and no explanations, durability once, six
enchantments wrapped 2-2-2 in chroma; the aqua "Bow" wears LEGENDARY
gold with "Damage: +15 [5]", "Velocity: 60 blocks/s", a green "1s"
draw time and five chroma-wrapped enchantments; the plain enchanted
sword, the Protection IV chestplate and the untouched vanilla golden
apple all behave; the maxed enchantment colors rotate on the
eight-second hue wheel two-and-a-half seconds apart (blue-violet →
orange). One trap documented for the next session: `/reload`
rebuilds the enchantment registry, and `EnchantBudget`'s per-item
cache then holds stale holders — every enchantment reads foreign
gray and rarity falls to the material tier until the world is
re-entered; not a code bug, but a cache-invalidation seam worth
wiring to the reload event someday.

## 2026-11-17 (session 23) — The stat calculation layer: item, enchantment, wielder

The presentation was right and the numbers were not. The damage line
baked the player's fist into the item's contribution and showed the
enchantment's level where its damage belonged; the sweeping line
carried a stray level bracket; armor showed vanilla points where the
RPG scale wanted defense and explained Protection twice; Looting said
nothing; durability echoed its maximum back at full repair. All of it
repaired in place on the same pipeline — no UI rebuild.

### The root causes

The melee base came from `ItemAttributeModifiers.compute` starting at
the player's base (1.0), so "the item's damage" was already item plus
fist and there was no way to show either alone. The wielder had no
seat at the table at all: no context knew whether the hovered stack
was the one actually held, what the player's real base is, or which
attack-damage effects are running. Armor scaled nothing. Looting was
never asked for. The durability line printed both numbers always.

### The wielder context

A new `WielderContext` reads the player per tooltip: the live
main-hand stack (held = reference identity — an identical item
elsewhere is a different instance and stays spare), the attribute
bases via `getBaseValue` (never assumed 1.0), and the active
attack-damage effects with their per-level amounts from
`MobEffect.createModifiers` (Strength +3 per level, Weakness -4,
bytecode-verified in the 26.3 `MobEffects` static init). The engine
threads it through; the calculators stay component-free.

### The rules now

Damage splits by source: the item's own contribution is
`compute(base) - base`; Sharpness is the data-driven damage effect
(the 26.3 datapack writes `1 + 0.5 per level above first`, which is
`0.5 × level + 0.5`); the fist joins only when held; the named
effects join only when held, annotated `(+3 Strength)` in green. The
annotation set appears once the total has more than one contributor —
a spare unenchanted sword reads just "+6". Attack speed presents the
effective value (base plus main-hand modifiers; the live instance when
held) in yellow. Sweeping reads the live `sweeping_damage_ratio`
fraction as a plain percentage. Looting reads as Magic Find, one aqua
point per level, from the hovered stack's own component. Defense is
the Hypixel scale: armor points times ten plus ten per Protection
level, the actual level in the blue bracket, never clamped; Toughness
keeps its vanilla value under its renamed label; the specialized
protections (Fire Resistance, Explosion Resistance, Ranged Defense,
Fall Resistance) stay out of defense with the piece's EPF as the
value. The bow's velocity reads gold at "+60 Blocks/s". Durability
shows the maximum alone when fully repaired, current / maximum once
damaged. The stat block sits directly under the item name.

### One client-side trap found live

The integrated client's `ATTACK_DAMAGE` instance does not carry the
held item's equipment modifier (the attack-speed instance does) —
reading Strength as `value - base - item` produced a phantom "-6".
The effect bonus now sums the enumerated effect amounts instead, and
the instance's value is left alone. Found by an in-game delta of
"+4 (6) [5] (1) (-6)" against a server-reported attribute value of
7.0.

### Verification

`./gradlew test` green: **1367 tests, 0 failures** (+30 over the
merge baseline: the held/spare/Strength matrix, the reference-identity
seams, the keyed-holder protection reads, the Magic Find level
mapping, and the presentation pins for every new format rule). Live
in the 26.3 client, survival mode, items from
`run/saves/New World/datapacks/zea_test` (the dev run directory is
`run/`, not the repo root — the previous session's datapack landed in
the wrong tree and the world never saw it): the held Sharpness V
EarthMC sword reads "Damage: +13 (6) [5] (1) (+3 Strength)" with
Strength I active and "+10 (6) (1) (+3 Strength)" without Sharpness
on the plain sword; the same swords hovered as spares drop the fist
and the effect ("+9 (6) [5]", "+6"); the Protection V chestplate
reads "Defense: +130 [5]" with "Toughness: +2" and no protection
percentage; the bow reads "Velocity: +60 Blocks/s" in gold with a
green "1s"; durability shows maxima alone at full repair; the golden
apple keeps its vanilla tooltip; F3+H advanced diagnostics survive
under the rebuilt block. The creative screen's hotbar row renders
item tooltips without calling `getTooltipFromContainerItem` — the
verification runs through the survival inventory, whose slots take
the container hook; worth wiring the creative path someday if
hovering hotbars in creative matters.


## 2026-11-17 — The shop index: the NEU-style inventory browser and the legacy removal

### What replaced what

The standalone market system is gone: `/zeamarket`, `MarketScreen`,
`ShopScreen`/`ShopScreens`, `MarketTravel`, `MarketSort`,
`market.search`, and the entire `discovery` package (the broken
`/zea zeadiscovery` path — its mutable-POJO observation model, its
second capture loop, its `RepoClient` whose navigation hook read a
slash-prefixed command form the send path never produces). What
survived from the old work is the machinery that had actually proven
itself under tests: the response parser and its correlation tracker,
the fingerprint model, the shop/observation store, the pricing engine,
the submission queue, and the market tooltips.

### The parser now covers every observed response shape

The EarthMC fixtures pinned as regression tests — boxed blocks
(`| ... |` between `+---+` rules), plain blocks, `100% remaining.`,
`You can buy 4.` (an item limit, never confused with the stock of 47),
buying shops that price per bulk (`Price per 12x ... - 1G`) and report
their limit in bulks, and the server's enchantment section in both
styles — go past what the parser anchored on. New recorded fields:
`remainingPercent`, `maxBuyItems`, `maxSellBulks`, the enchantment
lines verbatim; store schema v2, additive and nullable. A per-item
selling shop is complete without a bulk size — the old rule demanded
one and degraded those responses to PARTIAL.

### Two identity bugs the fixtures flushed out

- The fingerprint codec ran against the built-in registry view only.
  Enchantments live in a data-driven registry since 26.3, so every
  enchanted item encoded as the same `unencodable` token — all
  enchanted variants of one item collapsed into one identity. The
  codec now runs against the level's registry view, and a patch that
  cannot canonically encode degrades to an unknown fingerprint rather
  than a token that would silently match a different product.
- The spawn-command hook assumed a slash-prefixed command; the send
  path strips it. The observer accepts both forms.

### The new pipeline, end to end

Sign click → response correlation → exact fingerprint → shop +
observation records (unchanged, now with the richer fields) →
`ItemVariantIndex` (one tile per exact token; offers are per shop and
never merged away) → `VariantPricing` (owner-balanced: one vote per
owner, best/worst/median over the votes, documented exactly) → the
`browser` package's inventory panel (tiles rendered from the variant's
own display stack — the token's encoded patch parsed back through the
codec against the level's registries; approximate tiles are marked) →
`nav`'s smart navigation (already-in-nation ⇒ waypoint only; town ⇒
`/t spawn`; nation ⇒ `/n spawn`; unknown ⇒ nothing invented; the
dispatch goes through the tracked send path; the waypoint clears on
inspection of that shop, on affirmatively leaving its nation, on
replacement, on session end — never on a timer) → `market.sync`'s
`RepoSync` (manifest-following, hash-gated download, isolated
validation, offline cache) → `DatasetMerger` (remote strictly newer
appends, ties keep local, stale skips, re-merges dedupe).

### Verification

`./gradlew build` green; **1347 tests, 0 failures**. The fixtures run
as parser regression tests whole-block and line-by-line; the merge
rules, the pricing balance, the navigation decisions and the
destination lifecycle are pinned by tests. In-game verification — the
panel against the GUI at every scale, the search-driven filter, the
detail view's offer rows, the Navigate flow against a live server —
remains the live checklist.

## 2026-11-17 — The NEU item pane, the migrated organization, and the derived-stat rework

### What the browser was, and what it became

The shop index beside the inventory was a small floating panel: its height
followed the container GUI's, its header was a 16-pixel strip with glyph
buttons, its grid scrolled by rows with the page hint tucked into the
corner, and selecting a tile blew the whole thing up into a centered modal.
None of that was NEU's item pane, so the pane is rebuilt on the actual
NEUOverlay layout and interaction model — studied from the upstream source
with its full history, not from screenshots: the mandatory commits
(9f372b3's keyboard-repeat lesson, 02019f3's search length, 5014caa's
null-safety, 0ca1609's dedup, cf9fee0's stack-override handling) all inform
the rebuild.

The geometry now is NEU's math adapted to the modern scaled coordinate
space: the pane anchors to the screen's right edge and spans the screen's
full height, a third of the screen wide; the grid centers inside it by
splitting the leftover width and height in half and paginates by NEU's
ceiling-based column and row counts; the tile hit box is the item plus its
one-pixel gutter, so a click in the stride's middle gap lands nowhere. The
nav element carries Prev and Next flanking the page pill (NEU's
renderNavElement), the sort bar carries the order icons from the left and
the direction filters from the right, scaled down on narrow panes the way
NEU scales them, and the pane gains a real close and NEU's edge tab to
reopen it. The detail view is NEU's info-pane model: the variant's offers
open inside the pane — back arrow, summary, grouped offer rows, the
explicit Navigate footer — never a centered modal. The layout math is
pinned to the values NEU's own formulas produce, computed by hand from the
upstream source, so a drift against NEU's model fails the geometry tests
first.

### The organization moved

ZEA and ZEA-REPO transferred from ZaminMC to ZaminEarthMCAddon through the
GitHub transfer API — history, branches, tags, releases, issues and the
Actions all preserved, old addresses redirecting. The organization profile
carries its location and description, the `.github` profile README
introduces the projects, and every stale reference — READMEs, schema
ids, the map client's user agent, the repo sync's raw URLs — now resolves
to the canonical location. `docs/ECOSYSTEM.md` records the mapping to the
upstream ecosystem and, deliberately, which companion repositories are
not built until reuse demands them.

### The derived-stat rework

The pickaxe tooltip read "Mining Speed: 8" and then "Mining Efficiency:
+26 [5]" — a statistic invented for an enchantment, followed by its raw
level as an unexplained bracket. The mining section now shows one row,
"Mining Speed: 34", with Efficiency folded in. Fortune becomes Mining
Fortune, the vanilla expected-yield formula as +33 %, +75 %, +120 %,
suppressed under Silk Touch (the blocked case is the indicators'). The
conditional damage enchantments take their stat names — Undead Damage,
Arthropod Damage, Aquatic Damage — with values still read live from each
enchantment's own effect data. The special enchantments gain a symbol
registry classified applied/missing/not-applicable against the same
isSupportedItem and areCompatible mechanisms the rarity budget uses,
hidden when not applicable, hover-annotated, omitted when nothing applies.
Breaking Power's mechanism exists and reads one authoritative mapping —
which is deliberately empty today, because no authoritative source exists;
a missing value is never fabricated.

### Verification

`./gradlew build` green; **1422 tests, 0 failures** (the geometry tests
pin NEU's own formula outputs; the state classifier runs against
fixtures constructed as the vanilla data writes exclusive sets). The
GitHub transfer is verified through the API, the Actions run green on the
merged develop. In-game verification — the pane at every GUI scale, the
page navigation, the sort bar, the in-pane detail, the indicator glyphs'
font coverage on the actual renderer, the search-driven filter against a
live server — remains the live checklist: the sandbox cannot launch a
client, and the report says so rather than claiming pixels it never drew.

## 2026-11-17 (late) — "not a custom ai slop ui": the pane rebuilt on NEU's rendering, verified live

### The verdict that reopened the pane

The pane built last session reproduced NEU's *geometry* but drew
everything as flat rects and text glyphs, and the user's in-game
screenshot from the Paris market came back with a one-line verdict: the
sidebar "no way looks like the NEU's level", not "a custom ai slop ui".
The complaint was right — the geometry was NEU's, the surface was not.
This session re-read NEUOverlay.java for its *rendering* — not its
layout math — and rebuilt both surfaces on what the source actually
does, then drove the real client through the whole matrix with synthetic
input and read every frame from screenshots.

### What the source study changed

NEU's surfaces run on textures and animation, not rects: the search bar
is a nine-slice of a 20×20 plate (4-pixel border slices, the gold
variant in search mode); the nav arrows are 480×160 textures with a
hover/press overlay and a one-pixel press shift; every item sits on an
18×18 rounded `item_mask` tinted the foreground colour; the page pill is
a three-layer bevel with a bold black label; the close button is a red
disc; the edge tab is a 64 px chevron at 30 % alpha; the order and
filter icons are textured squares with gold active variants and
ascending/descending overlays; the pane, the info pane and the tab all
slide on a 200 ms `LerpingFloat` (ported verbatim); the pane's
background is the blur-plus-`0xc8101010` backdrop plus a config tint
(alpha 6 blue by default — nearly invisible); tooltips carry yellow
bold action hints after a blank line; presses play the vanilla click;
the search group — settings plate, bar, search-mode plate — anchors
bottom-center of the *screen* and takes click precedence over the pane.
Most of those textures are now copied unmodified into
`assets/zea/textures/browser/` (LGPL-3.0, credited in NOTICE.txt); the
two icon shapes NEU lacks — observation-age ordering and the two shop
directions — are drawn by `scripts/gen_zea_icons.py` in NEU's exact
icon language (frame colours and palettes sampled from the upstream
PNGs pixel by pixel).

The geometry layer became NEU's scale-dependent formulas exactly: box
padding `panePadding * 2 / scaleFactor + 5`, the nav strip's height
`max(searchBarHeight / scaleFactor, 16)`, the centering splits, the
ceiling column and row counts, the nav buttons' search-bar-derived size,
and NEU's `drawStringCenteredScaledMaxWidth` (the page pill scales its
label instead of clipping). The detail view became NEU's info pane: a
second same-width pane sliding in from the screen's *left* while the
grid stays live on the right — the close disc at its top-right, the
offer rows trimmed into the pane's width, the Navigate footer styled as
NEU's wide button. The search bar is NEU's bar: nine-slice texture,
white value, blinking cursor, light-gray selection, gold in matching
mode, the settings plate opening ZEA's config and the search-mode plate
toggling matching mode, right-click clearing.

### The live harness, and what it caught

The previous sessions' "the sandbox cannot launch a client" ended here:
the dev client boots for real again — Xvfb display, the GLX probe
failing into the Vulkan fallback on llvmpipe (Mesa 25.0.7 unpacked from
the Debian debs into the project because the sandbox has no root), a
local flat-world dedicated server behind RCON, quickPlay auto-connect
injected through loom's launch.cfg (devlaunchinjector forwards
`clientArgs`, not leftover argv), and the whole flow — server, client,
XTEST input, x11grab screenshots — driven inside single tool calls
because the sandbox reaps background processes between them. The
browser's data came from a seeded `market.json` (five shops, 132
observations, 29 exact-fingerprint variants) written to the config dir
loom 1.19 actually hands the client (`<project>/config`, not `run/`).

Five defects fell out of the pixels and are fixed:

- the mid-slide click gate swallowed every click over the open pane —
  its offset is 2/3, always below the 0.99 gate — replaced by NEU's
  factor rule (handled when open, swallowed only over the moving edge);
- the info pane's offer rows overlapped at third scale — the rows are
  NEU's trimmed-line model now (tag, owner in the shrinking middle,
  price right-aligned; stock, bulk note and where trimmed with the age
  right-aligned), and prices compact to two decimals;
- the item plates rendered white — the mask blit carries the foreground
  tint, and the nav labels scale instead of clipping;
- the search group is clicked before the pane (NEU's guiGroup
  precedence) so its plates stay reachable over an open pane;
- the harness coordinates were rebased to the game's real GUI scale
  (3, not the assumed 2 — measured from the pixels, as before).

### What the live pass now covers

Pane slide-in on tab hover; the grid's variant tiles with enchantment
glints on tinted rounded plates; tile tooltips with the market summary
and the yellow `LMB : View offers!` hint; the info pane's slide from
the left with the red disc closing it; offer selection lighting the
Navigate footer (selection never navigates); the search bar filtering
the grid live ("pick" → the pickaxe variants); `e`, `.` and `/` staying
text while the bar is focused and the inventory staying open; right-click
clearing; escape unfocusing; the order and direction icons switching
with their gold active states; the gold matching-mode bar. The flavor
texts, the page pill, the empty-state, the page turn and the Next button
were checked against a one-page index (Next correctly does nothing when
there is no second page).

### Verification

`./gradlew build` green; **1423 tests, 0 failures** — the geometry
tests re-pinned to NEU's formulas computed by hand at three scales
(480×320@1, 1280×800@2, 1920×1080@3), the state tests extended for the
ascending direction, and the search layout tests rewritten for the
bottom-center anchor. The in-game pass itself is the other half of the
receipts: the shots live in `zea/shots/` (kept out of the repository
along with the other dev-runtime artifacts by the new ignore rules).
Not yet verified live: the Navigate action against a real EarthMC town
(the dev server has no claims), the info pane against a variant whose
token decodes only approximately, and the pane at every GUI scale —
scale 3 is the one the harness ran.

## 2026-11-18 — The indicator bug report: brackets, rarity colors, real glyphs

### The report

The player filed the indicator bug report against the row as shipped in
dev.80: the symbols must render inside square brackets (`[∞]`, `[♻]`,
`[🔮]`, `[🛡]`, `[🔥]`); the applied color must come from **ZEA's own
rarity hierarchy** — the level maps to a tier, the maximum legitimate
level is always Legendary gold, lower levels step down proportionally,
single-level enchantments are gold — with no separate enchantment
palette; missing-but-applicable stays neutral gray; incompatible stays
hidden; no permanent APPLIED/MISSING labels; the row stays below the
statistics; and missing-glyph boxes are not accepted.

### What was actually wrong

Three real defects, all in `SpecialEnchantments`:

- **A palette of its own.** `APPLIED_COLOR` 0x55FFFF (aqua) and
  `MISSING_COLOR` 0x555555 — every applied indicator the same aqua
  regardless of level, exactly what the report forbids.
- **Broken glyphs.** The registry writes `🛡️` with a bare U+FE0F
  variation selector, which has no glyph and renders as a box. Worse:
  Minecraft 26.3 **removed unifont** (verified against the
  sha1-matched client jar: `include/unifont.json` ships an empty
  provider list, no `unicode_page_*.png` exists), so eight of the
  registry's codepoints — `♻ 🔮 💥 🎯 💨 🫧 🏃 👣`, including the
  report's own Mending and Silk Touch examples — have no glyph at all
  in the default font.
- **No brackets, double spaces** — the row read as bare glyphs padded
  apart.

### The fix

- **The color rule is one formula, not a palette.**
  `tierFor(level, maxLevel)` maps the ladder endpoints (minimum
  Common, maximum Legendary) and distributes the levels between
  proportionally, nearest tier, halves up. The report's own example —
  Unbreaking I/II/III → Common/Epic/Legendary — falls out of the
  formula; M=2 spans Common→Legendary; M=4 fills each tier once; M=5
  reads Common, Uncommon, Epic, Epic, Legendary. Single-level
  enchantments short-circuit to Legendary (their only level is their
  maximum). Every color is `ItemRarity`'s own RGB through
  `TextColor.fromRgb` — nothing redefined, nothing duplicated. Missing
  indicators take `ChatFormatting.GRAY`'s value, the same neutral gray
  the stat labels and the basic enchantment lines already use.
- **The glyphs are shipped, not wished for.** ZEA now carries a bitmap
  font, `zea:enchant_indicators`
  (`assets/zea/font/enchant_indicators.json` over a generated
  `enchant_symbols.png`, 64×8): the eight missing codepoints as
  hand-pixelled 8×8 white masks in the exact style of the emoji
  vanilla still ships in `nonlatin_european.png` — same cell size,
  same ascent 7, ink densities 13–27 px against vanilla's 11–34.
  `scripts/gen_enchant_font.py` (the `gen_button_texture.py`
  precedent) draws them; advance simulation with the BitmapProvider
  formula (`int(0.5+ink)+1`) puts every glyph at 8–9 px, inside
  vanilla emoji's own range. The registry records per entry which font
  renders its symbol (`FontSource.VANILLA`/`ZEA`, verified against the
  shipped 26.3 assets), the renderer routes only the ZEA-font symbols
  through `Style.withFont`, and the brackets always stay in the
  vanilla font — a codepoint is never sent to a font that lacks it.
- **Brackets and spacing.** Each indicator renders as `[symbol]`, the
  hover text now on the whole part so it works over the brackets too,
  single spaces between indicators. The VS16 is gone from the
  registry; symbols are bare base codepoints, pinned to exactly one
  codepoint each by test.
- Untouched, per the report's "preserve" list: the registry membership
  and symbols, the per-class profiles, the three-state classifier and
  its vanilla-mechanism gates, the row's position under the stat
  block, the hover summaries, and every other tooltip element.

### Test

`SpecialEnchantmentsTest` grew from eight to twenty-three: the tier
rule against the report's Unbreaking example verbatim, single-level
gold, two-level span, four-level one-each, five-level proportional and
monotonic, clamping both directions, colors being the rarity
constants; bracketed single-spaced row text; gold/epic/gray on all
three parts of an indicator; no state labels; font routing both ways
through the rendered styles; brackets always default font; one
codepoint per symbol (no VS16); and the shipped bitmap font's
codepoint set asserted equal to the registry's ZEA-font set with the
texture present. `./gradlew build` green. In-game verification — the
seventeen glyphs live in the client font, the three states, the tier
colors at Unbreaking I/II/III — is this session's live checklist.

### The live pass (Xvfb + EGL/lavapipe + quickPlay + RCON), and its receipts

Booted the real 26.3 dev client headless — Xvfb, SDL forced onto EGL
(`SDL_VIDEO_FORCE_EGL=1`, the missing env from session 19's recipe;
without it the client dies after the GLX probe), renderer on lavapipe
Vulkan, quickPlay auto-connect into the flat-world dev server, RCON for
the `/give` probes (26.3 wants the **flat** enchantment component form
`[minecraft:enchantments={"minecraft:unbreaking":1}]`; the
`{levels:{}}` wrapper is rejected). The first-run accessibility
onboarding still blocks the boot — the `onboardAccessibility:true`
options seed did not suppress it; the Continue click at (640, 608)
after the atlas load does. The survival inventory at this window runs
GUI scale 2 exactly (slot pitch 36 px, panel left 466 top 236), so the
hotbar slots were measured from the slot-border pixels, not assumed.

Six probe items, hovered, read from screenshots (vision model for the
glyphs, exact-pixel color scans for the tiers):

- **Unbreaking I pickaxe** — `[🔮] [♻] [🛡]` gray, gray, **white**
  (COMMON).
- **Unbreaking II pickaxe** — gray, gray, **purple**: a 224-px cluster
  of exactly 0xAA00AA at the shield's position.
- **Unbreaking III pickaxe** — gray, gray, **gold** (0xFFAA00 cluster at
  the third symbol). The spec's example, live.
- **Efficiency V + Unbreaking III + Mending pickaxe** — `[🔮] [♻] [🛡]`
  gray, gold, gold: Silk Touch missing-gray beside two max-level golds,
  "Mining Speed: 34" above, "Efficiency V, Mending / Unbreaking III"
  below, EPIC PICKAXE footer.
- **Bow with Infinity** — `[∞] [🛡] [💥] [🏹]` gold, gray, gray, gray:
  the vanilla-font ∞ beside three ZEA-font glyphs, and **no ♻** —
  Mending hidden by the conflict, exactly as specified.
- **Helmet Respiration III + Aqua Affinity** — `[⚓] [🫧] [♻] [🛡]`
  gold, gold, gray, gray: the single-level Aqua Affinity and the maxed
  Respiration both gold, the two missing grays behind.

Every symbol rendered from a real glyph — the vision reads describe
the ZEA bitmap doodles as distinct shapes (the 8×8 shield reads as a
"heart", the crystal ball as a "dome", the recycle triangle as an
"omega") and explicitly report **no tofu or empty boxes**; the old VS16
box is gone with the variation selector. Brackets and single spaces on
every row; the row sits between the stat block and the enchantment
list everywhere it appeared. The pass scripts live in
`/home/z/my-project/scripts/indicator-phase*.sh` (session scratch, not
the repository); the shots in `zea/shots/` (ignored dev artifacts).

## Session 25 — live side-by-side: ZEA browser vs NEU 1.8.9 (both booted headless)

Both clients actually ran on this sandbox and were driven with real input;
every claim below has a screenshot receipt.

### The NEU 1.8.9 stack, built from scratch (reusable recipe)

`/home/z/my-project/neu-run/` + `/home/z/my-project/scripts/neu-boot.sh`:
Temurin JDK 8 (`/home/z/my-project/jdk8`), vanilla 1.8.9 client jar +
libraries from piston-meta, Forge 11.15.1.2318-1.8.9 via the installer's
`--extract` (universal jar) plus a `--installServer` run into a temp dir to
harvest the forge/scala/launchwrapper libraries the vanilla json lacks,
NEU 2.6.0 release jar in `mods/`, natives extracted to `neu-run/natives`,
item repo pre-seeded from codeload (GitHub API rate limits) +
`currentCommit.json`. Launch: launchwrapper + FMLTweaker with
`-Djava.library.path` — the three headless traps: (1) AWT needs
libXtst/libXi (apt-get download + unpack into gpu/), (2) Xvfb must start
with `+extension RANDR` AND the `xrandr` BINARY on PATH (LWJGL2's XRandR
class shells out to it; missing binary = ArrayIndexOutOfBounds in
LinuxDisplay), (3) never set `Display.noinput` (kills all input) and
singleplayer auto-pauses without window focus — `xdotool windowfocus`
(XSetInputFocus, no WM needed) fixes it. NEU config seed
`config/notenoughupdates/configNew.json` with
`misc.onlyShowOnSkyblock: false` lets the overlay render off-Hypixel;
clicking the search bar (or hovering the tab) opens the item pane.

### ZEA run recipe updates

The dev client's gameDir is the project ROOT (`zea/`), not `run/` —
options.txt, config/ and market.json live at `zea/config/zea/market.json`.
The dev server launches with the bundled-layout classpath
(`-cp libraries/*:versions/26.3/server-26.3.jar net.minecraft.server.Main`).
`run-client.sh` must pass `--width 1280 --height 800`; without them loom
defaults to 854x480 and every click coordinate lands outside the window.
Keyboard input needs a first click into the window (XTEST focus).
GUI scale is 3 at this window size — NEU 1.8.9 picks the same scale, so
the comparison is apples-to-apples. `scripts/browser-pass.sh` drives the
whole browser sequence; `scripts/drive.py` now takes DISPLAY from the
environment (NEU runs on :100, ZEA on :99).

### The comparison (pixel-measured, not eyeballed)

Tile plates: both `#646464`, 41 px wide on a 60 px stride — exact match.
Nav element (Prev / Page pill / Next, bevel B1B1B1/323232), sort bar icon
row with the gold-active ALL filter, bottom toolbar (gear + search +
torch), search filtering (NEU "diamond" → 1/2, ZEA "pick" → filtered
grid), tooltip action hints (yellow bold) — all match. The pane backdrop:
NEU's release jar draws blur + `0xc8101010` together (decompiled: the dark
rect sits inside the `bgBlurFactor > 0.5` branch, default 5), so ZEA's
constant dark backdrop is faithful to real-GPU NEU; the headless blur FBO
failing makes NEU look lighter in these shots only.

### One fix: the info-pane title no longer slides off-screen

NEU's TextInfoPane centers the title naively
(`(leftSide + rightSide - titleLen) / 2`); market display names
("Aqua Affinity Respiration III Diamond Helmet") are longer than Skyblock
names, so ZEA's faithful port rendered them mostly outside the screen.
`drawInfoPane` now routes the title through the same
`drawStringCenteredScaledMaxWidth` treatment the nav pill label already
uses — centered, scaled down to the pane's width, shadowed like NEU's
white title. `drawScaledCentered` grew a shadow parameter; the pill and
arrow labels keep their unshadowed bold black. Before/after:
`download/screenshots/zea-3-detail-{before,after}-fix.png`.

### Receipts

`download/screenshots/` — ten shots plus `COMPARISON.md` (the measured
table): ZEA pane page 1, search filter, detail before/after fix, and two
enchant-indicator tooltips (`[🔮] [♻] [🛡]` gold/gray/white tiers verified
by pixel scan — all-max gold, single-level gold, missing gray, Unbreaking I
→ Common); NEU title screen, item pane (Page: 1/92), "diamond" filter, and
item tooltip. Test suite green after the fix (154 test classes).

## dev.84 — the pane's pushed scale and the seamless calculator (2026-11-18)

The user's four screenshot complaints, reproduced on the real client and
fixed against the real NEU 2.6.0 jar (booted headless, same 1280×800,
same container scale 3, driven with XTEST, measured pixel-wise):

### 1. The pane now draws at NEU's pushed gui scale

Measuring the real NEU 2.6.0 run at container scale 3: item plates land
at a ~30 px pixel pitch (~11 columns) while the container's slots sit at
54 px — the pane renders through NEU's `Utils.pushGuiScale`, at roughly
half the container's factor, while its search-bar band stays at the
container's. ZEA drew the pane at the container's scale: stride 60 px,
six sparse columns, the grid ending ~90 px short of the pane's edge —
exactly the user's "the list did not appear all the way to the right /
very off".

`BrowserGeometry.paneScale(containerScale)` now models it: the pane
keeps the container's scale up to 2 and stays at 2 beyond it. The
geometry runs in pane units (the virtual pushed ScaledResolution) and
`ItemBrowser.render` draws the whole pane through one pose scale
(`unitToGui = paneScale / containerScale`), with the mouse converted the
same way for hit tests (`paneMouse`). Measured after the fix: plates at
an exact 40 px pitch (NEU's own reference screenshots at the same
resolution read ~36–40), tiles at `rgb(100,100,100)` — NEU's foreground
colour bit-for-bit, nav spanning the pane from x 890 (NEU: 891).

### 2. The calculator result rides the text line, backdrop gone

The user's "the '= x' text has its own background and is not seamless":
NEU draws `text + " §e= §a" + result` as one string — no backdrop, no
fallback box. ZEA's bar drew a `0xB0000000` plate behind the result plus
NEC's floating marquee box. Both removed: the yellow equals and the
green value now draw on the value's own line, the same baseline as the
typed text (`drawResult` anchors on the displayed substring end, the
same y as `drawValue`), and when the bar runs out of room the result
stays undrawn like NEU's. Live shot: "2+2 = 4" seamless in the bar.

### 3. Plain search words stay white

The syntax colouring engaged on any tokenizable input, so a search
query like "pick" rendered magenta. NEU's bar keeps plain queries white;
the colouring is for calculations. `zea$highlight` now bails unless the
input carries a digit or an operator.

### Verification (real client, headless, XTEST-driven)

`download/screenshots/zea-4-*.png`: the fixed pane (nav row visible
after disabling the vanilla 26.3 movement tutorial toast, which parks
over the pane's top strip until dismissed), the detail pane with the
offer rows, the inline "2+2 = 4", `neu-260-reference-*.png` from the
real NEU 2.6.0 client under the same conditions, and a side-by-side pane
crop. Pixel receipts: tile pitch 40/40/40, plate colour exactly
(100,100,100), nav extent 890–1217 vs NEU's 891–1239. Test suite green
after the changes.

## dev.88 — the browser's sort bar is NEU's, the pane sits under the GUI

### 1. The panel background was ZEA's invention

The "#1B1C28" look was `SLATE_WASH` (`0x8C3C4054`, slate blue at 55%)
stacked on a weakened dark overlay — layers NEU never draws. Vanilla
26.3's in-game screen gradient (`0xC0101010` → `0xD0101010`) is already
NEU's panel color; the pane now adds only NEU's alpha-6 blue tint.

### 2. The pane renders at extractContents head, the GUI wins clicks

ZEA's browser extracted at the TAIL — after widgets, labels, slots,
carried item and tooltip, so it drew on top of everything. It now
extracts at the HEAD of `AbstractContainerScreen.extractContents`
(26.3's pipeline), the exact NEU layering: the GUI's slots cover the
pane where they overlap, and the search bar — a real widget — rides
above it. Clicks and the wheel follow the same rule: a hovered slot
wins, the browser takes the rest.

### 3. The sort bar is NEU's three orders and five buckets

The buy/sell direction filters are gone. The left group is NEU's three
sort buttons — alphabetical, rarity (through `RarityEngine`, no stack
resolution when the system is off), price — each cycling relevance →
primary → opposite → relevance with NEU's green ascending / red
descending arrow on the owning button only; relevance shows no arrow.
The right group is the five category buckets: all, weapons and tools
(NEU's own sort_weapon), armor (NEU's sort_armor), general goods and
server items — the last two drawn in NEU's icon language
(`gen_browser_icons.py`: a beacon with a glowing core, a computed
circle globe), selected through the `earthmc` marker in the variant's
own custom data. The market's variants land in buckets through the
existing `ItemClassifier` — live shop items from the curated
observations, not a recipe index.

### Verification (real client, headless, xdotool-driven)

`download/verify2/` + `download/verify3/`: the full interaction matrix
against the seeded market (6 shops, 52 observations, every bucket
populated) — the three alphabetical states (no arrow / green down /
red up), rarity and price directions, the four category grids each
showing only its bucket, "dia" search and the inline "2+2 = 4", and at
container scale 3 the pane sitting under the inventory GUI with the
search bar above it. 1446 unit tests green. Boot pipeline: Xvfb +
SDL-forced EGL (user-space glvnd/Mesa extraction), direct java launch
with `--quickPlaySingleplayer`, xdotool input.

## Pass: the world map window, the search's platform text input, and the first live end-to-end verification (dev.94–dev.102)

### 1. The screen leaves fullscreen for a wide, short framed window

The rebuilt world map is no longer fullscreen chrome. It is a wide,
short window (360×200 GUI pixels, shrinking inside small viewports)
floating over a dimmed world, framed in the Item Browser's language —
the light edge ring, the gray plate, the dark interior — with the
viewed world's name seated in the frame's top band, centered on the
border itself, gold while viewing a world the player is not standing
in. The search pill, freshness readout, follow/world-switch hints and
the cursor's world coordinates live inside the interior; the map
surface clips to the interior so tiles and the curved path never spill
past the frame. The camera, wheel zoom and drag pan are viewport-local
to the window.

### 2. The search's text input goes through the vanilla focus chain

The pill's EditBox joins the screen's container (`addWidget`) and the
pill opens through `setFocused(searchField)`, so platform text input
engages the way vanilla fields do (`EditBox.setFocused` →
`Minecraft.onTextInputFocusChange` → `SDL_StartTextInput`). Key checks
name their keys through `InputConstants` — the 26.3 events carry SDL
scancodes, and the hand-picked GLFW-era numbers made Enter, F and W
unmatchable. Verified live: typing into the pill lands characters in
the field; Enter submits and the miss path reports honestly.

### 3. The HTTP client stops sharing the pool it blocks

Every fetch timed out because `HttpClient.newBuilder().executor(...)`
pointed at the same two-thread pool the callers parked inside
`send()` — no thread was left to deliver a response. The client keeps
its internal executor now; the callers' pool only parks sends. A
claims failure warns once per world instead of staying silent.

### 4. The zoom floor reaches the sphere from a smaller viewport

The window's interior puts the whole-map fit scale near 1/179 GUI
px/block; the old 1/1024 floor allowed barely one e-fold below it. The
floor drops to 1/4096 so the transition can finish and the receded
globe fits the window.

### 5. The country flags claim their anchors greedily

Dense clusters (Europe) piled flags on one spot. Bigger territories
place first; a flag whose rect would overlap an occupied one is culled
and reappears as zoom separates the anchors.

### Verification (real client, headless, EGL on Xvfb, scripted drives)

Boot-to-title screenshots, the map window design, the search pill
typing "london" with platform text input, the flat fit view with live
Terra Nostra imagery ("Claims 7s ago · Players 8s ago"), the continuous
wheel-driven curl into a fully formed receded globe and back, the
country flags riding the sphere, and the regional country-cell layer —
all captured and reviewed; the reviewed frames live under
`docs/verification/`. The dev client gains a fixed-size run window and
auto-join for reproducible drives.

## dev.101–dev.105 — the bug-report batch: the explanation bank, the meta block, the glyph colors, the mapped rarities, the pane that finally closes (2026-11-19)

Five reports from the live item-tooling session, each fixed in its
own commit.

### 1. The explanation prose grew with the server's enchantments

The phrase bank held only the confirmed combat and mining lines, so
the armor group's prose ("Reduces damage from most sources."), the
water enchants', Feather Falling's, Depth Strider's, Fortune's
("Some blocks drop more items then normal." — the server's own typo,
matched verbatim) and the spear lunge's line ("The user lunges
forward when using the spear.", which no synced registry description
ever carried) survived every tooltip pass. The bank grows by exactly
those seven observed lines.

### 2. The forged-item provenance block hides behind one switch

"Historical information / Forged by / Origin / Date" rides through
every tooltip pass. The new Miscellaneous switch removes every line
of the record — header by exact trimmed text, fields by prefix,
case-insensitive and padding-tolerant — and collapses the blanks the
removal doubles; a tooltip without the block is untouched line for
line.

### 3. The glyph carries its own color, the brackets the tier

Every applied symbol wore its level's rarity tier, so a row read as
one flat color. The brackets keep the tier (the progression signal),
and each registry entry pins its own meaningful color — Unbreaking's
shield is shield blue, Mending's loops XP green, Fire Aspect burns
flame red — eighteen constants, distinct within every profile row.
Missing glyphs stay gray so applied and missing still read apart.

### 4. Rarity is mapped out, not calculated

The old engine derived its enchantment budget from the live registry,
and EarthMC syncs custom enchantments into it — budgets wobbled with
the server's shipment of the day and the floating-point thresholds
turned the wobble into unexpected tiers. `scripts/gen_rarity_table.py`
transcribes the vanilla 26.3 data extracted from the client jar,
enumerates every legitimate level combination per item family in
exact integer arithmetic, applies the reference rule once, and
freezes the rarity ordinal for every (family, material tier, fill
numerator) combination into `RarityTables`. The runtime sums the
pinned universe, binary-searches and reads — unknown paths (the
server's customs) contribute nothing, a command-forced exclusive pair
maps to the nearest reachable state below its sum, and no division,
threshold compare or registry read happens at hover time. The mapped
space widens to the families the formulas never reached: shears and
the flint and steel on the iron tier, the shield beside the bow.
Every table cell is re-derived through `RarityCalculator` in the
tests, so the data cannot drift from the rule silently.

### 5. The shop index pane finally closes

The close existed in the state machine and nowhere else — the pane
greets every container screen wide open. NEU's rules port piece for
piece: the pane starts closed behind its edge tab, a genuinely new
screen closes it unless a search is already active (bound after the
search session so the rule reads the new screen's own query), and
NEU's close-panes keybind arrives as `zea.key.close_panes`,
unbound by default, taking the detail pane away with the grid.

## dev.105 — the map becomes Xaero's: the xeroworld engine, ZEA's EarthMC layer (2026-11-20)

The decision the map had been drifting toward since the first globe
frame: stop maintaining a world-map engine. Xaero's World Map — the
engine every EarthMC player already runs — becomes the map; ZEA keeps
only the EarthMC-only layer on top of it, which is what no general
renderer can have: the live claim fills, outlines and player markers
from the squaremap feed.

### How the integration works

A soft mixin (required=false, require=0) injects into Xaero's GuiMap
at the player-arrow pass of extractRenderState — the anchor the
EarthMC Map Addon verified against Xaero 26.3 — and draws through
GuiGraphicsExtractor, which composites over everything Xaero drew
wherever the inject sits. The layer builds ZEA's flat north-up
projection straight from Xaero's camera pair (scale / screenScale is
GUI pixels per world block, the one scalar the projection takes), so
layer and terrain agree by construction at every zoom. Claims and
players plan and draw through the same ClaimOverlay / PlayerOverlay /
OverlayDrawer the minimap uses — one drawing vocabulary, no second
renderer. /zeamap and the map keybind open Xaero's own GuiMap through
its session, the exact construction Xaero's keybind performs
(verified against the 1.47.2 bytecode); without Xaero installed ZEA
says what to install instead of rendering a second-rate screen.

The compile-only dependency resolves from Xaero's own Maven. The
mixin class names no Xaero type (targets and the field anchor are
strings, shadows are primitives); the one gateway class that imports
Xaero loads only when the mod is present. The mixin-package hygiene
test now validates both configs.

### What the retirement removed

The own full-screen world map with its camera persistence and the
follow-player option; the flat-to-globe projection family
(GlobeTransition, the mesh planner and element, the curved draw path,
the globe tile driver); the country mosaic and flags with their
bundled countries.json and the aggregation scheduler; MapCameraInput;
the world-map tile driver. The HUD minimap stays ZEA's, drawing from
the same stores through the same flat drawSurface path — and the
Xaero layer shares those stores, so one refresh cadence serves both.

### Verification

Clean compile, remapJar, and the full suite (1457 tests) green after
the deletion; the deleted engines' test suites go with them. The
in-game pass (layer over Xaero's terrain at every zoom, the opening
path, the no-Xaero message) runs when the dev client next has Xaero
installed — the mixin is soft, so its absence is a silent no-op, not
a crash.

## The market viewer — JEI's recipe gui rebuilt for a live market (2026-11-20)

The browser's tile click opened an in-pane detail — ZEA's own invention,
and it read like one. The reference the genre actually ships is JEI's
RecipesGui, so that is what replaces it: the upstream source (master,
2026-11-20, MC 26.3 / Java 25 — the same target ZEA builds against) was
read for its open/close semantics, its geometry constants and its
rendering passes, not approximated from screenshots.

### What JEI does, this does

Opening captures the current screen as the parent and swaps the viewer
in — the container stays alive behind it and returns exactly as it was
when the viewer closes; Esc and the inventory key both close (JEI's
"Close inventory" command); `isPauseScreen = false`, `isInGameUi = true`.
The panel sizes to JEI's defaults — width 198, height at most 350, the
screen minus 58 pixels, `guiTop` 45 — and the content area follows JEI's
`getRecipeLayoutsArea`: six pixels of frame padding under the two nav
rows. The chrome is JEI's own files copied unmodified (MIT;
`LICENSE-JEI.txt`, `textures/market/NOTICE.txt`): the `gui_background_v2`
nine-slice panel (ZEA's three-by-three blit, the sprite's 16 px border
fractions — the same pass the search bar uses), the vanilla-look button
plates with their disabled and hover states, the 9×7 arrows inside the
13 px nav buttons, and the 24 px side tabs riding the panel's top edge
with JEI's three-pixel overlap. The title truncates with an ellipsis and
shows the full name as a tooltip — JEI's `RecipeCategoryTitle` behavior.

The market translations: the header's prev/next arrows cycle the
browser's current results (JEI's previous/next category); the tabs pick
the market's side (all, buy from, sell to) with chest, emerald and gold
icons; the page row paginates the offer list over JEI's dark strip with
the centered page string; one offer per row in JEI's slot language —
the item in a vanilla slot plate, owner and unit price on the first
line, stock and where plus the age on the second; the identity block
carries the enchantment summary and both sides' aggregate statistics;
the footer's Navigate action draws on JEI's button sprites, disabled
until a row is selected. Summaries trim at their last whole "·" section
— a half-word like "1 owne" reads worse than the section the tooltip
already carries.

### What it replaced

The in-pane detail and its whole machinery: the info pane draw, the
offer-row geometry, the scroll state, the info lerp, the red close disc,
and the state's `View` enum with its selection fields — the browser
state keeps only open, page, order and filter, and the viewer owns the
selection (a selection still never issues a command). The mixin's Esc
branch for the detail went with it; the close-panes binding keeps the
grid pane's behavior untouched.

### Verification

Clean compile and the suite green (the state test follows the hand-off:
the detail-view cases give way to the close-reopen round-trip). The
in-game pass ran against a seeded market — six shops, seven variants,
fifteen exact `SERVER_RESPONSE` observations written straight into
`market.json`, the dev client driven under Xvfb/EGL with a screenshot
at every step: the pane opens closed behind its edge tab, a tile click
opens the viewer over the container, the tabs switch sides with their
tooltips, the next-item arrow cycles the title and identity, a row
selects with the gold frame and shows the full observation tooltip, the
wheel turns the list's page (a fourteen-shop diamond spans two pages and
the section header carries onto the second), the next-item arrow cycles
the title and identity, Esc
and the E key each return the inventory with the pane exactly as it
stood, and the disabled arrows carry JEI's disabled plate. One finding
the frames caught: `SideStats.shops()` is the variant-wide count, so the
identity block counts each side's offers itself — a one-shop SELL TO
read as fourteen.

## The market editor — /zea market-editor, the capture prompt, the aggregated tooltip (2026-11-20)

Three asks, one arc: a way to enter a listing by hand, a capture that
actually captures and then asks the player whether it did, and item
lore that speaks in aggregates.

### The capture fixes first (the "barely works" diagnosis)

The tracker closed a response run on the first message without
shop-shaped lines. One unanchored line between the server's fields —
another player talking, a broadcast, any wording the anchors do not
know — recorded half a response and dropped the rest. The run now
stays open and the quiet window closes it whole; the boundary between
two inspections is the next sign the player clicks, not random chat.
The response attaches to the newest pending use whose sign text
actually is a shop sign: every block use (chests, doors) also lands
here as a pending use, and a shop's response must not attach to
whatever block was last touched. Sign direction matching is
case-insensitive — signs are hand-written.

### MANUAL as provenance

`SourceType.MANUAL` marks what the player entered or confirmed in the
editor. An exact identity ranks with the verified responses in the
variant index and submits to the repository; a display-only identity
stays as preliminary as a sign advert. The wire format needed no
change — the enum name round-trips, and unknown names still degrade to
SIGN_ONLY on older builds.

### The editor

The viewer's chrome with a form inside: JEI's panel, the direction
toggle (Selling — you buy / Buying — you sell), the seller field, an
inventory item picker (the pick gives the listing its exact identity),
price with a per-item/per-bulk scope, bulk size, stock, and the sign
coordinates. A fresh listing seeds its location from the sign under
the crosshair — the sign's text seeds seller, item and direction too —
falling back to the player's feet. The review path opens over the
latest captured inspection: every field editable, Done records the
corrected listing as the shop's newest observation, an unchanged
review adds nothing, and Done that cannot parse a number keeps the
panel open with the field red.

### The capture prompt

One chat line — "▪ Captured <item> [Review]" — after a capture
records; the click runs `/zea market-editor review`, the same command
the editor registers for picking a prompt back up. Chat lines never
steal the screen: the capture is recorded as always, the prompt is the
invitation. The toggle sits in the Market config.

### The tooltip

The market section speaks in aggregates now: Sellers (distinct sellers
— one owner with several shops is one seller), Price (the range of
every known unit price on the side), Stock (combined, but only when
every stock report is item-denominated — stacks are not items, space
is capacity, an uninterpretable figure leaves the line out), Buyers
and Payout for the other side. Black bullet, white label, gold value —
the bullet the spec asked for is dark gray in the end: black is
invisible on the tooltip's near-black background. The section builds
from the variant index once per market revision; a hover after that is
a map lookup.

### Verification

The suite green (tracker: the split-response, chest-attribution,
run-flush and sign-casing cases; index: MANUAL in with an exact
identity, out with display text; validator and store: MANUAL submit
and round-trip; tooltip lines: the aggregate shapes, the honest stock
omissions, the colors). The in-game pass ran against the seeded
market under Xvfb/EGL with a screenshot at every step: the editor
opens with the JEI panel and readable labels (the first build's black
field text on black plates caught by the frames and fixed — the
EditBox paints its own dark plate, so field text is light always, and
the scope button became one cycling control after the two-hitboxes
bug it started with), the picker grid shows the inventory with the
diamond in the first plate, the pick fills the item slot and display
name, typed seller/price/stock land, Done records — the chat line
"Listing added to the market list: Diamond" lands and market.json
gains the MANUAL observation with the exact token, which the tooltip
then proves by counting it into Sellers: 11. The tooltip lore shows
the bullets, the ranges and the honest age. The sign round trip:
a setblock shop sign ("Abasing / Selling 36 / Diamond Pickaxe / 2G
each"), one right-click, twelve seconds — the chat HUD carries
"▪ Captured Diamond Pickaxe [Review]", and the review editor opens
seeded: seller Abasing, item Diamond Pickaxe, direction Selling, sign
coordinates 8/-60/-6, price empty awaiting the player. The chat-click
itself was exercised through the command (chat refused to open under
the driver in that session); the link runs the same command.
## Pass: the viewer's rows read clean — the stock suffix and the mid-word trims (2026-11-20)

The first full in-game matrix of the rebuilt drive harness (Xvfb + EGL,
XTEST, a seeded fifteen-shop market) caught two text defects the frames
showed at full resolution. The row and tooltip stock line appended the
parsed unit word after a raw stock that already carried its own — "Stock:
8 stacks items" — and the identity block's unit-price lines hid it; the
detail line that didn't fit then fell back to a character cut and ended
mid-word ("9 stacks ite"). The stock suffix now stays off when the raw
ends with the unit word it would print, and the trim falls from a section
boundary (·) to a word boundary before it ever ends inside a word. The
stock unit the capture parser admits is UNKNOWN, so live captures read
clean either way.

### Verification

Suite green, and the drive matrix re-run against the patched build: page
one and page two of the fifteen-shop diamond, the Sell-to tab, the
selection frame with its enabled Navigate plate, the item cycle, and the
Esc and E restores — every stock line renders "Stock: N stacks" with no
stray unit and no mid-word tail.

## 2026-11-21 — The capture flow, redone as the spec actually reads

### What the maintainer's feedback corrected

The first market-editor build treated capture as an add-on to a manual
listing form: an editor with a seller field, a direction toggle, a price
row and an inventory pick grid ("what the fuck is picker"). The actual
ask was the opposite — the server already sends everything a listing
needs in the shop chat block (owner, item with its component, stock or
space, the per-bulk price line, the enchantment section), so the flow is:
capture automatically, announce it with the full information, and open a
screen only when a specific detail could not be found — the canonical
case being the shop's spawn, typed the way the player runs it
(`/n spawn france`). And the tooltip rework landed on container and
creative screens instead of the items sidebar listing it was asked for.

### What changed

- **Capture is the whole flow.** `MarketSubsystem` announces every
  recorded inspection in chat: "Listing found and added successfully"
  (server response) or "Shop sign read — captured from the sign" (sign
  fallback), followed by gray detail lines — owner, direction + item,
  stock (with its unit word when the server named one), price (with the
  normalized per-item figure), the enchantment section, the sign
  coordinates and the spawn. A second line lists what is missing and
  carries the clickable `[Add details]` prompt (gated by the renamed
  `capturePrompts` config option, formerly `captureReviewPrompt`).
- **The spawn, end to end.** `PhysicalShop` grows a nullable `spawn`
  reference; claims resolve it automatically (`/n spawn X` for a nation,
  `/t spawn X` for a town) and the completion screen takes the player's
  typing otherwise — `SpawnRef` normalizes `/n spawn france`,
  `n spawn france`, `spawn france` and bare `france` into the canonical
  command, declining command heads without a name. The store wire gains
  the field (schema v3, additive), the shared-dataset record too.
- **The completion screen.** `MarketCaptureScreen` replaces
  `MarketEditorScreen` (deleted with its picker and manual form): the
  captured listing shown read-only, one input per missing detail, Done
  records what was filled (`completeCapture` — the spawn annotation on
  the shop; a filled price or stock joins the latest observation as the
  player-vouched MANUAL record). Commands: `/zea market-dev`, with
  `/zea market-editor` kept as an alias; both open the latest capture.
- **Same coordinates, different shop — the old listing leaves.** The
  variant index now takes each shop's *latest* observation — any source,
  a sign-only advert too — as the only offer the shop contributes. A
  revisit that says something different (new item, flipped direction)
  removes the old listing instead of haunting the index as a phantom
  seller; a sign-only revisit confirming the same item text keeps the
  exact listing alive, a different text retires it.
- **The tooltip lives where it was asked for.** The browser sidebar's
  item tooltip speaks the bullet lore — Sellers, Price (min-max range),
  Stock (combined only when every report is item-denominated), Buyers,
  Payout, the age line — via `MarketTooltipLines` straight from the
  variant. The container and creative tooltip hooks and
  `MarketTooltipEditor` are gone; the market section no longer rides
  ordinary item tooltips.

### Verification

Suite green (1494 tests): the replacement cases (changed item stops
selling the old one, flipped direction leaves the old side, sign-only
same-text keeps and different-text retires), `SpawnRef` normalization
and declines, `MarketData.annotate`, the wire round-trip with spawn.

The in-game pass ran twice against the flat world under Xvfb/EGL, each
against a seeded three-shop market (frames in shots/capture2/): the
sign at exactly (8, -60, -6) read through a right-click captures after
the sign-only window, the chat carries the announcement block — "Shop
sign read — captured from the sign", the gray detail lines (Owner
Abasing · Selling Diamond Pickaxe, Stock 36, Sign 8, -60, -6) and the
"Missing: spawn, price [Add details]" prompt — the chat click opens the
completion screen (captured listing read-only, spawn and price inputs
only, the sign's own stock never prompting), the typed `/n spawn
france` and `2` land, Done records, and the chat gains "Listing added
to the market list · Item: Diamond Pickaxe · Sign: 8, -60, -6 · Spawn:
/n spawn france". market.json after the flush carries the spawn on the
shop record and the price-2 MANUAL observation. The browser after the
capture: the seeded Block of Iron at those same coordinates is gone —
the shop's latest observation names a different item, so the old
listing left the index — and the Diamond Pickaxe tile's tooltip speaks
the bullet lore: Sellers: 2, Price: 1.5G-2G, Stock: 55 items, the age
line, and NEU's yellow LMB hint. The first pass caught the completion
panel drawing its inputs at a relative offset in absolute space (the
rows on top of the captured facts) and the rows overlapping each other
at an 18 px pitch — both fixed and re-driven clean.
