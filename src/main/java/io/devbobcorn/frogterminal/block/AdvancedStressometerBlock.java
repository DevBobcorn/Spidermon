package io.devbobcorn.frogterminal.block;

import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.gauge.GaugeBlock;
import com.simibubi.create.content.kinetics.gauge.GaugeShaper;
import com.simibubi.create.foundation.block.IBE;

import io.devbobcorn.frogterminal.FrogTerminalMod;

import net.createmod.catnip.levelWrappers.WrappedLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AdvancedStressometerBlock extends DirectionalAxisKineticBlock implements IBE<AdvancedStressometerBlockEntity> {

    public static final GaugeShaper GAUGE = GaugeBlock.GAUGE;

    public AdvancedStressometerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level world = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos placedOnPos = context.getClickedPos()
            .relative(face.getOpposite());
        BlockState placedOnState = world.getBlockState(placedOnPos);
        Block block = placedOnState.getBlock();

        if (block instanceof IRotate iRotate && iRotate.hasShaftTowards(world, placedOnPos, placedOnState, face)) {
            BlockState toPlace = defaultBlockState();
            Direction horizontalFacing = context.getHorizontalDirection();
            Direction nearestLookingDirection = context.getNearestLookingDirection();
            boolean lookPositive = nearestLookingDirection.getAxisDirection() == AxisDirection.POSITIVE;
            if (face.getAxis() == Axis.X) {
                toPlace = toPlace.setValue(FACING, lookPositive ? Direction.NORTH : Direction.SOUTH)
                    .setValue(AXIS_ALONG_FIRST_COORDINATE, true);
            } else if (face.getAxis() == Axis.Y) {
                toPlace = toPlace.setValue(FACING, horizontalFacing.getOpposite())
                    .setValue(AXIS_ALONG_FIRST_COORDINATE, horizontalFacing.getAxis() == Axis.X);
            } else {
                toPlace = toPlace.setValue(FACING, lookPositive ? Direction.WEST : Direction.EAST)
                    .setValue(AXIS_ALONG_FIRST_COORDINATE, false);
            }
            return toPlace;
        }

        return super.getStateForPlacement(context);
    }

    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext context) {
        return context.getClickedFace();
    }

    @Override
    protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) {
        return context.getHorizontalDirection().getAxis() != Axis.X;
    }

    public boolean shouldRenderHeadOnFace(Level world, BlockPos pos, BlockState state, Direction face) {
        if (face.getAxis().isVertical())
            return false;
        if (face == state.getValue(FACING).getOpposite())
            return false;
        if (face.getAxis() == getRotationAxis(state))
            return false;
        if (getRotationAxis(state) == Axis.Y && face != state.getValue(FACING))
            return false;
        if (!Block.shouldRenderFace(state, world, pos, face, pos.relative(face)) && !(world instanceof WrappedLevel))
            return false;
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return GAUGE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<AdvancedStressometerBlockEntity> getBlockEntityClass() {
        return AdvancedStressometerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AdvancedStressometerBlockEntity> getBlockEntityType() {
        return FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get();
    }
}
