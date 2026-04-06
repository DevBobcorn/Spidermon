package io.devbobcorn.spidermon.compat.xaero;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Pixel-buffer backed overlay for the chain conveyor world-map layer.
 * Modeled after Create's {@code TrainMapRenderer}: all drawing is done
 * into tiled {@link NativeImage}s which are uploaded as
 * {@link DynamicTexture}s and rendered as textured quads, giving
 * proper per-pixel alpha compositing.
 */
public class ChainMapOverlay implements AutoCloseable {
	static final int TILE = 128;

	private final Long2ObjectMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
	private Tile cached;

	/** Clears every tile to transparent and marks them for stale-checking. */
	public void startDrawing() {
		cached = null;
		tiles.values().forEach(t -> {
			t.image().fillRect(0, 0, TILE, TILE, 0);
			t.untouched = true;
		});
	}

	/** Overwrites the pixel at world ({@code x}, {@code z}) with an ARGB color. */
	public void setPixel(int x, int z, int argb) {
		Tile tile = getOrCreate(x, z);
		tile.image().setPixelRGBA(
			Mth.positiveModulo(x, TILE),
			Mth.positiveModulo(z, TILE),
			argbToNative(argb));
	}

	/** Alpha-blends an ARGB color onto the pixel at world ({@code x}, {@code z}). */
	public void blendPixel(int x, int z, int argb) {
		Tile tile = getOrCreate(x, z);
		tile.image().blendPixel(
			Mth.positiveModulo(x, TILE),
			Mth.positiveModulo(z, TILE),
			argbToNative(argb));
	}

	/** Overwrites a rectangular region [{@code x0}..{@code x1}] x [{@code z0}..{@code z1}] (inclusive). */
	public void setRect(int x0, int z0, int x1, int z1, int argb) {
		int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
		int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				setPixel(x, z, argb);
	}

	/** Alpha-blends a rectangular region [{@code x0}..{@code x1}] x [{@code z0}..{@code z1}] (inclusive). */
	public void blendRect(int x0, int z0, int x1, int z1, int argb) {
		int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
		int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				blendPixel(x, z, argb);
	}

	/** Removes tiles that were not drawn to since the last {@link #startDrawing()} call. */
	public void finishDrawing() {
		cached = null;
		List<Long> stale = new ArrayList<>();
		tiles.forEach((key, t) -> {
			if (t.untouched) {
				t.close();
				stale.add(key);
			}
		});
		stale.forEach(key -> tiles.remove(key.longValue()));
	}

	/** Renders every tile as a textured quad in the current pose-stack context. */
	public void render(GuiGraphics graphics) {
		PoseStack pose = graphics.pose();
		MultiBufferSource.BufferSource buf = graphics.bufferSource();
		tiles.forEach((key, t) -> {
			pose.pushPose();
			pose.translate(unpackX(key) * TILE, unpackZ(key) * TILE, 0);
			t.draw(pose, buf);
			pose.popPose();
		});
	}

	@Override
	public void close() {
		tiles.values().forEach(Tile::close);
		tiles.clear();
	}

	// ── helpers ──────────────────────────────────────────────────────────

	private Tile getOrCreate(int x, int z) {
		long key = key(x, z);
		if (cached != null && cached.key == key)
			return cached;
		cached = tiles.computeIfAbsent(key, Tile::new);
		return cached;
	}

	private static long key(int x, int z) {
		return ((long) Math.floorDiv(x, TILE) << 32)
			| (Math.floorDiv(z, TILE) & 0xFFFFFFFFL);
	}

	private static int unpackX(long k) {
		return (int) (k >> 32);
	}

	private static int unpackZ(long k) {
		return (int) k;
	}

	/** Converts ARGB ({@code 0xAARRGGBB}) to the ABGR layout {@link NativeImage} expects. */
	static int argbToNative(int argb) {
		return (argb & 0xFF00FF00)
			| ((argb & 0x00FF0000) >>> 16)
			| ((argb & 0x000000FF) << 16);
	}

	// ── tile ─────────────────────────────────────────────────────────────

	private static class Tile implements AutoCloseable {
		final long key;
		private final DynamicTexture texture;
		private final RenderType renderType;
		private boolean dirty;
		boolean untouched;

		Tile(long key) {
			this.key = key;
			this.dirty = true;
			this.texture = new DynamicTexture(TILE, TILE, true);
			TextureManager tm = Minecraft.getInstance().getTextureManager();
			ResourceLocation loc = tm.register(
				"spidermon_chainmap/" + unpackX(key) + "_" + unpackZ(key), texture);
			this.renderType = RenderType.text(loc);
		}

		NativeImage image() {
			untouched = false;
			dirty = true;
			return texture.getPixels();
		}

		void draw(PoseStack pose, MultiBufferSource buf) {
			if (texture.getPixels() == null)
				return;
			if (dirty) {
				texture.upload();
				dirty = false;
			}
			Matrix4f m = pose.last().pose();
			VertexConsumer vc = buf.getBuffer(renderType);
			int light = LightTexture.FULL_BRIGHT;
			vc.addVertex(m, 0, TILE, 0).setColor(255, 255, 255, 255).setUv(0, 1).setLight(light);
			vc.addVertex(m, TILE, TILE, 0).setColor(255, 255, 255, 255).setUv(1, 1).setLight(light);
			vc.addVertex(m, TILE, 0, 0).setColor(255, 255, 255, 255).setUv(1, 0).setLight(light);
			vc.addVertex(m, 0, 0, 0).setColor(255, 255, 255, 255).setUv(0, 0).setLight(light);
		}

		@Override
		public void close() {
			texture.close();
		}
	}
}
