package io.devbobcorn.spidermon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class PackageSpidermonRenderer extends SmartBlockEntityRenderer<PackageSpidermonBlockEntity> {

	public PackageSpidermonRenderer(Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(PackageSpidermonBlockEntity blockEntity, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		SuperByteBuffer body = CachedBuffers.partial(
			blockEntity.goggles ? PackageSpidermonPartialModels.PACKAGE_SPIDERMON_GOGGLES : PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY,
			blockEntity.getBlockState());

		float yaw = blockEntity.getYaw();

		if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
			return;
		}

		body.center()
			.rotateYDegrees(yaw)
			.uncenter()
			.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

		PackageSpidermonTargetSelectionHandler.renderPackageSpidermonScreenChainParticles(Minecraft.getInstance(),
			blockEntity, partialTicks);
	}
}
