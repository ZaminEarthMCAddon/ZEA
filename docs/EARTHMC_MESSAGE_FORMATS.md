# EarthMC message formats

Observed EarthMC chat message structures, recorded as fixtures for the
classifier (Stage 2 — implemented, see
[CHAT_CLASSIFICATION.md](CHAT_CLASSIFICATION.md)). These are **observed
samples, not universal templates**: formats vary (badges come and go, towns
may be absent, nicknames carry per-character formatting). Parsers read
component structure first (styles, siblings, hover/click events) and anchor
on text structure; unknown messages are never dropped.

Each family below has a fixture in `src/test/resources/fixtures/messages.json`
and a regression test.

## Global

```
🌎❣ [Yugoslavia|Pest] GavriloPrincip: lemme speak with his driver rq
```

Components, in order: zero or more **badges** (emoji, optional hover
descriptions, the snowstorm badge carries a clickable store link), then
`[Nation|Town]` (nation and town stored separately; town may be absent;
original colors preserved), then the display name (may be a nickname with
per-character formatting), then `:` and the message.

Variants observed: multiple badges, no badges, different badge/town colors,
nicknames. Do **not** build one regex assuming a fixed shape.

## Badges (known so far — see ZEA-REPO `datasets/badges.json`)

| Symbol | Meaning |
|---|---|
| 🌎 | EarthMC 10th anniversary Globe |
| ❣ | Server Heart |
| + | Supporter |
| 🐇 | Rabbit |
| 🔨 | Golden Hammer |
| 🐉 | Dragon |
| 🎉 | New Player |
| 💎 | Top 10 Mining |
| 🔥 | Snowstorm badge |

Badge model preserves: symbol, style, hover description, click event, and a
known semantic identity **only when verified**. Unknown symbols pass through
untouched with no invented meaning. The list is not assumed exhaustive — the
ZEA-REPO dataset exists so it can be corrected without a mod release.

## Nation / town segment

`[Yugoslavia|Pest]` — split on the `|` into nation and town. Not a username,
not a message prefix. Town may be missing entirely.

## Nicknames

Displayed name may differ from the account name (observed: displayed
`Coriolanus`, actual `pengpy`). Rules: option to show the real username when
an authoritative source identifies it (EarthMC API by name/UUID); never
invent a username; keep the nickname and its cosmetics available.

## Local / Trade / Nation / Town

Observed samples (legacy codes from in-game logs):

```
§8[§rLocal§r§8] §r§7Abasing§r§7: §ryo§r
§8[§r§bTrade§r§8] §r§7Abasing§r§7: §r§fEveryone Say Cookie, and see what happens§r
§8[§r§eNation Chat§r§8|§rSosnovka§r§8]§r §r§7Abasing§r§7: §r§eyo§r
§8[§r§bTown Chat§r§8] §r§7Abasing§r§7: §r§bYOOO!§r
```

Flattened: `[Local] Name: content`, `[Trade] Name: content`,
`[Nation Chat|Town] Name: content`, `[Town Chat] Name: content`. The
bracket label opens the message and matches exactly (case included). In
nation chat the bracket carries the sender's **town** (observed:
`Sosnovka`) — the nation is implicit, since only nation members can write
there. No badges have been observed on these channels; the name is plain
and gray. The nation form without a town is unobserved and declined
rather than guessed.

Classification note: these are claimed before the global classifier, so a
global affiliation can never shadow them. The inverse — a nation or town
actually named "Local", "Trade" or "Town Chat" — would be claimed by the
tag classifier first; EarthMC names are geographic and none collide with
these labels in practice, and the tags are data in ZEA-REPO
(`datasets/channels.json`) so a wrong tag can be corrected without a mod
release.

## Vote messages

```
AdamGladio voted and received a gold crate /vote
```

Class: Votes. Kept out of the main Global feed by default.

## Crate wins

```
akirarocks won 16 gold from a bruh box
jediknight7491 won 32 gold from a gold crate /vote
```

Class: Crate Wins. Amounts and crate names are parsed, never hardcoded.

