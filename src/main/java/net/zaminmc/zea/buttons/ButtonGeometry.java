package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure layout math for inventory buttons. All positions are in GUI units,
 * computed from the container rectangle the screen reports
 * ({@code leftPos}, {@code topPos}, {@code imageWidth}, {@code imageHeight})
 * and the screen's own dimensions — never from stored pixel offsets.
 *
 * <p>The inputs record fixes the occupied areas the layout must respect:
 * the search bar's row (when enabled) sits directly below the GUI, the
 * under-search button row beneath it, the bottom row beneath that, and the
 * recipe book's panel (when open) claims the strip left of the GUI, so
 * the left column yields instead of fighting it. Every rectangle the
 * layout emits lies fully inside the screen; buttons that do not fit are
 * returned as unrendered placements rather than colliding — the overflow
 * answer this file implements is "hide, never overlap".
 *
 * <p>Layout is recomputed on demand from live values (each frame while a
 * container screen is open, and per input event for hit tests), so resize
 * and recipe-book toggles are correct by construction — nothing is cached
 * across geometry changes.
 */
public final class ButtonGeometry {
        /** Button side length in GUI units; icons render at 16 with 1 padding. */
        public static final int SIZE = 18;

        /** Gap between a button and the GUI or its neighbours. */
        public static final int GAP = 2;

        /** Coordinates for placements with no home (region unavailable); far outside any hit test. */
        private static final int UNPLACED = -100_000;

        /** Height of the inventory search bar. */
        public static final int SEARCH_BAR_HEIGHT = 12;

        /**
         * The geometry the layout runs against. All fields are the live
         * values of the open screen; the record is built per use and never
         * stored.
         *
         * @param left           the container's left edge ({@code leftPos})
         * @param top            the container's top edge ({@code topPos})
         * @param width          the container's width ({@code imageWidth})
         * @param height         the container's height ({@code imageHeight})
         * @param screenWidth    the scaled screen width
         * @param screenHeight   the scaled screen height
         * @param searchBarShown whether the inventory search bar is rendered
         *                       beneath this GUI
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
                        boolean searchBarShown,
                        boolean recipeBookOpen) {
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

        /** Sorts buttons into region draw order: (region, order, id). */
        private static final Comparator<InventoryButton> ORDER = Comparator
                        .comparing(InventoryButton::region)
                        .thenComparingInt(InventoryButton::order)
                        .thenComparing(button -> button.id().toString());

