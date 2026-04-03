package io.devbobcorn.frogterminal.client;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;

import io.devbobcorn.frogterminal.FrogTerminalMod;
import io.devbobcorn.frogterminal.network.FrogTerminalPlacementPacket;

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
import net.neoforged.neoforge.network.PacketDistributor;

public class FrogTerminalTargetSelectionHandler {

	public static PackagePortTarget activePackageTarget;
	public static Vec3 exactPositionOfTarget;

	public static void flushSettings(BlockPos pos) {
		if (activePackageTarget == null) {
			Minecraft.getInstance().player.displayClientMessage(
				Component.translatable("frogterminal.frog_terminal.not_targeting_anything"), true);
			return;
		}

		if (validateDiff(exactPositionOfTarget, pos) == null) {
			activePackageTarget.relativePos = activePackageTarget.relativePos.subtract(pos);
			PacketDistributor.sendToServer(new FrogTerminalPlacementPacket(activePackageTarget, pos));
		}

		activePackageTarget = null;
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (activePackageTarget == null)
			return;
		if (!player.getMainHandItem().is(FrogTerminalMod.FROG_TERMINAL_ITEM.get()))
			return;

		HitResult objectMouseOver = mc.hitResult;
		if (!(objectMouseOver instanceof BlockHitResult blockRayTraceResult))
			return;

		Vec3 target = exactPositionOfTarget;
		if (blockRayTraceResult.getType() == Type.MISS) {
			Outliner.getInstance()
				.chaseAABB("FrogTerminalChainPointSelected", new AABB(target, target))
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
				? "frogterminal.frog_terminal.valid"
				: validateDiff)
				.withColor(color.getRGB()),
			true);

		Outliner.getInstance()
			.chaseAABB("FrogTerminalChainPointSelected", new AABB(target, target))
			.colored(color)
			.lineWidth(1 / 5f)
			.disableLineNormals();

		if (!mc.level.getBlockState(pos)
			.canBeReplaced())
			return;

		Outliner.getInstance()
			.chaseAABB("FrogTerminalTargetedPos", new AABB(pos).contract(0, 1, 0)
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

	public static String validateDiff(Vec3 target, BlockPos placedPos) {
		Vec3 source = Vec3.atBottomCenterOf(placedPos);
		Vec3 diff = target.subtract(source);
		if (diff.y < 0)
			return "frogterminal.frog_terminal.cannot_reach_down";
		if (diff.length() > AllConfigs.server().logistics.packagePortRange.get())
			return "frogterminal.frog_terminal.too_far";
		return null;
	}
}
