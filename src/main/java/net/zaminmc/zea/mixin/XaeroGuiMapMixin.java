package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.zaminmc.zea.map.xaero.XaeroWorldLayer;

/**
 * Draws ZEA's EarthMC layer inside Xaero's world map — the xeroworld
 * integration. Xaero's GuiMap stays the whole engine (terrain, camera,
 * zoom, waypoints, chrome); ZEA stops owning a map screen and instead
 * renders the one thing only ZEA has — the live claim and player layer
 * from the squaremap feed — over Xaero's terrain, at Xaero's camera.
 *
 * <p>The injection point is Xaero's own player-arrow pass inside
 * {@code extractRenderState}: everything ZEA draws goes through
 * GuiGraphicsExtractor, which records into the render state and composites
 * after the whole screen render, so the layer covers Xaero's terrain and
 * its map elements alike regardless of where within the method the inject
 * sits — the ARROW read is the same anchor the EarthMC Map Addon verified
 * against Xaero 26.3.
 *
 * <p>Coordinates come straight from Xaero's camera fields: its
 * {@code scale}/{@code screenScale} pair expresses GUI pixels per world
 * block ({@code scale / screenScale}), which is exactly the single zoom
 * scalar ZEA's flat {@code MapProjection} takes — so the layer and the
 * terrain agree by construction, at every zoom, without any projection
 * code of ZEA's own.
 *
 * <p>Soft by design: the config is {@code required=false} and every
 * inject and shadow carries {@code require=0} semantics, so a Xaero
 * version that changed its internals simply loses the ZEA layer instead
 * of crashing the game; without Xaero installed the mixin never applies
 * at all. All Xaero-typed references stay out of this class — the target
 * and the field anchor are strings, the shadows are primitives — so it
 * compiles without Xaero on any classpath and cannot load a missing
 * class by itself.
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroGuiMapMixin {

        @Shadow(remap = false)
        private double cameraX;

        @Shadow(remap = false)
        private double cameraZ;

        @Shadow(remap = false)
        private double scale;

        @Shadow(remap = false)
        private double screenScale;

        /**
         * Draws the EarthMC claim and player layer over Xaero's terrain,
         * anchored to Xaero's live camera.
         */
        @Inject(require = 0,
                        method = "extractRenderState",
                        at = @At(
                                        value = "FIELD",
                                        target = "Lxaero/map/common/config/option/WorldMapProfiledConfigOptions;"
                                                        + "ARROW:Lxaero/lib/common/config/option/BooleanConfigOption;",
                                        opcode = Opcodes.GETSTATIC,
                                        shift = At.Shift.BEFORE
                        ),
                        remap = false
        )
        private void zea$drawEarthMcLayer(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                          float delta, CallbackInfo ci) {
                XaeroWorldLayer.draw(ctx, cameraX, cameraZ, scale, screenScale);
        }
}
