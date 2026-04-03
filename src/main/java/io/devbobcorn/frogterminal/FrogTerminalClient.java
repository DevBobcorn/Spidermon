package io.devbobcorn.frogterminal;

import io.devbobcorn.frogterminal.client.AdvancedStressometerRenderer;
import io.devbobcorn.frogterminal.client.AdvancedStressometerVisual;
import io.devbobcorn.frogterminal.client.FrogTerminalPartialModels;
import io.devbobcorn.frogterminal.client.FrogTerminalRenderer;
import io.devbobcorn.frogterminal.client.FrogTerminalScreen;
import io.devbobcorn.frogterminal.client.FrogTerminalTargetSelectionHandler;
import io.devbobcorn.frogterminal.client.FrogTerminalVisual;

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

@Mod(value = FrogTerminalMod.MODID, dist = Dist.CLIENT)
public class FrogTerminalClient {
    public FrogTerminalClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        FrogTerminalPartialModels.init();

        modEventBus.addListener(FrogTerminalClient::onClientSetup);
        modEventBus.addListener(FrogTerminalClient::registerRenderers);
        modEventBus.addListener(FrogTerminalClient::registerScreens);

        NeoForge.EVENT_BUS.register(FrogTerminalClient.class);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        FrogTerminalMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        FrogTerminalMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        SimpleBlockEntityVisualizer.builder(FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get())
                .factory(AdvancedStressometerVisual::new)
                .skipVanillaRender(be -> true)
                .apply();

        SimpleBlockEntityVisualizer.builder(FrogTerminalMod.FROG_TERMINAL_BE.get())
                .factory(FrogTerminalVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        FrogTerminalTargetSelectionHandler.tick();
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FrogTerminalMod.FROG_TERMINAL_MENU.get(), FrogTerminalScreen::new);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get(),
                AdvancedStressometerRenderer::new);
        event.registerBlockEntityRenderer(FrogTerminalMod.FROG_TERMINAL_BE.get(),
                FrogTerminalRenderer::new);
    }
}
