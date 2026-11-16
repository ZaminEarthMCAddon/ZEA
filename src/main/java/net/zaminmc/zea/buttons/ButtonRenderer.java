package net.zaminmc.zea.buttons;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws the buttons of a container screen from the layout's placements.
 * Pure presentation: positions come from {@link ButtonGeometry}, icons
 * from {@link ButtonIcons}, tooltip text from {@link ButtonTooltips};
 * this class owns only the sprite and the colors.
 *
 * <p>The background is a real texture — ZEA's own vanilla-styled button
 * sheet ({@code zea:textures/gui/buttons.png}, five 18×18 variants in a
 * 90×18 strip), drawn the way the reference port draws its sheet — so a
 * button reads as a GUI control instead of the flat box the first
 * iteration drew. The icon draws over it at 16×16 with one pixel of
 * padding: an item stack, or one of the custom texture glyphs. The
 * editor draws its own canvas (see {@code ButtonEditorScreen}); this
 * renderer is the in-container view.
 */
public final class ButtonRenderer {
	/** ZEA's button background sheet: five 18×18 styles in a row. */
	public static final Identifier BUTTONS_TEXTURE =
			Identifier.fromNamespaceAndPath("zea", "textures/gui/buttons.png");

	private static final int TEXTURE_WIDTH = 90;
	private static final int TEXTURE_HEIGHT = 18;

	private ButtonRenderer() {
	}

	/**
	 * Renders the buttons. Unrendered placements (over the slot band of a
	 * foreign container, under the recipe book, off-screen) are skipped;
	 * disabled buttons never draw in the container view. A button
	 * without a command still draws — the editor and the container
	 * agree on what exists, an unconfigured button just does nothing
	 * when clicked (the activator refuses it) and its tooltip says
	 * so. What never draws is a button with no face at all: no icon,
	 * no text — there is nothing to see.
	 *
	 * @param placements the layout's resolved placements, in draw order
	 */
	public static void render(
			GuiGraphicsExtractor graphics,
			Font font,
			List<ButtonGeometry.Placement> placements,
			int left,
			int top,
			int mouseX,
			int mouseY) {
		for (ButtonGeometry.Placement placement : placements) {
			InventoryButton button = placement.button();
			if (!placement.rendered() || !button.enabled()) {
				continue;
			}
			int x = placement.x();
			int y = placement.y();
			boolean hovered = ButtonGeometry.contains(x, y, ButtonGeometry.SIZE, ButtonGeometry.SIZE, mouseX, mouseY);

			drawBackground(graphics, x, y, button.style());
			drawFace(graphics, font, button, x, y, hovered);
			if (hovered) {
				drawBorder(graphics, x, y, 0xFFFFFFFF);
				List<Component> lines = ButtonTooltips.lines(button,
						ButtonIcons.isKnown(button.icon()), placement.rendered());
				graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
			}
		}
	}

	/** One style swatch of the sheet — the editor's style picker. */
	public static void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int style) {
		drawBackground(graphics, x, y, style);
	}

	/** The styled background sprite of one button. */
	private static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int style) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTONS_TEXTURE, x, y,
				(float) (style * ButtonGeometry.SIZE), 0.0f,
				ButtonGeometry.SIZE, ButtonGeometry.SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	/** A one-pixel frame around a button-sized square. */
	public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int color) {
		drawBorder(graphics, x, y, ButtonGeometry.SIZE, ButtonGeometry.SIZE, color);
	}

	/** A one-pixel frame around an arbitrary rectangle. */
	public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/**
	 * The button's face over its background: display text scaled to fit
	 * the 18×18 box, else the icon (custom texture or item), else a
	 * question mark — the same drawing in the container view and in the
	 * editor, so the editor preview is exactly what renders in the
	 * inventory.
	 */
	public static void drawFace(GuiGraphicsExtractor graphics, Font font, InventoryButton button, int x, int y,
			boolean bright) {
		if (!button.text().isBlank()) {
			drawFittedText(graphics, font, button.text(), x, y, bright);
			return;
		}
		Identifier custom = ButtonIcons.customTextureFor(button.icon());
		if (custom != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, custom, x + 1, y + 1,
					0.0f, 0.0f, 16, 16, 16, 16);
			return;
		}
		ItemStack stack = ButtonIcons.iconFor(button.icon());
		if (!stack.isEmpty()) {
			graphics.item(stack, x + 1, y + 1);
			return;
		}
		graphics.centeredText(font, "?", x + ButtonGeometry.SIZE / 2,
				y + (ButtonGeometry.SIZE - font.lineHeight) / 2,
				bright ? 0xFFFFFFFF : 0xFFC8C8E0);
	}

	/** Draws text scaled down until it fits the button box, centered. */
	private static void drawFittedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
			boolean bright) {
		int color = bright ? 0xFFFFFFFF : 0xFFC8C8E0;
		int box = ButtonGeometry.SIZE - 4;
		int width = font.width(text);
		if (width <= box) {
			graphics.centeredText(font, text, x + ButtonGeometry.SIZE / 2,
					y + (ButtonGeometry.SIZE - font.lineHeight) / 2, color);
			return;
		}
		// Scale to fit: draw at reduced size by trimming font scale in steps.
		float scale = Math.min(1f, (float) box / width);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + ButtonGeometry.SIZE / 2f, y + ButtonGeometry.SIZE / 2f);
		pose.scale(scale, scale);
		int textY = -font.lineHeight / 2;
		int textX = -font.width(text) / 2;
		graphics.text(font, text, textX, textY, color, true);
		pose.popMatrix();
	}
}
