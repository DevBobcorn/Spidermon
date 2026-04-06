package io.devbobcorn.spidermon.compat.xaero;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import io.devbobcorn.spidermon.SpidermonMod;
import io.devbobcorn.spidermon.client.ChainMapColors;
import io.devbobcorn.spidermon.mixin.compat.xaero.XaeroGuiMapAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xaero.lib.client.gui.ScreenBase;
import xaero.map.gui.GuiMap;

public class XaeroChainMap {
	private static boolean encounteredException = false;
	private static final ChainMapOverlay overlay = new ChainMapOverlay();

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

	public static void tick() {
		if (!isMapOpen(Minecraft.getInstance().screen))
			return;
		ChainMapManager.INSTANCE.tick();
	}

	/**
	 * Called from {@link io.devbobcorn.spidermon.mixin.compat.xaero.XaeroGuiMapMixin}
	 * during GuiMap rendering. Transforms the pose stack into world-space coordinates
	 * and draws chain conveyor network overlays.
	 */
	public static void onRender(GuiGraphics graphics, GuiMap screen, int mX, int mY, float pt) {
		ChainMapManager mgr = ChainMapManager.INSTANCE;
		if (mgr.getConveyorPositions().isEmpty() && mgr.getUnloadedConveyorPositions().isEmpty())
			return;

		XaeroGuiMapAccessor accessor = (XaeroGuiMapAccessor) screen;
		double cameraX = accessor.getCameraX();
		double cameraZ = accessor.getCameraZ();
		double mapScale = accessor.getScale();

		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();

		double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
		double scale = mapScale / guiScale / interfaceScale;

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
	}

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

	public static boolean isMapOpen(Screen screen) {
		if (encounteredException)
			return false;
		try {
			return screen instanceof ScreenBase sb
				&& (sb instanceof GuiMap || sb.parent instanceof GuiMap);
		} catch (Throwable e) {
			SpidermonMod.LOGGER.error("Failed to check if Xaero's World Map was open for chain map integration:", e);
			encounteredException = true;
			return false;
		}
	}
}
