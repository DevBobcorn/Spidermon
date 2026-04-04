package io.devbobcorn.frogterminal.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.devbobcorn.frogterminal.FrogTerminalMod;
import net.minecraft.resources.ResourceLocation;

public class FrogTerminalPartialModels {

    public static final PartialModel FROG_TERMINAL_BODY =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/frog_terminal/body"));

    public static final PartialModel FROG_TERMINAL_HEAD =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/frog_terminal/head"));

    public static final PartialModel FROG_TERMINAL_HEAD_GOGGLES =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/frog_terminal/head_goggles"));

    public static final PartialModel FROG_TERMINAL_TONGUE =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(FrogTerminalMod.MODID, "block/frog_terminal/tongue"));

    public static void init() {
        // Force class loading to register partial models with Flywheel
    }
}
