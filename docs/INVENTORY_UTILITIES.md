# Inventory utilities

ZEA's inventory utility systems, inspired by NEU's inventory buttons,
search bar, slot binding and slot locking, implemented for ZEA's
architecture and Minecraft 26.3:

1. **Inventory buttons** — configurable buttons beside container GUIs.
2. **Inventory search bar** — a calculator and an inventory matching
   mode under the GUI.
3. **Slot binding** — pair an inventory slot with a hotbar slot.
4. **Slot locking** — protect slots from accidental actions.

Everything here is client-side rendering and input routing. Nothing
modifies an item stack, a slot, or the menu; the only server-bound
action is a button's command dispatch.

The NEU research this design is built on (source files and the
historical bug-fix commits) is recorded in WORKLOG.md under the
dev.27 entry.

## Inventory buttons

### User-facing behavior

- Buttons render in the dev.58 placement zones around the container GUI
  (the "no floating buttons" spec): up to 8 on top of the inventory; up
  to 8 up each side, bottom-aligned to the hotbar's level; up to 8
  directly under the inventory's hotbar row; up to 9 in the row right
  above the search bar; 1 inside the inventory's top-right corner; and
  up to 4 just under the 2×2 crafting grid (that row and the corner are
  the player's own inventory only). A button owns a zone and an order
  within it — never pixel offsets, never free placement.
- Hovering shows the button name, its command, and warnings for unknown
  icons, unconfigured buttons, and buttons currently hidden (no room,
  recipe book open, search bar off).
- A button with display text draws the text instead of an icon.
- Left click runs the command (no leading slash needed). While an item
  is on the cursor the click is consumed but nothing runs, so clicking
  a button never drops the carried item.
- *Close on execute*: when enabled, the screen closes after the command
  dispatched — and only if the command itself did not already open
  another screen.
- `/zeabuttons` opens the button editor: a live canvas of the inventory
  with every button at its region position, a control panel (add, edit,
  move up/down, region, enable, delete) and the preset row. The same
  editor is reachable from the config's *Open button editor* button.
- Presets: **Default** (a starter canvas), **Buttons Everywhere**
  (every region full) and **Empty** (none). Preset buttons carry blank
  commands — like NEU's default set, they are a positioned canvas to
  fill in, and ZEA does not invent EarthMC commands. Applying a preset
  keeps the replaced set recoverable through *Restore previous* until
  the next regular edit.
- The rebindable *Toggle button editing* key (Controls → ZEA, unbound
  by default) turns edit mode on in a container screen: gold borders,
  dragging a button between regions, right-click to edit, and a **+**
  ghost in the top row for new buttons. Esc leaves edit mode first.

### Layout rules

`ButtonGeometry` computes every rectangle from the live screen values —
container bounds, screen size, search-bar visibility, recipe-book
state — so resizing and recipe-book toggles are correct by
construction:

- The search bar spans the screen's bottom edge (the dev.58 request:
  it used to hug the GUI's lower edge, reading as part of the
  inventory); the above-search row hangs right above it, the
  under-hotbar row right under the GUI. On a short screen the
  above-search row hides rather than stacking onto the under-hotbar row.
- The left column yields while the recipe book panel is open. Dragging
  magnetically docks to the nearest free zone slot; occupied slots
  refuse.
- Buttons that cannot fit (capacity exceeded, screen edge) are hidden,
  never overlapped — edit mode still draws them with their positions so
  crowding is visible and fixable.
- Hit tests use the same computed rectangles as rendering.

### Persistence

`zea/buttons.json`, schema version 4 (zone + order), safe-write
protocol. Versions 1–3 migrate on load: the old free positions resolve
to the exact pixels their era's formulas produced, and those are then
classified into the zones their geometry lived in, with orders
re-assigned sequentially per zone. Blank-command buttons are kept — they are the
preset templates' shape — and render nowhere until configured.

## Inventory search bar

- The bar renders near the bottom of the screen, horizontally centered
  on the container GUI's own bounds (not on the creative screen, which
  has its own search). Single click focuses it.
- **Calculator** (the grammar is NotEnoughCalculator's, LGPL-3.0, adapted;
  the SkyBlock tables, taxes and storage units are dropped): numbers,
  `+ - * / % ^ !`, `x` as multiplication (`2x3`), implicit
  multiplication (`2(3+4)`, `2sqrt(4)`), parentheses, percentages
  (`100+10%` = 110; a spaced `%` is modulo instead), bitwise
  `& | ~ << >>` and `xor(a,b)`, radix literals `0b101 0x1F 0o17`
  (they shape the result too: `0x1F` shows `0x1F`), units `k m b t` and
  `s st stack stacks` (= 64; `2k` = 2,000), the constants `pi` and
  `e`, `ans` (the previous result), session variables (`$x = 5`, then
  `$x * 2`; reserved names refuse), and the functions `sqrt abs floor
  ceil round log ln sin cos tan rad deg min max gcd lcm xor pct clamp
  avg hex bin oct fmt` (`hex(255)` shows `0xFF`, `fmt(1500000)`
  shows `1.5m`). Arithmetic is BigDecimal, 50 significant digits,
  results group digits and cap at ten fraction digits.
