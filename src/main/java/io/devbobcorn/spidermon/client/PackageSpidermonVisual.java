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

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class PackageSpidermonVisual extends AbstractBlockEntityVisual<PackageSpidermonBlockEntity> implements SimpleDynamicVisual {
	private TransformedInstance body;
	private final TransformedInstance chain;

	private final Matrix4f basePose = new Matrix4f();
	private float lastYaw = Float.NaN;
	private boolean lastGoggles = false;

	public PackageSpidermonVisual(VisualizationContext ctx, PackageSpidermonBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);

		body = ctx.instancerProvider()
			.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY))
			.createInstance();

		chain = ctx.instancerProvider()
			.instancer(InstanceTypes.TRANSFORMED, Models.block(PackageSpidermonTargetSelectionHandler.CHAIN_SEGMENT_VISUAL_STATE))
			.createInstance();
		chain.handle()
			.setVisible(false);

		animate(partialTick);
	}

	@Override
	public void beginFrame(Context ctx) {
		animate(ctx.partialTick());
		updateChain(ctx.partialTick());
	}

	private void updateChain(float partialTick) {
		if (!PackageSpidermonTargetSelectionHandler.shouldRenderPackageSpidermonScreenChain(blockEntity, partialTick)) {
			chain.handle()
				.setVisible(false);
			return;
		}

		Vec3 ro = Vec3.atLowerCornerOf(new BlockPos(renderOrigin()));
		Vec3 source = Vec3.atBottomCenterOf(blockEntity.getBlockPos())
			.subtract(ro);
		Vec3 target = blockEntity.getExactTargetLocation()
			.subtract(ro);
		Vec3 corner = new Vec3(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
		float extent = PackageSpidermonTargetSelectionHandler.screenChainExtent(blockEntity, partialTick);

		Matrix4f local = new Matrix4f();
		if (!PackageSpidermonTargetSelectionHandler.chainSegmentLocalMatrix(local, corner, source, target, extent)) {
			chain.handle()
				.setVisible(false);
			return;
		}

		chain.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(local)
			.setChanged();
		chain.handle()
			.setVisible(true);
	}

	private void animate(float partialTicks) {
		updateGoggles();

		float yaw = blockEntity.getYaw();

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
				.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY_GOGGLES))
				.createInstance();
			updateLight(0);
			lastGoggles = true;
			// New instance has no transform; force animate() to reapply world position and rotation.
			lastYaw = Float.NaN;
		}

		if (!blockEntity.goggles && lastGoggles) {
			body.delete();
			body = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED, Models.partial(PackageSpidermonPartialModels.PACKAGE_SPIDERMON_BODY))
				.createInstance();
			updateLight(0);
			lastGoggles = false;
			lastYaw = Float.NaN;
		}
	}

	@Override
	public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
		consumer.accept(body);
	}

	@Override
	public void updateLight(float partialTick) {
		relight(body, chain);
	}

	@Override
	protected void _delete() {
		body.delete();
		chain.delete();
	}
}
