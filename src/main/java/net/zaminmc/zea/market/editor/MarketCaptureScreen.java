package net.zaminmc.zea.market.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.SpawnRef;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.parse.ShopSignParser;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The capture completion screen: the one screen the capture flow opens —
 * and only when the server's response left something out. The captured
 * listing is shown as it stands (owner, item, direction, stock, price,
 * enchantments, the sign it sits at, the spawn that leads there), and
 * each missing detail gets one input: the spawn typed the way a player
 * runs it ({@code /n spawn france}), a price the block lacked, a stock
 * the block lacked. Done records what was filled — the player's word
 * outranks the half-parsed capture — and the capture flow never asked
 * for anything the server had already delivered.
 *
 * <p>The chrome is the viewer's: JEI's nine-slice panel and button
 * plates, JEI's geometry paddings, the press sound. A Done that cannot
 * parse what was typed keeps the panel open and marks the field red —
 * the screen never invents defaults for what the player typed.
 */
public final class MarketCaptureScreen extends Screen {
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

        private static final int PANEL_WIDTH = 236;
        private static final int CONTENT_X = PANEL_BORDER + 2;
        private static final int CONTENT_WIDTH = PANEL_WIDTH - 2 * CONTENT_X;
        private static final int FIELD_HEIGHT = 14;
        private static final int LABEL_PITCH = 10;
        private static final int FIELD_PITCH = FIELD_HEIGHT + 4;
        private static final int INFO_PITCH = 10;
        private static final int FOOTER_HEIGHT = 24;

        private static final int LABEL_COLOR = 0xFF4C4C4C;
        private static final int TEXT = 0xFF000000;
        /** The red a rejected value wears until it parses. */
        private static final int FIELD_ERROR = 0xFFFF5555;
        /** Field text sits on the EditBox's own dark plate — light always. */
        private static final int FIELD_TEXT = 0xFFE0E0E0;
        private static final int FIELD_TEXT_DIM = 0xFFA0A0A0;
        /** Vanilla button labels — light on the button plates. */
        private static final int BUTTON_TEXT = 0xFFE0E0E0;

        // ------------------------------------------------------------------
        // State
        // ------------------------------------------------------------------

        /** JEI's {@code parentScreen} — restored on close. */
        private final Screen parent;
        /** The capture being completed — the latest inspection. */
        private final ShopInspectionTracker.Inspection review;
        private final String shopId;

        /** The captured listing as it stands: label and value pairs. */
        private record InfoLine(String label, String value) {
        }
        private final List<InfoLine> infoLines = new ArrayList<>();

        private @Nullable EditBox spawnField;
        private @Nullable EditBox priceField;
        private @Nullable EditBox stockField;

        private int panelHeight;
        private int infoTop;
        private int fieldsTop;
        private int guiLeft;
        private int guiTop;
        private int lastMouseX;
        private int lastMouseY;
        private int @Nullable [] doneBox;
        private int @Nullable [] cancelBox;

        public MarketCaptureScreen(Screen parent, ShopInspectionTracker.Inspection review) {
                super(Component.literal("ZEA Market capture"));
                this.parent = parent;
                this.review = review;
                this.shopId = PhysicalShop.idOf(review.serverId(), review.world(), review.signPos());
        }

        /**
         * JEI's {@code open()}: capture the current screen as the parent and
         * swap the screen in. The command runs this through the client queue
         * (a client command executes mid-keystroke inside the chat screen).
         */
        public static void open(ShopInspectionTracker.Inspection review) {
                Minecraft client = Minecraft.getInstance();
                Screen parent = client.gui.screen();
                client.gui.setScreen(new MarketCaptureScreen(parent, review));
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
                buildInfo();
                boolean spawnMissing = spawnUnknown();
                ShopResponse response = review.response();
                boolean priceMissing = response == null || response.price() == null;
                boolean stockMissing = response == null || response.stockRaw() == null;

                if (spawnMissing) {
                        spawnField = field(64);
                        spawnField.setHint(Component.literal("/n spawn france").withStyle(ChatFormatting.DARK_GRAY));
                }
                if (priceMissing) {
                        priceField = field(16);
                        priceField.setHint(Component.literal("e.g. 2").withStyle(ChatFormatting.DARK_GRAY));
                }
                if (stockMissing) {
                        stockField = field(12);
                        stockField.setHint(Component.literal("e.g. 55").withStyle(ChatFormatting.DARK_GRAY));
                }

                int missingCount = (spawnMissing ? 1 : 0) + (priceMissing ? 1 : 0) + (stockMissing ? 1 : 0);
                infoTop = 24;
                panelHeight = infoTop + infoLines.size() * INFO_PITCH + 8
                                + (missingCount == 0 ? 0 : 12 + missingCount * FIELD_PITCH)
                                + FOOTER_HEIGHT;
                guiLeft = (width - PANEL_WIDTH) / 2;
                guiTop = (height - panelHeight) / 2;
                fieldsTop = infoTop + infoLines.size() * INFO_PITCH + 8;

                positionFields();
                EditBox[] fields = fields();
                for (EditBox box : fields) {
                        box.setFocused(true);
                        box.setFocused(false);
                }
                if (fields.length > 0) {
                        fields[0].setFocused(true);
                }
        }

