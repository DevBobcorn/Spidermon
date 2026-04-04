package io.devbobcorn.spidermon.block;

import io.devbobcorn.spidermon.network.SpidermonPlacementPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class PackageSpidermonItem extends BlockItem {

	public PackageSpidermonItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, Player player, ItemStack stack,
		BlockState state) {
		if (!world.isClientSide && player instanceof ServerPlayer sp)
			PacketDistributor.sendToPlayer(sp, new SpidermonPlacementPacket.ClientBoundRequest(pos));
		return super.updateCustomBlockEntityTag(pos, world, player, stack, state);
	}
}
