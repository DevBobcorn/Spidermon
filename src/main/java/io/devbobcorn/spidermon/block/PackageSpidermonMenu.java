package io.devbobcorn.spidermon.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.animatedContainer.AnimatedContainerBehaviour;
import com.simibubi.create.foundation.gui.menu.MenuBase;

import io.devbobcorn.spidermon.SpidermonMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class PackageSpidermonMenu extends MenuBase<PackageSpidermonBlockEntity> {

	public static PackageSpidermonMenu create(int containerId, Inventory playerInventory, PackageSpidermonBlockEntity be) {
		return new PackageSpidermonMenu(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), containerId, playerInventory, be);
	}

	public PackageSpidermonMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		super(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), containerId, playerInventory, buf);
	}

	public PackageSpidermonMenu(MenuType<?> type, int containerId, Inventory playerInventory, PackageSpidermonBlockEntity be) {
		super(type, containerId, playerInventory, be);
		BlockEntityBehaviour.get(be, AnimatedContainerBehaviour.TYPE).startOpen(player);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	protected PackageSpidermonBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
		BlockPos pos = extraData.readBlockPos();
		ClientLevel world = Minecraft.getInstance().level;
		if (world == null)
			return null;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity instanceof PackageSpidermonBlockEntity be ? be : null;
	}

	@Override
	protected void initAndReadInventory(PackageSpidermonBlockEntity contentHolder) {
	}

	@Override
	protected void addSlots() {
	}

	@Override
	protected void saveData(PackageSpidermonBlockEntity contentHolder) {
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
		if (!playerIn.level().isClientSide() && contentHolder != null)
			BlockEntityBehaviour.get(contentHolder, AnimatedContainerBehaviour.TYPE).stopOpen(playerIn);
	}

	@Override
	public boolean stillValid(Player player) {
		if (contentHolder == null)
			return false;
		return stillValid(
			ContainerLevelAccess.create(contentHolder.getLevel(), contentHolder.getBlockPos()),
			player, SpidermonMod.PACKAGE_SPIDERMON_BLOCK.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}
}
