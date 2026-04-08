package io.devbobcorn.spidermon.compat.journeymap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.client.SpidermonGuiTextures;
import io.devbobcorn.spidermon.compat.ChainMapColors;
import io.devbobcorn.spidermon.compat.ChainMapManager;
import io.devbobcorn.spidermon.compat.ChainMapOverlay;
import io.devbobcorn.spidermon.compat.ChainMapTooltips;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.display.Context.UI;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.event.FullscreenEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.InputEvent;

/**
 * JourneyMap integration for the chain conveyor network overlay.
 *
 * <p>Registered automatically via the {@link JourneyMapPlugin} annotation (JourneyMap
 * discovers it through NeoForge's {@code ModFileScanData} annotation scanning — no
 * manual registration is required). Note: JourneyMap's scanner looks specifically for
 * {@code journeymap.api.v2.client.JourneyMapPlugin} even though that annotation is
 * marked deprecated-for-removal in the API; the common-package replacement is not yet
 * scanned by this version of JourneyMap.
 *
 * <p>Render flow:
 * <ol>
 *   <li>{@link #initialize} subscribes to {@link FullscreenEventRegistry#FULLSCREEN_RENDER_EVENT}</li>
 *   <li>{@link #onRender} fires during JourneyMap's fullscreen map render pass</li>
 *   <li>World-space PoseStack transform is applied (same math as the Xaero version)</li>
 *   <li>Drawing is delegated to the shared {@link ChainMapManager} (data) and
 *       {@link ChainMapOverlay} (pixel-buffer renderer)</li>
 * </ol>
 *
 * <p>Tick / mouse-click routing is handled by
 * {@link io.devbobcorn.spidermon.SpidermonClient} which guards those calls behind a
 * JourneyMap loaded check.
 */
