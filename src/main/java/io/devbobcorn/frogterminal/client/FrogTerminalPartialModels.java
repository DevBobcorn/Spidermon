package io.devbobcorn.frogterminal.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.devbobcorn.frogterminal.FrogTerminalMod;
import net.minecraft.resources.ResourceLocation;

public class FrogTerminalPartialModels {

    public static final PartialModel ADV_STRESSOMETER_HEAD_SMILE =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/advanced_stressometer/head_smile"));

    public static final PartialModel ADV_STRESSOMETER_HEAD_SLANT =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/advanced_stressometer/head_slant"));

    public static final PartialModel ADV_STRESSOMETER_HEAD_FROWN =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/advanced_stressometer/head_frown"));

    public static PartialModel getAdvancedStressometerHead(float stressLevel) {
        if (stressLevel <= 0.5f)
            return ADV_STRESSOMETER_HEAD_SMILE;
        if (stressLevel <= .75f)
            return ADV_STRESSOMETER_HEAD_SLANT;
        return ADV_STRESSOMETER_HEAD_FROWN;
    }

    public static void init() {
        // Force class loading to register partial models with Flywheel
    }
}
