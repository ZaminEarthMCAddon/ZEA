package net.zaminmc.zea.itempresentation.recognition;

/**
 * The high-level kind of an item, as far as the RPG-style tooltip is
 * concerned. One category maps to one family of statistics: a sword is
 * shown melee damage and sweeping, a pickaxe is shown mining speed and
 * effectiveness, a helmet is shown armor and toughness, and so on.
 *
 * <p>The category is resolved by {@link ItemClassifier} from Minecraft
 * 26.3's actual item definitions — tags where vanilla ships them
 * ({@code #minecraft:swords}, {@code #minecraft:spears}, the armor tags,
 * …), the item's own class where vanilla still has one
 * ({@link net.minecraft.world.item.BowItem}, {@link
 * net.minecraft.world.item.MaceItem}, …), and a component presence test
 * where the item is purely data-driven (food, tools). Never from the
 * item's display name.
 *
 * <p>{@link #OTHER} is the catch-all for items ZEA has no specific
 * presentation for — blocks, materials, utility items. They keep their
 * vanilla tooltip; the category exists so the rest of the pipeline can
 * short-circuit cleanly instead of carrying a {@code null} around.
 */
public enum ItemCategory {
        /** Melee cutting weapon: {@code #minecraft:swords}. Sweeping applies. */
        SWORD,
        /** Melee cleaving weapon: {@code #minecraft:axes}. Both combat and mining. */
        AXE,
        /** Melee stabbing weapon: {@code #minecraft:spears}. Jab and lunge mechanics. */
        SPEAR,
        /** Smash weapon: {@link net.minecraft.world.item.MaceItem}. Fall-distance damage. */
        MACE,
        /** Drawn ranged weapon: {@link net.minecraft.world.item.BowItem}. */
        BOW,
        /** Loaded ranged weapon: {@link net.minecraft.world.item.CrossbowItem}. */
        CROSSBOW,
        /** Thrown ranged weapon: {@link net.minecraft.world.item.TridentItem}. */
        TRIDENT,
        /** Hard-rock mining tool: {@code #minecraft:pickaxes}. */
        PICKAXE,
        /** Soft-block mining tool: {@code #minecraft:shovels}. */
        SHOVEL,
        /** Crop-and-leaf tool: {@code #minecraft:hoes}. */
        HOE,
        /** Shearing utility: {@link net.minecraft.world.item.ShearsItem}. */
        SHEARS,
        /** Head-slot armor: {@code #minecraft:head_armor}. */
        HELMET,
        /** Chest-slot armor: {@code #minecraft:chest_armor}. */
        CHESTPLATE,
        /** Legs-slot armor: {@code #minecraft:leg_armor}. */
        LEGGINGS,
        /** Feet-slot armor: {@code #minecraft:foot_armor}. */
        BOOTS,
        /** Held defensive shield: {@link net.minecraft.world.item.ShieldItem}. */
        SHIELD,
        /** Chest-slot glider: elytra (no armor points, flies). */
        ELYTRA,
        /** Eaten item: carries {@link net.minecraft.world.food.FoodProperties}. */
        FOOD,
        /** Anything else: keeps its vanilla tooltip. */
        OTHER;

        /** Whether this category is a melee weapon swung by hand. */
        public boolean isMeleeWeapon() {
                return switch (this) {
                        case SWORD, AXE, SPEAR, MACE -> true;
                        default -> false;
                };
        }

        /** Whether this category fires projectiles rather than swinging. */
        public boolean isRangedWeapon() {
                return switch (this) {
                        case BOW, CROSSBOW, TRIDENT -> true;
                        default -> false;
                };
        }

        /** Whether this category mines blocks when used. */
        public boolean isMiningTool() {
                return switch (this) {
                        case PICKAXE, SHOVEL, HOE, AXE, SHEARS -> true;
                        default -> false;
                };
        }

        /** Whether this category is a piece of worn armor. */
        public boolean isArmor() {
                return switch (this) {
                        case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> true;
                        default -> false;
                };
        }
}
