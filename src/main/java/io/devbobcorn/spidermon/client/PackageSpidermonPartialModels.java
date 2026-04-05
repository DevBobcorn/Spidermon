package io.devbobcorn.spidermon.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.devbobcorn.spidermon.SpidermonMod;
import net.minecraft.resources.ResourceLocation;

public class PackageSpidermonPartialModels {

    public static final PartialModel PACKAGE_SPIDERMON_BODY =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(SpidermonMod.MODID, "block/package_spidermon/body"));

    public static final PartialModel PACKAGE_SPIDERMON_GOGGLES =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(SpidermonMod.MODID, "block/package_spidermon/goggles"));

    public static void init() {
        // Force class loading to register partial models with Flywheel
    }
}
