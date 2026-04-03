package io.devbobcorn.frogterminal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import io.devbobcorn.frogterminal.block.FrogTerminalBlockEntity;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FrogTerminalRenderer extends SmartBlockEntityRenderer<FrogTerminalBlockEntity> {

	public FrogTerminalRenderer(Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(FrogTerminalBlockEntity blockEntity, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		SuperByteBuffer body = CachedBuffers.partial(FrogTerminalPartialModels.FROG_TERMINAL_BODY, blockEntity.getBlockState());

		float yaw = blockEntity.getYaw();

		float headPitch = 80;
		float tonguePitch = 0;
		float tongueLength = 0;
		float headPitchModifier = 0;

		boolean hasTarget = blockEntity.target != null;

		if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
			return;
		}

		if (hasTarget) {
			Vec3 diff = blockEntity.getExactTargetLocation()
				.subtract(0, 0.75, 0)
				.subtract(Vec3.atCenterOf(blockEntity.getBlockPos()));
			tonguePitch = (float) Mth.atan2(diff.y, diff.multiply(1, 0, 1)
				.length() + (3 / 16f)) * Mth.RAD_TO_DEG;
			tongueLength = Math.max((float) diff.length(), 1);
			headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
		}

		tongueLength = 0;

		headPitch *= headPitchModifier;

		headPitch = Math.max(headPitch, blockEntity.manualOpenAnimationProgress.getValue(partialTicks) * 60);
		tongueLength = Math.max(tongueLength, blockEntity.manualOpenAnimationProgress.getValue(partialTicks) * 0.25f);

		body.center()
			.rotateYDegrees(yaw)
			.uncenter()
			.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

		SuperByteBuffer head = CachedBuffers.partial(
			blockEntity.goggles ? FrogTerminalPartialModels.FROG_TERMINAL_HEAD_GOGGLES : FrogTerminalPartialModels.FROG_TERMINAL_HEAD,
			blockEntity.getBlockState());

		head.center()
			.rotateYDegrees(yaw)
			.uncenter()
			.translate(8 / 16f, 10 / 16f, 11 / 16f)
			.rotateXDegrees(headPitch)
			.translateBack(8 / 16f, 10 / 16f, 11 / 16f);

		head.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

		SuperByteBuffer tongue = CachedBuffers.partial(FrogTerminalPartialModels.FROG_TERMINAL_TONGUE, blockEntity.getBlockState());

		tongue.center()
			.rotateYDegrees(yaw)
			.uncenter()
			.translate(8 / 16f, 10 / 16f, 11 / 16f)
			.rotateXDegrees(tonguePitch)
			.scale(1f, 1f, tongueLength / (7 / 16f))
			.translateBack(8 / 16f, 10 / 16f, 11 / 16f);

		tongue.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}
}
