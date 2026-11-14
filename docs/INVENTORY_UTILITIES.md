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

- Buttons render in five placement regions around the container GUI:
  a top row (up to 8), a bottom row (up to 8), the row under the search
  bar (up to 5), and left/right columns (up to 8 each). A button owns a
  region and an order within it — never pixel offsets.
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

- The search bar sits flush under the GUI; under-search buttons under
  it; the bottom row under those (directly under the GUI when the bar
  or the under-search row is absent).
- The left column yields while the recipe book panel is open.
- Buttons that cannot fit (capacity exceeded, screen edge) are hidden,
  never overlapped — edit mode still draws them with their positions so
  crowding is visible and fixable.
- Hit tests use the same computed rectangles as rendering.

### Persistence

`zea/buttons.json`, schema version 2, safe-write protocol. Version 1
(corner anchors + pixel offsets) migrates on load: top corners map to
the top row, bottom corners to the bottom row, buttons pushed past the
GUI's right edge to the right column, orders derived from the old
horizontal offsets. Blank-command buttons are kept — they are the
preset templates' shape — and render nowhere until configured.

## Inventory search bar

- The bar renders under the container GUI (not on the creative screen,
  which has its own search). Single click focuses it.
- **Calculator**: typing an arithmetic expression shows the result in
  the bar (`39 + 4 = 43`): `+ - * /`, parentheses, decimals, unary
  minus, BigDecimal arithmetic, division by zero and incomplete
  expressions simply show nothing. The expression is preserved; the
  result is an overlay.
- **Double-click** toggles matching mode (the bar turns gold):
  items whose tooltip text matches the query get a subtle colored
  highlight and border; everything else is dimmed. Empty slots are left
  alone. Matching is case-insensitive, searches the item's full tooltip
  text (name, lore, enchantments), and supports `|` as OR. Clearing the
  query restores the inventory immediately.
- While the bar is focused it owns the keyboard: Esc leaves matching
  mode first, then focus; the inventory key defocuses instead of
  closing; number keys never trigger hotbar swaps mid-typing. Opening a
  different container resets query and mode.
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
