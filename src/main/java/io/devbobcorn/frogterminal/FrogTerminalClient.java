package io.devbobcorn.frogterminal;

import io.devbobcorn.frogterminal.client.AdvancedStressometerRenderer;
import io.devbobcorn.frogterminal.client.AdvancedStressometerVisual;
import io.devbobcorn.frogterminal.client.FrogTerminalPartialModels;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = FrogTerminalMod.MODID, dist = Dist.CLIENT)
public class FrogTerminalClient {
    public FrogTerminalClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        FrogTerminalPartialModels.init();

        modEventBus.addListener(FrogTerminalClient::onClientSetup);
        modEventBus.addListener(FrogTerminalClient::registerRenderers);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        FrogTerminalMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        FrogTerminalMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        SimpleBlockEntityVisualizer.builder(FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get())
                .factory(AdvancedStressometerVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get(),
                AdvancedStressometerRenderer::new);
    }
}
