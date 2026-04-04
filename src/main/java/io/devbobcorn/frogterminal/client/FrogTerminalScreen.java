package io.devbobcorn.frogterminal.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageItem;

import io.devbobcorn.frogterminal.block.FrogTerminalBlockEntity;
import io.devbobcorn.frogterminal.block.FrogTerminalBlockEntity.NetworkFrogport;
import io.devbobcorn.frogterminal.block.FrogTerminalMenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FrogTerminalScreen extends AbstractContainerScreen<FrogTerminalMenu> {

	private static final int BG_COLOR = 0xCC101010;
	private static final int MAP_BG_COLOR = 0xFF1a1a22;
	private static final int BORDER_COLOR = 0xFF9ede73;
	private static final int TITLE_COLOR = 0x9ede73;
	private static final int HINT_COLOR = 0x888888;
	private static final int EDGE_COLOR = 0xFF4a6a3a;
	private static final int CONVEYOR_COLOR = 0xFF6eb8d4;
	private static final int CONVEYOR_LOOPING_COLOR = 0xFFe066b3;
	private static final int FROGPORT_COLOR = 0xFF0d2d6e;
	private static final int FROGPORT_LABEL_COLOR = 0xFFb8c8e8;
	private static final int TERMINAL_COLOR = 0xFFe8c84a;

	private static final int MAP_PADDING = 6;
	private static final int TITLE_TOP = 6;
	private static final int MAP_TOP_OFFSET = 20;
	private static final int MAP_BOTTOM_PAD = 22;
	private static final double MIN_PIXELS_PER_BLOCK = 2.0;
	private static final double MAX_PIXELS_PER_BLOCK = 48.0;
	private static final int TERMINAL_SIZE_PX = 3;
	private static final int FROGPORT_SIZE_PX = 3;
	private static final int CONVEYOR_SIZE_PX = 4;
	private static final double CHAIN_HOVER_PX = 3.0;
	private static final double CHAIN_HOVER_PX_SQ = CHAIN_HOVER_PX * CHAIN_HOVER_PX;

	private List<BlockPos> connectedConveyors = List.of();
	private List<MapEdge> chainEdges = List.of();
	private List<NetworkFrogport> networkFrogports = List.of();

	private double viewCenterX;
	private double viewCenterZ;
	private double pixelsPerBlock = 8.0;
	private boolean draggingMap;

	public FrogTerminalScreen(FrogTerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 280;
		this.imageHeight = 200;
	}

	@Override
	protected void init() {
		super.init();
		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be != null) {
			connectedConveyors = be.getConnectedChainConveyors();
			chainEdges = buildChainEdges(be.getLevel(), connectedConveyors);
			networkFrogports = be.getNetworkFrogports(connectedConveyors);
		} else {
			connectedConveyors = List.of();
			chainEdges = List.of();
			networkFrogports = List.of();
		}
		resetViewToFit();
	}

	private static List<MapEdge> buildChainEdges(Level level, List<BlockPos> conveyors) {
		if (level == null || conveyors.isEmpty())
			return List.of();
		Set<BlockPos> set = new HashSet<>(conveyors);
		List<MapEdge> edges = new ArrayList<>();
		for (BlockPos p : conveyors) {
			if (!(level.getBlockEntity(p) instanceof ChainConveyorBlockEntity ccbe))
				continue;
			for (BlockPos off : ccbe.connections) {
				BlockPos q = p.offset(off);
				if (set.contains(q) && p.compareTo(q) < 0)
					edges.add(new MapEdge(p, q));
			}
		}
		return List.copyOf(edges);
	}

	private void resetViewToFit() {
		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be == null) {
			viewCenterX = 0.5;
			viewCenterZ = 0.5;
			pixelsPerBlock = 8.0;
			return;
		}
		BlockPos term = be.getBlockPos();
		int minX = term.getX();
		int maxX = term.getX();
		int minZ = term.getZ();
		int maxZ = term.getZ();
		for (BlockPos p : connectedConveyors) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		for (NetworkFrogport fp : networkFrogports) {
			BlockPos p = fp.pos();
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		viewCenterX = (minX + maxX + 1) * 0.5;
		viewCenterZ = (minZ + maxZ + 1) * 0.5;
		int spanX = maxX - minX + 1;
		int spanZ = maxZ - minZ + 1;
		int span = Math.max(spanX, spanZ);
		int mapW = imageWidth - 2 * MAP_PADDING;
		int mapH = imageHeight - MAP_TOP_OFFSET - MAP_BOTTOM_PAD;
		double fit = Math.min(mapW, mapH) / (double) Math.max(span, 6);
		pixelsPerBlock = Mth.clamp(fit, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
	}

	private int mapLeft() {
		return leftPos + MAP_PADDING;
	}

	private int mapTop() {
		return topPos + MAP_TOP_OFFSET;
	}

	private int mapWidth() {
		return imageWidth - 2 * MAP_PADDING;
	}

	private int mapHeight() {
		return imageHeight - MAP_TOP_OFFSET - MAP_BOTTOM_PAD;
	}

	private int mapMidScreenX() {
		return mapLeft() + mapWidth() / 2;
	}

	private int mapMidScreenY() {
		return mapTop() + mapHeight() / 2;
	}

	private boolean isOverMap(double mouseX, double mouseY) {
		return mouseX >= mapLeft() && mouseX < mapLeft() + mapWidth()
			&& mouseY >= mapTop() && mouseY < mapTop() + mapHeight();
	}

	private double worldXFromScreen(double screenX) {
		return viewCenterX + (screenX - mapMidScreenX()) / pixelsPerBlock;
	}

	/** World Z increases south; screen Y increases down — so negative Z (north) is upward on the map. */
	private double worldZFromScreen(double screenY) {
		return viewCenterZ + (screenY - mapMidScreenY()) / pixelsPerBlock;
	}

	private int screenXFromWorld(double wx) {
		return Mth.floor(mapMidScreenX() + (wx - viewCenterX) * pixelsPerBlock);
	}

	private int screenYFromWorld(double wz) {
		return Mth.floor(mapMidScreenY() + (wz - viewCenterZ) * pixelsPerBlock);
	}

	private void zoomAtScreenPoint(double mouseX, double mouseY, double factor) {
		double wx = worldXFromScreen(mouseX);
		double wz = worldZFromScreen(mouseY);
		double newScale = Mth.clamp(pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
		if (newScale == pixelsPerBlock)
			return;
		pixelsPerBlock = newScale;
		viewCenterX = wx - (mouseX - mapMidScreenX()) / pixelsPerBlock;
		viewCenterZ = wz - (mouseY - mapMidScreenY()) / pixelsPerBlock;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isOverMap(mouseX, mouseY) && scrollY != 0) {
			double factor = scrollY > 0 ? 1.12 : 1 / 1.12;
			zoomAtScreenPoint(mouseX, mouseY, factor);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isOverMap(mouseX, mouseY)) {
			draggingMap = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0)
			draggingMap = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingMap && button == 0) {
			viewCenterX -= dragX / pixelsPerBlock;
			viewCenterZ -= dragY / pixelsPerBlock;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
		guiGraphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

		int ml = mapLeft();
		int mt = mapTop();
		int mw = mapWidth();
		int mh = mapHeight();
		guiGraphics.fill(ml, mt, ml + mw, mt + mh, MAP_BG_COLOR);
		guiGraphics.renderOutline(ml, mt, mw, mh, BORDER_COLOR);

		guiGraphics.enableScissor(ml, mt, ml + mw, mt + mh);

		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be != null) {
			for (MapEdge edge : chainEdges) {
				int x0 = screenXFromWorld(edge.a().getX() + 0.5);
				int y0 = screenYFromWorld(edge.a().getZ() + 0.5);
				int x1 = screenXFromWorld(edge.b().getX() + 0.5);
				int y1 = screenYFromWorld(edge.b().getZ() + 0.5);
				drawLine(guiGraphics, x0, y0, x1, y1, EDGE_COLOR);
			}

			for (BlockPos p : connectedConveyors) {
				int cx = screenXFromWorld(p.getX() + 0.5);
				int cy = screenYFromWorld(p.getZ() + 0.5);
				int convColor = CONVEYOR_COLOR;
				if (be.getLevel().getBlockEntity(p) instanceof ChainConveyorBlockEntity ccbe
					&& !ccbe.getLoopingPackages().isEmpty())
					convColor = CONVEYOR_LOOPING_COLOR;
				guiGraphics.fill(cx - CONVEYOR_SIZE_PX + 1, cy - CONVEYOR_SIZE_PX + 1, cx + CONVEYOR_SIZE_PX, cy + CONVEYOR_SIZE_PX, convColor);
			}

			for (NetworkFrogport fp : networkFrogports) {
				BlockPos p = fp.pos();
				int fx = screenXFromWorld(p.getX() + 0.5);
				int fy = screenYFromWorld(p.getZ() + 0.5);
				guiGraphics.fill(fx - FROGPORT_SIZE_PX + 1, fy - FROGPORT_SIZE_PX + 1, fx + FROGPORT_SIZE_PX, fy + FROGPORT_SIZE_PX, FROGPORT_COLOR);
				String filter = fp.addressFilter();
				String label = filter.isEmpty()
					? Component.translatable("frogterminal.screen.map_frogport_label_empty").getString()
					: filter;
				int textX = fx + 4;
				int maxTextW = ml + mw - textX - 2;
				if (maxTextW > 8) {
					String clipped = truncateLabelToWidth(font, label, maxTextW);
					int textY = fy - font.lineHeight / 2 + 1;
					guiGraphics.drawString(font, clipped, textX, textY, FROGPORT_LABEL_COLOR, false);
				}
			}

			BlockPos term = be.getBlockPos();
			int tx = screenXFromWorld(term.getX() + 0.5);
			int ty = screenYFromWorld(term.getZ() + 0.5);
			guiGraphics.fill(tx - TERMINAL_SIZE_PX + 1, ty - TERMINAL_SIZE_PX + 1, tx + TERMINAL_SIZE_PX, ty + TERMINAL_SIZE_PX, TERMINAL_COLOR);
		}

		guiGraphics.disableScissor();
	}

	private static String truncateLabelToWidth(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth)
			return text;
		String ellipsis = "...";
		if (font.width(ellipsis) > maxWidth)
			return "";
		String s = text;
		while (s.length() > 1 && font.width(s + ellipsis) > maxWidth)
			s = s.substring(0, s.length() - 1);
		return s + ellipsis;
	}

	private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0;
		int y = y0;
		while (true) {
			g.fill(x, y, x + 1, y + 1, color);
			if (x == x1 && y == y1)
				break;
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		List<Component> hover = pickMapHoverTooltip(mouseX, mouseY);
		if (!hover.isEmpty())
			guiGraphics.renderComponentTooltip(font, hover, mouseX, mouseY);
	}

	private List<Component> pickMapHoverTooltip(int mouseX, int mouseY) {
		if (!isOverMap(mouseX, mouseY) || draggingMap)
			return List.of();
		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be == null)
			return List.of();

		BlockPos term = be.getBlockPos();
		int tx = screenXFromWorld(term.getX() + 0.5);
		int ty = screenYFromWorld(term.getZ() + 0.5);
		if (mouseX >= tx - TERMINAL_SIZE_PX + 1 && mouseX < tx + TERMINAL_SIZE_PX && mouseY >= ty - TERMINAL_SIZE_PX + 1 && mouseY < ty + TERMINAL_SIZE_PX)
			return List.of(Component.translatable("frogterminal.screen.map_tooltip_terminal",
				term.getX(), term.getY(), term.getZ()));

		for (NetworkFrogport fp : networkFrogports) {
			BlockPos p = fp.pos();
			int fx = screenXFromWorld(p.getX() + 0.5);
			int fy = screenYFromWorld(p.getZ() + 0.5);
			if (mouseX >= fx - FROGPORT_SIZE_PX + 1 && mouseX < fx + FROGPORT_SIZE_PX && mouseY >= fy - FROGPORT_SIZE_PX + 1 && mouseY < fy + FROGPORT_SIZE_PX) {
				String filter = fp.addressFilter();
				Component location = Component.translatable("frogterminal.screen.map_tooltip_frogport_location",
					p.getX(), p.getY(), p.getZ());
				if (filter.isEmpty())
					return List.of(location,
						Component.translatable("frogterminal.screen.map_tooltip_frogport_empty"));
				return List.of(location, Component.literal(filter));
			}
		}

		for (BlockPos p : connectedConveyors) {
			int cx = screenXFromWorld(p.getX() + 0.5);
			int cy = screenYFromWorld(p.getZ() + 0.5);
			if (mouseX >= cx - CONVEYOR_SIZE_PX + 1 && mouseX < cx + CONVEYOR_SIZE_PX && mouseY >= cy - CONVEYOR_SIZE_PX + 1 && mouseY < cy + CONVEYOR_SIZE_PX) {
				ChainConveyorBlockEntity ccbe = be.getLevel().getBlockEntity(p) instanceof ChainConveyorBlockEntity c ? c : null;
				return conveyorHoverLines(p, ccbe);
			}
		}

		double mx = mouseX + 0.5;
		double my = mouseY + 0.5;
		for (MapEdge edge : chainEdges) {
			int x0 = screenXFromWorld(edge.a().getX() + 0.5);
			int y0 = screenYFromWorld(edge.a().getZ() + 0.5);
			int x1 = screenXFromWorld(edge.b().getX() + 0.5);
			int y1 = screenYFromWorld(edge.b().getZ() + 0.5);
			if (distSqPointSegment(mx, my, x0, y0, x1, y1) <= CHAIN_HOVER_PX_SQ)
				return List.of(Component.translatable("frogterminal.screen.map_tooltip_chain",
					edge.a().getX(), edge.a().getY(), edge.a().getZ(),
					edge.b().getX(), edge.b().getY(), edge.b().getZ()));
		}

		return List.of();
	}

	private static List<Component> conveyorHoverLines(BlockPos p, ChainConveyorBlockEntity ccbe) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("frogterminal.screen.map_tooltip_conveyor",
			p.getX(), p.getY(), p.getZ()));
		if (ccbe == null)
			return lines;
		List<ChainConveyorPackage> looping = ccbe.getLoopingPackages();
		if (looping.isEmpty())
			return lines;
		lines.add(Component.translatable("frogterminal.screen.map_tooltip_looping_header"));
		for (ChainConveyorPackage pkg : looping)
			lines.add(loopingPackageLine(pkg));
		return lines;
	}

	private static Component loopingPackageLine(ChainConveyorPackage pkg) {
		ItemStack stack = pkg.item;

		MutableComponent line = Component.empty();
		line.append(stack.getDisplayName());

		String addr = PackageItem.getAddress(stack);
		if (!addr.isEmpty()) {
			line.append(Component.literal(" · "));
			line.append(Component.literal(addr));
		}
		return line;
	}

	private static double distSqPointSegment(double px, double py, double x0, double y0, double x1, double y1) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double lenSq = dx * dx + dy * dy;
		if (lenSq < 1e-6)
			return (px - x0) * (px - x0) + (py - y0) * (py - y0);
		double t = Mth.clamp(((px - x0) * dx + (py - y0) * dy) / lenSq, 0.0, 1.0);
		double qx = x0 + t * dx;
		double qy = y0 + t * dy;
		double qdx = px - qx;
		double qdy = py - qy;
		return qdx * qdx + qdy * qdy;
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawCenteredString(font, this.title, imageWidth / 2, TITLE_TOP, TITLE_COLOR);
		guiGraphics.drawCenteredString(font,
			Component.translatable("frogterminal.screen.map_hint"),
			imageWidth / 2,
			imageHeight - 14,
			HINT_COLOR);
	}

	private record MapEdge(BlockPos a, BlockPos b) {
	}
}