- **A bare number shows nothing.** NEC's gate, deliberately: `2` is not
  a calculation, and neither is `diamond sword` or an unfinished
  `2+`. A query must carry an operator, parenthesis, function,
  constant, variable or unit before a result appears.
- **The result sits next to the expression** — `2+2 = 4` reads as one
  line, placed after the typed text (following the field's scroll)
  with NEC-style syntax highlighting on the input (dark-aqua numbers,
  aqua units and constants, yellow functions, red operators, purple
  words). When the expression grows too long for the result to fit
  beside it, the result moves into a small dark box under the bar
  (above it if the screen ends first) that slowly scrolls long
  numbers sideways — NEC's floating fallback. The expression is
  always preserved; the result is an overlay.
- Matching mode cannot take a frame down: an out-of-range slot index
  cannot reach the slot list, and a stack whose tooltip explodes
  degrades to its hover name.
- **Double-click** toggles matching mode (the bar turns gold): items
  whose tooltip text matches the query get a subtle colored highlight
  and border; everything else is dimmed — empty slots too, exactly
  like NEU, so a search leaves nothing undimmed. Matching is
  case-insensitive, searches the item's full tooltip text (name,
  lore, enchantments), and supports `|` as OR. Clearing the query
  restores the inventory immediately.
- While the bar is focused it owns the keyboard, the vanilla
  creative-search rule: Esc leaves matching mode first, then focus;
  plain letters — the inventory key E included — type into the bar
  instead of interrupting it, so `e` finally inserts an `e`; number
  keys never trigger hotbar swaps mid-typing; and E-to-close only
  works once the bar is unfocused (Esc or a click outside releases
  it). Opening a different container resets query and mode (but `ans`
  and `$` variables last the whole game session).
- The per-slot search text is cached against the stack it was built
  from and revalidated with `ItemStack.matches`, so it rebuilds only
  when a stack actually changes.

## Slot binding

NEU's model, on MC 26.3 interaction APIs:

- Bind any player-inventory slot except the hotbar itself (main
  inventory, armor, off-hand) to a hotbar slot: hold the lock key over
  the slot, click the hotbar slot.
- Right-click a bound slot to swap it with its hotbar partner — the
  same swap the number key performs, synthesized through the live
  menu's slot index (which is what makes armor slots and the
  player-inventory rows of chest GUIs resolve correctly; NEU needed two
  separate historical fixes for exactly those cases).
- A binding is exclusive with a lock and monogamous per hotbar slot.
- The bound slot shows its partner's digit; the item tooltip explains
  the binding.

## Slot locking

- Press the rebindable lock key (Controls → ZEA, unbound by default)
  over a player inventory slot to lock it. All nine hotbar slots, the
  off-hand and armor slots included, are lockable — MC 26.3's clean
  slot numbering has no need for NEU's ninth-slot exclusion.
- Every click path that vanilla funnels through
  `AbstractContainerScreen.slotClicked` — picks, shift-clicks,
  number-key swaps, the drop key over a slot, quick-craft drags,
  double-click collection — is blocked on a locked slot; number-key
  swaps *into* a locked hotbar slot are blocked; locked slots never
  join a drag; the drop key without a hovered slot is blocked while the
  selected slot is locked (modern `dropItem` sends the carried-item
  sync first, so unlike 1.8.9 there is no scroll-away race to guard).
- Keybinds that do not touch slots are never intercepted — NEU once
  broke every keybind while hovering a locked slot by nulling the
  hovered slot; ZEA never touches the hover state.
- Locks are per server (by connect hostname, the same key chat history
  isolates), persisted in `zea/slotlocks.json`, and survive restarts.
- Known limitation, shared with NEU: double-click collection on an
  unlocked slot can still pull matching stacks out of locked slots,
  because the server decides which slots it drains.

## Model

| Package | Role |
|---|---|
| `net.zaminmc.zea.buttons` | Buttons: model, region layout, presets, editor, rendering, dispatch |
| `net.zaminmc.zea.search` | Search bar: calculator, matching, text cache, widget |
| `net.zaminmc.zea.slots` | Locks and bindings: state, persistence, click guard, indicators |

See the class javadocs for the fine print; the container-screen
integration lives in `mixin/ContainerScreenMixin` (plus
`RecipeBookScreenMixin` and `MultiPlayerGameModeMixin`).