        /**
         * Resolves every button's rectangle for the given inputs. The list
         * is in draw order; hit tests scan it backwards so the topmost
         * button wins. Regions the predicate refuses contribute no
         * rendered placements (the config's per-region toggles).
         */
        public static List<Placement> placements(List<InventoryButton> buttons, Inputs inputs,
                        Predicate<ButtonRegion> regionEnabled) {
                List<InventoryButton> sorted = new ArrayList<>(buttons == null ? List.of() : buttons);
                sorted.sort(ORDER);
                List<Placement> out = new ArrayList<>(sorted.size());
                // The bottom row stacks below the under-search row only when
                // that row actually holds buttons — no wasted gap otherwise.
                boolean underSearchOccupied = false;
                for (InventoryButton button : sorted) {
                        if (button.region() == ButtonRegion.UNDER_SEARCH && regionEnabled.test(ButtonRegion.UNDER_SEARCH)) {
                                underSearchOccupied = true;
                                break;
                        }
                }
                int[] regionIndex = new int[ButtonRegion.values().length];
                for (InventoryButton button : sorted) {
                        int index = regionIndex[button.region().ordinal()]++;
                        if (!regionEnabled.test(button.region())) {
                                out.add(new Placement(button, UNPLACED, UNPLACED, false));
                                continue;
                        }
                        Placement placement = place(button, index, inputs, underSearchOccupied);
                        out.add(placement);
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

        /** The placement of one button at {@code index} within its region. */
        private static Placement place(InventoryButton button, int index, Inputs inputs, boolean underSearchOccupied) {
                int x;
                int y;
                boolean rendered = true;
                switch (button.region()) {
                        case TOP -> {
                                x = inputs.left() + index * (SIZE + GAP);
                                y = inputs.top() - SIZE - GAP;
                        }
                        case UNDER_SEARCH -> {
                                if (!inputs.searchBarShown()) {
                                        // The search bar is the anchor for this region; without
                                        // it the buttons have no home and must not drift.
                                        return new Placement(button, UNPLACED, UNPLACED, false);
                                }
                                x = inputs.left() + index * (SIZE + GAP);
                                y = inputs.top() + inputs.height() + GAP + SEARCH_BAR_HEIGHT + GAP;
                        }
                        case BOTTOM -> {
                                int below = inputs.top() + inputs.height() + GAP;
                                if (inputs.searchBarShown()) {
                                        below += SEARCH_BAR_HEIGHT + GAP;
                                        if (underSearchOccupied) {
                                                below += SIZE + GAP;
                                        }
                                }
                                x = inputs.left() + index * (SIZE + GAP);
                                y = below;
                        }
                        case LEFT -> {
                                if (inputs.recipeBookOpen()) {
                                        // The recipe book panel owns this strip while it is open.
                                        return new Placement(button, UNPLACED, UNPLACED, false);
                                }
                                x = inputs.left() - SIZE - GAP;
                                y = inputs.top() + index * (SIZE + GAP);
                        }
                        case RIGHT -> {
                                x = inputs.left() + inputs.width() + GAP;
                                y = inputs.top() + index * (SIZE + GAP);
                        }
                        default -> {
                                return new Placement(button, UNPLACED, UNPLACED, false);
                        }
                }
                if (index >= button.region().capacity()) {
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

        /** The topmost placement containing the point, disabled ones included (edit mode). */
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
         * The rectangle of the inventory search bar for these inputs:
         * flush with the GUI's width, directly beneath it.
         */
        public static int searchBarY(Inputs inputs) {
                return inputs.top() + inputs.height() + GAP;
        }

        /**
         * The region and insertion index a dragged button dropped at
         * ({@code bx}, {@@code by}) belongs to: the region whose strip
         * contains the point, and the index that keeps the drop's
         * neighbours ordered. Vertical regions round toward the slot the
         * cursor is over.
         */
        public static DropTarget dropTarget(Inputs inputs, int bx, int by, boolean searchBarShown) {
                int step = SIZE + GAP;
                // Horizontal strips.
                if (by >= inputs.top() - SIZE - GAP && by < inputs.top()) {
                        return new DropTarget(ButtonRegion.TOP, indexFromCoordinate(bx - inputs.left(), step));
                }
                int underY = inputs.top() + inputs.height() + GAP;
                if (searchBarShown && by >= underY + SEARCH_BAR_HEIGHT + GAP
                                && by < underY + SEARCH_BAR_HEIGHT + GAP + SIZE) {
                        return new DropTarget(ButtonRegion.UNDER_SEARCH, indexFromCoordinate(bx - inputs.left(), step));
                }
                int bottomY = underY + (searchBarShown ? SEARCH_BAR_HEIGHT + GAP : 0);
                if (by >= bottomY && by < bottomY + SIZE) {
                        return new DropTarget(ButtonRegion.BOTTOM, indexFromCoordinate(bx - inputs.left(), step));
                }
                // Vertical strips: anywhere beside the GUI vertically.
                if (bx >= inputs.left() - SIZE - GAP - step && bx < inputs.left()) {
                        return new DropTarget(ButtonRegion.LEFT, indexFromCoordinate(by - inputs.top(), step));
                }
                if (bx >= inputs.left() + inputs.width() && bx < inputs.left() + inputs.width() + SIZE + GAP + step) {
                        return new DropTarget(ButtonRegion.RIGHT, indexFromCoordinate(by - inputs.top(), step));
                }
                return null;
        }

        private static int indexFromCoordinate(int offset, int step) {
                if (offset < 0) {
                        return 0;
                }
                return offset / step;
        }

        /** Where a drag released: the region to insert into and the index. */
        public record DropTarget(ButtonRegion region, int index) {
        }
}
