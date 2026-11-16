package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure placement math for freely placed inventory buttons, the NEU /
 * Inventory-Buttons model. A button's stored position is an offset from
 * the container rectangle's corner (which corner depends on its two edge
 * anchors); everything here converts those stored positions into screen
 * rectangles for one open container screen, decides whether a position
 * renders on that screen at all, validates drops against overlaps, and
 * implements the editor's grid snap.
 *
 * <p>Positions are authored on the standard inventory canvas — the
 * 176×166 survival inventory texture — because that is the one layout
 * every player knows and the editor shows. Two rules make those
 * positions portable to other containers, both taken from the reference
 * port: on the player's own inventory screen everything renders as
 * placed; on other containers (chests and friends), buttons placed in
 * the container-slot band of the canvas (the top {@link #CONTAINER_BAND}
 * rows) would sit on top of live slots, so unanchored ones there are not
 * rendered — anchored ones measure from the edges and sit outside anyway
 * — while buttons in the player-inventory band (the bottom rows) shift
 * down by however much taller the container is, exactly like the
 * inventory rows do on a chest's texture.
 *
 * <p>All computations are pure functions of the live values; nothing is
 * cached across geometry changes, so resize and recipe-book toggles are
 * correct by construction.
 */
public final class ButtonGeometry {
        /** Button side length in GUI units; icons render at 16 with 1 padding. */
        public static final int SIZE = 18;

        /** Gap between a button and the GUI or its neighbours. */
        public static final int GAP = 2;

        /** The standard canvas every position is authored against. */
        public static final int CANVAS_WIDTH = 176;
        public static final int CANVAS_HEIGHT = 166;

        /**
         * The rows of the standard canvas a chest's own slots occupy:
         * unanchored buttons placed above this line do not render on
         * non-inventory containers (they would cover live slots).
         */
        public static final int CONTAINER_BAND = 80;

        /** The standard canvas's height; taller containers shift the inventory band down by the difference. */
        private static final int STANDARD_HEIGHT = 166;

        /** Farthest a stored offset may reach in either direction. */
        public static final int MAX_OFFSET = 1000;

        /** Coordinates for placements with no home; far outside any hit test. */
        private static final int UNPLACED = -100_000;

        /**
         * The fixed slots of the survival inventory canvas the editor's snap
         * snaps to: the four armor slots, offhand, crafting grid and result,
         * and the four row starts. In canvas coordinates.
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

        /** The outside grid the editor's snap uses around the GUI. */
        public static final int OUTER_PADDING = 2;
        public static final int OUTER_GRID = SIZE + OUTER_PADDING;
        public static final int TOP_BOTTOM_START_X = 8;

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
         *                        screen (positions render as authored there)
         * @param recipeBookOpen whether the recipe book panel is open (it
         *                       claims the strip left of the GUI)
         * @param bottomBandOffset vertical pixels below the GUI that are
         *                       already occupied (the search bar's strip);
         *                       bottom-anchored buttons shift down by it so
         *                       the two never overlap. Zero when nothing
         *                       reserves the strip.
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
                        int bottomBandOffset) {
                /** The historical shape: no strip reserved below the GUI. */
                public Inputs(int left, int top, int width, int height, int screenWidth, int screenHeight,
                                boolean playerInventory, boolean recipeBookOpen) {
                        this(left, top, width, height, screenWidth, screenHeight, playerInventory,
                                        recipeBookOpen, 0);
                }
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
                int x = inputs.left() + button.x() + (button.anchorRight() ? inputs.width() : 0);
                int y = inputs.top() + button.y();
                if (button.anchorBottom()) {
                        // The reserved strip below the GUI (the search bar's
                        // row, when the bar exists) pushes the docked row down
                        // whole bands, so a docked button is never under it.
                        y += inputs.height() + inputs.bottomBandOffset();
                } else if (!inputs.playerInventory() && button.y() >= CONTAINER_BAND) {
                        // The player-inventory band sits lower on taller containers;
                        // a button parked on it rides along.
                        y += inputs.height() - STANDARD_HEIGHT;
                }
                boolean rendered = true;
                if (!inputs.playerInventory() && !button.anchorRight() && !button.anchorBottom()
                                && button.y() < CONTAINER_BAND
                                && button.x() >= 0 && button.x() <= CANVAS_WIDTH) {
                        // Inside the container-slot band of a non-inventory container:
                        // it would cover live slots.
                        rendered = false;
                }
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
         * Whether a button parked at the stored position ({@code x},
         * {@code y} with the given anchors) would overlap any other button —
         * the drop rule the editor enforces. Overlap is compared in absolute
         * canvas space (anchors resolved against the standard canvas), so an
         * anchored and an unanchored button cannot sneak onto the same spot.
         */
        public static boolean overlapsAny(List<InventoryButton> buttons, InventoryButton except,
                        int x, int y, boolean anchorRight, boolean anchorBottom) {
                int ax = x + (anchorRight ? CANVAS_WIDTH : 0);
                int ay = y + (anchorBottom ? CANVAS_HEIGHT : 0);
                for (InventoryButton other : buttons) {
                        if (other == except || other.id().equals(except.id())) {
                                continue;
                        }
                        int bx = other.x() + (other.anchorRight() ? CANVAS_WIDTH : 0);
                        int by = other.y() + (other.anchorBottom() ? CANVAS_HEIGHT : 0);
                        if (Math.abs(bx - ax) < SIZE && Math.abs(by - ay) < SIZE) {
                                return true;
                        }
                }
                return false;
        }

        /** A snapped position. */
        public record Snapped(int x, int y) {
        }

        /**
         * The editor's snap: where a button dragged to canvas position
         * ({@code x}, {@code y}) should land.
         *
         * <p>With the grid on (the S key), positions inside the canvas snap
         * to the nearest fixed inventory slot, and positions outside it snap
         * to the 20-pixel ring grid around the GUI — the NEU editor's
         * discipline: buttons line up with slots inside and with each other
         * outside. With the grid off, positions inside the bottom band and
         * the left column still magnetically snap to the nearest fixed slot
         * (the reference port's behavior: the armor/offhand/crafting area
         * collects buttons into its slots), and everything else lands exactly
         * where dropped.
         */
        public static Snapped snap(int x, int y, boolean gridSnap) {
                boolean outsideX = x < 0 || x > CANVAS_WIDTH - SIZE;
                boolean outsideY = y < 0 || y > CANVAS_HEIGHT - SIZE;
                if (!gridSnap) {
                        boolean bottomZone = x + SIZE > 0 && x < CANVAS_WIDTH
                                        && y + SIZE > CONTAINER_BAND + 3 && y < CANVAS_HEIGHT;
                        boolean leftZone = x + SIZE > 0 && x < 26
                                        && y + SIZE > 7 && y < CONTAINER_BAND + 3;
                        if (bottomZone || leftZone) {
                                return nearestFixedSlot(x, y);
                        }
                        return new Snapped(x, y);
                }
                if (!outsideX && !outsideY) {
                        return nearestFixedSlot(x, y);
                }
                int snappedX = x;
                int snappedY = y;
                if (x < 0) {
                        int col = (x + OUTER_PADDING) / OUTER_GRID;
                        snappedX = -OUTER_PADDING - SIZE + col * OUTER_GRID;
                } else if (x > CANVAS_WIDTH - SIZE) {
                        int col = (x - CANVAS_WIDTH + OUTER_PADDING) / OUTER_GRID;
                        snappedX = CANVAS_WIDTH + OUTER_PADDING + col * OUTER_GRID;
                } else if (outsideY) {
                        int col = Math.round((float) (x - TOP_BOTTOM_START_X) / OUTER_GRID);
                        snappedX = TOP_BOTTOM_START_X + col * OUTER_GRID;
                }
                if (y < 0) {
                        int row = (y + OUTER_PADDING) / OUTER_GRID;
                        snappedY = -OUTER_PADDING - SIZE + row * OUTER_GRID;
                } else if (y > CANVAS_HEIGHT - SIZE) {
                        int row = (y - CANVAS_HEIGHT + OUTER_PADDING) / OUTER_GRID;
                        snappedY = CANVAS_HEIGHT + OUTER_PADDING + row * OUTER_GRID;
                } else if (outsideX) {
                        int row = Math.round((float) (y - OUTER_PADDING) / OUTER_GRID);
                        snappedY = OUTER_PADDING + row * OUTER_GRID;
                }
                return new Snapped(snappedX, snappedY);
        }

        private static Snapped nearestFixedSlot(int x, int y) {
                int[] best = null;
                long bestDistSq = Long.MAX_VALUE;
                for (int[] slot : INVENTORY_FIXED_SLOTS) {
                        long distSq = (long) (x - slot[0]) * (x - slot[0]) + (long) (y - slot[1]) * (y - slot[1]);
                        if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                best = slot;
                        }
                }
                return best == null ? new Snapped(x, y) : new Snapped(best[0], best[1]);
        }

        /**
         * The stored position for a screen rectangle: converts the absolute
         * canvas point back into an offset plus anchors, anchoring to the
         * edges the position sits beyond — a drop past the right edge docks
         * to it, so the button survives every container width.
         */
        public static InventoryButton atCanvasPosition(InventoryButton button, int x, int y) {
                boolean anchorRight = x >= CANVAS_WIDTH;
                boolean anchorBottom = y >= CANVAS_HEIGHT;
                int storedX = anchorRight ? x - CANVAS_WIDTH : x;
                int storedY = anchorBottom ? y - CANVAS_HEIGHT : y;
                return button.withPosition(storedX, storedY, anchorRight, anchorBottom);
        }

        /**
         * The stored position for a drop on the editor's container preview —
         * the preview frame's rectangle (a taller chest, say) decides where
         * the edges are, and the point must already be canvas-authored (the
         * band ride-along undone by the caller). Horizontal behavior is the
         * canvas rule: every standard container is 176 GUI pixels wide, the
         * same width the canvas anchors measure against.
         */
        public static InventoryButton atPreviewPosition(InventoryButton button, int x, int y,
                        int previewWidth, int previewHeight) {
                boolean anchorRight = x >= previewWidth;
                boolean anchorBottom = y >= previewHeight;
                int storedX = anchorRight ? x - previewWidth : x;
                int storedY = anchorBottom ? y - previewHeight : y;
                return button.withPosition(storedX, storedY, anchorRight, anchorBottom);
        }
}
