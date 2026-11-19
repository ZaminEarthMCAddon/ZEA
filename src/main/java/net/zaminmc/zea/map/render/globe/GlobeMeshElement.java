package net.zaminmc.zea.map.render.globe;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The curved surface's GPU submission: one {@link GuiElementRenderState}
 * carrying the planner's quads — positions already in absolute screen
 * pixels, four vertices per quad in the blit contract's order (TL, BL,
 * BR, TR) through the identity pose, so the stock GUI pipelines draw
 * them exactly like any vanilla fill or blit.
 *
 * <p>Two flavors share the class: colored quads (the water disc and the
 * world plate, {@link RenderPipelines#GUI}, no texture) and textured
 * quads (the imagery cells, {@link RenderPipelines#GUI_TEXTURED}, one
 * texture setup per tile). Because the GUI renderer sorts elements by
 * scissor, then pipeline, then texture setup, the colored layer draws
 * first and the imagery over it without any z fighting — the same
 * guarantee the flat path gets from draw order.
 *
 * <p>Render thread only, like everything that touches the render state.
 */
public final class GlobeMeshElement implements GuiElementRenderState {

        private static final org.joml.Matrix3x2f IDENTITY = new org.joml.Matrix3x2f();

        /**
         * One quad: four screen corners plus the texture region they
         * sample. Corner order matches the blit contract: TL, BL, BR, TR.
         */
        public record Quad(double[] xs, double[] ys, double u0, double v0, double u1, double v1,
                        int argb) {
        }

        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final List<Quad> quads;
        @Nullable
        private final ScreenRectangle scissorArea;
        @Nullable
        private final ScreenRectangle bounds;

        private GlobeMeshElement(RenderPipeline pipeline, TextureSetup textureSetup,
                        List<Quad> quads, @Nullable ScreenRectangle scissorArea) {
                this.pipeline = pipeline;
                this.textureSetup = textureSetup;
                this.quads = List.copyOf(quads);
                this.scissorArea = scissorArea;
                this.bounds = computeBounds(quads, scissorArea);
        }

        /** The colored layer: the water disc and the world plate. */
        public static GlobeMeshElement colored(List<Quad> quads,
                        @Nullable ScreenRectangle scissorArea) {
                return new GlobeMeshElement(RenderPipelines.GUI, TextureSetup.noTexture(),
                                                quads, scissorArea);
        }

        /** The imagery layer: one element per tile texture group. */
        public static GlobeMeshElement textured(List<Quad> quads, TextureSetup setup,
                        @Nullable ScreenRectangle scissorArea) {
                return new GlobeMeshElement(RenderPipelines.GUI_TEXTURED, setup,
                                                quads, scissorArea);
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
                for (Quad quad : quads) {
                        vertex(vertexConsumer, quad.xs()[0], quad.ys()[0], quad.u0(), quad.v0(), quad.argb());
                        vertex(vertexConsumer, quad.xs()[1], quad.ys()[1], quad.u0(), quad.v1(), quad.argb());
                        vertex(vertexConsumer, quad.xs()[2], quad.ys()[2], quad.u1(), quad.v1(), quad.argb());
                        vertex(vertexConsumer, quad.xs()[3], quad.ys()[3], quad.u1(), quad.v0(), quad.argb());
                }
        }

        private static void vertex(VertexConsumer consumer, double x, double y,
                        double u, double v, int argb) {
                consumer.addVertexWith2DPose(IDENTITY, (float) x, (float) y)
                                                .setUv((float) u, (float) v)
                                                .setColor(argb);
        }

        @Override
        public RenderPipeline pipeline() {
                return pipeline;
        }

        @Override
        public TextureSetup textureSetup() {
                return textureSetup;
        }

        @Nullable
        @Override
        public ScreenRectangle scissorArea() {
                return scissorArea;
        }

        @Nullable
        @Override
        public ScreenRectangle bounds() {
                return bounds;
        }

        /** The quads' bounding box, clipped by the scissor — vanilla's rule. */
        @Nullable
        private static ScreenRectangle computeBounds(List<Quad> quads,
                        @Nullable ScreenRectangle scissorArea) {
                if (quads.isEmpty()) {
                        return null;
                }
                double minX = Double.MAX_VALUE;
                double minY = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE;
                for (Quad quad : quads) {
                        for (int i = 0; i < 4; i++) {
                                minX = Math.min(minX, quad.xs()[i]);
                                minY = Math.min(minY, quad.ys()[i]);
                                maxX = Math.max(maxX, quad.xs()[i]);
                                maxY = Math.max(maxY, quad.ys()[i]);
                        }
                }
                ScreenRectangle box = new ScreenRectangle(
                                                (int) Math.floor(minX), (int) Math.floor(minY),
                                                (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
                return scissorArea != null ? scissorArea.intersection(box) : box;
        }
}
