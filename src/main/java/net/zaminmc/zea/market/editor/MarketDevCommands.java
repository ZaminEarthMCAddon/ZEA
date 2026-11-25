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
 * {@code /zea market-dev} — the capture workflow's command face (the
 * earlier {@code /zea market-editor} spelling keeps working).
 *
 * <p>Opening it shows the latest captured listing: everything the server
 * delivered is on the panel, and the details it never carried — a spawn,
 * a price, a stock — get their inputs. The capture flow opens this screen
 * by itself only when something is missing; the command is the manual way
 * back in.
 */
public final class MarketDevCommands {
        private MarketDevCommands() {
        }

        /** Registers {@code market-dev} (and the {@code market-editor} alias) onto the /zea root. */
        public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
                for (String name : new String[] {"market-dev", "market-editor"}) {
                        root.then(ClientCommands.literal(name)
                                        .executes(context -> {
                                                openLatest(context.getSource());
                                                return 1;
                                        })
                                        .then(ClientCommands.literal("review").executes(context -> {
                                                openLatest(context.getSource());
                                                return 1;
                                        })));
                }
        }

        private static void openLatest(FabricClientCommandSource source) {
                MarketSubsystem market = ZeaClient.market();
                ShopInspectionTracker.@Nullable Inspection latest =
                                market == null ? null : market.latestInspection();
                if (latest == null) {
                        source.sendError(Component.literal(
                                        "Nothing captured yet — inspect a shop sign first."));
                        return;
                }
                Minecraft client = source.getClient();
                client.execute(() -> MarketCaptureScreen.open(latest));
        }
}
