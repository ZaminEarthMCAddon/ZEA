package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;

import java.util.Optional;

/**
 * Mining speed for pickaxes, shovels, hoes, axes and shears.
 *
 * <p>The base speed is the {@link Tool} component's
 * {@link Tool#defaultMiningSpeed()} — the speed the tool applies to a
 * block it is correct for but that has no rule-specific override. A real
 * tooltip cannot pick a target block, so the default is the honest
 * representative: it is the number the tool actually contributes against
 * most mineable blocks, and the calculator marks it as correct-for-drops
 * so the presentation does not imply Efficiency applies to blocks the
 * tool is wrong for.
 *
 * <p>Efficiency is folded in only when the tool is correct for drops —
 * this is the load-bearing vanilla rule the task calls out. Efficiency on
 * a golden pickaxe mining stone is real; Efficiency on a wooden pickaxe
 * mining obsidian (wrong tool) is not, and the {@code
 * appliesOnlyWhenCorrect} flag tells the presentation to state the
 * condition rather than present a flat number.
 *
 * <p>Block-specific speed overrides (a tool's {@code rules} list) are
 * not resolved here: they depend on a block the tooltip does not have,
 * and presenting one block's speed as universal would misrepresent the
 * tool. The advanced-info mode exposes the rule count instead.
 */
public final class MiningSpeedCalculator {
        /** The Efficiency enchantment's per-level speed bonus, vanilla-constant. */
        static final float EFFICIENCY_PER_LEVEL = 1.0f;

        private MiningSpeedCalculator() {
        }

        /**
         * The mining speed for {@code stack} from {@code snapshot}, or
         * empty when the item carries no {@link Tool} component.
         */
        public static Optional<ItemStatistics.MiningSpeed> calculate(ItemStack stack, ItemSnapshot snapshot) {
                Tool tool = snapshot.tool();
                if (tool == null) {
                        return Optional.empty();
                }
                double baseSpeed = tool.defaultMiningSpeed();
                int efficiency = EnchantmentLevel.efficiency(snapshot);
                double efficiencyBonus = efficiency > 0 ? efficiency * EFFICIENCY_PER_LEVEL : 0.0;
                return Optional.of(new ItemStatistics.MiningSpeed(
                                baseSpeed,
                                efficiencyBonus,
                                true));
        }
}