        private EditBox field(int maxLength) {
                EditBox box = new EditBox(font, 0, 0, CONTENT_WIDTH, FIELD_HEIGHT, Component.literal(""));
                box.setMaxLength(maxLength);
                return box;
        }

        /** The captured listing, as it stands, ready to read in one glance. */
        private void buildInfo() {
                MarketSubsystem market = ZeaClient.market();
                PhysicalShop shop = market == null ? null : market.data().shop(shopId);
                ShopResponse response = review.response();
                String owner = shop == null || shop.owner() == null || shop.owner().isBlank()
                                ? "?" : shop.owner();
                infoLines.add(new InfoLine("Owner", owner));
                String direction = response != null ? direction(response.direction())
                                : signDirection();
                String item = response != null && response.itemDisplay() != null
                                ? response.itemDisplay()
                                : signItem();
                infoLines.add(new InfoLine("Item", direction + " " + (item == null ? "?" : item)));
                String stock = response == null ? null : stockText(response);
                infoLines.add(new InfoLine("Stock", stock == null ? "—" : stock));
                String price = response == null ? null : priceText(response);
                infoLines.add(new InfoLine("Price", price == null ? "—" : price));
                String enchantments = response == null || response.enchantments().isEmpty()
                                ? null : String.join(", ", response.enchantments());
                infoLines.add(new InfoLine("Enchantments", enchantments == null ? "—" : enchantments));
                String spawn = shop == null ? null : shop.spawn();
                infoLines.add(new InfoLine("Spawn", spawn == null ? "—" : spawn));
                infoLines.add(new InfoLine("Sign", coordinates(review.signPos())));
        }

        /** Whether no spawn reference stands for this shop yet. */
        private boolean spawnUnknown() {
                MarketSubsystem market = ZeaClient.market();
                PhysicalShop shop = market == null ? null : market.data().shop(shopId);
                return shop == null || shop.spawn() == null;
        }

        /** The sign advert's direction, when the response is absent. */
        private String signDirection() {
                if (review.signLines() == null) {
                        return "";
                }
                return ShopSignParser.parse(review.signLines())
                                .map(advert -> direction(advert.direction()))
                                .orElse("");
        }

        /** The sign advert's item display, when the response is absent. */
        private @Nullable String signItem() {
                if (review.signLines() == null) {
                        return null;
                }
                return ShopSignParser.parse(review.signLines())
                                .map(advert -> advert.itemDisplay())
                                .orElse(null);
        }

        private static String direction(ShopDirection direction) {
                return direction == ShopDirection.SELLING ? "Selling" : "Buying";
        }

        /** The stock as the server worded it, unit word only when it is known. */
        private static @Nullable String stockText(ShopResponse response) {
                if (response.stockRaw() == null) {
                        return null;
                }
                return switch (response.stockUnit()) {
                        case ITEMS -> response.stockRaw() + " items";
                        case SPACE -> response.stockRaw() + " space";
                        default -> response.stockRaw();
                };
        }

        /** The price as captured, with the per-item figure when it normalizes. */
        private static @Nullable String priceText(ShopResponse response) {
                if (response.price() == null) {
                        return null;
                }
                StringBuilder text = new StringBuilder(gold(response.price()));
                if (response.priceScope() == PriceScope.PER_BULK && response.bulkSize() != null) {
                        text.append(" per ").append(response.bulkSize()).append("x");
                } else if (response.priceScope() == PriceScope.PER_ITEM) {
                        text.append(" each");
                }
                var unit = net.zaminmc.zea.market.pricing.PriceMath.normalize(response.price(),
                                response.priceScope(), response.bulkSize());
                if (unit.isPresent()) {
                        BigDecimal perItem = unit.get();
                        boolean isBulk = response.priceScope() == PriceScope.PER_BULK;
                        if (isBulk || perItem.compareTo(response.price()) != 0) {
                                text.append(" (").append(gold(perItem)).append(" each)");
                        }
                }
                return text.toString();
        }

