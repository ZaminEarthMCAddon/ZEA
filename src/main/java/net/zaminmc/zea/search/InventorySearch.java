package net.zaminmc.zea.search;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The inventory search's session state: the query, the interaction mode
 * (calculator input vs. inventory matching), the screen the state
 * belongs to, and the per-slot search text cache.
 *
 * <p>Mode and query are attached to one screen instance: opening a
 * different container resets both, so an old search never tints an
 * unrelated inventory (NEU kept one global search bar; the reset is the
 * modern correction). The cache holds one entry per slot index — the
 * stack it was built from plus its normalized search text — validated
 * with {@link ItemStack#matches} each frame, so a stack that changes
 * (or its components) rebuilds its text and everything else is a cheap
 * compare.
 *
 * <p>The search text is the stack's full tooltip text (name, lore,
 * enchantments, attribute lines), built through
 * {@link ItemStack#getTooltipLines} — the same lines the player reads,
 * so anything visible about an item is findable, and nothing internal
 * leaks in.
 */
public final class InventorySearch {
        /** The two behaviors of the bar, toggled by double-click. */
        public enum Mode {
                /** Typing acts as a calculator; the inventory is untouched. */
                CALCULATOR,
                /** Typing highlights matching items and dims the rest. */
                MATCHING
        }

        /** Double-clicks within this window toggle the mode. */
        public static final long DOUBLE_CLICK_MILLIS = 350;

        private Mode mode = Mode.CALCULATOR;
        private String query = "";
        private Screen screen;
        private long lastBarClick;
        private final Map<Integer, CachedText> cache = new HashMap<>();

        /** One cached slot text: the stack it came from, for invalidation. */
        private record CachedText(ItemStack stack, String text) {
        }

        /** Binds the state to a screen, resetting when it is a different one. */
        public void bind(Screen current) {
                if (screen != current) {
                        screen = current;
                        mode = Mode.CALCULATOR;
                        query = "";
                        cache.clear();
                }
        }

        /** Releases the screen binding (screen closed). */
        public void release(Screen current) {
                if (screen == current) {
                        screen = null;
                        cache.clear();
                }
        }

        /** The current mode. */
        public Mode mode() {
                return mode;
        }

        /** The current query text. */
        public String query() {
                return query;
        }

        /** Sets the query (the search bar's responder). */
        public void query(String query) {
                this.query = query == null ? "" : query;
        }

        /** Whether the matching visuals should run right now. */
        public boolean matchingActive() {
                return mode == Mode.MATCHING;
        }

        /**
         * A click landed on the bar: single clicks focus, the second click
         * of a double-click toggles matching mode. Returns true when the
         * mode toggled — the caller may want the feedback.
         */
        public boolean barClicked(long nowMillis) {
                boolean toggled = nowMillis - lastBarClick <= DOUBLE_CLICK_MILLIS;
                lastBarClick = nowMillis;
                if (toggled) {
                        mode = mode == Mode.MATCHING ? Mode.CALCULATOR : Mode.MATCHING;
                        return true;
                }
                return false;
        }

        /** Leaves matching mode (the clear way out besides toggling back). */
        public void leaveMatchingMode() {
                mode = Mode.CALCULATOR;
        }

        /**
         * The normalized search text for a slot's stack, cached by slot
         * index and invalidated when the stack changes. Empty slots yield
         * an empty string — they never match a non-blank query. A stack
         * whose tooltip explodes on this client (custom server data the
         * client cannot resolve — the dev.36 live crash report) falls back
         * to its hover name: the search keeps working, the frame keeps
         * standing.
         */
        public String searchText(AbstractContainerScreen<?> container, int slotIndex) {
                java.util.List<? extends net.minecraft.world.inventory.Slot> slots = container.getMenu().slots;
                if (slotIndex < 0 || slotIndex >= slots.size()) {
                        return "";
                }
                ItemStack stack = slots.get(slotIndex).getItem();
                if (stack.isEmpty()) {
                        cache.remove(slotIndex);
                        return "";
                }
                CachedText cached = cache.get(slotIndex);
                if (cached != null && ItemStack.matches(cached.stack(), stack)) {
                        return cached.text();
                }
                String text = SearchText.normalize(safeTooltipLines(stack));
                cache.put(slotIndex, new CachedText(stack.copy(), text));
                return text;
        }

        /** Whether the slot's stack matches the current query (matching mode). */
        public boolean slotMatches(AbstractContainerScreen<?> container, int slotIndex) {
                if (!matchingActive()) {
                        return true;
                }
                return SearchText.matches(query, searchText(container, slotIndex));
        }

        /**
         * The stack's searchable lines: the full tooltip, with the
         * enchantment names guaranteed present even when the tooltip
         * display hides them (a matching mode must find "sharpness").
         */
        /**
         * The stack's searchable lines: the full tooltip, with the
         * enchantment names guaranteed present even when the tooltip
         * display hides them (a matching mode must find "sharpness"). A
         * tooltip that throws on this client degrades to the hover name —
         * the search must never crash the frame it renders in.
         */
        private static List<String> safeTooltipLines(ItemStack stack) {
                try {
                        return tooltipLines(stack);
                } catch (Exception e) {
                        return List.of(stack.getHoverName().getString());
                }
        }

        private static List<String> tooltipLines(ItemStack stack) {
                Minecraft minecraft = Minecraft.getInstance();
                List<String> lines = new ArrayList<>();
                if (minecraft.player != null && minecraft.level != null) {
                        for (Component line : stack.getTooltipLines(
                                        net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                                        minecraft.player,
                                        TooltipFlag.NORMAL)) {
                                lines.add(line.getString());
                        }
                } else {
                        lines.add(stack.getHoverName().getString());
                }
                ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
                if (enchantments != null) {
                        enchantments.keySet().forEach(holder -> {
                                Enchantment enchantment = holder.value();
                                lines.add(enchantment.description().getString());
                        });
                }
                return lines;
        }
}
