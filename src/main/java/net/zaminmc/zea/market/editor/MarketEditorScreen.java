package net.zaminmc.zea.market.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.parse.ShopSignParser;
import net.zaminmc.zea.market.ItemFingerprints;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The market editor: the clean form behind {@code /zea market-editor} —
 * where a listing is entered by hand or a capture is reviewed before it
 * is trusted as the market's answer.
 *
 * <p>Two modes, one panel:
 *
 * <ul>
 *   <li><b>New listing</b> — the player stands at a shop (or anywhere) and
 *       fills in seller, direction, item, price and stock. The location
 *       prefills from the sign under the crosshair, falling back to the
 *       player's feet; the item prefills from whatever is picked from the
 *       inventory grid, which also gives the listing its exact identity —
 *       a name typed without a pick stays display text, as preliminary
 *       as a sign advert.</li>
 *   <li><b>Review</b> — seeded from the latest captured inspection when
 *       the capture prompt is clicked. Every field is editable: details
 *       the server captured wrong can be fixed and details it never
 *       carried (a price, the exact item) can be added. Done records the
 *       corrected listing as the shop's newest observation — what the
 *       player vouches for outranks what arrived half-parsed; closing
 *       with nothing changed leaves the captured data alone.</li>
 * </ul>
 *
 * <p>The chrome is the viewer's: JEI's nine-slice panel and button
 * plates, JEI's geometry paddings, the press sound. A Done that cannot
 * parse the numbers keeps the panel open and marks the field red — the
 * form never invents defaults for what the player typed.
 */
public final class MarketEditorScreen extends Screen {
        // ------------------------------------------------------------------
        // Textures and geometry — the viewer's JEI chrome
        // ------------------------------------------------------------------

        private static final String BASE = "zea:textures/market/";
        private static net.minecraft.resources.Identifier id(String name) {
                return net.minecraft.resources.Identifier.parse(BASE + name);
        }
        /** The recipe gui's nine-slice panel (64×64, 16 px border). */
        private static final net.minecraft.resources.Identifier PANEL = id("panel.png");
        /** The vanilla-look button plates (20×20, 6 px border). */
        private static final net.minecraft.resources.Identifier BUTTON = id("button.png");
        private static final net.minecraft.resources.Identifier BUTTON_HOVER = id("button_hover.png");
        private static final int PANEL_SPRITE = 64;
        private static final int PANEL_BORDER = 16;
        private static final int BUTTON_SPRITE = 20;
        private static final int BUTTON_BORDER = 6;

        /** JEI's {@code titleInnerPadding}-style title height budget. */
        private static final int TITLE_HEIGHT = 12;
        private static final int LABEL_COLOR = 0xFF4C4C4C;
        private static final int TEXT = 0xFF000000;
        private static final int TEXT_DIM = 0xFF666666;
        /** The vanilla slot plate colors, for the item pick box and the grid. */
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SLOT_LIGHT = 0xFFFFFFFF;
        private static final int SLOT_INTERIOR = 0xFF8B8B8B;
        /** The red a rejected number wears until it parses. */
        private static final int FIELD_ERROR = 0xFFFF5555;
        private static final int FIELD_OK = 0xFFE0E0E0;
        /** The selected toggle's gold wash — the browser's selection accent. */
        private static final int SELECTED_WASH = 0x30E8C14A;
        private static final int SELECTED_FRAME = 0xFFE8C14A;

        private static final int PANEL_WIDTH = 236;
        private static final int PANEL_HEIGHT = 268;
        private static final int CONTENT_X = PANEL_BORDER + 2;
        private static final int CONTENT_WIDTH = PANEL_WIDTH - 2 * CONTENT_X;
        private static final int FIELD_HEIGHT = 14;
        private static final int LABEL_PITCH = 10;
        private static final int FIELD_PITCH = FIELD_HEIGHT + 4;
        /** The vanilla slot's 18 px plate, drawn around a picked item. */
        private static final int SLOT_SIZE = 18;
        /** The item grid: nine columns of the player's inventory. */
        private static final int GRID_COLS = 9;
        private static final int GRID_PITCH = 20;

        // Row tops relative to guiTop, form mode.
        private static final int ROW_DIRECTION = 26;
        private static final int ROW_SELLER = 46;
        private static final int ROW_ITEM = 74;
        private static final int ROW_PRICE = 108;
        private static final int ROW_BULK = 138;
        private static final int ROW_STOCK = 166;
        private static final int ROW_SIGN = 194;
        private static final int ROW_FOOTER = PANEL_HEIGHT - 24;

