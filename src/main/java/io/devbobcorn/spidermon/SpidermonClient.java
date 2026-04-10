package io.devbobcorn.spidermon;

import io.devbobcorn.spidermon.client.PackageSpidermonPartialModels;
import io.devbobcorn.spidermon.client.PackageSpidermonRenderer;
import io.devbobcorn.spidermon.client.PackageSpidermonScreen;
import io.devbobcorn.spidermon.client.PackageSpidermonTargetSelectionHandler;
import io.devbobcorn.spidermon.client.PackageSpidermonVisual;
import io.devbobcorn.spidermon.compat.ChainMapManager;
import io.devbobcorn.spidermon.compat.journeymap.JourneyChainMap;
import io.devbobcorn.spidermon.compat.xaero.XaeroChainMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SpidermonMod.MODID, dist = Dist.CLIENT)
public class SpidermonClient {
    private static final boolean XAERO_LOADED = LoadingModList.get().getModFileById("xaeroworldmap") != null;
    private static final boolean JOURNEYMAP_LOADED = LoadingModList.get().getModFileById("journeymap") != null;

    public SpidermonClient(IEventBus modEventBus, ModContainer container) {
        SpidermonMod.LOGGER.info("[Spidermon] Xaero's World Map detected: {}", XAERO_LOADED);
        SpidermonMod.LOGGER.info("[Spidermon] JourneyMap detected: {}", JOURNEYMAP_LOADED);

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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        // Do not use event.getPoseStack(): it carries per-section transforms. Build a clean view stack like
        // LevelRenderer (pitch, yaw+180, then translate -camera) and draw in world space.
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        var camera = event.getCamera();
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        Vec3 cam = camera.getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        PackageSpidermonTargetSelectionHandler.renderPackageSpidermonScreenChainMeshesWorld(mc, poseStack, buffer,
            partialTick);
        buffer.endBatch(PackageSpidermonTargetSelectionHandler.chainSegmentRenderType());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (XAERO_LOADED || JOURNEYMAP_LOADED)
            ChainMapManager.INSTANCE.ensureClientLevel(mc.level);
        if (mc.level == null || mc.player == null)
            return;
        PackageSpidermonTargetSelectionHandler.tick();
        if (XAERO_LOADED)
            tickXaeroChainMap();
        if (JOURNEYMAP_LOADED)
            tickJourneyChainMap();
    }

    private static void tickXaeroChainMap() {
        XaeroChainMap.tick();
    }

    private static void tickJourneyChainMap() {
        JourneyChainMap.tick();
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (XAERO_LOADED)
            handleXaeroMouseClick(event);
        if (JOURNEYMAP_LOADED)
            handleJourneyMouseClick(event);
    }

    private static void handleXaeroMouseClick(InputEvent.MouseButton.Pre event) {
        XaeroChainMap.mouseClick(event);
    }

    private static void handleJourneyMouseClick(InputEvent.MouseButton.Pre event) {
        JourneyChainMap.mouseClick(event);
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SpidermonMod.PACKAGE_SPIDERMON_MENU.get(), PackageSpidermonScreen::new);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SpidermonMod.SPIDERMON_BE.get(),
                PackageSpidermonRenderer::new);
    }
}