@SuppressWarnings("removal") // JourneyMap's scanner specifically looks for this annotation type
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyChainMap implements IClientPlugin {

    private static boolean encounteredException = false;
    private static final ChainMapOverlay overlay = new ChainMapOverlay();
    private static boolean overlayEnabled = true;

    private static final int TOGGLE_X = 3;
    // Create's train map toggle occupies Y=30..43 (14px tall); place ours directly below
    private static final int TOGGLE_Y = 48;

    private static final int EDGE_COLOR = ChainMapColors.EDGE_COLOR;
    private static final int EDGE_OUTLINE_COLOR = ChainMapColors.EDGE_OUTLINE_COLOR;
    private static final int CONVEYOR_COLOR = ChainMapColors.CONVEYOR_COLOR;
    private static final int CONVEYOR_OUTLINE_COLOR = ChainMapColors.CONVEYOR_OUTLINE_COLOR;
    private static final int CONVEYOR_LOOPING_COLOR = ChainMapColors.CONVEYOR_LOOPING_COLOR;
    private static final int CONVEYOR_LOOPING_OUTLINE_COLOR = ChainMapColors.CONVEYOR_LOOPING_OUTLINE_COLOR;
    private static final int FROGPORT_COLOR = ChainMapColors.FROGPORT_COLOR;
    private static final int PACKAGE_COLOR = ChainMapColors.PACKAGE_COLOR;
    private static final int UNLOADED_EDGE_COLOR = ChainMapColors.UNLOADED_EDGE_COLOR;
    private static final int UNLOADED_EDGE_OUTLINE_COLOR = ChainMapColors.UNLOADED_EDGE_OUTLINE_COLOR;
    private static final int UNLOADED_CONVEYOR_COLOR = ChainMapColors.UNLOADED_CONVEYOR_COLOR;
    private static final int UNLOADED_CONVEYOR_OUTLINE_COLOR = ChainMapColors.UNLOADED_CONVEYOR_OUTLINE_COLOR;

    // ── IClientPlugin ─────────────────────────────────────────────────────

    @Override
    public void initialize(IClientAPI jmClientApi) {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(SpidermonMod.MODID, JourneyChainMap::onRender);
    }

    @Override
    public String getModId() {
        return SpidermonMod.MODID;
    }

    // ── Tick & input ──────────────────────────────────────────────────────

    /**
     * Called each client tick by {@link io.devbobcorn.spidermon.SpidermonClient}
     * when JourneyMap is loaded and the fullscreen map is open.
     */
    public static void tick() {
        if (!(Minecraft.getInstance().screen instanceof IFullscreen))
            return;
        if (overlayEnabled)
            ChainMapManager.INSTANCE.tick();
    }

    /**
     * Called on each mouse-button press by {@link io.devbobcorn.spidermon.SpidermonClient}
     * when JourneyMap is loaded, to handle toggle widget clicks.
     */
    public static void mouseClick(InputEvent.MouseButton.Pre event) {
        if (encounteredException)
            return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS)
            return;

        Minecraft mc = Minecraft.getInstance();
        try {
            if (!(mc.screen instanceof IFullscreen))
                return;
        } catch (Throwable e) {
            SpidermonMod.LOGGER.error("Failed to handle mouseClick for JourneyMap chain map integration:", e);
            encounteredException = true;
            return;
        }

        Window window = mc.getWindow();
        double mX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();

        if (handleToggleClick(Mth.floor(mX), Mth.floor(mY)))
            event.setCanceled(true);
    }

    // ── Render ────────────────────────────────────────────────────────────

    /**
     * Subscribed to {@link FullscreenEventRegistry#FULLSCREEN_RENDER_EVENT} in
     * {@link #initialize}. Mirrors {@code XaeroChainMap.onRender} but uses JourneyMap's
     * {@link UIState#blockSize} and {@link IFullscreen#getCenterBlockX}/{@code Z} instead
     * of Xaero's accessor mixin.
     */
    public static void onRender(FullscreenRenderEvent event) {
        if (encounteredException)
            return;
        try {
            doRender(event);
        } catch (Throwable e) {
            SpidermonMod.LOGGER.error("Failed to render chain map overlay on JourneyMap:", e);
            encounteredException = true;
        }
    }

    private static void doRender(FullscreenRenderEvent event) {
        IFullscreen fullscreen = event.getFullscreen();
        UIState state = fullscreen.getUiState();
        if (state == null)
            return;
        if (state.ui != UI.Fullscreen)
            return;
        if (!state.active)
            return;

        GuiGraphics graphics = event.getGraphics();
        Screen screen = fullscreen.getScreen();
        int mX = event.getMouseX();
        int mY = event.getMouseY();

        ChainMapManager mgr = ChainMapManager.INSTANCE;

        if (!overlayEnabled) {
            renderToggleWidgetAndTooltip(graphics, screen, mX, mY);
            return;
        }

        if (mgr.getConveyorPositions().isEmpty() && mgr.getUnloadedConveyorPositions().isEmpty()) {
            renderToggleWidgetAndTooltip(graphics, screen, mX, mY);
            return;
        }

        // Map center in world-block coordinates (fractional)
        double cameraX = fullscreen.getCenterBlockX(true);
        double cameraZ = fullscreen.getCenterBlockZ(true);

        // Scale: blockSize is GUI pixels per block; divide by guiScale to get screen pixels per block
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scale = state.blockSize / guiScale;

        PoseStack pose = graphics.pose();
        pose.pushPose();

        pose.translate(screen.width / 2.0f, screen.height / 2.0f, 0);
        pose.scale((float) scale, (float) scale, 1);
        pose.translate(-cameraX, -cameraZ, 0);

        overlay.startDrawing();

        drawUnloadedEdges(mgr);
        drawUnloadedConveyors(mgr);
        drawEdges(mgr);
        drawConveyors(mgr);
        drawFrogports(mgr);
        drawTravelingPackages(mgr);

        overlay.finishDrawing();
        overlay.render(graphics);

        graphics.bufferSource().endBatch();

        pose.popPose();

        renderChainMapHoverTooltips(graphics, screen, mgr, mX, mY, cameraX, cameraZ, scale);

        renderToggleWidgetAndTooltip(graphics, screen, mX, mY);
    }

    // ── Hover tooltips ────────────────────────────────────────────────────

    private static void renderChainMapHoverTooltips(GuiGraphics graphics, Screen screen, ChainMapManager mgr,
        int mouseX, int mouseY, double cameraX, double cameraZ, double mapScale) {
        if (isToggleHovered(mouseX, mouseY))
            return;
        Level level = Minecraft.getInstance().level;
        List<Component> hover = pickChainMapHoverTooltip(screen, mgr, mouseX, mouseY, cameraX, cameraZ, mapScale, level);
        if (!hover.isEmpty())
            graphics.renderComponentTooltip(Minecraft.getInstance().font, hover, mouseX, mouseY);
    }

    private static List<Component> pickChainMapHoverTooltip(Screen screen, ChainMapManager mgr,
        int mouseX, int mouseY, double cameraX, double cameraZ, double scale, Level level) {
        double wx = cameraX + (mouseX - screen.width / 2.0) / scale;
        double wz = cameraZ + (mouseY - screen.height / 2.0) / scale;
        int bx = Mth.floor(wx);
        int bz = Mth.floor(wz);

        List<BlockPos> conveyorHits = new ArrayList<>();
        for (BlockPos p : mgr.getConveyorPositions()) {
            if (mouseInConveyorMarkerBlock(bx, bz, p))
                conveyorHits.add(p);
        }
        for (BlockPos p : mgr.getUnloadedConveyorPositions()) {
            if (mouseInConveyorMarkerBlock(bx, bz, p))
                conveyorHits.add(p);
        }
        conveyorHits.sort(Comparator.<BlockPos>comparingLong(BlockPos::getY).thenComparingLong(BlockPos::getX));

        List<ChainMapTooltips.FrogportEntry> frogportHits = new ArrayList<>();
        for (ChainMapManager.ChainFrogport fp : mgr.getFrogports()) {
            BlockPos p = fp.pos();
            if (bx == p.getX() && bz == p.getZ())
                frogportHits.add(new ChainMapTooltips.FrogportEntry(p, fp.addressFilter()));
        }
        frogportHits.sort(Comparator
            .<ChainMapTooltips.FrogportEntry>comparingLong(f -> f.pos().getY())
            .thenComparingLong(f -> f.pos().getX()));

        if (!conveyorHits.isEmpty() || !frogportHits.isEmpty())
            return buildCombinedChainMapTooltip(level, conveyorHits, frogportHits);

        for (ChainMapManager.ChainEdge edge : mgr.getEdges()) {
            if (chainEdgeNearMouse(screen, mouseX, mouseY, edge, cameraX, cameraZ, scale))
                return ChainMapTooltips.chainEdgeHoverLines(level, edge.a(), edge.b());
        }
        for (ChainMapManager.ChainEdge edge : mgr.getUnloadedEdges()) {
            if (chainEdgeNearMouse(screen, mouseX, mouseY, edge, cameraX, cameraZ, scale))
                return ChainMapTooltips.chainEdgeHoverLines(level, edge.a(), edge.b());
        }

        return List.of();
    }

    private static boolean mouseInConveyorMarkerBlock(int bx, int bz, BlockPos p) {
        return bx >= p.getX() - 1 && bx <= p.getX() + 1 && bz >= p.getZ() - 1 && bz <= p.getZ() + 1;
    }

    private static int screenMapX(Screen screen, double worldX, double cameraX, double scale) {
        return Mth.floor(screen.width / 2.0 + (worldX - cameraX) * scale);
    }

    private static int screenMapY(Screen screen, double worldZ, double cameraZ, double scale) {
        return Mth.floor(screen.height / 2.0 + (worldZ - cameraZ) * scale);
    }

    private static boolean chainEdgeNearMouse(Screen screen, int mouseX, int mouseY, ChainMapManager.ChainEdge edge,
        double cameraX, double cameraZ, double scale) {
        int x0 = screenMapX(screen, edge.a().getX() + 0.5, cameraX, scale);
        int y0 = screenMapY(screen, edge.a().getZ() + 0.5, cameraZ, scale);
        int x1 = screenMapX(screen, edge.b().getX() + 0.5, cameraX, scale);
        int y1 = screenMapY(screen, edge.b().getZ() + 0.5, cameraZ, scale);
        return ChainMapTooltips.isNearChainEdgeScreen(mouseX, mouseY, x0, y0, x1, y1);
    }

    private static List<Component> buildCombinedChainMapTooltip(Level level, List<BlockPos> conveyorHits,
        List<ChainMapTooltips.FrogportEntry> frogportHits) {
        List<Component> lines = new ArrayList<>();
        if (!conveyorHits.isEmpty()) {
            boolean firstConv = true;
            for (BlockPos p : conveyorHits) {
                if (!firstConv)
                    lines.add(Component.empty());
                firstConv = false;
                ChainConveyorBlockEntity ccbe = level != null && level.getBlockEntity(p) instanceof ChainConveyorBlockEntity c
                    ? c
                    : null;
                lines.addAll(ChainMapTooltips.conveyorHoverLines(p, ccbe));
            }
        }
        if (!frogportHits.isEmpty()) {
            if (!lines.isEmpty())
                lines.add(Component.empty());
            lines.addAll(ChainMapTooltips.frogportsHoverLines(frogportHits));
        }
        return lines;
    }

    // ── Toggle widget ──────────────────────────────────────────────────────

    private static boolean renderToggleWidgetAndTooltip(GuiGraphics graphics, Screen screen,
        int mouseX, int mouseY) {
        renderToggleWidget(graphics);
        if (!isToggleHovered(mouseX, mouseY))
            return false;

        graphics.renderTooltip(Minecraft.getInstance().font,
            List.of(Component.translatable("spidermon.chain_map.toggle")),
            java.util.Optional.empty(), mouseX, mouseY + 20);
        return true;
    }

    private static void renderToggleWidget(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        SpidermonGuiTextures.CHAINMAP_TOGGLE_PANEL.render(graphics, TOGGLE_X, TOGGLE_Y);
        (overlayEnabled ? SpidermonGuiTextures.CHAINMAP_TOGGLE_ON : SpidermonGuiTextures.CHAINMAP_TOGGLE_OFF)
            .render(graphics, TOGGLE_X + 18, TOGGLE_Y + 3);
        pose.popPose();
    }

    private static boolean handleToggleClick(int mouseX, int mouseY) {
        if (!isToggleHovered(mouseX, mouseY))
            return false;
        overlayEnabled = !overlayEnabled;
        return true;
    }

    private static boolean isToggleHovered(int mouseX, int mouseY) {
        if (mouseX < TOGGLE_X || mouseX >= TOGGLE_X + SpidermonGuiTextures.CHAINMAP_TOGGLE_PANEL.getWidth())
            return false;
        if (mouseY < TOGGLE_Y || mouseY >= TOGGLE_Y + SpidermonGuiTextures.CHAINMAP_TOGGLE_PANEL.getHeight())
            return false;
        return true;
    }

    // ── Drawing ────────────────────────────────────────────────────────────

    private static void drawEdges(ChainMapManager mgr) {
        for (ChainMapManager.ChainEdge edge : mgr.getEdges()) {
            drawWorldLine(
                edge.a().getX() + 0.5, edge.a().getZ() + 0.5,
                edge.b().getX() + 0.5, edge.b().getZ() + 0.5,
                EDGE_OUTLINE_COLOR, 1);
        }
        for (ChainMapManager.ChainEdge edge : mgr.getEdges()) {
            drawWorldLine(
                edge.a().getX() + 0.5, edge.a().getZ() + 0.5,
                edge.b().getX() + 0.5, edge.b().getZ() + 0.5,
                EDGE_COLOR, 0);
        }
    }

    private static void drawUnloadedEdges(ChainMapManager mgr) {
        for (ChainMapManager.ChainEdge edge : mgr.getUnloadedEdges()) {
            drawWorldLine(
                edge.a().getX() + 0.5, edge.a().getZ() + 0.5,
                edge.b().getX() + 0.5, edge.b().getZ() + 0.5,
                UNLOADED_EDGE_OUTLINE_COLOR, 1);
        }
        for (ChainMapManager.ChainEdge edge : mgr.getUnloadedEdges()) {
            drawWorldLine(
                edge.a().getX() + 0.5, edge.a().getZ() + 0.5,
                edge.b().getX() + 0.5, edge.b().getZ() + 0.5,
                UNLOADED_EDGE_COLOR, 0);
        }
    }

    private static void drawUnloadedConveyors(ChainMapManager mgr) {
        for (BlockPos pos : mgr.getUnloadedConveyorPositions()) {
            overlay.setRect(pos.getX() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getZ() + 1,
                UNLOADED_CONVEYOR_OUTLINE_COLOR);
        }
        for (BlockPos pos : mgr.getUnloadedConveyorPositions()) {
            overlay.setPixel(pos.getX(), pos.getZ(), UNLOADED_CONVEYOR_COLOR);
        }
    }

    private static void drawConveyors(ChainMapManager mgr) {
        Level level = Minecraft.getInstance().level;
        for (BlockPos pos : mgr.getConveyorPositions()) {
            int color = CONVEYOR_OUTLINE_COLOR;
            if (level != null && level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity ccbe
                && !ccbe.getLoopingPackages().isEmpty()) {
                color = CONVEYOR_LOOPING_OUTLINE_COLOR;
            }
            overlay.setRect(pos.getX() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getZ() + 1, color);
        }
        for (BlockPos pos : mgr.getConveyorPositions()) {
            int color = CONVEYOR_COLOR;
            if (level != null && level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity ccbe
                && !ccbe.getLoopingPackages().isEmpty()) {
                color = CONVEYOR_LOOPING_COLOR;
            }
            overlay.setPixel(pos.getX(), pos.getZ(), color);
        }
    }

    private static void drawFrogports(ChainMapManager mgr) {
        for (ChainMapManager.ChainFrogport fp : mgr.getFrogports()) {
            BlockPos p = fp.pos();
            overlay.setPixel(p.getX(), p.getZ(), FROGPORT_COLOR);
        }
    }

    private static void drawTravelingPackages(ChainMapManager mgr) {
        Level level = Minecraft.getInstance().level;
        if (level == null)
            return;
        for (BlockPos convPos : mgr.getConveyorPositions()) {
            if (!(level.getBlockEntity(convPos) instanceof ChainConveyorBlockEntity ccbe))
                continue;
            for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : ccbe.getTravellingPackages().entrySet()) {
                for (ChainConveyorPackage pkg : entry.getValue()) {
                    Vec3 pos = ccbe.getPackagePosition(pkg.chainPosition, entry.getKey());
                    if (pos == Vec3.ZERO)
                        continue;
                    overlay.setPixel(Mth.floor(pos.x), Mth.floor(pos.z), PACKAGE_COLOR);
                }
            }
        }
    }

    private static void drawWorldLine(double x0d, double z0d, double x1d, double z1d, int color, int pad) {
        int x0 = Mth.floor(x0d);
        int z0 = Mth.floor(z0d);
        int x1 = Mth.floor(x1d);
        int z1 = Mth.floor(z1d);
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int x = x0, z = z0;
        while (true) {
            if (pad > 0)
                overlay.setRect(x - pad, z - pad, x + pad, z + pad, color);
            else
                overlay.setPixel(x, z, color);
            if (x == x1 && z == z1)
                break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }
}