        // ------------------------------------------------------------------
        // State
        // ------------------------------------------------------------------

        /** JEI's {@code parentScreen} — restored on close. */
        private final Screen parent;
        /** The capture under review, or {@code null} for a fresh listing. */
        private final ShopInspectionTracker.@Nullable Inspection review;

        private boolean pickMode;

        private EditBox sellerField;
        private EditBox itemField;
        private EditBox priceField;
        private EditBox bulkField;
        private EditBox stockField;
        private EditBox signXField;
        private EditBox signYField;
        private EditBox signZField;

        private ShopDirection direction = ShopDirection.SELLING;
        /** The mouse position the current frame's tooltips anchor to. */
        private int lastMouseX;
        private int lastMouseY;
        private PriceScope scope = PriceScope.PER_ITEM;
        /** The picked stack: the listing's exact identity when present. */
        private @Nullable ItemStack pickedItem;
        // Hit boxes, recomputed in init.
        private int guiLeft;
        private int guiTop;
        private int @Nullable [] sellingBox;
        private int @Nullable [] buyingBox;
        private int @Nullable [] itemBox;
        private int @Nullable [] perItemBox;
        private int @Nullable [] perBulkBox;
        private int @Nullable [] doneBox;
        private int @Nullable [] cancelBox;
        private final List<int[]> gridBoxes = new ArrayList<>();

        public MarketEditorScreen(Screen parent) {
                this(parent, null);
        }

        public MarketEditorScreen(Screen parent, ShopInspectionTracker.@Nullable Inspection review) {
                super(Component.literal("ZEA Market editor"));
                this.parent = parent;
                this.review = review;
        }

        /**
         * JEI's {@code open()}: capture the current screen as the parent and
         * swap the editor in. The command runs this through the client queue
         * (a client command executes mid-keystroke inside the chat screen).
         */
        public static void open(ShopInspectionTracker.@Nullable Inspection review) {
                Minecraft client = Minecraft.getInstance();
                Screen parent = client.gui.screen();
                client.gui.setScreen(new MarketEditorScreen(parent, review));
        }

        // ------------------------------------------------------------------
        // Lifecycle
        // ------------------------------------------------------------------

        @Override
        public boolean isPauseScreen() {
                return false;
        }

        @Override
        public boolean isInGameUi() {
                return true;
        }

        @Override
        protected void init() {
                guiLeft = (width - PANEL_WIDTH) / 2;
                guiTop = (height - PANEL_HEIGHT) / 2;

                sellerField = field(32);
                itemField = field(64);
                priceField = field(12);
                bulkField = field(6);
                stockField = field(9);
                signXField = field(7);
                signYField = field(7);
                signZField = field(7);

                if (review != null) {
                        seedFromReview();
                } else {
                        seedFromWorld();
                }
                focus(sellerField);
        }

        private EditBox field(int maxLength) {
                EditBox box = new EditBox(font, 0, 0, CONTENT_WIDTH, FIELD_HEIGHT, Component.literal(""));
                box.setMaxLength(maxLength);
                return box;
        }

