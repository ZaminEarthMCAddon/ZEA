package net.zaminmc.zea.map.render.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.TileId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The render-side half of the tile cache's ownership contract: one GPU
 * texture per uploaded tile, keyed by tile id. This class is exactly
 * the two roles the pure pipeline hands Minecraft work to — the
 * {@link TileUploads.Sink} that turns a claimed image into a drawable
 * texture, and the {@link TileCache.Disposer} that frees what the cache
 * retired — so production wiring is
 * {@code uploads.run(cache, textures, moving, clock)} and
 * {@code TileUploads.disposeRetired(cache, textures)} with nothing
 * between.
 *
 * <p><b>Ownership.</b> Vanilla's {@code DynamicTexture} is the pattern:
 * create the texture with usage copy-dst | texture-binding in RGBA8,
 * take a view, push the pixels with
 * {@code CommandEncoder.writeToTexture}, and close the texture before
 * the view. ZEA's tiles differ in one respect that shapes everything
 * here: a decoded tile is a full megabyte of pixel memory, so the CPU
 * image is closed the moment its pixels reach the GPU. The cache keeps
 * the now-empty image as its drawable identity, and the second
 * {@code close} when the cache retires that husk is the guarded no-op
 * {@code NativeImage} already is. GPU handles never outlive the cache
 * entry: every path that retires an entry funnels through the disposal
 * drain, and a claimed entry cannot be evicted underneath an in-flight
 * upload.
 *
 * <p><b>Failure is refusal, never death.</b> A rejected upload — a null
 * texture from a GL error, a rejected allocation, an image that is not
 * a tile-shaped rectangle — returns false, and the tile retries on the
 * cache's failed ladder instead of wedging the queue. The edge cap is
 * the last line of a poisoned-mirror defense: PNG compresses solid
 * colour to almost nothing, so a hostile host can hide a 8192-square
 * texture inside a few kilobytes, and the upload — not the decode — is
 * where that would surface.
 *
 * <p>Render thread only, like everything that touches the device.
 */
public final class TileTextures implements TileUploads.Sink<NativeImage>, TileCache.Disposer<NativeImage> {

	/** Largest tile edge accepted for upload; the pyramid publishes 512. */
	public static final int MAX_UPLOAD_EDGE = 1024;

	private final Map<TileId, Handle> handles = new HashMap<>();

	private record Handle(GpuTexture texture, GpuTextureView view) {
	}

	@Override
	public boolean upload(TileId id, NativeImage image) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(image, "image");
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0 || width > MAX_UPLOAD_EDGE || height > MAX_UPLOAD_EDGE) {
			return false;
		}
		GpuDevice device = RenderSystem.getDevice();
		GpuTexture texture = null;
		GpuTextureView view = null;
		try {
			texture = device.createTexture("zea map tile " + id.texturePath(),
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					GpuFormat.RGBA8_UNORM, width, height, 1, 1);
			if (texture == null) {
				return false;
			}
			device.createCommandEncoder().writeToTexture(texture, image);
			view = device.createTextureView(texture);
		} catch (RuntimeException failure) {
			if (texture != null) {
				texture.close();
			}
			if (view != null) {
				view.close();
			}
			return false;
		}
		// The GPU copy owns the pixels from here: closing the CPU image
		// now returns its megabyte instead of holding it until the cache
		// retires the entry, and that later disposal of the emptied
		// husk is a guarded no-op.
		image.close();
		free(id);
		handles.put(id, new Handle(texture, view));
		return true;
	}

	@Override
	public void dispose(TileId id, NativeImage image) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(image, "image");
		free(id);
		image.close();
	}

	/**
	 * The drawable view of an uploaded tile, or null while the tile has
	 * no GPU copy. Render thread only.
	 */
	public GpuTextureView viewOf(TileId id) {
		Handle handle = handles.get(Objects.requireNonNull(id, "id"));
		return handle == null ? null : handle.view();
	}

	/** Frees every handle — shutdown, and full cache clears. */
	public void freeAll() {
		for (Handle handle : handles.values()) {
			handle.texture().close();
			handle.view().close();
		}
		handles.clear();
	}

	/**
	 * The shared tile sampler: edge-clamped so a parent-fallback region
	 * never filters in the texels of the tile's opposite side, linear so
	 * the massively minified overview layer stays smooth instead of
	 * shimmering. Owned by the sampler cache; never closed here.
	 */
	public static GpuSampler tileSampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	private void free(TileId id) {
		Handle handle = handles.remove(id);
		if (handle != null) {
			handle.texture().close();
			handle.view().close();
		}
	}
}
