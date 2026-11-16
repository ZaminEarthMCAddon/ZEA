package net.zaminmc.zea.discovery;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * The {@code /zeadiscovery} command: on, off, status — and a bare
 * invocation that prints usage. Feedback goes to the chat like every
 * other ZEA command; the enabled state persists through the subsystem's
 * own file, so the switch is the same one the capture pipeline reads.
 */
public final class DiscoveryCommands {
        private DiscoveryCommands() {
        }

        /** Registers {@code /zeadiscovery} onto the command root. */
        public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root,
                        Supplier<DiscoverySubsystem> subsystem) {
                root.then(ClientCommands.literal("zeadiscovery")
                                .executes(context -> {
                                        context.getSource().sendFeedback(Component.literal(
                                                        "Usage: /zeadiscovery on | off | status — turns "
                                                                        + "native shop and navigation discovery on or off."));
                                        context.getSource().sendFeedback(Component.literal(
                                                        subsystem.get().statusLine()));
                                        return 1;
                                })
                                .then(ClientCommands.literal("on").executes(context -> {
                                        subsystem.get().setEnabled(true);
                                        subsystem.get().save();
                                        context.getSource().sendFeedback(Component.literal(
                                                        "Discovery is on: shops you inspect and navigation "
                                                                        + "commands you run are collected locally."));
                                        return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(context -> {
                                        subsystem.get().setEnabled(false);
                                        subsystem.get().save();
                                        context.getSource().sendFeedback(Component.literal(
                                                        "Discovery is off: nothing is collected. Existing "
                                                                        + "observations are kept."));
                                        return 1;
                                }))
                                .then(ClientCommands.literal("status").executes(context -> {
                                        context.getSource().sendFeedback(Component.literal(
                                                        subsystem.get().statusLine()));
                                        return 1;
                                })));
        }
}
