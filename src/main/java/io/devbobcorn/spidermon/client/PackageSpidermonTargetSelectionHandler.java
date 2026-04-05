package io.devbobcorn.spidermon.client;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;
import io.devbobcorn.spidermon.network.SpidermonPlacementPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

public class PackageSpidermonTargetSelectionHandler {

	public static PackagePortTarget activePackageTarget;
	public static Vec3 exactPositionOfTarget;

	public static void flushSettings(BlockPos pos) {
		if (activePackageTarget == null) {
			Minecraft.getInstance().player.displayClientMessage(
				Component.translatable("create.package_port.not_targeting_anything"), true);
			return;
		}

		if (validateDiff(exactPositionOfTarget, pos) == null) {
			activePackageTarget.relativePos = activePackageTarget.relativePos.subtract(pos);
			PacketDistributor.sendToServer(new SpidermonPlacementPacket(activePackageTarget, pos));
		}

		activePackageTarget = null;
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		boolean isWrench = player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH);

		if (!isWrench) {
			if (activePackageTarget == null)
				return;
			if (!player.getMainHandItem().is(SpidermonMod.PACKAGE_SPIDERMON_ITEM.get()))
				return;
		}

		HitResult objectMouseOver = mc.hitResult;
		if (!(objectMouseOver instanceof BlockHitResult blockRayTraceResult))
			return;

		if (isWrench) {
			if (blockRayTraceResult.getType() == Type.MISS)
				return;
			BlockPos pos = blockRayTraceResult.getBlockPos();
			if (!mc.level.getBlockState(pos).is(SpidermonMod.PACKAGE_SPIDERMON_BLOCK.get()))
				return;
			if (!(mc.level.getBlockEntity(pos) instanceof PackageSpidermonBlockEntity be))
				return;
			if (be.target == null)
				return;
			Vec3 source = Vec3.atBottomCenterOf(pos);
			Vec3 target = be.target.getExactTargetLocation(null, mc.level, pos);
			if (target == Vec3.ZERO)
				return;
			Color color = new Color(0x9ede73);
			animateConnection(mc, source, target, color);
			Outliner.getInstance()
				.chaseAABB("ChainPointSelected", new AABB(target, target))
				.colored(color)
				.lineWidth(1 / 5f)
				.disableLineNormals();
			return;
		}

		Vec3 target = exactPositionOfTarget;
		if (blockRayTraceResult.getType() == Type.MISS) {
			Outliner.getInstance()
				.chaseAABB("ChainPointSelected", new AABB(target, target))
				.colored(0x9ede73)
				.lineWidth(1 / 5f)
				.disableLineNormals();
			return;
		}

		BlockPos pos = blockRayTraceResult.getBlockPos();
		if (!mc.level.getBlockState(pos)
			.canBeReplaced())
			pos = pos.relative(blockRayTraceResult.getDirection());

		String validateDiff = validateDiff(target, pos);
		boolean valid = validateDiff == null;
		Color color = new Color(valid ? 0x9ede73 : 0xff7171);
		Vec3 source = Vec3.atBottomCenterOf(pos);

		player.displayClientMessage(
			Component.translatable(valid
				? "create.package_port.valid"
				: validateDiff)
				.withColor(color.getRGB()),
			true);

		Outliner.getInstance()
			.chaseAABB("ChainPointSelected", new AABB(target, target))
			.colored(color)
			.lineWidth(1 / 5f)
			.disableLineNormals();

		if (!mc.level.getBlockState(pos)
			.canBeReplaced())
			return;

		Outliner.getInstance()
			.chaseAABB("TargetedFrogPos", new AABB(pos).contract(0, 1, 0)
				.deflate(0.125, 0, 0.125))
			.colored(color)
			.lineWidth(1 / 16f)
			.disableLineNormals();

		animateConnection(mc, source, target, color);
	}

	public static void animateConnection(Minecraft mc, Vec3 source, Vec3 target, Color color) {
		DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1);
		ClientLevel world = mc.level;
		double totalFlyingTicks = 10;
		int segments = (((int) totalFlyingTicks) / 3) + 1;
		double tickOffset = totalFlyingTicks / segments;

		for (int i = 0; i < segments; i++) {
			double ticks = ((AnimationTickHolder.getRenderTime() / 3) % tickOffset) + i * tickOffset;
			Vec3 vec = source.lerp(target, ticks / totalFlyingTicks);
			world.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
		}
	}

	/**
	 * While the Spidermon UI is open, draws a white dust particle string from the block to the chain
	 * point. Length follows {@link PackageSpidermonBlockEntity#manualOpenAnimationProgress} (same
	 * open/close timing as the block model).
	 */
	public static void renderPackageSpidermonScreenChainParticles(Minecraft mc,
		PackageSpidermonBlockEntity be, float partialTick) {
		if (be == null || mc.level == null)
			return;
		if (be.target == null)
			return;
		Vec3 source = Vec3.atBottomCenterOf(be.getBlockPos());
		Vec3 target = be.getExactTargetLocation();
		if (target == Vec3.ZERO)
			return;
		float extent = be.manualOpenAnimationProgress.getValue(partialTick);
		if (extent <= 1e-4f)
			return;
		Vec3 end = source.lerp(target, extent);
		animateConnection(mc, source, end, new Color(0xFFFFFF));
	}

	public static String validateDiff(Vec3 target, BlockPos placedPos) {
		Vec3 source = Vec3.atBottomCenterOf(placedPos);
		Vec3 diff = target.subtract(source);
		if (diff.y < 0)
			return "create.package_port.cannot_reach_down";
		if (diff.length() > AllConfigs.server().logistics.packagePortRange.get())
			return "create.package_port.too_far";
		return null;
	}
}
