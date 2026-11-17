package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure placement math for inventory buttons, constrained to fixed zones
 * (the dev.36 spec). A button no longer carries a pixel offset: it owns
 * a {@link Zone} and an order within it, and every zone computes its
 * slot rectangles from the live screen — so the player composes with
 * capacity limits instead of parking buttons "floating in air", and a
 * button placed in a zone sits in the same spot on every container.
 *
 * <p>The zones, top to bottom: a row of up to 8 on top of the
 * inventory; up to 8 stacked along each side, bottom-aligned to the
 * hotbar's level and climbing up; up to 8 directly under the GUI (under
 * the inventory's hotbar row); up to 9 in the row directly above the
 * search bar (which sits at the screen's bottom edge); one button
 * inside the GUI's top-right corner; and up to 4 just under the 2×2
 * crafting grid — that row and the corner belong to the player's own
 * inventory screen only (on chests they would cover live slots).
 *
 * <p>All computations are pure functions of the live values; nothing is
 * cached across geometry changes, so resize and recipe-book toggles are
 * correct by construction. Buttons that cannot render on the current
 * screen (foreign container band, recipe book open, off-screen, zone
 * rows that would collide) are hidden, never overlapped — edit mode
 * still draws them so crowding is visible.
 */
public final class ButtonGeometry {
	/** Button side length in GUI units; icons render at 16 with 1 padding. */
	public static final int SIZE = 18;

	/** Gap between a button and the GUI or its neighbours. */
	public static final int GAP = 2;

	/** The standard canvas every position is authored against (editor view). */
	public static final int CANVAS_WIDTH = 176;
	public static final int CANVAS_HEIGHT = 166;

	/** The standard canvas's height; taller containers shift nothing — zones hug the edges. */
	private static final int STANDARD_HEIGHT = 166;

	/**
	 * The x the top / under-hotbar / above-search rows start at: aligned
	 * with the inventory's leftmost slot column, so a full row reads as
	 * a ninth column of slots.
	 */
	public static final int ROW_START_X = 8;

	/** The pitch of every row and column: one button plus the gap. */
	public static final int STEP = SIZE + GAP;

	/**
	 * The 2×2 crafting grid's top-left slot on the standard canvas — the
	 * under-crafting row hangs {@link #UNDER_CRAFTING_Y} below it.
	 */
	public static final int CRAFTING_X = 97;
	public static final int CRAFTING_Y = 17;

	/** The under-crafting row's canvas y: below the grid's bottom edge (17 + 34 = 51). */
	public static final int UNDER_CRAFTING_Y = 55;

	/**
	 * The search bar's footprint, duplicated from the widget to keep
	 * this class free of Minecraft types: the bar sits at the screen's
	 * bottom edge with a small margin, and the above-search row hangs
	 * above it.
	 */
	public static final int SEARCH_BAR_HEIGHT = 20;
	public static final int SEARCH_BAR_BOTTOM_MARGIN = 5;

	/**
	 * The NEU search-bar group's pure numbers — the same values
	 * {@link net.zaminmc.zea.search.SearchBarLayout} computes — held
	 * here as constants so the above-search zone can center on the bar
	 * without reaching into a class that touches Minecraft widgets.
	 * The geometry tests pin these to the live layout math.
	 */
	static final class SearchBarLayoutCompat {
		static final int BAR_WIDTH = 200;
		static final int BUTTON_SIZE = 22;
		static final int GROUP_PADDING = 4;

		static int groupWidth(int scaleFactor) {
			int buttonSize = Math.max(40 / Math.max(1, scaleFactor), 16) + 2 * Math.max(1, 2 / Math.max(1, scaleFactor));
			return BAR_WIDTH + 2 * (buttonSize + GROUP_PADDING);
		}

		private SearchBarLayoutCompat() {
		}
	}

	/**
	 * The fixed slots of the survival inventory canvas the editor's
	 * snap snapped to in the free-placement era; kept for the v3 → v4
	 * zone migration's nearest-zone decisions.
	 */
	public static final List<int[]> INVENTORY_FIXED_SLOTS = List.of(
			new int[] {25, 8},
			new int[] {57, 8},
			new int[] {25, 60},
			new int[] {57, 60},
			new int[] {97, 17},
			new int[] {115, 17},
			new int[] {97, 35},
			new int[] {115, 35},
			new int[] {153, 27},
			new int[] {98, 61},
			new int[] {116, 61},
			new int[] {134, 61},
			new int[] {152, 61});

	/**
	 * The placement zones, with their capacities. {@code inventoryOnly}
	 * zones render on the player's own inventory screen only — they
	 * cover canvas areas that belong to live slots on other containers.
	 */
	public enum Zone {
		/** A row of up to 8 buttons on top of the GUI. */
		TOP(8, false),
		/** Up to 8 buttons up the left edge, bottom at the hotbar's level. */
		LEFT(8, false),
		/** Up to 8 buttons up the right edge, bottom at the hotbar's level. */
		RIGHT(8, false),
		/** Up to 8 buttons directly under the GUI's hotbar row. */
		UNDER_HOTBAR(8, false),
		/** Up to 9 buttons in the row directly above the search bar. */
		ABOVE_SEARCH(9, false),
		/** Up to 4 buttons just under the 2×2 crafting grid (inventory only). */
		UNDER_CRAFTING(4, true),
		/** One button in the GUI's top-right corner (inventory only). */
		TOP_RIGHT(1, true);

		/** The zone's capacity. */
		public final int capacity;
		/** Whether the zone renders on the player's inventory screen only. */
		public final boolean inventoryOnly;

		Zone(int capacity, boolean inventoryOnly) {
			this.capacity = capacity;
			this.inventoryOnly = inventoryOnly;
		}

		/** The zone with this id, or {@code null} for an unknown name. */
		public static Zone byName(String name) {
			for (Zone zone : values()) {
				if (zone.name().equalsIgnoreCase(name)) {
					return zone;
				}
			}
			return null;
		}
	}

	/**
	 * The geometry the placement math runs against. All fields are the
	 * live values of the open screen; the record is built per use and
	 * never stored.
	 *
	 * @param left           the container's left edge ({@code leftPos})
	 * @param top            the container's top edge ({@code topPos})
	 * @param width          the container's width ({@code imageWidth})
	 * @param height         the container's height ({@code imageHeight})
	 * @param screenWidth    the scaled screen width
	 * @param screenHeight   the scaled screen height
	 * @param playerInventory whether this is the player's own inventory
	 *                        screen (inventory-only zones render there)
	 * @param recipeBookOpen whether the recipe book panel is open (it
	 *                       claims the strip left of the GUI)
	 */
	public record Inputs(
			int left,
			int top,
			int width,
			int height,
			int screenWidth,
			int screenHeight,
			boolean playerInventory,
			boolean recipeBookOpen,
			int scaleFactor) {
	}

	/**
	 * One button's resolved position for a frame.
	 *
	 * @param button   the button placed
	 * @param x        left edge, meaningful only when rendered
	 * @param y        top edge, meaningful only when rendered
	 * @param rendered whether the button is drawn and clickable here
	 */
	public record Placement(InventoryButton button, int x, int y, boolean rendered) {
	}

	private ButtonGeometry() {
	}

	/** One zone slot's rectangle on the given inputs. */
	public static record SlotRect(Zone zone, int order, int x, int y) {
	}

	/**
	 * Resolves every button's rectangle for the given inputs, in draw
	 * order (stored order). Hit tests scan the list backwards so the
	 * topmost button wins.
	 */
	public static List<Placement> placements(List<InventoryButton> buttons, Inputs inputs) {
		List<InventoryButton> list = buttons == null ? List.of() : buttons;
		List<Placement> out = new ArrayList<>(list.size());
		for (InventoryButton button : list) {
			out.add(place(button, inputs));
		}
		return out;
	}

	/** The rendered placements only, in draw order. */
	public static List<Placement> rendered(List<Placement> placements) {
		List<Placement> out = new ArrayList<>(placements.size());
		for (Placement placement : placements) {
			if (placement.rendered()) {
				out.add(placement);
			}
		}
		return out;
	}

	/** One button's resolved rectangle on the given screen. */
	private static Placement place(InventoryButton button, Inputs inputs) {
		Zone zone = Zone.byName(button.zone());
		if (zone == null) {
			return new Placement(button, 0, 0, false);
		}
		SlotRect rect = slotRect(zone, button.order(), inputs);
		boolean rendered = rect != null;
		int x = rect == null ? 0 : rect.x();
		int y = rect == null ? 0 : rect.y();
		if (rendered && inputs.recipeBookOpen() && x < inputs.left()) {
			// The recipe book panel owns the strip left of the GUI while open.
			rendered = false;
		}
		if (rendered && (x < 0 || y < 0
				|| x + SIZE > inputs.screenWidth()
				|| y + SIZE > inputs.screenHeight())) {
			rendered = false;
		}
		return new Placement(button, x, y, rendered);
	}

	/**
	 * The rectangle of one zone slot, or null when the slot cannot
	 * render here: an out-of-range order, an inventory-only zone on a
	 * foreign container, or an above-search row that would collide with
	 * the under-hotbar row on a short screen.
	 */
	public static SlotRect slotRect(Zone zone, int order, Inputs inputs) {
		if (zone == null || order < 0 || order >= zone.capacity) {
			return null;
		}
		if (zone.inventoryOnly && !inputs.playerInventory()) {
			return null;
		}
		switch (zone) {
			case TOP:
				return new SlotRect(zone, order,
						inputs.left() + ROW_START_X + order * STEP,
						inputs.top() - SIZE - GAP);
			case LEFT:
				// Bottom-aligned: order 0 hugs the hotbar's level, later
				// orders climb toward the GUI's top (never past it).
				return new SlotRect(zone, order,
						inputs.left() - SIZE - GAP,
						inputs.top() + inputs.height() - (order + 1) * SIZE - order * GAP);
			case RIGHT:
				return new SlotRect(zone, order,
						inputs.left() + inputs.width() + GAP,
						inputs.top() + inputs.height() - (order + 1) * SIZE - order * GAP);
			case UNDER_HOTBAR:
				return new SlotRect(zone, order,
						inputs.left() + ROW_START_X + order * STEP,
						inputs.top() + inputs.height() + GAP);
			case ABOVE_SEARCH: {
				// The NEU search bar the row hangs above: its height
				// and its bottom-center anchor are the search bar
				// layout's own math (pinned to it by the geometry
				// tests), duplicated here so this class stays free
				// of Minecraft types.
				int barHeight = Math.max(40 / Math.max(1, inputs.scaleFactor()), 16);
				int y = inputs.screenHeight() - 10 - barHeight - GAP - SIZE;
				if (y < inputs.top() + inputs.height() + GAP + SIZE) {
					// The screen is too short for both bottom rows: the
					// above-search row hides rather than stacking onto
					// the under-hotbar row.
					return null;
				}
				int groupWidth = SearchBarLayoutCompat.groupWidth(inputs.scaleFactor());
				int barLeft = Math.max(0,
						(inputs.screenWidth() - groupWidth) / 2) + 22 + 4;
				int barWidth = SearchBarLayoutCompat.BAR_WIDTH;
				int rowWidth = 9 * STEP - GAP;
				int rowStart = barLeft + (barWidth - rowWidth) / 2;
				return new SlotRect(zone, order,
						rowStart + order * STEP,
						y);
			}
			case UNDER_CRAFTING:
				return new SlotRect(zone, order,
						inputs.left() + CRAFTING_X + order * STEP,
						inputs.top() + UNDER_CRAFTING_Y);
			case TOP_RIGHT:
				return new SlotRect(zone, order,
						inputs.left() + inputs.width() - SIZE - 2,
						inputs.top() + 2);
			default:
				return null;
		}
	}

	/** The topmost rendered placement containing the point, if any. */
	public static Placement at(List<Placement> placements, double mx, double my) {
		for (int i = placements.size() - 1; i >= 0; i--) {
			Placement placement = placements.get(i);
			if (placement.rendered()
					&& contains(placement.x(), placement.y(), SIZE, SIZE, mx, my)) {
				return placement;
			}
		}
		return null;
	}

	/** The topmost placement containing the point, unrendered ones included (edit mode). */
	public static Placement editableAt(List<Placement> placements, double mx, double my) {
		for (int i = placements.size() - 1; i >= 0; i--) {
			Placement placement = placements.get(i);
			if (contains(placement.x(), placement.y(), SIZE, SIZE, mx, my)) {
				return placement;
			}
		}
		return null;
	}

	/** Whether the point ({@code mx}, {@code my}) lies in the rectangle [x, x+w) × [y, y+h). */
	public static boolean contains(int x, int y, int w, int h, double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/**
	 * The zone slot nearest to a drag's landing point, for the editor's
	 * drop: every zone slot that can render on the editor's canvas is
	 * scored by center distance, and the nearest free slot wins. When
	 * the nearest slot is occupied the same zone's next free order is
	 * taken; a full zone falls through to the next-nearest zone.
	 *
	 * @param buttons the live set (occupancy is checked against it)
	 * @param except  the dragged button — it does not block its old slot
	 * @param x       the landing point in canvas coordinates
	 * @param y       the landing point in canvas coordinates
	 * @param inputs  the editor canvas's geometry (its own screen values)
	 * @return the target zone and order, or null when no zone fits
	 */
	public static SlotRect dropTarget(List<InventoryButton> buttons, InventoryButton except,
			int x, int y, Inputs inputs) {
		List<SlotRect> candidates = new ArrayList<>();
		for (Zone zone : Zone.values()) {
			for (int order = 0; order < zone.capacity; order++) {
				SlotRect rect = slotRect(zone, order, inputs);
				if (rect != null) {
					candidates.add(rect);
				}
			}
		}
		candidates.sort((a, b) -> Long.compare(
				distanceSq(a, x, y), distanceSq(b, x, y)));
		for (SlotRect candidate : candidates) {
			if (!occupied(buttons, except, candidate.zone(), candidate.order())) {
				return candidate;
			}
		}
		return null;
	}

	/** Squared distance from the point to the slot's center. */
	private static long distanceSq(SlotRect rect, int x, int y) {
		long dx = rect.x() + SIZE / 2 - x;
		long dy = rect.y() + SIZE / 2 - y;
		return dx * dx + dy * dy;
	}

	/** Whether some other button already owns the zone slot. */
	public static boolean occupied(List<InventoryButton> buttons, InventoryButton except,
			Zone zone, int order) {
		for (InventoryButton button : buttons) {
			if (button == except || button.id().equals(except == null ? null : except.id())) {
				continue;
			}
			Zone other = Zone.byName(button.zone());
			if (other == zone && button.order() == order) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The first free order in a zone — where a fresh button lands.
	 * Returns -1 when the zone is full.
	 */
	public static int firstFreeOrder(List<InventoryButton> buttons, Zone zone) {
		for (int order = 0; order < zone.capacity; order++) {
			if (!occupied(buttons, null, zone, order)) {
				return order;
			}
		}
		return -1;
	}
}
