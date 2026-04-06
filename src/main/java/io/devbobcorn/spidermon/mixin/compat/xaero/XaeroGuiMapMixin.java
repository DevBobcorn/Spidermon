package io.devbobcorn.spidermon.mixin.compat.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.compat.xaero.XaeroChainMap;

import net.minecraft.client.gui.GuiGraphics;
import xaero.map.gui.GuiMap;

@Mixin(GuiMap.class)
public abstract class XaeroGuiMapMixin {
	@Unique
	boolean spidermon$failedToRender = false;

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), require = 0)
	private void spidermon$onRender(GuiGraphics graphics, int mouseX, int mouseY, float pt, CallbackInfo ci) {
		try {
			if (!spidermon$failedToRender)
				XaeroChainMap.onRender(graphics, (GuiMap) (Object) this, mouseX, mouseY, pt);
		} catch (Throwable e) {
			SpidermonMod.LOGGER.error("Failed to render chain map overlay on Xaero's World Map:", e);
			spidermon$failedToRender = true;
		}
	}
}
