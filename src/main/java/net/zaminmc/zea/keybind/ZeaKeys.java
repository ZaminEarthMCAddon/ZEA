package net.zaminmc.zea.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * ZEA's key bindings, registered into the vanilla controls screen.
 *
 * <p>ZEA's built-in shortcuts are modifier-based mouse and keyboard
 * combinations (right-click copy, alt + right-click formatted copy,
 * Ctrl+F search) that the vanilla key-mapping system cannot express —
 * a binding is one key without modifiers. These bindings therefore
 * offer *additional*, rebindable triggers for screen-scoped actions;
 * the built-in shortcuts always keep working.
 */
public final class ZeaKeys {
        /** ZEA's group in the controls screen. */
        private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("zea", "zea"));

        /**
         * Toggles the chat search field while the chat screen is open.
         * Unbound by default — Ctrl+F (Cmd+F on macOS) already does this.
         */
        public static final KeyMapping SEARCH_CHAT = new KeyMapping(
                        "zea.key.search_chat", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

        /**
         * Toggles inventory-button editing while a container screen is
         * open. Unbound by default — bind it once in Controls → ZEA.
         */
        public static final KeyMapping EDIT_BUTTONS = new KeyMapping(
                        "zea.key.edit_buttons", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

        /**
         * While a container screen is open, pressing this over a player
         * inventory slot toggles its lock; keeping it held and clicking a
         * hotbar slot binds the two. Unbound by default — bind it once in
         * Controls → ZEA.
         */
        public static final KeyMapping LOCK_SLOT = new KeyMapping(
                        "zea.key.lock_slot", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

        /**
         * Every binding {@link #register()} delegates to — the single source
         * of truth, so a mapping can never be defined and consumed but left
         * out of registration (the edit-mode keybind once was, which left
         * button editing unreachable).
         */
        /**
         * Closes the shop index's panes while a container screen is open —
         * NEU's close-panes binding: the grid pane slides away, the
         * detail pane with it, leaving only the reopen tab. Unbound by
         * default — the tab and a fresh screen close the pane too.
         */
        public static final KeyMapping CLOSE_PANES = new KeyMapping(
                        "zea.key.close_panes", InputConstants.Type.KEYBOARD,
                        InputConstants.UNKNOWN.getValue(), CATEGORY);

        private static final List<KeyMapping> ALL = List.of(SEARCH_CHAT, EDIT_BUTTONS, LOCK_SLOT,
                        CLOSE_PANES);

        private ZeaKeys() {
        }

        /** Registers the bindings; called once from the client entrypoint. */
        public static void register() {
                for (KeyMapping mapping : ALL) {
                        KeyMappingHelper.registerKeyMapping(mapping);
                }
        }

        /** The bindings registration hands to Fabric; the test surface. */
        public static List<KeyMapping> registered() {
                return ALL;
        }
}
