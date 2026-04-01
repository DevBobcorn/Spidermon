package io.devbobcorn.frogterminal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;

import io.devbobcorn.frogterminal.block.AdvancedStressometerBlock;
import io.devbobcorn.frogterminal.block.AdvancedStressometerBlockEntity;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class AdvancedStressometerRenderer extends ShaftRenderer<AdvancedStressometerBlockEntity> {

    public AdvancedStressometerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AdvancedStressometerBlockEntity be, float partialTicks, PoseStack ms,
        MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState blockState = be.getBlockState();
        SuperByteBuffer headBuffer =
            CachedBuffers.partial(FrogTerminalPartialModels.getAdvancedStressometerHead(be.dialTarget), blockState);

        for (Direction facing : Iterate.directions) {
            if (!((AdvancedStressometerBlock) blockState.getBlock())
                .shouldRenderHeadOnFace(be.getLevel(), be.getBlockPos(), blockState, facing))
                continue;

            VertexConsumer vb = buffer.getBuffer(RenderType.solid());
            rotateBufferTowards(headBuffer, facing)
                .light(light)
                .renderInto(ms, vb);
        }
    }

    protected SuperByteBuffer rotateBufferTowards(SuperByteBuffer buffer, Direction target) {
        return buffer.rotateCentered((float) ((-target.toYRot() - 90) / 180 * Math.PI), Direction.UP);
    }
}
