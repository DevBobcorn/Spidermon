package io.devbobcorn.spidermon.client;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PackageSpidermonVisual extends AbstractBlockEntityVisual<PackageSpidermonBlockEntity> implements SimpleDynamicVisual {
	private TransformedInstance body;

	private final Matrix4f basePose = new Matrix4f();
	private float lastYaw = Float.NaN;
	private boolean lastGoggles = false;

	public PackageSpidermonVisual(VisualizationContext ctx, PackageSpidermonBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);

		body = ctx.instancerProvider()
			.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY))
			.createInstance();

		animate(partialTick);
	}

	@Override
	public void beginFrame(Context ctx) {
		animate(ctx.partialTick());
		Minecraft mc = Minecraft.getInstance();
		if (mc != null)
			PackageSpidermonTargetSelectionHandler.renderPackageSpidermonScreenChainParticles(mc, blockEntity,
				ctx.partialTick());
	}

	private void animate(float partialTicks) {
		updateGoggles();

		float yaw = blockEntity.getYaw();

		float headPitch = 80;
		float tonguePitch = 0;
		float tongueLength = 0;
		float headPitchModifier = 0;

		boolean hasTarget = blockEntity.target != null;

		Vec3 diff = Vec3.ZERO;

		if (hasTarget) {
			diff = blockEntity.getExactTargetLocation()
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

		if (yaw != lastYaw) {
			body.setIdentityTransform()
				.translate(getVisualPosition())
				.center()
				.rotateYDegrees(yaw)
				.uncenter()
				.setChanged();

			basePose.set(body.pose)
				.translate(8 / 16f, 10 / 16f, 11 / 16f);

			lastYaw = yaw;
		}
	}

	public void updateGoggles() {
		if (blockEntity.goggles && !lastGoggles) {
			body.delete();
			body = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_GOGGLES))
				.createInstance();
			updateLight(0);
			lastGoggles = true;
		}

		if (!blockEntity.goggles && lastGoggles) {
			body.delete();
			body = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY))
				.createInstance();
			updateLight(0);
			lastGoggles = false;
		}
	}

	@Override
	public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
		consumer.accept(body);
	}

	@Override
	public void updateLight(float partialTick) {
		relight(body);
	}

	@Override
	protected void _delete() {
		body.delete();
	}
}