        /**
         * The world seed for a fresh listing: the sign under the crosshair
         * when the player is looking at one, its chest, its text; otherwise
         * the player's feet. Standing at the shop and running the command
         * prefills everything the sign itself advertises.
         */
        private void seedFromWorld() {
                Minecraft client = minecraft;
                if (client == null || client.player == null || client.level == null) {
                        return;
                }
                BlockPos signPos = client.player.blockPosition();
                BlockPos chestPos = null;
                if (client.hitResult instanceof BlockHitResult hit
                                && client.level.getBlockEntity(hit.getBlockPos()) instanceof SignBlockEntity sign) {
                        signPos = hit.getBlockPos();
                        chestPos = net.zaminmc.zea.market.interaction.ShopSignGeometry
                                        .attachedBlock(client.level.getBlockState(signPos), signPos)
                                        .filter(candidate -> client.level.getBlockState(candidate)
                                                        .getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                                        .orElse(null);
                        List<String> lines = sign.getText(SignTextSlot.FRONT).getMessages(false).stream()
                                        .map(Component::getString)
                                        .toList();
                        ShopSignParser.parse(lines).ifPresent(advert -> {
                                if (advert.owner() != null) {
                                        sellerField.setValue(advert.owner());
                                }
                                if (advert.itemDisplay() != null && pickedItem == null) {
                                        itemField.setValue(advert.itemDisplay());
                                }
                                direction = advert.direction();
                        });
                }
                signXField.setValue(String.valueOf(signPos.getX()));
                signYField.setValue(String.valueOf(signPos.getY()));
                signZField.setValue(String.valueOf(signPos.getZ()));
        }

        /** The review seed: everything the capture carried, ready to correct. */
        private void seedFromReview() {
                ShopInspectionTracker.@Nullable Inspection inspection = review;
                if (inspection == null) {
                        return;
                }
                signXField.setValue(String.valueOf(inspection.signPos().getX()));
                signYField.setValue(String.valueOf(inspection.signPos().getY()));
                signZField.setValue(String.valueOf(inspection.signPos().getZ()));
                net.zaminmc.zea.market.model.ShopResponse response = inspection.response();
                if (response != null) {
                        if (response.owner() != null) {
                                sellerField.setValue(response.owner());
                        }
                        if (response.itemDisplay() != null) {
                                itemField.setValue(response.itemDisplay());
                        }
                        if (response.price() != null) {
                                priceField.setValue(response.price().stripTrailingZeros().toPlainString());
                        }
                        direction = response.direction();
                        scope = response.priceScope() != null ? response.priceScope() : PriceScope.PER_ITEM;
                        if (response.bulkSize() != null) {
                                bulkField.setValue(String.valueOf(response.bulkSize()));
                        }
                        if (response.stockRaw() != null
                                        && response.stockUnit() == StockUnit.ITEMS) {
                                stockField.setValue(response.stockRaw());
                        }
                } else if (inspection.signLines() != null) {
                        ShopSignParser.parse(inspection.signLines()).ifPresent(advert -> {
                                if (advert.owner() != null) {
                                        sellerField.setValue(advert.owner());
                                }
                                if (advert.itemDisplay() != null) {
                                        itemField.setValue(advert.itemDisplay());
                                }
                                direction = advert.direction();
                        });
                }
        }

        @Override
        public void onClose() {
                if (minecraft != null) {
                        minecraft.gui.setScreen(parent);
                } else {
                        super.onClose();
                }
        }

        // ------------------------------------------------------------------
        // Rendering
        // ------------------------------------------------------------------

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                super.extractBackground(graphics, mouseX, mouseY, a);
                drawNineSlice(graphics, PANEL, guiLeft, guiTop, PANEL_WIDTH, PANEL_HEIGHT,
                                PANEL_SPRITE, PANEL_BORDER);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                Font f = font;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                sellOrBuyBoxes();
                drawTitle(graphics, f);
                if (pickMode) {
                        drawPickGrid(graphics, f, mouseX, mouseY);
                        return;
                }
                drawDirectionToggle(graphics, f, mouseX, mouseY);
                drawRow(graphics, f, "Seller", ROW_SELLER, mouseX, mouseY, sellerField);
                drawItemRow(graphics, f, mouseX, mouseY);
                drawPriceRow(graphics, f, mouseX, mouseY);
                drawRow(graphics, f, "Bulk size (items per bulk)", ROW_BULK, mouseX, mouseY, bulkField);
                drawRow(graphics, f, "Stock (items)", ROW_STOCK, mouseX, mouseY, stockField);
                drawSignRow(graphics, f, mouseX, mouseY);
                drawFooter(graphics, f, mouseX, mouseY);
        }

        private void drawTitle(GuiGraphicsExtractor graphics, Font f) {
                String title = review == null ? "New market listing" : "Review captured listing";
                int textWidth = f.width(title);
                graphics.text(f, title, guiLeft + (PANEL_WIDTH - textWidth) / 2, guiTop + 9,
                                0xFFFFFFFF, true);
        }

        private void drawDirectionToggle(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                int x = guiLeft + CONTENT_X;
                int y = guiTop + ROW_DIRECTION;
                int half = (CONTENT_WIDTH - 4) / 2;
                drawToggle(graphics, f, x, y, half, "Selling — you buy",
                                direction == ShopDirection.SELLING, hover(sellingBox, mouseX, mouseY));
                drawToggle(graphics, f, x + half + 4, y, half, "Buying — you sell",
                                direction == ShopDirection.BUYING, hover(buyingBox, mouseX, mouseY));
        }

