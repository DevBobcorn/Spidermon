package io.devbobcorn.frogterminal.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FrogTerminalBlockEntity extends SmartBlockEntity implements MenuProvider {

	public PackagePortTarget target;

	public LerpedFloat manualOpenAnimationProgress;
	public LerpedFloat animationProgress;
	public LerpedFloat anticipationProgress;
	public ItemStack animatedPackage;
	public boolean currentlyDepositing;

	public boolean goggles;
	public float passiveYaw;
	public int openCount;

	private FrogTerminalSounds sounds;

	public FrogTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		sounds = new FrogTerminalSounds();
		animationProgress = LerpedFloat.linear();
		anticipationProgress = LerpedFloat.linear();
		manualOpenAnimationProgress = LerpedFloat.linear()
			.startWithValue(0)
			.chase(0, 0.35, Chaser.LINEAR);
		goggles = false;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public boolean isAnimationInProgress() {
		return false;
	}

	public Vec3 getExactTargetLocation() {
		if (target == null)
			return Vec3.ZERO;
		return target.getExactTargetLocation(null, level, worldPosition);
	}

	@Override
	public AABB getRenderBoundingBox() {
		AABB bb = super.getRenderBoundingBox().expandTowards(0, 1, 0);
		if (target != null) {
			Vec3 loc = getExactTargetLocation();
			if (loc != Vec3.ZERO)
				bb = bb.minmax(new AABB(BlockPos.containing(loc)))
					.inflate(0.5);
		}
		return bb;
	}

	@Override
	public void tick() {
		super.tick();

		manualOpenAnimationProgress.updateChaseTarget(openCount > 0 ? 1 : 0);
		boolean wasOpen = manualOpenAnimationProgress.getValue() > 0;

		manualOpenAnimationProgress.tickChaser();

		if (level.isClientSide() && wasOpen && manualOpenAnimationProgress.getValue() == 0)
			sounds.close(level, worldPosition);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (target != null)
			tag.put("Target", CatnipCodecUtils.encode(PackagePortTarget.CODEC, registries, target).orElseThrow());
		tag.putFloat("PlacedYaw", passiveYaw);
		if (goggles)
			tag.putBoolean("Goggles", true);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		PackagePortTarget prevTarget = target;
		target = CatnipCodecUtils.decodeOrNull(PackagePortTarget.CODEC, registries, tag.getCompound("Target"));
		passiveYaw = tag.getFloat("PlacedYaw");
		goggles = tag.getBoolean("Goggles");
		if (clientPacket && prevTarget != target)
			invalidateRenderBoundingBox();
	}

	public float getYaw() {
		if (target == null)
			return passiveYaw;
		Vec3 loc = getExactTargetLocation();
		if (loc == Vec3.ZERO)
			return passiveYaw;
		Vec3 diff = loc.subtract(Vec3.atCenterOf(worldPosition));
		return (float) (Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG) + 180;
	}

	public List<BlockPos> getConnectedChainConveyors() {
		if (target == null || level == null)
			return List.of();
		BlockPos startPos = worldPosition.offset(target.relativePos);
		if (!level.isLoaded(startPos))
			return List.of();
		if (!(level.getBlockEntity(startPos) instanceof ChainConveyorBlockEntity))
			return List.of();

		List<BlockPos> result = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();

		visited.add(startPos);
		queue.add(startPos);

		while (!queue.isEmpty() && result.size() < 64) {
			BlockPos current = queue.poll();
			result.add(current);

			if (level.getBlockEntity(current) instanceof ChainConveyorBlockEntity ccbe) {
				for (BlockPos offset : ccbe.connections) {
					BlockPos neighbor = current.offset(offset);
					if (!visited.contains(neighbor) && level.isLoaded(neighbor)) {
						visited.add(neighbor);
						queue.add(neighbor);
					}
				}
			}
		}

		return result;
	}

	public ItemInteractionResult use(Player player) {
		if (player == null || player.isCrouching())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		ItemStack mainHandItem = player.getMainHandItem();
		if (!goggles && AllItems.GOGGLES.isIn(mainHandItem)) {
			goggles = true;
			if (!level.isClientSide()) {
				notifyUpdate();
				level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
			}
			return ItemInteractionResult.SUCCESS;
		}

		if (level.isClientSide()) {
			sounds.open(level, worldPosition);
			return ItemInteractionResult.SUCCESS;
		}

		player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.frogterminal.frog_terminal");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new FrogTerminalMenu(containerId, playerInventory, this);
	}
}
