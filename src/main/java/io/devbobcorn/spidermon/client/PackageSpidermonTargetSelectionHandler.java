package io.devbobcorn.spidermon.client;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;
import io.devbobcorn.spidermon.network.SpidermonPlacementPacket;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

public class PackageSpidermonTargetSelectionHandler {

	/** Debug: ignore menu/animation gates and use full chain length (set {@code false} before release). */
	private static final boolean DEBUG_ALWAYS_RENDER_SCREEN_CHAIN = false;

	/** Visual for the chain segment (end rod along +Z in model space before stretch). */
	public static final BlockState CHAIN_SEGMENT_VISUAL_STATE = Blocks.END_ROD.defaultBlockState()
		.setValue(DirectionalBlock.FACING, Direction.SOUTH);

	@SuppressWarnings("deprecation")
	public static RenderType chainSegmentRenderType() {
		return ItemBlockRenderTypes.getRenderType(CHAIN_SEGMENT_VISUAL_STATE, false);
	}

	public static PackagePortTarget activePackageTarget;
	public static Vec3 exactPositionOfTarget;

	public static void flushSettings(BlockPos pos) {
		if (activePackageTarget == null) {
			Minecraft.getInstance().player.displayClientMessage(
				Component.translatable("create.gui.package_port.not_targeting_anything"), true);
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
	 * Transform from block min corner to stretched end-rod geometry. {@code source}, {@code target}, and
	 * {@code blockCornerMin} must use the same coordinate system (world, or Flywheel origin-shifted space).
	 *
	 * @return {@code false} if nothing should be drawn (zero length segment).
	 */
	public static boolean chainSegmentLocalMatrix(Matrix4f out, Vec3 blockCornerMin, Vec3 source, Vec3 target,
		float extent) {
		Vec3 end = source.lerp(target, extent);
		Vec3 relSource = source.subtract(blockCornerMin);
		Vec3 relEnd = end.subtract(blockCornerMin);
		Vec3 dir = relEnd.subtract(relSource);
		double len = dir.length();
		if (len < 1e-4)
			return false;
		dir = dir.scale(1.0 / len);

		Vec3 relMid = new Vec3(
			(relSource.x + relEnd.x) * 0.5,
			(relSource.y + relEnd.y) * 0.5,
			(relSource.z + relEnd.z) * 0.5);

		var forward = new Vector3f(0f, 0f, 1f);
		var along = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
		Quaternionf align = new Quaternionf().rotationTo(forward, along);

		out.identity()
			.translate((float) relMid.x, (float) relMid.y, (float) relMid.z)
			.rotate(align)
			.translate(-0.5f, -0.5f, 0f)
			.scale(1f, 1f, (float) len)
			.translate(0f, 0f, -0.5f);
		return true;
	}

	/**
	 * Renders the chain mesh from the block entity renderer. {@code originOffset} must match the block min corner
	 * in the same space as world positions (typically {@link Vec3#atLowerCornerOf(BlockPos)}).
	 */
	public static void renderPackageSpidermonScreenChainMesh(Minecraft mc, PackageSpidermonBlockEntity be,
		float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, Vec3 originOffset) {
		if (!shouldRenderPackageSpidermonScreenChain(be, partialTick))
			return;

		BlockPos pos = be.getBlockPos();
		Vec3 source = Vec3.atBottomCenterOf(pos);
		Vec3 target = be.getExactTargetLocation();
		float extent = screenChainExtent(be, partialTick);

		Matrix4f local = new Matrix4f();
		if (!chainSegmentLocalMatrix(local, originOffset, source, target, extent))
			return;

		poseStack.pushPose();
		poseStack.last()
			.pose()
			.mul(local);
		mc.getBlockRenderer()
			.renderSingleBlock(CHAIN_SEGMENT_VISUAL_STATE, poseStack, buffer, light, overlay, ModelData.EMPTY,
				chainSegmentRenderType());
		poseStack.popPose();
	}

	public static boolean shouldRenderPackageSpidermonScreenChain(PackageSpidermonBlockEntity be, float partialTick) {
		if (be == null || be.getLevel() == null || !be.getLevel().isClientSide())
			return false;
		if (be.target == null)
			return false;
		if (be.getExactTargetLocation().lengthSqr() < 1e-8)
			return false;
		if (DEBUG_ALWAYS_RENDER_SCREEN_CHAIN)
			return true;
		float extent = be.manualOpenAnimationProgress.getValue(partialTick);
		return be.getMenuOpenCount() > 0 || extent > 1e-4f;
	}

	static float screenChainExtent(PackageSpidermonBlockEntity be, float partialTick) {
		if (DEBUG_ALWAYS_RENDER_SCREEN_CHAIN)
			return 1f;
		return be.manualOpenAnimationProgress.getValue(partialTick);
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