        private void drawToggle(GuiGraphicsExtractor graphics, Font f, int x, int y, int w,
                        String label, boolean selected, boolean hovered) {
                if (selected) {
                        graphics.fill(x, y, x + w, y + FIELD_HEIGHT, SELECTED_WASH);
                        drawNineSlice(graphics, BUTTON, x, y, w, FIELD_HEIGHT, BUTTON_SPRITE, BUTTON_BORDER);
                } else if (hovered) {
                        drawNineSlice(graphics, BUTTON_HOVER, x, y, w, FIELD_HEIGHT, BUTTON_SPRITE, BUTTON_BORDER);
                } else {
                        drawNineSlice(graphics, BUTTON, x, y, w, FIELD_HEIGHT, BUTTON_SPRITE, BUTTON_BORDER);
                }
                if (selected) {
                        drawNineSliceBorder(graphics, x, y, w, FIELD_HEIGHT);
                }
                int textWidth = f.width(label);
                int color = selected ? TEXT : TEXT_DIM;
                graphics.text(f, label, x + (w - textWidth) / 2, y + (FIELD_HEIGHT - 8) / 2 + 1, color, false);
        }

        private void drawNineSliceBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
                // A one-pixel gold frame over the plate, inside the border.
                graphics.fill(x + 1, y + 1, x + w - 1, y + 2, SELECTED_FRAME);
                graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SELECTED_FRAME);
                graphics.fill(x + 1, y + 1, x + 2, y + h - 1, SELECTED_FRAME);
                graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SELECTED_FRAME);
        }

        private void drawRow(GuiGraphicsExtractor graphics, Font f, String label, int row,
                        int mouseX, int mouseY, EditBox field) {
                graphics.text(f, label, guiLeft + CONTENT_X, guiTop + row, LABEL_COLOR, false);
                field.setX(guiLeft + CONTENT_X);
                field.setY(guiTop + row + LABEL_PITCH);
                field.setWidth(CONTENT_WIDTH);
                field.setTextColor(field.isFocused() ? FIELD_OK : TEXT);
                field.extractRenderState(graphics, mouseX, mouseY, 0f);
        }

        private void drawItemRow(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                graphics.text(f, "Item", guiLeft + CONTENT_X, guiTop + ROW_ITEM, LABEL_COLOR, false);
                int y = guiTop + ROW_ITEM + LABEL_PITCH;
                // The pick slot: the picked item, or an empty plate inviting one.
                int slotX = guiLeft + CONTENT_X;
                int slotY = y - 1;
                drawSlotPlate(graphics, slotX, slotY);
                if (pickedItem != null && !pickedItem.isEmpty()) {
                        graphics.item(pickedItem, slotX + 1, slotY + 1);
                        graphics.itemDecorations(f, pickedItem, slotX + 1, slotY + 1);
                }
                itemField.setX(slotX + SLOT_SIZE + 4);
                itemField.setY(y);
                itemField.setWidth(CONTENT_WIDTH - SLOT_SIZE - 4);
                itemField.setTextColor(itemField.isFocused() ? FIELD_OK : TEXT);
                itemField.extractRenderState(graphics, mouseX, mouseY, 0f);
                // The hint: what the slot click does.
                if (hover(itemBox, mouseX, mouseY)) {
                        setTooltipFor(graphics, f, "Pick the item from your inventory");
                }
        }

        private void drawSlotPlate(GuiGraphicsExtractor graphics, int x, int y) {
                graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_INTERIOR);
                graphics.fill(x, y, x + SLOT_SIZE - 1, y + 1, SLOT_DARK);
                graphics.fill(x, y, x + 1, y + SLOT_SIZE - 1, SLOT_DARK);
                graphics.fill(x + 1, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_LIGHT);
                graphics.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_LIGHT);
        }

        private void drawPriceRow(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                graphics.text(f, "Price (gold)", guiLeft + CONTENT_X, guiTop + ROW_PRICE, LABEL_COLOR, false);
                int y = guiTop + ROW_PRICE + LABEL_PITCH;
                int fieldWidth = 84;
                priceField.setX(guiLeft + CONTENT_X);
                priceField.setY(y);
                priceField.setWidth(fieldWidth);
                priceField.setTextColor(priceField.isFocused() ? FIELD_OK
                                : priceTextValid() ? TEXT : FIELD_ERROR);
                priceField.extractRenderState(graphics, mouseX, mouseY, 0f);
                int toggleX = guiLeft + CONTENT_X + fieldWidth + 6;
                int toggleWidth = CONTENT_WIDTH - fieldWidth - 6;
                drawToggle(graphics, f, toggleX, y, toggleWidth,
                                scope == PriceScope.PER_ITEM ? "per item" : "per bulk",
                                false, hover(scope == PriceScope.PER_ITEM ? perItemBox : perBulkBox, mouseX, mouseY));
                // The scope button shows the current scope and cycles on click.
        }

        private void drawSignRow(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                graphics.text(f, "Shop sign at X / Y / Z", guiLeft + CONTENT_X, guiTop + ROW_SIGN,
                                LABEL_COLOR, false);
                int y = guiTop + ROW_SIGN + LABEL_PITCH;
                int fieldWidth = (CONTENT_WIDTH - 12) / 3;
                EditBox[] fields = {signXField, signYField, signZField};
                for (int i = 0; i < fields.length; i++) {
                        EditBox box = fields[i];
                        box.setX(guiLeft + CONTENT_X + i * (fieldWidth + 6));
                        box.setY(y);
                        box.setWidth(fieldWidth);
                        box.setTextColor(box.isFocused() ? FIELD_OK : TEXT);
                        box.extractRenderState(graphics, mouseX, mouseY, 0f);
                }
        }

        private void drawFooter(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                int buttonWidth = 80;
                int cancelX = guiLeft + CONTENT_X;
                int doneX = guiLeft + PANEL_WIDTH - CONTENT_X - buttonWidth;
                int y = guiTop + ROW_FOOTER;
                drawNineSlice(graphics, BUTTON, cancelX, y, buttonWidth, FIELD_HEIGHT + 4,
                                BUTTON_SPRITE, BUTTON_BORDER);
                centerLabel(graphics, f, "Cancel", cancelX, y, buttonWidth, FIELD_HEIGHT + 4, TEXT);
                if (hover(cancelBox, mouseX, mouseY)) {
                        drawNineSlice(graphics, BUTTON_HOVER, cancelX, y, buttonWidth, FIELD_HEIGHT + 4,
                                        BUTTON_SPRITE, BUTTON_BORDER);
                        centerLabel(graphics, f, "Cancel", cancelX, y, buttonWidth, FIELD_HEIGHT + 4, TEXT);
                }
                boolean doneReady = doneReady();
                drawNineSlice(graphics, doneReady ? BUTTON : BUTTON, doneX, y, buttonWidth, FIELD_HEIGHT + 4,
                                BUTTON_SPRITE, BUTTON_BORDER);
                if (hover(doneBox, mouseX, mouseY) && doneReady) {
                        drawNineSlice(graphics, BUTTON_HOVER, doneX, y, buttonWidth, FIELD_HEIGHT + 4,
                                        BUTTON_SPRITE, BUTTON_BORDER);
                }
                centerLabel(graphics, f, "Done", doneX, y, buttonWidth, FIELD_HEIGHT + 4,
                                doneReady ? TEXT : TEXT_DIM);
                if (hover(doneBox, mouseX, mouseY) && !doneReady()) {
                        setTooltipFor(graphics, f, doneBlocker());
                }
        }

        private void centerLabel(GuiGraphicsExtractor graphics, Font f, String label,
                        int x, int y, int w, int h, int color) {
                int textWidth = f.width(label);
                graphics.text(f, label, x + (w - textWidth) / 2, y + (h - 8) / 2 + 1, color, false);
        }

        private void drawPickGrid(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                Player player = minecraft == null ? null : minecraft.player;
                graphics.text(f, "Pick the item — Esc goes back",
                                guiLeft + CONTENT_X, guiTop + 30, LABEL_COLOR, false);
                gridBoxes.clear();
                if (player == null) {
                        return;
                }
                List<ItemStack> items = player.getInventory().getNonEquipmentItems();
                int gridX = guiLeft + CONTENT_X + (CONTENT_WIDTH - GRID_COLS * GRID_PITCH + (GRID_COLS - 1) * 2) / 2;
                int y = guiTop + 44;
                for (int index = 0; index < items.size() && index < GRID_COLS * 4; index++) {
                        int column = index % GRID_COLS;
                        int row = index / GRID_COLS;
                        int x = gridX + column * GRID_PITCH;
                        int slotY = y + row * GRID_PITCH;
                        drawSlotPlate(graphics, x, slotY);
                        ItemStack stack = items.get(index);
                        if (!stack.isEmpty()) {
                                graphics.item(stack, x + 1, slotY + 1);
                                graphics.itemDecorations(f, stack, x + 1, slotY + 1);
                        }
                        gridBoxes.add(new int[] {x, slotY, SLOT_SIZE, SLOT_SIZE, index});
                }
        }

        private void setTooltipFor(GuiGraphicsExtractor graphics, Font f, String text) {
                graphics.setTooltipForNextFrame(f, List.of(Component.literal(text)),
                                java.util.Optional.empty(), lastMouseX, lastMouseY);
        }

        // ------------------------------------------------------------------
        // Hit boxes
        // ------------------------------------------------------------------

        private void sellOrBuyBoxes() {
                int x = guiLeft + CONTENT_X;
                int y = guiTop + ROW_DIRECTION;
                int half = (CONTENT_WIDTH - 4) / 2;
                sellingBox = new int[] {x, y, half, FIELD_HEIGHT};
                buyingBox = new int[] {x + half + 4, y, half, FIELD_HEIGHT};
                itemBox = new int[] {guiLeft + CONTENT_X, guiTop + ROW_ITEM + LABEL_PITCH - 1,
                                SLOT_SIZE, SLOT_SIZE};
                int fieldWidth = 84;
                int toggleX = guiLeft + CONTENT_X + fieldWidth + 6;
                int toggleWidth = CONTENT_WIDTH - fieldWidth - 6;
                perItemBox = new int[] {toggleX, guiTop + ROW_PRICE + LABEL_PITCH, toggleWidth, FIELD_HEIGHT};
                perBulkBox = perItemBox;
                int buttonWidth = 80;
                cancelBox = new int[] {guiLeft + CONTENT_X, guiTop + ROW_FOOTER, buttonWidth, FIELD_HEIGHT + 4};
                doneBox = new int[] {guiLeft + PANEL_WIDTH - CONTENT_X - buttonWidth,
                                guiTop + ROW_FOOTER, buttonWidth, FIELD_HEIGHT + 4};
        }

        private static boolean hover(int @Nullable [] box, int x, int y) {
                return contains(box, x, y);
        }

        /** A nine-slice blit: ZEA's three-by-three pass, the sprite's border fractions. */
        private static void drawNineSlice(GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier texture,
                        int x, int y, int width, int height, int spriteSize, int border) {
                float slice = (float) border / spriteSize;
                for (int yIndex = 0; yIndex <= 2; yIndex++) {
                        for (int xIndex = 0; xIndex <= 2; xIndex++) {
                                float uMin = 0;
                                float uMax = slice;
                                int partX = x;
                                int partW = border;
                                if (xIndex == 1) {
                                        partX = x + border;
                                        uMin = slice;
                                        uMax = 1 - slice;
                                        partW = width - 2 * border;
                                } else if (xIndex == 2) {
                                        partX = x + width - border;
                                        uMin = 1 - slice;
                                        uMax = 1f;
                                }
                                float vMin = 0;
                                float vMax = slice;
                                int partY = y;
                                int partH = border;
                                if (yIndex == 1) {
                                        partY = y + border;
                                        vMin = slice;
                                        vMax = 1 - slice;
                                        partH = height - 2 * border;
                                } else if (yIndex == 2) {
                                        partY = y + height - border;
                                        vMin = 1 - slice;
                                        vMax = 1f;
                                }
                                graphics.blit(texture, partX, partY, partX + partW, partY + partH,
                                                uMin, uMax, vMin, vMax);
                        }
                }
        }

        private static boolean contains(int @Nullable [] box, int x, int y) {
                return box != null && x >= box[0] && x < box[0] + box[2]
                                && y >= box[1] && y < box[1] + box[3];
        }

        private static void playPressSound() {
                Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        // ------------------------------------------------------------------
        // Input
        // ------------------------------------------------------------------

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                int mouseX = (int) click.x();
                int mouseY = (int) click.y();
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                if (pickMode) {
                        for (int[] box : gridBoxes) {
                                if (contains(box, mouseX, mouseY)) {
                                        pick(box[4]);
                                        playPressSound();
                                        return true;
                                }
                        }
                        pickMode = false;
                        return true;
                }
                if (contains(sellingBox, mouseX, mouseY) && direction != ShopDirection.SELLING) {
                        direction = ShopDirection.SELLING;
                        playPressSound();
                        return true;
                }
                if (contains(buyingBox, mouseX, mouseY) && direction != ShopDirection.BUYING) {
                        direction = ShopDirection.BUYING;
                        playPressSound();
                        return true;
                }
                if (contains(itemBox, mouseX, mouseY)) {
                        pickMode = true;
                        playPressSound();
                        return true;
                }
                if (contains(perItemBox, mouseX, mouseY)) {
                        scope = PriceScope.PER_ITEM;
                        playPressSound();
                        return true;
                }
                if (contains(perBulkBox, mouseX, mouseY) && scope != PriceScope.PER_BULK) {
                        scope = PriceScope.PER_BULK;
                        playPressSound();
                        return true;
                }
                if (contains(cancelBox, mouseX, mouseY)) {
                        onClose();
                        return true;
                }
                if (contains(doneBox, mouseX, mouseY) && doneReady()) {
                        done();
                        return true;
                }
                // The fields take focus by click, one at a time.
                EditBox hit = fieldAt(mouseX, mouseY);
                focus(hit);
                return hit != null || super.mouseClicked(click, doubled);
        }

        private EditBox fieldAt(int x, int y) {
                EditBox[] fields = {sellerField, itemField, priceField, bulkField, stockField,
                                signXField, signYField, signZField};
                for (EditBox field : fields) {
                        if (field != null && x >= field.getX() && x < field.getX() + field.getWidth()
                                        && y >= field.getY() && y < field.getY() + FIELD_HEIGHT) {
                                return field;
                        }
                }
                return null;
        }

        private void focus(@Nullable EditBox field) {
                EditBox[] fields = {sellerField, itemField, priceField, bulkField, stockField,
                                signXField, signYField, signZField};
                for (EditBox other : fields) {
                        if (other != null) {
                                other.setFocused(other == field);
                        }
                }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
                if (input.isEscape()) {
                        if (pickMode) {
                                pickMode = false;
                                return true;
                        }
                        onClose();
                        return true;
                }
                if (!pickMode) {
                        EditBox[] fields = {sellerField, itemField, priceField, bulkField, stockField,
                                        signXField, signYField, signZField};
                        for (EditBox field : fields) {
                                if (field != null && field.isFocused() && field.keyPressed(input)) {
                                        return true;
                                }
                        }
                }
                return super.keyPressed(input);
        }

        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
                if (!pickMode) {
                        EditBox[] fields = {sellerField, itemField, priceField, bulkField, stockField,
                                        signXField, signYField, signZField};
                        for (EditBox field : fields) {
                                if (field != null && field.isFocused() && field.charTyped(input)) {
                                        return true;
                                }
                        }
                }
                return super.charTyped(input);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                        double verticalAmount) {
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        private void pick(int index) {
                Player player = minecraft == null ? null : minecraft.player;
                if (player == null) {
                        return;
                }
                List<ItemStack> items = player.getInventory().getNonEquipmentItems();
                if (index >= 0 && index < items.size()) {
                        ItemStack stack = items.get(index);
                        if (!stack.isEmpty()) {
                                pickedItem = stack.copyWithCount(1);
                                itemField.setValue(pickedItem.getHoverName().getString());
                        }
                }
                pickMode = false;
        }

        // ------------------------------------------------------------------
        // The Done path
        // ------------------------------------------------------------------

        /** Whether the form currently holds a recordable listing. */
        private boolean doneReady() {
                return itemText() != null
                                && priceTextValid()
                                && signPosition() != null
                                && (scope != PriceScope.PER_BULK || bulkSize() != null);
        }

        /** What keeps Done disabled, for the tooltip. */
        private String doneBlocker() {
                if (itemText() == null) {
                        return "Pick an item or type its name";
                }
                if (!priceTextValid()) {
                        return "The price is not a number";
                }
                if (signPosition() == null) {
                        return "The sign coordinates are not numbers";
                }
                return "The bulk size is not a number";
        }

        private @Nullable String itemText() {
                String name = itemField.getValue().trim();
                if (pickedItem != null && !pickedItem.isEmpty()) {
                        return name.isEmpty() ? pickedItem.getHoverName().getString() : name;
                }
                return name.isEmpty() ? null : name;
        }

        private boolean priceTextValid() {
                return price().isPresent();
        }

        private java.util.Optional<BigDecimal> price() {
                String text = priceField.getValue().trim();
                if (text.isEmpty()) {
                        return java.util.Optional.empty();
                }
                try {
                        BigDecimal price = new BigDecimal(text);
                        return price.signum() >= 0 ? java.util.Optional.of(price) : java.util.Optional.empty();
                } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                }
        }

        private @Nullable Integer bulkSize() {
                String text = bulkField.getValue().trim();
                if (text.isEmpty()) {
                        return null;
                }
                try {
                        int value = Integer.parseInt(text);
                        return value > 0 ? value : null;
                } catch (NumberFormatException e) {
                        return null;
                }
        }

        private @Nullable String stockText() {
                String text = stockField.getValue().trim();
                return text.isEmpty() ? null : text;
        }

        private @Nullable BlockPos signPosition() {
                try {
                        int x = Integer.parseInt(signXField.getValue().trim());
                        int y = Integer.parseInt(signYField.getValue().trim());
                        int z = Integer.parseInt(signZField.getValue().trim());
                        return new BlockPos(x, y, z);
                } catch (NumberFormatException e) {
                        return null;
                }
        }

        /** Done: the listing joins the market list, or the review corrects it. */
        private void done() {
                MarketSubsystem market = ZeaClient.market();
                BlockPos signPos = signPosition();
                String itemDisplay = itemText();
                if (market == null || signPos == null || itemDisplay == null) {
                        return;
                }
                ClientLifecycle lifecycle = ZeaClient.lifecycle();
                String serverId = lifecycle == null ? "unknown"
                                : lifecycle.earthMcServer().map(ServerIdentity::host).orElse("unknown");
                String world = minecraft != null && minecraft.level != null
                                ? minecraft.level.dimension().identifier().toString()
                                : "unknown";
                ItemFingerprint fingerprint = pickedItem != null && !pickedItem.isEmpty()
                                ? ItemFingerprints.ofStack(pickedItem)
                                : ItemFingerprints.fromDisplay(itemDisplay);
                java.util.Optional<BigDecimal> price = price();
                if (review != null && !dirtyReview()) {
                        // The capture was already recorded; confirming it
                        // unchanged adds nothing the market does not have.
                        onClose();
                        return;
                }
                boolean recorded = market.submitManualListing(
                                serverId, world, signPos,
                                blankToNull(sellerField.getValue()),
                                review == null ? null : review.chestPos(),
                                direction, fingerprint, itemDisplay,
                                price.orElse(null), scope,
                                scope == PriceScope.PER_BULK ? bulkSize() : null,
                                stockText(), stockText() == null ? StockUnit.UNKNOWN : StockUnit.ITEMS,
                                review != null && review.response() != null
                                                ? review.response().enchantments() : List.of(),
                                Instant.now());
                if (recorded) {
                        playPressSound();
                }
                onClose();
        }

        /**
         * Whether the review's fields differ from what the capture recorded —
         * an unchanged review has nothing to add.
         */
        private boolean dirtyReview() {
                if (review == null) {
                        return true;
                }
                net.zaminmc.zea.market.model.ShopResponse response = review.response();
                if (response == null) {
                        // A sign-only capture corrected by the player is always
                        // worth recording: the player's word outranks the advert.
                        return true;
                }
                return !java.util.Objects.equals(blankToNull(sellerField.getValue()), response.owner())
                                || !java.util.Objects.equals(itemText(), response.itemDisplay())
                                || !price().equals(java.util.Optional.ofNullable(response.price()))
                                || direction != response.direction()
                                || scope != response.priceScope()
                                || !java.util.Objects.equals(bulkSize(), response.bulkSize())
                                || !java.util.Objects.equals(stockText(),
                                                response.stockUnit() == StockUnit.ITEMS ? response.stockRaw() : null);
        }

        private static @Nullable String blankToNull(String text) {
                if (text == null) {
                        return null;
                }
                String trimmed = text.trim();
                return trimmed.isEmpty() ? null : trimmed;
        }
}
