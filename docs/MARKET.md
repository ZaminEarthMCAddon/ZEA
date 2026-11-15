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
| `market` | The subsystem (wiring), the data index, persistence, observation factory, travel |
| `market.store` | The versioned JSON store with per-entry tolerance |
| `market.pricing` | Exact-decimal normalization and best-offer resolution |
| `market.search` | Local, offline-capable search over the index |
| `market.tx` | Transaction calculator and the transaction state machine |
| `market.ui` | Shop screen, market browser, observation ages |
| `market.tooltip` | The market section in item tooltips |
| `market.sync` | The validated submission queue for ZEA-REPO |
| `market.config` | The Market settings category |

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

## Transaction safety

The shop screen drives an explicit state machine: entering → review →
submitted → timed out (unknown outcome). The confirm exists only in
review, fires once (a second confirm can never send a duplicate), and
goes through `ClientPacketListener.sendChat` — the same pathway EarthMC
expects. A wait that outlives the timeout becomes an honest unknown: no
retry, no invented success. EarthMC's success/failure message shapes
have not been captured yet; the state machine will grow those
transitions only from real captures.

## Navigation

Travel is a button. The nation spawn command is sent through
`sendCommand` (the same path as manual typing) when the shop's nation
is resolved; exact coordinates are always recorded and displayed.
Today no nation-resolution source is wired (see the blocker below), so
shops without a resolved nation show coordinates and the reason. A
waypoint system arrives with the map subsystem's screens.

## ZEA-REPO integration

Implemented: the local submission queue. Server-verified observations
with usable identity, price and position validate into submission
records that preserve observation time and submission time separately;
the queue dedupes by shop + identity + measurement; the file survives
restarts; a future repository client drains batches it has actually
accepted.

Not implemented, and deliberately not invented: the remote side. The
ZEA-REPO repository has no shop dataset — no `schemas/shop.schema.json`,
no manifest entry, no contribution workflow. The missing contract, for
whoever implements the adapter:

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
  message shapes); every send ends in the honest unknown state.
- Nation resolution has no wired source; the field is plumbed through
  and stays null until an authorized dataset or verified API path
  exists.
- Waypoints do not exist yet; travel is the nation spawn command only.
- The shop response component tree was parsed from observed text
  shapes; a real capture via `/zea capture` should confirm the hover
  layout (the parser tolerates missing hovers already).
