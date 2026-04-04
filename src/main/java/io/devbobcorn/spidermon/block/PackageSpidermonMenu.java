package io.devbobcorn.spidermon.block;

import io.devbobcorn.spidermon.SpidermonMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class PackageSpidermonMenu extends AbstractContainerMenu {

	public final PackageSpidermonBlockEntity blockEntity;

	public PackageSpidermonMenu(int containerId, Inventory playerInventory, PackageSpidermonBlockEntity be) {
		super(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), containerId);
		this.blockEntity = be;
		if (be != null)
			be.openCount++;
	}

	public PackageSpidermonMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		super(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), containerId);
		BlockPos pos = buf.readBlockPos();
		this.blockEntity = playerInventory.player.level().getBlockEntity(pos) instanceof PackageSpidermonBlockEntity ftbe
			? ftbe : null;
		if (this.blockEntity != null)
			this.blockEntity.openCount++;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (blockEntity != null)
			blockEntity.openCount--;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (blockEntity == null)
			return false;
		return stillValid(
			ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
			player, SpidermonMod.PACKAGE_SPIDERMON_BLOCK.get());
	}
}
