# ZEA Market

The physical-shop marketplace: EarthMC's distributed chest-and-sign
shops, observed one inspection at a time, into a persistent, searchable
price-comparison system. This document is the feature's contract — what
is implemented, what the protocol guarantees, and what is deliberately
not claimed.

## The core idea

EarthMC has no central catalogue. A shop is a physical structure: a sign
attached to a chest, owned by a player, advertising one item in one
direction. The only reliable evidence about a shop is what its response
says when a player actually inspects it. ZEA therefore:

1. Observes sign right-clicks (the trigger of EarthMC's shop response).
2. Captures the server's response from chat, structurally, including the
   `[Item Preview]` item hover.
3. Correlates the response with the physical sign that caused it.
4. Records the shop (a place) and an observation (a measurement at that
   place) locally, durably, offline.
5. Ranks offers exactly, per individual item, per direction.
6. Shares validated observations through the ZEA-REPO pipeline.

Nothing is inferred about unexplored areas. No shop exists in the
database that no player has inspected. Stale observations never present
themselves as live availability.

## The two transaction directions (never confuse these)

EarthMC phrases direction from the shop's point of view:

- **SELLING shop** — the shop sells; the player **buys**. The player
  enters a number of **bulks**; `items = bulks × bulkSize`,
  `cost = bulks × pricePerBulk`. The bulk size is whatever the server
  reported, never an assumed 64. The server's `you can buy N bulks` is
  the hard maximum.
- **BUYING shop** — the shop buys; the player **sells**. The player
  enters **individual items**; `payout = items × pricePerItem`.
  `Space: N` is the shop's free capacity, not its stock, and
  `You can sell 0.` means every ordinary submission is refused.

`Stock:` values are preserved verbatim with their unit marked
`UNKNOWN` — observed behavior suggests stackable items may report
stacks while equipment reports individual items, and the client cannot
reliably tell which. No screen shows a quantity more confidently than
its unit justifies.

## Packages

| Package | Responsibility |
|---|---|
| `market.model` | Immutable domain: shop, observation, fingerprint, response |
| `market.parse` | Response and sign parsers (anchored, structural-first) |
| `market.interaction` | Sign-use geometry and the response correlation tracker |
| `market` | The subsystem (wiring), the data index, persistence, observation factory |
| `market.store` | The versioned JSON store with per-entry tolerance |
| `market.pricing` | Exact-decimal normalization and best-offer resolution |
| `market.index` | The item-variant index and the owner-balanced price aggregation |
| `market.tx` | The transaction calculator behind the chat's shop-quantity flow |
| `market.ui` | Observation ages (presentation formatting) |
| `market.tooltip` | The market section in item tooltips |
| `market.sync` | The shared-dataset sync (manifest, cache, merge) and the validated submission queue for ZEA-REPO |
| `market.config` | The Market settings category |
| `browser` | The inventory shop index: variant grid, detail views, variant display stacks |
| `nav` | Spawn-command observation, shop destinations, smart navigation |

Threading follows the house model: all state mutations and reads are on
the client thread; disk writes snapshot onto the `ZEA-Market` executor
(bounded, one thread); there is no network IO anywhere in the feature
today.

## Item identity

An exact fingerprint token is the item id plus the component patch of
the server's item preview, encoded canonically through the component
patch codec; the stack count is normalized away. Only exact tokens
match in the market — an enchanted item with different enchantments is
a different product, damaged and undamaged equipment differ, and a
display-text token (sign-only evidence) can never join an exact match.
When the preview cannot be decoded, identity degrades to unknown rather
than guessing.

## The inventory shop index

The browser draws NEU-style beside the container GUI: one tile per exact
item variant, each rendered from the variant's own display stack (the
fingerprint token's encoded patch parsed back through the codec against
the level's registries — glint and durability included; a tile whose
patch cannot decode is marked approximate). The existing inventory
search bar drives the filter — one search system, shared with slot
matching. A tile's tooltip carries the identity, counts and best prices;
clicking opens the market viewer — JEI's RecipesGui rebuilt for a live
market (`market.viewer`), studied from the upstream source with its
history: the screen captures the container as its parent and swaps
itself in the way JEI's `open()` does, and Esc or the inventory key
returns the container exactly as it was. Its chrome is JEI's own files
(MIT, `textures/market/NOTICE.txt`): the nine-slice panel, the 13 px
nav arrows, the 24 px side tabs, the vanilla-look buttons. The tabs
pick the market's side (all, buy from, sell to), the header's arrows
cycle the browser's results and paginate the offers, and the rows show
one offer each in JEI's slot language — cheapest purchase and highest
payout first, grouped by transaction direction. Selecting an offer only
selects it — a selection and a navigation are separate interactions.