        private static String gold(BigDecimal price) {
                return price.stripTrailingZeros().toPlainString() + "G";
        }

        private static String coordinates(BlockPos pos) {
                return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
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
                drawNineSlice(graphics, PANEL, guiLeft, guiTop, PANEL_WIDTH, panelHeight,
                                PANEL_SPRITE, PANEL_BORDER);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                Font f = font;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                drawTitle(graphics, f);
                int y = guiTop + infoTop;
                for (InfoLine line : infoLines) {
                        drawInfoLine(graphics, f, line.label(), line.value(), y);
                        y += INFO_PITCH;
                }
                int fieldY = fieldsTop;
                fieldY = drawFieldRow(graphics, f, spawnField, "Spawn", fieldY, mouseX, mouseY);
                fieldY = drawFieldRow(graphics, f, priceField, "Price (gold)", fieldY, mouseX, mouseY);
                fieldY = drawFieldRow(graphics, f, stockField, "Stock (items)", fieldY, mouseX, mouseY);
                drawFooter(graphics, f, mouseX, mouseY);
        }

        private void drawTitle(GuiGraphicsExtractor graphics, Font f) {
                String title = "Captured listing";
                int textWidth = f.width(title);
                graphics.text(f, title, guiLeft + (PANEL_WIDTH - textWidth) / 2, guiTop + 9,
                                0xFFFFFFFF, true);
        }

        /** One captured fact: the dim label, the black value, one line wide. */
        private void drawInfoLine(GuiGraphicsExtractor graphics, Font f,
                        String label, String value, int y) {
                graphics.text(f, label, guiLeft + CONTENT_X, y, LABEL_COLOR, false);
                int valueX = guiLeft + CONTENT_X + f.width(label) + 6;
                String trimmed = f.plainSubstrByWidth(value, guiLeft + CONTENT_X + CONTENT_WIDTH - valueX);
                graphics.text(f, trimmed, valueX, y, TEXT, false);
        }

        /** One missing-detail row: the label, then the input. Returns the next row's top. */
        private int drawFieldRow(GuiGraphicsExtractor graphics, Font f, @Nullable EditBox field,
                        String label, int y, int mouseX, int mouseY) {
                if (field == null) {
                        return y;
                }
                graphics.text(f, label, guiLeft + CONTENT_X, y, LABEL_COLOR, false);
                field.setX(guiLeft + CONTENT_X);
                field.setY(y + LABEL_PITCH);
                field.setWidth(CONTENT_WIDTH);
                field.setTextColor(!valueValid(field) ? FIELD_ERROR
                                : field.isFocused() ? FIELD_TEXT : FIELD_TEXT_DIM);
                field.extractRenderState(graphics, mouseX, mouseY, 0f);
                return y + FIELD_PITCH;
        }

        private void drawFooter(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY) {
                int buttonWidth = 80;
                int buttonHeight = FIELD_HEIGHT + 4;
                int cancelX = guiLeft + CONTENT_X;
                int doneX = guiLeft + PANEL_WIDTH - CONTENT_X - buttonWidth;
                int y = guiTop + panelHeight - FOOTER_HEIGHT + 6;
                drawNineSlice(graphics, BUTTON, cancelX, y, buttonWidth, buttonHeight,
                                BUTTON_SPRITE, BUTTON_BORDER);
                centerLabel(graphics, f, "Cancel", cancelX, y, buttonWidth, buttonHeight, BUTTON_TEXT);
                if (contains(cancelBox, mouseX, mouseY)) {
                        drawNineSlice(graphics, BUTTON_HOVER, cancelX, y, buttonWidth, buttonHeight,
                                        BUTTON_SPRITE, BUTTON_BORDER);
                        centerLabel(graphics, f, "Cancel", cancelX, y, buttonWidth, buttonHeight, BUTTON_TEXT);
                }
                drawNineSlice(graphics, BUTTON, doneX, y, buttonWidth, buttonHeight,
                                BUTTON_SPRITE, BUTTON_BORDER);
                if (contains(doneBox, mouseX, mouseY)) {
                        drawNineSlice(graphics, BUTTON_HOVER, doneX, y, buttonWidth, buttonHeight,
                                        BUTTON_SPRITE, BUTTON_BORDER);
                }
                centerLabel(graphics, f, "Done", doneX, y, buttonWidth, buttonHeight, BUTTON_TEXT);
        }

