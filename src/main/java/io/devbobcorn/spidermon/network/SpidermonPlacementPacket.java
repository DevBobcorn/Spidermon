package io.devbobcorn.spidermon.network;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;
import io.devbobcorn.spidermon.client.PackageSpidermonTargetSelectionHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpidermonPlacementPacket(PackagePortTarget target, BlockPos pos) implements CustomPacketPayload {
	public static final Type<SpidermonPlacementPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(SpidermonMod.MODID, "place_package_spidermon"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpidermonPlacementPacket> STREAM_CODEC =
		StreamCodec.composite(
			PackagePortTarget.STREAM_CODEC, SpidermonPlacementPacket::target,
			BlockPos.STREAM_CODEC, SpidermonPlacementPacket::pos,
			SpidermonPlacementPacket::new
		);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SpidermonPlacementPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			Level world = player.level();
			if (!world.isLoaded(packet.pos()))
				return;
			BlockEntity blockEntity = world.getBlockEntity(packet.pos());
			if (!(blockEntity instanceof PackageSpidermonBlockEntity ftbe))
				return;

			Vec3 targetLocation = packet.target().getExactTargetLocation(null, world, packet.pos());
			if (targetLocation == Vec3.ZERO || !targetLocation.closerThan(Vec3.atBottomCenterOf(packet.pos()),
				AllConfigs.server().logistics.packagePortRange.get() + 2))
				return;

			packet.target().setup(null, world, packet.pos());
			ftbe.target = packet.target();
			ftbe.notifyUpdate();
		});
	}

	public record ClientBoundRequest(BlockPos pos) implements CustomPacketPayload {
		public static final Type<ClientBoundRequest> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(SpidermonMod.MODID, "flush_package_spidermon"));

		public static final StreamCodec<ByteBuf, ClientBoundRequest> STREAM_CODEC =
			BlockPos.STREAM_CODEC.map(ClientBoundRequest::new, ClientBoundRequest::pos);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(ClientBoundRequest packet, IPayloadContext context) {
			context.enqueueWork(() -> PackageSpidermonTargetSelectionHandler.flushSettings(packet.pos()));
		}
	}
}
