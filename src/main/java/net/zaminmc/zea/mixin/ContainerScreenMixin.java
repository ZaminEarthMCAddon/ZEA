package net.zaminmc.zea.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.buttons.ButtonEditorScreen;
import net.zaminmc.zea.buttons.ButtonGeometry;
import net.zaminmc.zea.buttons.ButtonRenderer;
import net.zaminmc.zea.buttons.InventoryButton;
import net.zaminmc.zea.buttons.InventoryButtons;
import net.zaminmc.zea.browser.ItemBrowser;
import net.zaminmc.zea.browser.ItemBrowserState;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.duck.ContainerScreenAccess;
import net.zaminmc.zea.duck.HoveredSlotAccess;
import net.zaminmc.zea.duck.RecipeBookAccess;
import net.zaminmc.zea.duck.ScreenWidgetAccess;
import net.zaminmc.zea.keybind.ZeaKeys;
import net.zaminmc.zea.misc.TooltipScroller;
import net.zaminmc.zea.rarity.RarityEngine;
import net.zaminmc.zea.market.tooltip.MarketTooltipEditor;
import net.zaminmc.zea.rarity.RarityTooltipEditor;
import net.zaminmc.zea.search.InventorySearch;
import net.zaminmc.zea.search.SearchBarLayout;
import net.zaminmc.zea.search.SearchBarWidget;
import net.zaminmc.zea.search.SearchOverlay;
import net.zaminmc.zea.slots.SlotGuard;
import net.zaminmc.zea.slots.SlotIndicators;
import net.zaminmc.zea.slots.SlotLocks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * ZEA's inventory utilities on container screens: the region-placed
 * buttons, the search bar (calculator and matching mode), the slot
 * locks and bindings, plus the item rarity visuals and the
 * scrollable-tooltips tracking that live on the same screen. All of it
 * is client-side rendering and input routing — nothing here writes a
 * stack, a slot or the menu; the only server-bound action is the button
 * command dispatch through the activator.
 *
 * <p>Every click path vanilla routes through
 * {@code slotClicked(Slot, int, int, ContainerInput)} — mouse picks,
 * shift-clicks, number-key swaps, the drop key over a slot, quick-craft
 * drags and double-click collection — meets one interception point for
 * the lock and binding rules, and quick-craft membership is filtered so
 * locked slots never join a drag. Keybinds that do not touch slots are
 * never swallowed: NEU once broke all keybinds over locked slots by
 * nulling the hovered slot, and this screen deliberately never touches
 * the hover state.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin implements HoveredSlotAccess, ContainerScreenAccess {
        @Shadow
        protected int leftPos;

        @Shadow
        protected int topPos;

        @Shadow
        @Final
        protected int imageWidth;

        @Shadow
        @Final
        protected int imageHeight;

        @Shadow
        @Final
        protected AbstractContainerMenu menu;

        @Shadow
        protected boolean isQuickCrafting;

        @Shadow
        protected Slot hoveredSlot;

        /** The search bar of this screen instance; created on init. */
        @Nullable
        private SearchBarWidget zea$searchBar;

        @Override
        public int zea$imageWidth() {
                return imageWidth;
        }

        @Override
        public int zea$imageHeight() {
                return imageHeight;
        }

        @Override
        public int zea$leftPos() {
                return leftPos;
        }

        @Override
        public int zea$topPos() {
                return topPos;
        }

        // ------------------------------------------------------------------
        // Init: the search bar widget
        // ------------------------------------------------------------------

        /**
         * Creates (once per screen instance) and registers the search bar,
         * repositioned after every layout change. The query state lives in
         * {@link InventorySearch}, not the widget, so an init rebuild never
         * loses it; the bar itself is a plain vanilla {@link EditBox} with
         * a flat background, so clicks, caret and selection are vanilla
         * behavior.
         */
        @Inject(method = "init", at = @At("TAIL"))
        private void zea$initSearchBar(CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                ZeaConfig config = ZeaClient.config();
                if (config == null || !config.inventorySearchEnabled() || zea$isCreative(self)) {
                        zea$searchBar = null;
                        return;
                }
                if (zea$searchBar == null) {
                        InventorySearch search = ZeaClient.search();
                        zea$searchBar = new SearchBarWidget(self.getFont(), 0, 0,
                                        SearchBarLayout.BAR_WIDTH, SearchBarLayout.height(
                                                        Minecraft.getInstance().getWindow().getGuiScale()),
                                        search);
                        zea$searchBar.calculatorEnabled(config.inventorySearchCalculatorEnabled());
                        zea$searchBar.setValue(search.query());
                        zea$searchBar.setResponder(search::query);
                }
                ((ScreenWidgetAccess) self).zea$addRenderableWidget(zea$searchBar);
        }

        /** Creative screens carry their own search; ZEA's bar stays out. */
        private static boolean zea$isCreative(AbstractContainerScreen<?> screen) {
                return screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        }

        // ------------------------------------------------------------------
        // Render: buttons, search bar position
        // ------------------------------------------------------------------

        /**
         * The shop-index browser, extracted FIRST — before the GUI's own
         * content, exactly NEU's layering. In 26.3's extraction pipeline
         * the container screen's widgets (the search bar), the labels, the
         * slots, the carried item and the tooltip all extract after this
         * injection point, so where the pane and the GUI overlap, the GUI
         * wins on pixels — NEU's itemlist-under-the-gui behavior — and the
         * search bar rides on top of the pane.
         */
        @Inject(method = "extractContents", at = @At("HEAD"))
        private void zea$renderBrowser(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                        CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                if (ZeaClient.config() == null || !ZeaClient.config().inventoryShopBrowserEnabled()
                                || zea$isCreative(self)) {
                        return;
                }
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null) {
                        browser.render(graphics, self, mouseX, mouseY);
                }
        }

        @Inject(method = "extractContents", at = @At("TAIL"))
        private void zea$renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                        CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                InventoryButtons buttons = ZeaClient.inventoryButtons();
                ZeaClient.search().bind(self);
                zea$positionSearchBar(self);
                if (zea$buttonsActive(buttons) && !buttons.buttons().isEmpty()) {
                        ButtonRenderer.render(
                                        graphics,
                                        self.getFont(),
                                        zea$placements(buttons, self),
                                        leftPos,
                                        topPos,
                                        mouseX,
                                        mouseY);
                }
        }

        /**
         * Places the bar under the GUI, horizontally centered on the GUI's
         * own bounds — never the framebuffer — through the shared layout
         * math ({@link SearchBarLayout}) the button editor's preview also
         * uses. The layout stays live: resize and recipe-book toggles
         * re-place it, and the y never leaves the screen.
         */
        private void zea$positionSearchBar(AbstractContainerScreen<?> self) {
                SearchBarWidget bar = zea$searchBar;
                if (bar == null) {
                        return;
                }
                // The shared layout: horizontally centered on the GUI's
                // own bounds (never the framebuffer), vertically near the
                // screen's bottom edge and clamped onto it. The editor
                // preview goes through the same math, so what it shows is
                // what renders here.
                SearchBarLayout.apply(bar, self.width, self.height,
                                Minecraft.getInstance().getWindow().getGuiScale());
        }

        /** The layout inputs for this screen right now. */
        private ButtonGeometry.Inputs zea$inputs(AbstractContainerScreen<?> self) {
                return new ButtonGeometry.Inputs(
                                leftPos,
                                topPos,
                                imageWidth,
                                imageHeight,
                                self.width,
                                self.height,
                                self instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen,
                                self instanceof RecipeBookAccess access && access.zea$recipeBookOpen(),
                                Minecraft.getInstance().getWindow().getGuiScale());
        }

        private List<ButtonGeometry.Placement> zea$placements(InventoryButtons buttons,
                        AbstractContainerScreen<?> self) {
                return ButtonGeometry.placements(buttons.buttons(), zea$inputs(self));
        }

        // ------------------------------------------------------------------
        // Render: slots (rarity tint, search highlights, lock indicators)
        // ------------------------------------------------------------------

        /**
         * Rarity slot tint and search-match highlight, both drawn before
         * the item so texture, count, durability bar and glint stay on
         * top. The match adds a one-pixel border around the slot's 16×16
         * area — outside the item, so nothing is obscured.
         */
        @Inject(method = "extractSlot", at = @At("HEAD"))
        private void zea$rarityTintAndSearchHighlight(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                        CallbackInfo ci) {
                int tint = RarityEngine.slotTintArgb(slot.getItem());
                if (tint != 0) {
                        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint);
                }
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                InventorySearch search = ZeaClient.search();
                if (search.matchingActive() && !search.query().isBlank() && !slot.getItem().isEmpty()) {
                        // The search text resolves through the menu's slot list; a
                        // slot the list does not know cannot be searched, and a
                        // stack whose tooltip explodes on this client falls back to
                        // its hover name (the dev.36 live crash report) — neither
                        // may take the frame down.
                        int slotIndex = zea$slotIndex(slot);
                        if (slotIndex < 0) {
                                return;
                        }
                        if (search.slotMatches(self, slotIndex)) {
                                ZeaConfig config = ZeaClient.config();
                                SearchOverlay.HighlightColor color = config == null
                                                ? SearchOverlay.HighlightColor.GREEN
                                                : config.inventorySearchHighlight();
                                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                                                SearchOverlay.fillArgb(color));
                                graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y,
                                                SearchOverlay.borderArgb(color));
                                graphics.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17,
                                                SearchOverlay.borderArgb(color));
                                graphics.fill(slot.x - 1, slot.y, slot.x, slot.y + 16,
                                                SearchOverlay.borderArgb(color));
                                graphics.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16,
                                                SearchOverlay.borderArgb(color));
                        }
                }
        }

        /**
         * After the item: the non-matching dim (matching mode only —
         * empty slots dim too, exactly like NEU, so a search leaves
         * nothing in the inventory undimmed), the lock padlock, the
         * binding digit, and the pairing candidate highlight on
         * hotbar slots while the lock key is held.
         */
        @Inject(method = "extractSlot", at = @At("TAIL"))
        private void zea$searchDimAndSlotIndicators(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                        CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                InventorySearch search = ZeaClient.search();
                if (search.matchingActive() && !search.query().isBlank()
                                && !search.slotMatches(self, zea$slotIndex(slot))) {
                        ZeaConfig config = ZeaClient.config();
                        int intensity = config == null ? SearchOverlay.DEFAULT_DIM_INTENSITY
                                        : config.inventorySearchDimIntensity();
                        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, SearchOverlay.dimArgb(intensity));
                }
                SlotLocks locks = ZeaClient.slotLocks();
                if (locks == null) {
                        return;
                }
                int containerSlot = zea$containerSlot(slot);
                if (containerSlot < 0) {
                        return;
                }
                ZeaConfig config = ZeaClient.config();
                boolean indicators = config == null || config.slotLockIndicatorsEnabled();
                if (locks.pairingActive() && containerSlot <= 8) {
                        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x50FFFFFF);
                }
                if (indicators && locks.isLocked(containerSlot)) {
                        SlotIndicators.drawLock(graphics, slot.x, slot.y);
                }
                int bindTarget = locks.bindTarget(containerSlot);
                if (indicators && bindTarget != -1) {
                        SlotIndicators.drawBindDigit(graphics, self.getFont(), bindTarget, slot.x, slot.y);
                }
        }

        /** The slot's index within this screen's menu. */
        private int zea$slotIndex(Slot slot) {
                return menu.slots.indexOf(slot);
        }

        /** The slot's player-inventory index, or -1 when it is not one. */
        private int zea$containerSlot(Slot slot) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null || slot.container != minecraft.player.getInventory()) {
                        return -1;
                }
                return slot.getContainerSlot();
        }

        // ------------------------------------------------------------------
        // Tooltips
        // ------------------------------------------------------------------

        @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
        private void zea$suppressSlotTooltipOverButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                        CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null && ZeaClient.config() != null
                                && ZeaClient.config().inventoryShopBrowserEnabled()
                                && !zea$isCreative(self)
                                && browser.hoversPanel(mouseX, mouseY)) {
                        ci.cancel();
                        return;
                }
                InventoryButtons buttons = ZeaClient.inventoryButtons();
                if (!zea$buttonsActive(buttons)) {
                        return;
                }
                if (ButtonGeometry.editableAt(zea$placements(buttons, self), mouseX, mouseY) != null) {
                        ci.cancel();
                }
        }

        /**
         * Rarity tooltip effects plus the lock/binding hint lines: vanilla
         * builds the item tooltip list fresh every frame; ZEA edits the
         * copy without touching the stack. The creative screen overrides
         * this method without calling super, so it carries its own hook in
         * {@link CreativeScreenMixin}.
         */
        // cancellable is load-bearing: the handler writes the return value
        // back, and a non-cancellable injection makes that first hover crash
        // the game (pinned by InjectionCancellableTest).
        @Inject(method = "getTooltipFromContainerItem", at = @At("TAIL"), cancellable = true)
        private void zea$rarityTooltip(ItemStack stack,
                                        CallbackInfoReturnable<List<Component>> cir) {
                ZeaConfig config = ZeaClient.config();
                if (config != null && config.hideServerItemMetaEnabled()) {
                        net.zaminmc.zea.misc.ServerItemMeta.filter(cir.getReturnValue());
                }
                List<Component> lines = RarityTooltipEditor.edit(cir.getReturnValue(), stack);
                SlotLocks locks = ZeaClient.slotLocks();
                if (locks != null && hoveredSlot != null && hoveredSlot.getItem() == stack) {
                        int containerSlot = zea$containerSlot(hoveredSlot);
                        if (containerSlot >= 0) {
                                lines.addAll(SlotIndicators.tooltipLines(containerSlot, locks));
                        }
                }
                MarketTooltipEditor.edit(lines, stack);
                cir.setReturnValue(lines);
        }

        /**
         * Feeds the tooltip scroller the hovered-slot identity so the
         * scroll resets when the item under the cursor changes.
         */
        @Inject(method = "extractTooltip", at = @At("HEAD"))
        private void zea$scrollerTrackSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        CallbackInfo ci) {
                TooltipScroller.trackSlot(hoveredSlot);
        }

        // ------------------------------------------------------------------
        // Keys
        // ------------------------------------------------------------------

        /**
         * Key routing, in priority order: the button-editor keybind
         * (opens the NEU-style editor over this screen), the search
         * bar, and the slot lock key. While the bar is focused it
         * owns the keyboard the way vanilla's creative search does:
         * Esc first leaves matching mode, then focus; the field sees
         * its editing keys; and every other key is captured — plain
         * letters arrive as charTyped and insert into the field, the
         * inventory key E included, so typing "e" is never
         * interrupted. Esc or a click outside defocuses; hotbar
         * swaps and E-to-close never fire mid-typing.
         */
        @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
        private void zea$handleKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                // The detail overlay is the top layer: Esc closes it
                // before anything else sees the key. It never captures
                // typing keys — the search bar keeps its ownership.
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null && browser.state().view() == ItemBrowserState.View.DETAIL
                                && event.isEscape()) {
                        browser.state().closeDetail();
                        cir.setReturnValue(true);
                        return;
                }
                InventoryButtons buttons = ZeaClient.inventoryButtons();
                if (zea$buttonsActive(buttons) && ZeaKeys.EDIT_BUTTONS.matches(event)) {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.gui.setScreen(new ButtonEditorScreen(self, buttons));
                        cir.setReturnValue(true);
                        return;
                }
                SearchBarWidget bar = zea$searchBar;
                if (bar != null && self.getFocused() == bar) {
                        InventorySearch search = ZeaClient.search();
                        if (event.isEscape()) {
                                if (search.matchingActive()) {
                                        search.leaveMatchingMode();
                                } else {
                                        self.setFocused(null);
                                }
                                cir.setReturnValue(true);
                                return;
                        }
                        if (bar.keyPressed(event)) {
                                cir.setReturnValue(true);
                                return;
                        }
                        if (bar.capturesInput()) {
                                // The vanilla creative-search rule: a
                                // focused, editable field captures every
                                // remaining key. The letter E inserts as
                                // text via charTyped instead of defocusing
                                // the bar mid-word.
                                cir.setReturnValue(true);
                                return;
                        }
                        self.setFocused(null);
                }
                ZeaConfig config = ZeaClient.config();
                boolean locksOn = config != null && config.slotLockingEnabled();
                if (locksOn && ZeaKeys.LOCK_SLOT.matches(event)) {
                        zea$handleLockKey(self);
                        cir.setReturnValue(true);
                }
        }

        /** The lock key over a player-inventory slot: toggle, then pair. */
        private void zea$handleLockKey(AbstractContainerScreen<?> self) {
                SlotLocks locks = ZeaClient.slotLocks();
                ZeaConfig config = ZeaClient.config();
                if (locks == null || config == null || !config.slotLockingEnabled()) {
                        return;
                }
                Slot slot = hoveredSlot;
                if (slot == null) {
                        return;
                }
                int containerSlot = zea$containerSlot(slot);
                if (containerSlot < 0) {
                        return;
                }
                locks.toggleLock(containerSlot);
                if (SlotLocks.bindable(containerSlot) && config.slotBindingEnabled()) {
                        locks.beginPairing(containerSlot);
                }
        }

        // ------------------------------------------------------------------
        // Mouse
        // ------------------------------------------------------------------

        /**
         * Mouse routing: a stale pairing ends when its key is no longer
         * held; a pairing click on a hotbar slot binds; a normal click
         * on a button runs its command (consumed without running while
         * an item is carried, so the click cannot drop it); the search
         * bar's double-click toggles matching mode; and clicking
         * anywhere else releases the bar's focus so typing resumes to
         * the inventory. Button editing lives in the editor screen the
         * keybind opens — not in place.
         */
        @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
        private void zea$onMouseClick(MouseButtonEvent event, boolean doubleClick,
                        CallbackInfoReturnable<Boolean> cir) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                SearchBarWidget bar = zea$searchBar;
                if (bar != null) {
                        // NEU's group: the settings plate opens the config,
                        // the search-mode plate toggles matching mode.
                        int scaleFactor = Minecraft.getInstance().getWindow().getGuiScale();
                        int[] settings = SearchBarLayout.settingsButton(self.width, self.height, scaleFactor);
                        int[] mode = SearchBarLayout.modeButton(self.width, self.height, scaleFactor);
                        if (ButtonGeometry.contains(settings[0], settings[1], settings[2], settings[3],
                                        (int) event.x(), (int) event.y())) {
                                cir.setReturnValue(true);
                                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                                        net.zaminmc.zea.config.ConfigScreen.open(Minecraft.getInstance(), ZeaClient.config());
                                }
                                return;
                        }
                        if (ButtonGeometry.contains(mode[0], mode[1], mode[2], mode[3],
                                        (int) event.x(), (int) event.y())) {
                                cir.setReturnValue(true);
                                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                                        ZeaClient.search().toggleMode();
                                        net.zaminmc.zea.browser.ItemBrowser.playUiClick();
                                }
                                return;
                        }
                        if (ButtonGeometry.contains(bar.getX(), bar.getY(), bar.getWidth(), bar.getHeight(),
                                        event.x(), event.y())) {
                                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                                        // NEU: right-click clears the search text.
                                        bar.setValue("");
                                        ZeaClient.search().query("");
                                        cir.setReturnValue(true);
                                        return;
                                }
                                ZeaConfig config = ZeaClient.config();
                                if (config == null || config.inventorySearchMatchingEnabled()) {
                                        ZeaClient.search().barClicked(System.currentTimeMillis());
                                }
                        } else if (self.getFocused() == bar) {
                                self.setFocused(null);
                        }
                }
                // NEU's layering: the pane renders UNDER the GUI, so where a
                // GUI slot sits under the cursor the slot wins the click —
                // the browser takes clicks only where no slot is hovered.
                // Its own controls (tiles, nav, sort bar, tab) never share a
                // pixel with a slot's hit box, so they stay reachable.
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null && hoveredSlot == null && ZeaClient.config() != null
                                && ZeaClient.config().inventoryShopBrowserEnabled()
                                && !zea$isCreative(self)
                                && browser.click(self, event.x(), event.y())) {
                        cir.setReturnValue(true);
                        return;
                }
                InventoryButtons buttons = ZeaClient.inventoryButtons();
                SlotLocks locks = ZeaClient.slotLocks();
                if (locks != null && locks.pairingActive() && !ZeaKeys.LOCK_SLOT.isDown()) {
                        locks.endPairing();
                }
                if (zea$buttonsActive(buttons)) {
                        ButtonGeometry.Placement hit = ButtonGeometry.at(zea$placements(buttons, self),
                                        event.x(), event.y());
                        if (hit != null) {
                                zea$activateButton(self, buttons, hit.button());
                                cir.setReturnValue(true);
                                return;
                        }
                }
                if (locks != null && locks.pairingActive() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                        Slot slot = hoveredSlot;
                        int containerSlot = slot == null ? -1 : zea$containerSlot(slot);
                        if (containerSlot >= 0 && containerSlot <= 8) {
                                locks.bind(locks.pairingSlot(), containerSlot);
                                locks.endPairing();
                                cir.setReturnValue(true);
                                return;
                        }
                }
        }

        /**
         * A wheel scroll over the browser scrolls it — but only where no
         * GUI slot sits under the cursor, the same slot-wins rule the
         * pane's clicks follow; anywhere else the wheel behaves exactly
         * as vanilla.
         */
        @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
        private void zea$onScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                        CallbackInfoReturnable<Boolean> cir) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null && hoveredSlot == null && ZeaClient.config() != null
                                && ZeaClient.config().inventoryShopBrowserEnabled()
                                && !zea$isCreative(self)
                                && browser.scroll(mouseX, mouseY, scrollY)) {
                        cir.setReturnValue(true);
                }
        }

        /**
         * Runs a button's command with the carried-item and close-on-execute
         * rules: while an item sits on the cursor the click is consumed but
         * nothing runs (NEU's guard against dropping onto buttons); a
         * successful dispatch closes the screen only when the command left
         * it in place.
         */
        private void zea$activateButton(AbstractContainerScreen<?> self, InventoryButtons buttons,
                        InventoryButton button) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null) {
                        return;
                }
                if (!menu.getCarried().isEmpty()) {
                        return; // consumed, not executed
                }
                var result = buttons.click(button, System.currentTimeMillis());
                boolean screenUnchanged = minecraft.gui.screen() == self;
                if (button.closeOnExecute()
                                && net.zaminmc.zea.buttons.ButtonActivator.shouldCloseAfter(result, screenUnchanged)) {
                        self.onClose();
                }
        }

        /**
         * A release over a button is consumed, so vanilla never treats
         * the button area as "outside the GUI" (which would drop a
         * carried item). Mid-quick-craft releases pass through so the
         * drag resolves normally.
         */
        @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
        private void zea$onButtonRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                ItemBrowser browser = ZeaClient.browser();
                if (browser != null && ZeaClient.config() != null
                                && ZeaClient.config().inventoryShopBrowserEnabled()
                                && !zea$isCreative(self)
                                && browser.hoversPanel(event.x(), event.y())) {
                        // The panel is never "outside the GUI": a release
                        // over it cannot drop a carried item.
                        cir.setReturnValue(true);
                        return;
                }
                InventoryButtons buttons = ZeaClient.inventoryButtons();
                if (!zea$buttonsActive(buttons) || isQuickCrafting) {
                        return;
                }
                if (ButtonGeometry.editableAt(zea$placements(buttons, self), event.x(), event.y()) != null) {
                        cir.setReturnValue(true);
                }
        }

        // ------------------------------------------------------------------
        // Slot click interception (locks and bindings)
        // ------------------------------------------------------------------

        /**
         * The single interception point for every slot interaction. A
         * locked slot blocks all inputs; a number-key swap into a locked
         * hotbar slot blocks; a right-click on a bound slot becomes the
         * binding's swap, synthesized through the same game-mode call
         * vanilla's own number-key swap uses — the live menu's slot index,
         * which is what makes armor slots and chest-GUI inventory rows
         * resolve correctly.
         */
        @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
                        at = @At("HEAD"), cancellable = true)
        private void zea$guardSlotClick(Slot slot, int slotIndex, int button, ContainerInput input,
                        CallbackInfo ci) {
                SlotLocks locks = ZeaClient.slotLocks();
                ZeaConfig config = ZeaClient.config();
                if (locks == null || config == null
                                || (!config.slotLockingEnabled() && !config.slotBindingEnabled())) {
                        return;
                }
                int containerSlot = slot == null ? -1 : zea$containerSlot(slot);
                SlotGuard.Action action = SlotGuard.onSlotClick(containerSlot, input, button, locks);
                if (action == SlotGuard.Action.BLOCK) {
                        ci.cancel();
                        return;
                }
                if (action == SlotGuard.Action.SWAP && slot != null) {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.player != null && minecraft.gameMode != null) {
                                int target = locks.bindTarget(containerSlot);
                                minecraft.gameMode.handleContainerInput(
                                                menu.containerId, slot.index, target, ContainerInput.SWAP,
                                                minecraft.player);
                        }
                        ci.cancel();
                }
        }

        /** Locked slots never join an in-progress quick-craft drag. */
        @Inject(method = "shouldAddSlotToQuickCraft", at = @At("HEAD"), cancellable = true)
        private void zea$excludeLockedFromQuickCraft(Slot slot, ItemStack stack,
                        CallbackInfoReturnable<Boolean> cir) {
                SlotLocks locks = ZeaClient.slotLocks();
                ZeaConfig config = ZeaClient.config();
                if (locks == null || config == null || !config.slotLockingEnabled()) {
                        return;
                }
                if (!SlotGuard.mayJoinQuickCraft(zea$containerSlot(slot), locks)) {
                        cir.setReturnValue(false);
                }
        }

        // ------------------------------------------------------------------
        // Close
        // ------------------------------------------------------------------

        /** A closed screen must not keep a half-finished search or pairing alive. */
        @Inject(method = "removed", at = @At("HEAD"))
        private void zea$cancelTransientState(CallbackInfo ci) {
                AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
                SlotLocks locks = ZeaClient.slotLocks();
                if (locks != null) {
                        locks.endPairing();
                }
                ZeaClient.search().release(self);
                zea$searchBar = null;
        }

        /** A closed screen leaves the tooltip scroller with nothing to scroll. */
        @Inject(method = "removed", at = @At("TAIL"))
        private void zea$resetTooltipScrollerOnClose(CallbackInfo ci) {
                TooltipScroller.reset();
        }

        /** The hovered-slot bridge the tooltip scroller reads on wheel events. */
        @Override
        public Slot zea$hoveredSlot() {
                return hoveredSlot;
        }

        private boolean zea$buttonsActive(InventoryButtons buttons) {
                return buttons != null
                                && ZeaClient.config() != null
                                && ZeaClient.config().inventoryButtonsEnabled()
                                // The creative screen's own tabs and search own its
                                // furniture; ZEA's buttons stay out of that fight.
                                && !zea$isCreative((AbstractContainerScreen<?>) (Object) this);
        }
}