        private void centerLabel(GuiGraphicsExtractor graphics, Font f, String label,
                        int x, int y, int w, int h, int color) {
                int textWidth = f.width(label);
                graphics.text(f, label, x + (w - textWidth) / 2, y + (h - 8) / 2 + 1, color, false);
        }

        // ------------------------------------------------------------------
        // Hit boxes and input
        // ------------------------------------------------------------------

        private void positionFields() {
                int buttonWidth = 80;
                int buttonHeight = FIELD_HEIGHT + 4;
                cancelBox = new int[] {guiLeft + CONTENT_X, guiTop + panelHeight - FOOTER_HEIGHT + 6,
                                buttonWidth, buttonHeight};
                doneBox = new int[] {guiLeft + PANEL_WIDTH - CONTENT_X - buttonWidth,
                                guiTop + panelHeight - FOOTER_HEIGHT + 6, buttonWidth, buttonHeight};
        }

        private EditBox[] fields() {
                List<EditBox> fields = new ArrayList<>();
                if (spawnField != null) {
                        fields.add(spawnField);
                }
                if (priceField != null) {
                        fields.add(priceField);
                }
                if (stockField != null) {
                        fields.add(stockField);
                }
                return fields.toArray(new EditBox[0]);
        }

        private static boolean contains(int @Nullable [] box, int x, int y) {
                return box != null && x >= box[0] && x < box[0] + box[2]
                                && y >= box[1] && y < box[1] + box[3];
        }

        /** Whether the typed value parses; an empty value is always fine. */
        private boolean valueValid(EditBox field) {
                String text = field.getValue().trim();
                if (text.isEmpty()) {
                        return true;
                }
                if (field == spawnField) {
                        return SpawnRef.normalize(text) != null;
                }
                if (field == priceField) {
                        try {
                                return new BigDecimal(text).signum() >= 0;
                        } catch (NumberFormatException e) {
                                return false;
                        }
                }
                try {
                        return Integer.parseInt(text) >= 0;
                } catch (NumberFormatException e) {
                        return false;
                }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                int mouseX = (int) click.x();
                int mouseY = (int) click.y();
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                if (contains(cancelBox, mouseX, mouseY)) {
                        onClose();
                        return true;
                }
                if (contains(doneBox, mouseX, mouseY)) {
                        done();
                        return true;
                }
                EditBox hit = fieldAt(mouseX, mouseY);
                focus(hit);
                return hit != null || super.mouseClicked(click, doubled);
        }

        private @Nullable EditBox fieldAt(int x, int y) {
                for (EditBox field : fields()) {
                        if (x >= field.getX() && x < field.getX() + field.getWidth()
                                        && y >= field.getY() && y < field.getY() + FIELD_HEIGHT) {
                                return field;
                        }
                }
                return null;
        }

        private void focus(@Nullable EditBox field) {
                for (EditBox other : fields()) {
                        other.setFocused(other == field);
                }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
                if (input.isEscape()) {
                        onClose();
                        return true;
                }
                for (EditBox field : fields()) {
                        if (field.isFocused() && field.keyPressed(input)) {
                                return true;
                        }
                }
                return super.keyPressed(input);
        }

        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
                for (EditBox field : fields()) {
                        if (field.isFocused() && field.charTyped(input)) {
                                return true;
                        }
                }
                return super.charTyped(input);
        }

        // ------------------------------------------------------------------
        // The Done path
        // ------------------------------------------------------------------

        /** Done: the spawn and the filled details join the captured listing. */
        private void done() {
                for (EditBox field : fields()) {
                        if (!valueValid(field)) {
                                return; // the red field keeps the panel open
                        }
                }
                MarketSubsystem market = ZeaClient.market();
                if (market == null) {
                        onClose();
                        return;
                }
                String spawn = spawnField == null ? null : SpawnRef.normalize(spawnField.getValue());
                BigDecimal price = null;
                if (priceField != null && !priceField.getValue().trim().isEmpty()) {
                        price = new BigDecimal(priceField.getValue().trim());
                }
                String stock = stockField != null && !stockField.getValue().trim().isEmpty()
                                ? stockField.getValue().trim()
                                : null;
                market.completeCapture(shopId, spawn, price, stock);
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onClose();
        }

        // ------------------------------------------------------------------
        // The nine-slice pass
        // ------------------------------------------------------------------

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
}
