package net.zaminmc.zea.hud;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

/**
 * The {@code /zeahud} command: opens the universal HUD manager, deferred
 * through the client queue exactly like the settings screen — a client
 * command runs mid-keystroke in the chat screen, whose follow-up close
 * would otherwise wipe the manager before a frame.
 */
public final class HudCommands {
        private HudCommands() {
        }

        /** Registers {@code /zeahud} onto the command root. */
        public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root,
                        Supplier<HudSubsystem> hud) {
                root.then(ClientCommands.literal("zeahud").executes(context -> {
                        Minecraft client = context.getSource().getClient();
                        client.execute(() -> client.gui.setScreen(new HudManagerScreen(hud.get())));
                        return 1;
                }));
        }
}
