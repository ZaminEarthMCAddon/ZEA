package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;

import java.util.Optional;

/**
 * Mining speed for pickaxes, shovels, hoes, axes and shears.
 *
 * <p>The base speed is the speed the tool applies to the blocks it
 * is meant for — the {@code correct_for_drops} family rule's speed
 * ({@code #mineable/pickaxe} at 8.0 on a diamond pickaxe), not the
 * {@code default_mining_speed} penalty value (1.0) the same component
 * carries for wrong-tool blocks. A real tooltip cannot pick a target
 * block, so the family speed is the honest representative, and the
 * calculator marks the result as correct-for-drops so the
 * presentation does not imply Efficiency applies to blocks the tool
 * is wrong for.
 *
 * <p>Efficiency is read live from the enchantment's {@code
 * mining_efficiency} attribute effect — in the 26.3 vanilla data a
 * {@code levels_squared} amount of {@code level squared + 1}, so
 * Efficiency V contributes 26, not 5. The rate is the datapack's to
 * change, and the tooltip follows it without a ZEA update. Efficiency
 * is folded in only when the tool is correct for drops — this is the
 * load-bearing vanilla rule the task calls out. Efficiency on a golden
 * pickaxe mining stone is real; Efficiency on a wooden pickaxe mining
 * obsidian (wrong tool) is not, and the {@code appliesOnlyWhenCorrect}
 * flag tells the presentation to state the condition rather than
 * present a flat number.
 *
 * <p>Block-specific speed overrides (a tool's {@code rules} list) are
 * not resolved here: they depend on a block the tooltip does not have,
 * and presenting one block's speed as universal would misrepresent the
 * tool. The advanced-info mode exposes the rule count instead.
 */
public final class MiningSpeedCalculator {

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
                double baseSpeed = correctForDropsSpeed(tool);
                double efficiencyBonus = EnchantmentEffects.attributeContribution(
                                snapshot, Attributes.MINING_EFFICIENCY);
                int efficiencyLevel = EnchantmentEffects.attributeLevel(
                                snapshot, Attributes.MINING_EFFICIENCY);
                return Optional.of(new ItemStatistics.MiningSpeed(
                                baseSpeed,
                                efficiencyBonus,
                                efficiencyLevel,
                                true));
        }

        /**
         * The speed the tool applies to the blocks it is meant for:
         * the 26.3 item data writes the family speed into the tool's
         * own {@code correct_for_drops} rule ({@code #mineable/pickaxe}
         * at 8.0 on a diamond pickaxe), while {@code
         * defaultMiningSpeed} is the penalty speed for blocks the tool
         * is wrong for (1.0). The first rule that both drops correctly
         * and carries a speed is the representative; a tool without
         * one falls back to the default.
         */
        static double correctForDropsSpeed(Tool tool) {
                for (Tool.Rule rule : tool.rules()) {
                        if (rule.correctForDrops().orElse(false) && rule.speed().isPresent()) {
                                return rule.speed().get();
                        }
                }
                return tool.defaultMiningSpeed();
        }
}
