package net.zaminmc.zea.rarity;

/**
 * The four ZEA item rarities, ordered lowest to highest. There is no
 * Mythic.
 *
 * <p>Colors follow the spec and the NEU precedent: white, lime green,
 * purple, gold. The RGB values are the plain sRGB triples of the matching
 * legacy colors ({@code white}, {@code green}, {@code dark_purple},
 * {@code gold}), kept here as constants so rendering never needs to
 * resolve a {@link net.minecraft.ChatFormatting} to know the color.
 */
public enum ItemRarity {
        COMMON(0xFFFFFF, "COMMON"),
        UNCOMMON(0x55FF55, "UNCOMMON"),
        EPIC(0xAA00AA, "EPIC"),
        LEGENDARY(0xFFAA00, "LEGENDARY");

        /** The rarity's sRGB color, without alpha. */
        private final int rgb;
        /** The uppercase label shown at the bottom of the tooltip. */
        private final String label;

        ItemRarity(int rgb, String label) {
                this.rgb = rgb;
                this.label = label;
        }

        /** The rarity's sRGB color, without alpha. */
        public int rgb() {
                return rgb;
        }

        /** The same color as a full-opacity ARGB value. */
        public int argb() {
                return 0xFF000000 | rgb;
        }

        /** The uppercase label shown at the bottom of the tooltip. */
        public String label() {
                return label;
        }

        /** The rarity that sits {@code levels} above this one, clamped at Legendary. */
        public ItemRarity raised(int levels) {
                return this.ordinal() >= values().length - 1 || levels <= 0
                                ? this
                                : values()[Math.min(values().length - 1, ordinal() + levels)];
        }
}
