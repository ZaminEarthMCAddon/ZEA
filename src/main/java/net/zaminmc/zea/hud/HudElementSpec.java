package net.zaminmc.zea.hud;

import net.minecraft.resources.Identifier;

/**
 * The registration contract of a ZEA HUD element — everything the
 * universal HUD manager needs to list, position, toggle and render one
 * element, and everything a future feature must provide to appear in the
 * manager without any manager redesign. Registration is one call: the
 * manager discovers registered elements automatically, in order.
 *
 * <p>Size and offset are in GUI pixels at 100% scale; the scale setting
 * multiplies them. Defaults are the element's factory look — the reset
 * action restores exactly these.
 *
 * @param id              stable element id, the persistence key
 * @param displayName     the manager's list label
 * @param defaultWidth    element width at 100% scale
 * @param defaultHeight   element height at 100% scale
 * @param defaultAnchor   the factory corner
 * @param defaultOffsetX  factory distance from the anchor's vertical edge
 * @param defaultOffsetY  factory distance from the anchor's horizontal edge
 * @param defaultVisible  factory visibility
 */
public record HudElementSpec(
                Identifier id,
                String displayName,
                int defaultWidth,
                int defaultHeight,
                HudAnchor defaultAnchor,
                int defaultOffsetX,
                int defaultOffsetY,
                boolean defaultVisible) {

        public HudElementSpec {
                if (id == null || displayName == null) {
                        throw new NullPointerException("hud element id and display name must not be null");
                }
                if (defaultWidth <= 0 || defaultHeight <= 0) {
                        throw new IllegalArgumentException("hud element must have a positive size: " + id);
                }
        }
}
