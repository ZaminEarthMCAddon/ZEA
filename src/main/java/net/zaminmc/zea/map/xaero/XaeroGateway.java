package net.zaminmc.zea.map.xaero;

import net.minecraft.client.Minecraft;
import xaero.map.gui.GuiMap;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/**
 * The only class in ZEA that names Xaero's classes. Opening Xaero's world
 * map is one call into their own session — the same construction their
 * keybind performs, verified against Xaero 1.47.2 for 26.3:
 * {@code new GuiMap(null, null, session.getMapProcessor(), cameraEntity)}
 * on the client's gui.
 *
 * <p>The class is loaded only when Xaero's World Map is actually
 * installed: the caller checks {@code isModLoaded("xaeroworldmap")} before
 * the first reference, so a ZEA without Xaero never resolves these
 * classes. The compile-only dependency keeps ZEA's jar clean of Xaero at
 * runtime; Xaero's own Maven supplies the API surface at build time.
 */
public final class XaeroGateway {

        private XaeroGateway() {
        }

        /**
         * Opens Xaero's world map screen — ZEA's {@code openWorldMap}
         * delegates here when Xaero is present. The session's processor is
         * the live one Xaero's own open path uses; passing anything else
         * would race the session's world loading.
         */
        public static void openWorldMap(Minecraft client) {
                MapProcessor processor = WorldMapSession.getCurrentSession().getMapProcessor();
                client.gui.setScreen(new GuiMap(null, null, processor, client.getCameraEntity()));
        }
}
