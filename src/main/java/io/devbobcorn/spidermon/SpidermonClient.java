package io.devbobcorn.spidermon;

import io.devbobcorn.spidermon.client.PackageSpidermonPartialModels;
import io.devbobcorn.spidermon.client.PackageSpidermonRenderer;
import io.devbobcorn.spidermon.client.PackageSpidermonScreen;
import io.devbobcorn.spidermon.client.PackageSpidermonTargetSelectionHandler;
import io.devbobcorn.spidermon.client.PackageSpidermonVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SpidermonMod.MODID, dist = Dist.CLIENT)
public class SpidermonClient {
    public SpidermonClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        PackageSpidermonPartialModels.init();

        modEventBus.addListener(SpidermonClient::onClientSetup);
        modEventBus.addListener(SpidermonClient::registerRenderers);
        modEventBus.addListener(SpidermonClient::registerScreens);

        NeoForge.EVENT_BUS.register(SpidermonClient.class);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        SpidermonMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        SpidermonMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        SimpleBlockEntityVisualizer.builder(SpidermonMod.SPIDERMON_BE.get())
                .factory(PackageSpidermonVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        PackageSpidermonTargetSelectionHandler.tick();
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), PackageSpidermonScreen::new);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SpidermonMod.SPIDERMON_BE.get(),
                PackageSpidermonRenderer::new);
    }
}
