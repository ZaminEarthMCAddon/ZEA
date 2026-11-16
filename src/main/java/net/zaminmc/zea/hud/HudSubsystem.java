package net.zaminmc.zea.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The universal HUD element subsystem: one registration point every ZEA
 * HUD feature plugs into, one persistence file, one Fabric HUD element
 * that draws them all. Adding a future element is one {@link #register}
 * call with a spec and a renderer — the manager screen, the toggle,
 * drag positioning, persistence and rendering discovery all follow
 * automatically, which is the whole point of the architecture: no HUD
 * feature ever rebuilds the manager.
 *
 * <p>Positions are anchor-based (see {@link HudLayoutMath}), stored in
 * {@code hud.json} through the safe-write protocol, and survive restarts,
 * updates, GUI scale and resolution changes. The manager screen mutates
 * the live states; the save is coalesced on screen close and by the
 * shutdown flush.
 */
public final class HudSubsystem {
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/HUD");

        /** The Fabric HUD slot all ZEA elements render through. */
        private static final Identifier FABRIC_ELEMENT_ID =
                        Identifier.fromNamespaceAndPath("zea", "hud");

        private final JsonStore json = new JsonStore();
        private final Path file;
        private final List<HudElementHandle> elements = new ArrayList<>();
        private Map<String, HudElementState> states = new LinkedHashMap<>();
        private HudSettingsDocument lastSaved;

        public HudSubsystem(Path file) {
                this.file = file;
                load();
        }

        /**
         * Registers an element. Must happen during client init, before the
         * first frame; the Fabric HUD slot is attached on the first
         * registration, so a mod with no elements costs nothing.
         */
        public void register(HudElementSpec spec, HudRenderer renderer) {
                HudElementState state = states.get(spec.id().toString());
                if (state == null) {
                        state = HudElementState.ofDefaults(spec);
                }
                elements.add(new HudElementHandle(spec, renderer, state));
                if (elements.size() == 1) {
                        HudElementRegistry.addLast(FABRIC_ELEMENT_ID, this::extractFabric);
                }
        }

        /** The registered elements, in registration order. */
        public List<HudElementHandle> elements() {
                return List.copyOf(elements);
        }

        /** The live state of one element, or null when the id is unknown. */
        public HudElementState stateOf(Identifier id) {
                for (HudElementHandle handle : elements) {
                        if (handle.spec().id().equals(id)) {
                                return handle.state();
                        }
                }
                return null;
        }

        /** The resolved on-screen rectangle of one element right now. */
        public HudLayoutMath.Rect rectOf(HudElementHandle handle, int screenWidth, int screenHeight) {
                HudElementState state = handle.state();
                return HudLayoutMath.rectFor(state.anchor(), state.offsetX(), state.offsetY(),
                                state.scaledWidth(handle.spec()), state.scaledHeight(handle.spec()),
                                screenWidth, screenHeight);
        }

        /**
         * Renders every visible element — the Fabric HUD callback. The
         * per-element gating (config, server, world) belongs to each
         * renderer, which the manager also calls for its preview.
         */
        public void extractFabric(GuiGraphicsExtractor graphics, DeltaTracker delta) {
                Minecraft minecraft = Minecraft.getInstance();
                for (HudElementHandle handle : elements) {
                        if (!handle.state().visible()) {
                                continue;
                        }
                        HudLayoutMath.Rect rect = rectOf(handle, graphics.guiWidth(), graphics.guiHeight());
                        try {
                                handle.renderer().render(graphics, rect.x(), rect.y(),
                                                rect.width(), rect.height(), false);
                        } catch (Exception e) {
                                // One broken element must not take the whole HUD down;
                                // the failure is logged, not swallowed silently.
                                LOGGER.warn("HUD element {} failed to render: {}", handle.spec().id(), e.toString());
                        }
                }
        }

        /** Reloads the settings file (the manager screen re-reads after a hand edit). */
        public void load() {
                HudSettingsDocument document = json.load(file, HudSettingsDocument.class, HudSettingsDocument::new);
                document.schemaVersion = HudSettingsDocument.CURRENT_VERSION;
                lastSaved = document.copy();
                Map<String, HudElementState> next = new LinkedHashMap<>();
                for (HudElementHandle handle : elements) {
                        String id = handle.spec().id().toString();
                        HudElementState state = document.elements.get(id);
                        next.put(id, state == null ? HudElementState.ofDefaults(handle.spec()) : state);
                }
                // Preserve entries for elements that are not registered here
                // (a future feature's settings must survive a downgrade).
                for (var entry : document.elements.entrySet()) {
                        next.putIfAbsent(entry.getKey(), entry.getValue());
                }
                states = next;
                rebindStates();
        }

        /**
         * Persists the current states. Called by the manager screen on
         * close and after each change; safe to call repeatedly — the write
         * is atomic and coalesced by the callers' dirty checks.
         */
        public void save() {
                HudSettingsDocument document = HudSettingsDocument.merge(elements, lastSaved);
                // The merge starts from lastSaved's carried entries; overlay
                // the live states (deep copies — the snapshot must not alias
                // the live objects the dirty check reads) for every element.
                for (HudElementHandle handle : elements) {
                        document.elements.put(handle.spec().id().toString(),
                                        HudElementState.copyOf(handle.state()));
                }
                document.schemaVersion = HudSettingsDocument.CURRENT_VERSION;
                try {
                        json.save(file, document);
                        lastSaved = document.copy();
                } catch (IOException e) {
                        LOGGER.warn("Could not save HUD settings to {}: {}", file, e.toString());
                }
        }

        /** Whether anything differs from the last save — the coalescing check. */
        public boolean isDirty() {
                HudSettingsDocument document = HudSettingsDocument.merge(elements, null);
                for (HudElementHandle handle : elements) {
                        document.elements.put(handle.spec().id().toString(),
                                        HudElementState.copyOf(handle.state()));
                }
                return document.differsFrom(lastSaved);
        }

        /** Restores one element's factory look. */
        public void resetToDefaults(HudElementHandle handle) {
                HudElementState defaults = HudElementState.ofDefaults(handle.spec());
                HudElementState state = handle.state();
                state.visible(defaults.visible());
                state.anchor(defaults.anchor());
                state.offsetX(defaults.offsetX());
                state.offsetY(defaults.offsetY());
                state.scalePercent(defaults.scalePercent());
                save();
        }

        private void rebindStates() {
                for (HudElementHandle handle : elements) {
                        HudElementState state = states.get(handle.spec().id().toString());
                        if (state != null) {
                                handle.bind(state);
                        }
                }
        }

        /** Flushes pending settings on client shutdown. */
        public void shutdown() {
                if (isDirty()) {
                        save();
                }
        }
}
