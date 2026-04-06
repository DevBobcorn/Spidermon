package io.devbobcorn.spidermon.client;

import io.devbobcorn.spidermon.SpidermonMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture atlas regions for Spidermon GUI elements, modeled after Create's
 * {@code AllGuiTextures}. All sprites live in a single 256x256 sheet at
 * {@code textures/gui/widgets.png}.
 *
 * <h3>Required texture layout ({@code assets/spidermon/textures/gui/widgets.png}):</h3>
 * <pre>
 * (0, 0)  33x14  — Toggle panel background (dark rounded rect with chain icon on left)
 * (0, 14) 12x7   — Toggle ON indicator (e.g. green slider knob)
 * (0, 21) 12x7   — Toggle OFF indicator (e.g. gray/red slider knob)
 * </pre>
 */
public enum SpidermonGuiTextures {
	CHAINMAP_TOGGLE_PANEL("widgets", 4, 4, 33, 14),
	CHAINMAP_TOGGLE_ON("widgets", 4, 19, 12, 7),
	CHAINMAP_TOGGLE_OFF("widgets", 4, 27, 12, 7);

	public final ResourceLocation location;
	private final int startX;
	private final int startY;
	private final int width;
	private final int height;

	SpidermonGuiTextures(String path, int startX, int startY, int width, int height) {
		this.location = ResourceLocation.fromNamespaceAndPath(SpidermonMod.MODID, "textures/gui/" + path + ".png");
		this.startX = startX;
		this.startY = startY;
		this.width = width;
		this.height = height;
	}

	public void render(GuiGraphics graphics, int x, int y) {
		graphics.blit(location, x, y, startX, startY, width, height);
	}

	public void bind() {
		com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, location);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
