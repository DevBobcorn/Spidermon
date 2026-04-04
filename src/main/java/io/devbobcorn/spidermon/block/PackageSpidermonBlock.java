package io.devbobcorn.spidermon.block;

import net.minecraft.world.ItemInteractionResult;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import io.devbobcorn.spidermon.SpidermonMod;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PackageSpidermonBlock extends Block implements IBE<PackageSpidermonBlockEntity>, IWrenchable {

	public PackageSpidermonBlock(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return AllShapes.PACKAGE_PORT;
	}

	@Override
	public @Nullable PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.NORMAL;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		if (pPlacer == null)
			return;
		withBlockEntityDo(pLevel, pPos, be -> {
			Vec3 diff = VecHelper.getCenterOf(pPos)
				.subtract(pPlacer.position());
			be.passiveYaw = (float) (Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG);
			be.passiveYaw = Math.round(be.passiveYaw / 11.25f) * 11.25f;
			be.notifyUpdate();
		});
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hitResult) {
		return onBlockEntityUseItemOn(level, pos, be -> be.use(player));
	}

	@Override
	public Class<PackageSpidermonBlockEntity> getBlockEntityClass() {
		return PackageSpidermonBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PackageSpidermonBlockEntity> getBlockEntityType() {
		return SpidermonMod.SPIDERMON_BE.get();
	}

	@Override
	public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
		IBE.onRemove(pState, pLevel, pPos, pNewState);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}
}
