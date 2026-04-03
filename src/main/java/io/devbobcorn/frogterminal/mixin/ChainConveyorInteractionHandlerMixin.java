package io.devbobcorn.frogterminal.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;

import io.devbobcorn.frogterminal.FrogTerminalMod;
import io.devbobcorn.frogterminal.client.FrogTerminalTargetSelectionHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChainConveyorInteractionHandler.class, remap = false)
public class ChainConveyorInteractionHandlerMixin {

	@Shadow
	public static BlockPos selectedLift;
	@Shadow
	public static float selectedChainPosition;
	@Shadow
	public static BlockPos selectedConnection;
	@Shadow
	public static Vec3 selectedBakedPosition;

	@Inject(method = "isActive", at = @At("RETURN"), cancellable = true)
	private static void frogterminal$isActive(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getMainHandItem().is(FrogTerminalMod.FROG_TERMINAL_ITEM.get()))
			cir.setReturnValue(true);
	}

	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private static void frogterminal$onUse(CallbackInfoReturnable<Boolean> cir) {
		if (selectedLift == null)
			return;
		Minecraft mc = Minecraft.getInstance();
		ItemStack mainHandItem = mc.player.getMainHandItem();
		if (mainHandItem.is(FrogTerminalMod.FROG_TERMINAL_ITEM.get())) {
			FrogTerminalTargetSelectionHandler.exactPositionOfTarget = selectedBakedPosition;
			FrogTerminalTargetSelectionHandler.activePackageTarget =
				new PackagePortTarget.ChainConveyorFrogportTarget(
					selectedLift, selectedChainPosition, selectedConnection, false);
			cir.setReturnValue(true);
		}
	}
}
