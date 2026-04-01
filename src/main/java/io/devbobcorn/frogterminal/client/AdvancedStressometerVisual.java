package io.devbobcorn.frogterminal.client;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.ShaftVisual;

import io.devbobcorn.frogterminal.block.AdvancedStressometerBlock;
import io.devbobcorn.frogterminal.block.AdvancedStressometerBlockEntity;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;

public class AdvancedStressometerVisual extends ShaftVisual<AdvancedStressometerBlockEntity>
        implements SimpleDynamicVisual {

    protected final ArrayList<HeadFace> faces = new ArrayList<>(2);
    protected PartialModel currentHeadModel;

    public AdvancedStressometerVisual(VisualizationContext context,
            AdvancedStressometerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        currentHeadModel = FrogTerminalPartialModels.getAdvancedStressometerHead(blockEntity.dialTarget);
        rebuildFaces();
    }

    protected void rebuildFaces() {
        faces.forEach(HeadFace::delete);
        faces.clear();

        AdvancedStressometerBlock block = (AdvancedStressometerBlock) blockState.getBlock();

        var headInstancer = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
                Models.partial(currentHeadModel));

        PoseStack ms = new PoseStack();
        var msr = TransformStack.of(ms);
        msr.translate(getVisualPosition());

        for (Direction facing : Iterate.directions) {
            if (!block.shouldRenderHeadOnFace(level, pos, blockState, facing))
                continue;

            HeadFace face = new HeadFace(facing, headInstancer.createInstance());
            faces.add(face);
            face.setupTransform(msr, ms);
        }

        relightFaces();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        PartialModel headModel = FrogTerminalPartialModels.getAdvancedStressometerHead(blockEntity.dialTarget);
        if (headModel != currentHeadModel) {
            currentHeadModel = headModel;
            rebuildFaces();
        }
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relightFaces();
    }

    protected void relightFaces() {
        relight(faces.stream()
                .map(f -> f.instance)
                .toArray(FlatLit[]::new));
    }

    @Override
    protected void _delete() {
        super._delete();
        faces.forEach(HeadFace::delete);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        for (HeadFace face : faces) {
            consumer.accept(face.instance);
        }
    }

    protected class HeadFace {
        final Direction face;
        final TransformedInstance instance;

        HeadFace(Direction face, TransformedInstance instance) {
            this.face = face;
            this.instance = instance;
        }

        void setupTransform(TransformStack<?> msr, PoseStack ms) {
            msr.pushPose();
            rotateToFace(msr);
            instance.setTransform(ms).setChanged();
            msr.popPose();
        }

        TransformStack<?> rotateToFace(TransformStack<?> msr) {
            return msr.center()
                    .rotate((float) ((-face.toYRot() - 90) / 180 * Math.PI), Direction.UP)
                    .uncenter();
        }

        void delete() {
            instance.delete();
        }
    }
}