## Transaction safety

Nothing is ever sent from the browser by selecting or navigating alone.
The quantity entry a shop purchase requires runs through the chat's
shop-quantity flow (`market.tx` computes the plan; the player types the
number), and the server's answer is the only success signal. EarthMC's
success/failure message shapes have not been captured yet; until they
are, no outcome is claimed.

## Smart navigation

Navigate is explicit — the viewer's footer button, for the
selected offer. The planner reads the shop's resolved town and nation
and the player's current nation from claim geometry:

- Already inside the destination nation: the waypoint is set and **no
  spawn command is issued** — teleporting back to a spawn the player
  is already inside would be a regression.
- The town confidently resolved (the sign sits inside its claim):
  `/t spawn <town>` is preferred.
- Only the nation resolved: `/n spawn <nation>`.
- Neither known: no command is invented; the waypoint alone guides.

The dispatch goes through the ordinary send path, where the spawn
observer records it like a typed command (macros included). The
temporary waypoint points at the shop's exact sign or chest, shows
label, distance and bearing in its HUD element, and clears when the
player reaches and inspects that shop, affirmatively leaves its
nation, or the world session ends — never on a timer. Unknown
geography says unknown; the clicked sign's coordinates stay the
authoritative location.

## ZEA-REPO integration

Implemented, the consumer side: the sync follows the repository's
CI-generated `manifest.json` (conditional GET), resolves whatever shop
dataset the manifest lists, skips the download when the manifest's
SHA-256 matches the last accepted snapshot, validates every record in
isolation, caches the accepted snapshot (a restart serves it from disk
before the network answers anything) and merges it on the client
thread. The merge rules are timestamp-safe and pinned by tests: a
shop the local set does not know joins with shared provenance; a
shared observation strictly newer than the shop's newest local one is
appended while the local record stays in the history; a timestamp tie
keeps the local one; an older shared record is skipped; re-merging
appends nothing twice. Only locally captured observations become
submission candidates.

Implemented, the producer side: the local submission queue.
Server-verified observations with usable identity, price and position
validate into submission records that preserve observation time and
submission time separately; the queue dedupes by shop + identity +
measurement; the file survives restarts; a future repository client
drains batches it has actually accepted.

Not implemented, and deliberately not invented: the repository's own
shop dataset. The ZEA-REPO repository has no shop dataset yet — no
`schemas/shop.schema.json`, no manifest entry, no contribution
workflow, and no credential path a public mod could use. The missing
contract, for whoever implements the adapter:

1. A dataset directory (`datasets/shops/`) plus a schema accepting the
   `SubmissionRecord` shape in `market.sync.SubmissionValidator` (shop
   identity, position, owner, direction, exact fingerprint token,
   price + scope + bulk size, limits, stock with its unit, both
   timestamps, provenance).
2. A manifest entry (`"shops": {"files": {...}}`) with content hashes,
   consumed by a repo client the same way badges and channels are.
3. A validation workflow rejecting malformed, contradictory or
   suspicious submissions (the local validator is the client-side
   pre-filter, not a substitute).

No personal data is included: a submission carries a shop owner's name
because the shop has one, never the observing player's identity.

## Known limitations

- Success/failure transaction outcomes are not recognized (no captured
  message shapes); no outcome is claimed after a send.
- Nation and town resolution follow the map subsystem's claim store;
  where claims are not loaded, the fields stay unknown — never guessed
  from proximity.
- The destination marker is a HUD element (label, distance, bearing),
  not yet a marker drawn on the world map and minimap canvases.
- The shop response component tree was parsed from observed text
  shapes; a real capture via `/zea capture` should confirm the hover
  layout (the parser tolerates missing hovers already).
