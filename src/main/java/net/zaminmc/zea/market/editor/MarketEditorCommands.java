package net.zaminmc.zea.market.editor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import org.jspecify.annotations.Nullable;

/**
 * {@code /zea market-editor} — the market editor's command face.
 *
 * <ul>
 *   <li>{@code /zea market-editor} opens the editor for a fresh listing:
 *       the location prefills from the sign under the crosshair, the item
 *       comes from the inventory picker, Done records the listing.</li>
 *   <li>{@code /zea market-editor review} opens the editor over the latest
 *       captured inspection — the same screen the capture prompt's Review
 *       click lands on, so a prompt the player closed can be picked back
 *       up.</li>
 * </ul>
 *
 * <p>The screen opens through the client queue: a client command runs
 * mid-keystroke inside the chat screen, whose follow-up close would
 * otherwise wipe the editor before a frame renders.
 */
public final class MarketEditorCommands {
        private MarketEditorCommands() {
        }

        /** Registers {@code market-editor} (with its {@code review} branch) onto the /zea root. */
        public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
                root.then(ClientCommands.literal("market-editor")
                                .executes(context -> {
                                        open(context.getSource(), null);
                                        return 1;
                                })
                                .then(ClientCommands.literal("review").executes(context -> {
                                        MarketSubsystem market = ZeaClient.market();
                                        ShopInspectionTracker.@Nullable Inspection latest =
                                                        market == null ? null : market.latestInspection();
                                        if (latest == null) {
                                                context.getSource().sendError(Component.literal(
                                                                "Nothing captured yet — inspect a shop sign first."));
                                                return 0;
                                        }
                                        open(context.getSource(), latest);
                                        return 1;
                                })));
        }

        private static void open(FabricClientCommandSource source,
                        ShopInspectionTracker.@Nullable Inspection review) {
                Minecraft client = source.getClient();
                client.execute(() -> MarketEditorScreen.open(review));
        }
}