## Deaths

```
☠ xfw_x was killed by dex247
☠ CherryPinkVelvet was killed by FORDaLoZ
```

Class: Deaths. Hover information (e.g. `Damage dealt: FORDaLoZ: 8.5 hearts
(100%)`) must be preserved with the message.

## Private messages

```
[FORDaLoZ → me] hi
[me → FORDaLoZ] please reply with hi
```

Class: Private Messages. Parse sender, recipient, direction, content,
timestamp. Clicking a sender opens the existing PM interaction. Not every
message containing an arrow is a PM — direction is only inferred from this
exact structure.

## System messages

```
EarthMC » You are now AFK.
EarthMC » You are no longer AFK.
```

Class: System. Includes the writing-channel report:

```
You are currently in global (write) and 5 other channels
```

This response is the source of truth for the current **server writing
channel** — it must be detected and applied to the client's channel model.
The writing channel is never inferred from the most recently received
message, and players do not necessarily start in Global.

## Discord

Messages forwarded from Discord carry a `[Discord]` prefix. Class: Discord.
Players reply through normal in-game chat; **there is no `/discordchat`
command** and ZEA must never invent one. Discord is an incoming category
only — it is not a writable channel.

## Channels (writable, server-side)

| Channel | Commands |
|---|---|
| Global | `/g`, `/global` |
| Local | `/local` |
| Trade | `/tradechat` |
| Nation | `/nc`, `/nationchat` |
| Town | `/tc` |

Standalone commands like `/global` **change the server's writing channel**;
short forms like `/g <message>` send a single message without switching.
Selecting a visual tab in ZEA does not silently change the server's writing
channel; the transition must be explicit and correct (Stage 3). The channel
list is data (ZEA-REPO `datasets/channels.json`) precisely because it may
change server-side.

## Shop responses

```
Shop Information:
Owner: Pelle_Nomme
Item: Block of Diamond [Item Preview]
Stock: 78
Price per 7x Block of Diamond - 1G
This shop is SELLING items.

Enter in chat, how many bulks you wish to BUY.
There are 7 items in each bulk and you can buy 64 bulks.
Enter all in chat to buy all.
```

```
Owner: Pelle_Nomme
Item: Netherite Ingot [Item Preview]
Space: 38
Price per Netherite Ingot - 17G
This shop is BUYING items.

Enter in chat, how much you wish to SELL.
You can sell 0.
Enter all in chat, to sell all.
```

Class: Shop (market subsystem — `ShopResponseParser`, fixtures are the
two shapes above). The lines anchor the parse; the `[Item Preview]`
hover carries the actual item as a show_item event, which is what item
identity fingerprints from. `Stock:` is preserved verbatim (stackable
items may report stacks, equipment individual items — the client cannot
tell reliably); `Space:` is free capacity, not stock; the per-bulk
price line's `7x` prefix and the `There are 7 items in each bulk and
you can buy 64 bulks.` line are the only sources of bulk size and
purchase limits; `You can sell 0.` is the authoritative sellable
quantity; `Enter all in chat` is the only license to offer an `all`
input. These observations come from the feature brief's recorded
responses; a `/zea capture` session at a real shop should confirm the
component-level tree (the parser tolerates both multi-line single
messages and line-per-message delivery, and missing hovers).

## Unclassified

Anything not confidently matched lands in the general/unclassified feed and
stays visible. This is an invariant, not a fallback of last resort.

## Unresolved — formats still needing captured samples

The following have **no observed samples yet** and therefore no classifiers
(they currently land in Unclassified). Add fixtures here as they are
captured in-game:

- Nation chat without a town in the bracket (unobserved; currently
  declined by the tag classifier — such messages would fall through to
  the global classifier's single-segment affiliation handling).
- The exact component tree (sibling/style structure) behind each observed
  text format — text-level fixtures exist; component-level structure needs
  in-game capture. The local/trade/nation/town legacy samples above came
  from rendered logs, so their per-segment styling is known, but the wire
  component layout is not.
- Whether bracket-less global messages exist (nationless/townless
  senders).
