package io.devbobcorn.spidermon.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageItem;

import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;
import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity.NetworkFrogport;
import io.devbobcorn.spidermon.block.PackageSpidermonMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.phys.Vec3;

public class PackageSpidermonScreen extends AbstractContainerScreen<PackageSpidermonMenu> {

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
	private static final int TRAVEL_PACKAGE_DOT_COLOR = 0xFFFFFFFF;

	private static final int MAP_PADDING = 6;
	private static final int TITLE_TOP = 6;
	private static final int MAP_TOP_OFFSET = 20;
	private static final int MAP_BOTTOM_PAD = 22;
	private static final int BOTTOM_HINT_PAD = 6;
	private static final int BOTTOM_HINT_Y = 14;
	private static final int MIN_PIXELS_PER_BLOCK = 1;
	private static final int MAX_PIXELS_PER_BLOCK = 16;
	private static final int TERMINAL_SIZE_PX = 3;
	private static final int FROGPORT_SIZE_PX = 3;
	private static final int CONVEYOR_SIZE_PX = 4;
	/** Minimum gap between a frogport label box and other map obstacles (not chain edges). */
	private static final int FROGPORT_LABEL_OBSTACLE_PAD = 2;
	private static final double CHAIN_HOVER_PX = 3.0;
	private static final double CHAIN_HOVER_PX_SQ = CHAIN_HOVER_PX * CHAIN_HOVER_PX;
	/** Spaces before looping-package destination and content lines (under the package title). */
	private static final String LOOPING_DETAIL_INDENT = "    ";

	private List<BlockPos> connectedConveyors = List.of();
	private List<MapEdge> chainEdges = List.of();
	private List<NetworkFrogport> networkFrogports = List.of();

	private double viewCenterX;
	private double viewCenterZ;
	private int pixelsPerBlock = 8;
	private boolean draggingMap;

	public PackageSpidermonScreen(PackageSpidermonMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 280;
		this.imageHeight = 200;
	}

	@Override
	protected void init() {
		super.init();
		PackageSpidermonBlockEntity be = menu.contentHolder;
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
		PackageSpidermonBlockEntity be = menu.contentHolder;
		if (be == null) {
			viewCenterX = 0.5;
			viewCenterZ = 0.5;
			pixelsPerBlock = 8;
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
		if (be.target != null) {
			Vec3 chain = be.getExactTargetLocation();
			if (chain != Vec3.ZERO) {
				int cx = Mth.floor(chain.x);
				int cz = Mth.floor(chain.z);
				minX = Math.min(minX, cx);
				maxX = Math.max(maxX, cx);
				minZ = Math.min(minZ, cz);
				maxZ = Math.max(maxZ, cz);
			}
		}
		viewCenterX = (minX + maxX + 1) * 0.5;
		viewCenterZ = (minZ + maxZ + 1) * 0.5;
		int spanX = maxX - minX + 1;
		int spanZ = maxZ - minZ + 1;
		int span = Math.max(spanX, spanZ);
		int mapW = imageWidth - 2 * MAP_PADDING;
		int mapH = imageHeight - MAP_TOP_OFFSET - MAP_BOTTOM_PAD;
		double fit = Math.min(mapW, mapH) / (double) Math.max(span, 6);
		pixelsPerBlock = Mth.clamp(Math.round((float) fit), MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
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
		return viewCenterX + (screenX - mapMidScreenX()) / (double) pixelsPerBlock;
	}

	/** World Z increases south; screen Y increases down — so negative Z (north) is upward on the map. */
	private double worldZFromScreen(double screenY) {
		return viewCenterZ + (screenY - mapMidScreenY()) / (double) pixelsPerBlock;
	}

	private int screenXFromWorld(double wx) {
		return Mth.floor(mapMidScreenX() + (wx - viewCenterX) * pixelsPerBlock);
	}

	private int screenYFromWorld(double wz) {
		return Mth.floor(mapMidScreenY() + (wz - viewCenterZ) * pixelsPerBlock);
	}

	/** @param delta +1 zoom in (more px/block), -1 zoom out */
	private void zoomAtScreenPoint(double mouseX, double mouseY, int delta) {
		if (delta == 0)
			return;
		double wx = worldXFromScreen(mouseX);
		double wz = worldZFromScreen(mouseY);
		int newScale = Mth.clamp(pixelsPerBlock + delta, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
		if (newScale == pixelsPerBlock)
			return;
		pixelsPerBlock = newScale;
		viewCenterX = wx - (mouseX - mapMidScreenX()) / (double) pixelsPerBlock;
		viewCenterZ = wz - (mouseY - mapMidScreenY()) / (double) pixelsPerBlock;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isOverMap(mouseX, mouseY) && scrollY != 0) {
			long rounded = Math.round(scrollY);
			int delta = rounded == 0
				? (scrollY > 0 ? 1 : -1)
				: Mth.clamp((int) rounded, -MAX_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
			zoomAtScreenPoint(mouseX, mouseY, delta);
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
			viewCenterX -= dragX / (double) pixelsPerBlock;
			viewCenterZ -= dragY / (double) pixelsPerBlock;
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

		PackageSpidermonBlockEntity be = menu.contentHolder;
		if (be != null) {
			BlockPos term = be.getBlockPos();
			for (MapEdge edge : chainEdges) {
				int x0 = screenXFromWorld(edge.a().getX() + 0.5);
				int y0 = screenYFromWorld(edge.a().getZ() + 0.5);
				int x1 = screenXFromWorld(edge.b().getX() + 0.5);
				int y1 = screenYFromWorld(edge.b().getZ() + 0.5);
				drawLine(guiGraphics, x0, y0, x1, y1, EDGE_COLOR);
			}

			List<IntRect> labelObstacles = new ArrayList<>(
				connectedConveyors.size() + networkFrogports.size() + 32);
			for (BlockPos p : connectedConveyors) {
				int cx = screenXFromWorld(p.getX() + 0.5);
				int cy = screenYFromWorld(p.getZ() + 0.5);
				labelObstacles.add(conveyorMarkerRect(cx, cy));
				int convColor = CONVEYOR_COLOR;
				if (be.getLevel().getBlockEntity(p) instanceof ChainConveyorBlockEntity ccbe
					&& !ccbe.getLoopingPackages().isEmpty())
					convColor = CONVEYOR_LOOPING_COLOR;
				guiGraphics.fill(cx - CONVEYOR_SIZE_PX + 1, cy - CONVEYOR_SIZE_PX + 1, cx + CONVEYOR_SIZE_PX, cy + CONVEYOR_SIZE_PX, convColor);
			}

			int txPre = screenXFromWorld(term.getX() + 0.5);
			int tyPre = screenYFromWorld(term.getZ() + 0.5);
			labelObstacles.add(terminalMarkerRect(txPre, tyPre));

			Set<MapColumn> frogportColumns = new HashSet<>();
			for (NetworkFrogport fp : networkFrogports)
				frogportColumns.add(MapColumn.of(fp.pos()));
			for (MapColumn col : frogportColumns) {
				int fx = screenXFromWorld(col.x + 0.5);
				int fy = screenYFromWorld(col.z + 0.5);
				labelObstacles.add(frogportMarkerRect(fx, fy));
			}

			drawTravelingPackageDots(be, guiGraphics, labelObstacles);

			int mapRight = ml + mw;
			int mapBottom = mt + mh;
			List<IntRect> placedFrogportLabels = new ArrayList<>(frogportColumns.size());
			Map<MapColumn, List<NetworkFrogport>> frogportGroups = frogportsByMapColumn();
			List<MapColumn> columnsSorted = new ArrayList<>(frogportGroups.keySet());
			columnsSorted.sort(Comparator.comparingInt((MapColumn c) -> c.z).thenComparingInt(c -> c.x));

			for (MapColumn col : columnsSorted) {
				List<NetworkFrogport> group = frogportGroups.get(col);
				int fx = screenXFromWorld(col.x + 0.5);
				int fy = screenYFromWorld(col.z + 0.5);
				guiGraphics.fill(fx - FROGPORT_SIZE_PX + 1, fy - FROGPORT_SIZE_PX + 1, fx + FROGPORT_SIZE_PX, fy + FROGPORT_SIZE_PX, FROGPORT_COLOR);
				if (!group.stream().anyMatch(fp -> !fp.addressFilter().isEmpty()))
					continue;
				int maxLabelW = mw - 4;
				if (maxLabelW <= 8)
					continue;
				String mergedLabel = mergedFrogportInlineLabel(group);
				String clipped = truncateLabelToWidth(font, mergedLabel, maxLabelW);
				int textW = font.width(clipped);
				if (textW <= 0)
					continue;
				int[] pos = pickFrogportLabelPos(font, fx, fy, textW, labelObstacles, placedFrogportLabels, ml, mt, mapRight, mapBottom);
				if (pos != null) {
					guiGraphics.drawString(font, clipped, pos[0], pos[1], FROGPORT_LABEL_COLOR, false);
					placedFrogportLabels.add(frogportLabelBounds(pos[0], pos[1], textW, font));
				}
			}

			guiGraphics.fill(txPre - TERMINAL_SIZE_PX + 1, tyPre - TERMINAL_SIZE_PX + 1, txPre + TERMINAL_SIZE_PX, tyPre + TERMINAL_SIZE_PX, TERMINAL_COLOR);
		}

		guiGraphics.disableScissor();
	}

	/**
	 * Draws traveling-package dots. When {@code labelObstacles} is non-null, appends each dot's screen rect so
	 * frogport labels can avoid covering them.
	 */
	private void drawTravelingPackageDots(PackageSpidermonBlockEntity be, GuiGraphics guiGraphics,
		List<IntRect> labelObstacles) {
		Level level = be.getLevel();
		if (level == null)
			return;
		for (BlockPos convPos : connectedConveyors) {
			if (!(level.getBlockEntity(convPos) instanceof ChainConveyorBlockEntity ccbe))
				continue;
			for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : ccbe.getTravellingPackages().entrySet()) {
				BlockPos connection = entry.getKey();
				for (ChainConveyorPackage pkg : entry.getValue()) {
					Vec3 pos = ccbe.getPackagePosition(pkg.chainPosition, connection);
					if (pos == Vec3.ZERO)
						continue;
					int px = Mth.floor(mapMidScreenX() + (pos.x - viewCenterX) * pixelsPerBlock);
					int py = Mth.floor(mapMidScreenY() + (pos.z - viewCenterZ) * pixelsPerBlock);
					guiGraphics.fill(px, py, px + 2, py + 2, TRAVEL_PACKAGE_DOT_COLOR);
					if (labelObstacles != null)
						labelObstacles.add(new IntRect(px, py, px + 2, py + 2));
				}
			}
		}
	}

	private static IntRect conveyorMarkerRect(int cx, int cy) {
		return new IntRect(cx - CONVEYOR_SIZE_PX + 1, cy - CONVEYOR_SIZE_PX + 1, cx + CONVEYOR_SIZE_PX, cy + CONVEYOR_SIZE_PX);
	}

	private static IntRect terminalMarkerRect(int tx, int ty) {
		return new IntRect(tx - TERMINAL_SIZE_PX + 1, ty - TERMINAL_SIZE_PX + 1, tx + TERMINAL_SIZE_PX, ty + TERMINAL_SIZE_PX);
	}

	private static IntRect frogportMarkerRect(int fx, int fy) {
		return new IntRect(fx - FROGPORT_SIZE_PX + 1, fy - FROGPORT_SIZE_PX + 1, fx + FROGPORT_SIZE_PX, fy + FROGPORT_SIZE_PX);
	}

	/** Bounds for a single-line string drawn with {@link GuiGraphics#drawString(Font, String, int, int, int, boolean)} (baseline {@code textY}). */
	private static IntRect frogportLabelBounds(int textX, int textY, int textWidth, Font font) {
		return new IntRect(textX, textY - font.lineHeight + 1, textX + textWidth, textY + 3);
	}

	private static boolean rectIntersectsPadded(IntRect a, IntRect b, int pad) {
		return a.x0 < b.x1 + pad && a.x1 > b.x0 - pad && a.y0 < b.y1 + pad && a.y1 > b.y0 - pad;
	}

	private static boolean intersectsAny(IntRect rect, List<IntRect> others, int pad) {
		for (IntRect o : others) {
			if (rectIntersectsPadded(rect, o, pad))
				return true;
		}
		return false;
	}

	/**
	 * Picks screen (textX, textY) for a frogport address label. Returns null if every candidate overlaps map
	 * bounds, static obstacles, prior labels, or traveling dots. Chain edges are not treated as obstacles.
	 */
	private static int[] pickFrogportLabelPos(Font font, int fx, int fy, int textWidth, List<IntRect> obstacles,
		List<IntRect> placedLabels, int mapLeft, int mapTop, int mapRight, int mapBottom) {
		int lh = font.lineHeight;
		int hHalf = lh / 2;
		int centeredX = fx - (textWidth + 1) / 2;
		int belowY = fy + FROGPORT_SIZE_PX + lh + 2;
		int aboveY = fy - lh - FROGPORT_SIZE_PX - 2;
		int midY = fy - hHalf + 1;
		int[][] candidates = {
			{ fx + FROGPORT_SIZE_PX + 3, midY },
			{ fx - FROGPORT_SIZE_PX - 3 - textWidth, midY },
			{ centeredX, belowY },
			{ centeredX, aboveY },
			{ fx + FROGPORT_SIZE_PX + 3, belowY },
			{ fx + FROGPORT_SIZE_PX + 3, aboveY },
			{ fx - FROGPORT_SIZE_PX - 3 - textWidth, belowY },
			{ fx - FROGPORT_SIZE_PX - 3 - textWidth, aboveY },
		};
		for (int[] c : candidates) {
			int textX = c[0];
			int textY = c[1];
			IntRect bounds = frogportLabelBounds(textX, textY, textWidth, font);
			if (bounds.x0 < mapLeft || bounds.y0 < mapTop || bounds.x1 > mapRight || bounds.y1 > mapBottom)
				continue;
			if (intersectsAny(bounds, obstacles, FROGPORT_LABEL_OBSTACLE_PAD))
				continue;
			if (intersectsAny(bounds, placedLabels, FROGPORT_LABEL_OBSTACLE_PAD))
				continue;
			return new int[] { textX, textY };
		}
		return null;
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
		PackageSpidermonBlockEntity be = menu.contentHolder;
		if (be == null)
			return List.of();

		BlockPos term = be.getBlockPos();
		int tx = screenXFromWorld(term.getX() + 0.5);
		int ty = screenYFromWorld(term.getZ() + 0.5);

		boolean terminalHit = mouseInTerminalMarker(mouseX, mouseY, tx, ty);

		List<BlockPos> conveyorHits = new ArrayList<>();
		for (BlockPos p : connectedConveyors) {
			if (mouseInConveyorMarker(mouseX, mouseY, p))
				conveyorHits.add(p);
		}
		conveyorHits.sort(Comparator.<BlockPos>comparingLong(p -> p.getY()).thenComparingLong(p -> p.getX()));

		List<NetworkFrogport> frogportHits = new ArrayList<>();
		for (NetworkFrogport fp : networkFrogports) {
			if (mouseInFrogportMarker(mouseX, mouseY, fp.pos()))
				frogportHits.add(fp);
		}
		frogportHits.sort(Comparator.comparingLong((NetworkFrogport f) -> f.pos().getY())
			.thenComparingLong(f -> f.pos().getX()));

		if (terminalHit || !conveyorHits.isEmpty() || !frogportHits.isEmpty())
			return buildCombinedMapTooltip(be, terminalHit, conveyorHits, frogportHits);

		double mx = mouseX + 0.5;
		double my = mouseY + 0.5;
		for (MapEdge edge : chainEdges) {
			int x0 = screenXFromWorld(edge.a().getX() + 0.5);
			int y0 = screenYFromWorld(edge.a().getZ() + 0.5);
			int x1 = screenXFromWorld(edge.b().getX() + 0.5);
			int y1 = screenYFromWorld(edge.b().getZ() + 0.5);
			if (distSqPointSegment(mx, my, x0, y0, x1, y1) <= CHAIN_HOVER_PX_SQ)
				return chainEdgeHoverLines(be.getLevel(), edge);
		}

		return List.of();
	}

	private static boolean mouseInTerminalMarker(int mouseX, int mouseY, int tx, int ty) {
		return mouseX >= tx - TERMINAL_SIZE_PX + 1 && mouseX < tx + TERMINAL_SIZE_PX
			&& mouseY >= ty - TERMINAL_SIZE_PX + 1 && mouseY < ty + TERMINAL_SIZE_PX;
	}

	private boolean mouseInConveyorMarker(int mouseX, int mouseY, BlockPos p) {
		int cx = screenXFromWorld(p.getX() + 0.5);
		int cy = screenYFromWorld(p.getZ() + 0.5);
		return mouseX >= cx - CONVEYOR_SIZE_PX + 1 && mouseX < cx + CONVEYOR_SIZE_PX
			&& mouseY >= cy - CONVEYOR_SIZE_PX + 1 && mouseY < cy + CONVEYOR_SIZE_PX;
	}

	private boolean mouseInFrogportMarker(int mouseX, int mouseY, BlockPos p) {
		int fx = screenXFromWorld(p.getX() + 0.5);
		int fy = screenYFromWorld(p.getZ() + 0.5);
		return mouseX >= fx - FROGPORT_SIZE_PX + 1 && mouseX < fx + FROGPORT_SIZE_PX
			&& mouseY >= fy - FROGPORT_SIZE_PX + 1 && mouseY < fy + FROGPORT_SIZE_PX;
	}

	private List<Component> buildCombinedMapTooltip(PackageSpidermonBlockEntity be, boolean terminalHit,
		List<BlockPos> conveyorHits, List<NetworkFrogport> frogportHits) {
		List<Component> lines = new ArrayList<>();
		BlockPos term = be.getBlockPos();
		if (terminalHit) {
			lines.add(Component.translatable("spidermon.screen.map_tooltip_terminal",
				term.getX(), term.getY(), term.getZ()));
		}
		Level level = be.getLevel();
		if (!conveyorHits.isEmpty()) {
			if (!lines.isEmpty())
				lines.add(Component.empty());
			boolean firstConv = true;
			for (BlockPos p : conveyorHits) {
				if (!firstConv)
					lines.add(Component.empty());
				firstConv = false;
				ChainConveyorBlockEntity ccbe = level != null && level.getBlockEntity(p) instanceof ChainConveyorBlockEntity c ? c : null;
				lines.addAll(conveyorHoverLines(p, ccbe));
			}
		}
		if (!frogportHits.isEmpty()) {
			if (!lines.isEmpty())
				lines.add(Component.empty());
			lines.addAll(frogportsHoverTooltipLines(frogportHits));
		}
		return lines;
	}

	private static List<Component> frogportsHoverTooltipLines(List<NetworkFrogport> frogports) {
		List<Component> lines = new ArrayList<>();
		boolean first = true;
		for (NetworkFrogport fp : frogports) {
			if (!first)
				lines.add(Component.empty());
			first = false;
			BlockPos p = fp.pos();
			lines.add(Component.translatable("spidermon.screen.map_tooltip_frogport_location",
				p.getX(), p.getY(), p.getZ()));
			String filter = fp.addressFilter();
			if (filter.isEmpty())
				lines.add(Component.translatable("spidermon.screen.map_tooltip_frogport_empty")
					.withStyle(ChatFormatting.AQUA).withStyle(ChatFormatting.ITALIC));
			else
				lines.add(Component.literal(filter).withStyle(ChatFormatting.AQUA));
		}
		return lines;
	}

	private Map<MapColumn, List<NetworkFrogport>> frogportsByMapColumn() {
		Map<MapColumn, List<NetworkFrogport>> map = new LinkedHashMap<>();
		for (NetworkFrogport fp : networkFrogports)
			map.computeIfAbsent(MapColumn.of(fp.pos()), k -> new ArrayList<NetworkFrogport>()).add(fp);
		for (List<NetworkFrogport> g : map.values())
			g.sort(Comparator.comparingLong((NetworkFrogport fp) -> fp.pos().getY())
				.thenComparingLong(fp -> fp.pos().getX()));
		return map;
	}

	private static String mergedFrogportInlineLabel(List<NetworkFrogport> group) {
		String emptyText = I18n.get("spidermon.screen.map_tooltip_frogport_empty");
		List<String> parts = new ArrayList<>(group.size());
		for (NetworkFrogport fp : group) {
			String f = fp.addressFilter();
			parts.add(f.isEmpty() ? emptyText : f);
		}
		return String.join(", ", parts);
	}

	private static List<Component> conveyorHoverLines(BlockPos p, ChainConveyorBlockEntity ccbe) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("spidermon.screen.map_tooltip_conveyor",
			p.getX(), p.getY(), p.getZ()));
		if (ccbe == null)
			return lines;

		List<ChainConveyorPackage> looping = ccbe.getLoopingPackages();
		if (!looping.isEmpty()) {
			lines.add(Component.empty());
			lines.add(Component.translatable("spidermon.screen.map_tooltip_looping_header")
				.withStyle(ChatFormatting.YELLOW));
			boolean first = true;
			for (ChainConveyorPackage pkg : looping) {
				if (!first)
					lines.add(Component.empty());
				first = false;
				appendConveyorPackageTooltipLines(lines, pkg);
			}
		}

		return lines;
	}

	/**
	 * Traveling packages on this segment are owned by the conveyor at each end (see
	 * {@link ChainConveyorBlockEntity#getTravellingPackages()}); gather both directions for this edge.
	 */
	private static List<Component> chainEdgeHoverLines(Level level, MapEdge edge) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("spidermon.screen.map_tooltip_chain",
			edge.a().getX(), edge.a().getY(), edge.a().getZ(),
			edge.b().getX(), edge.b().getY(), edge.b().getZ()));

		if (level == null)
			return lines;

		BlockPos a = edge.a();
		BlockPos b = edge.b();
		BlockPos aToB = b.subtract(a);
		BlockPos bToA = a.subtract(b);

		List<ChainConveyorPackage> fromA = List.of();
		if (level.getBlockEntity(a) instanceof ChainConveyorBlockEntity ccA) {
			List<ChainConveyorPackage> list = ccA.getTravellingPackages().get(aToB);
			if (list != null && !list.isEmpty())
				fromA = list;
		}
		List<ChainConveyorPackage> fromB = List.of();
		if (level.getBlockEntity(b) instanceof ChainConveyorBlockEntity ccB) {
			List<ChainConveyorPackage> list = ccB.getTravellingPackages().get(bToA);
			if (list != null && !list.isEmpty())
				fromB = list;
		}

		if (fromA.isEmpty() && fromB.isEmpty())
			return lines;

		lines.add(Component.empty());

		boolean firstDir = true;
		if (!fromA.isEmpty()) {
			appendTravelingDirectionOnEdge(lines, b, fromA, firstDir);
			firstDir = false;
		}
		if (!fromB.isEmpty()) {
			appendTravelingDirectionOnEdge(lines, a, fromB, firstDir);
		}

		return lines;
	}

	private static void appendTravelingDirectionOnEdge(List<Component> lines, BlockPos toward,
		List<ChainConveyorPackage> pkgs, boolean firstDirectionGroup) {
		if (!firstDirectionGroup)
			lines.add(Component.empty());
		lines.add(Component.translatable("spidermon.screen.map_tooltip_traveling_header",
			toward.getX(), toward.getY(), toward.getZ()).withStyle(ChatFormatting.DARK_AQUA));
		boolean firstPkg = true;
		for (ChainConveyorPackage pkg : pkgs) {
			if (!firstPkg)
				lines.add(Component.empty());
			firstPkg = false;
			appendConveyorPackageTooltipLines(lines, pkg);
		}
	}

	/**
	 * Destination and contents mirror {@link PackageItem#appendHoverText} in Create.
	 */
	private static void appendConveyorPackageTooltipLines(List<Component> lines, ChainConveyorPackage pkg) {
		ItemStack stack = pkg.item;
		MutableComponent title = stack.getDisplayName().copy().withStyle(ChatFormatting.WHITE);
		title.append(Component.literal(" "));
		String addr = PackageItem.getAddress(stack);
		if (addr.isEmpty())
			title.append(Component.translatable("spidermon.screen.map_tooltip_looping_no_destination")
				.withStyle(ChatFormatting.DARK_GRAY));
		else
			title.append(Component.literal("\u2192 ").append(Component.literal(addr)).withStyle(ChatFormatting.GOLD));
		lines.add(title);

		if (!PackageItem.isPackage(stack))
			return;

		ItemStackHandler contents = PackageItem.getContents(stack);
		int visibleNames = 0;
		int skippedNames = 0;
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack slotStack = contents.getStackInSlot(i);
			if (slotStack.isEmpty())
				continue;
			if (slotStack.getItem() instanceof SpawnEggItem)
				continue;
			if (visibleNames >= 3) {
				skippedNames++;
				continue;
			}
			visibleNames++;
			lines.add(Component.literal(LOOPING_DETAIL_INDENT).append(
				slotStack.getHoverName()
					.copy()
					.append(" x")
					.append(String.valueOf(slotStack.getCount()))
					.withStyle(ChatFormatting.GRAY)));
		}
		if (skippedNames > 0)
			lines.add(Component.literal(LOOPING_DETAIL_INDENT).append(
				Component.translatable("container.shulkerBox.more", skippedNames)
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)));
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
		int bottomY = imageHeight - BOTTOM_HINT_Y;
		Component hintLeft = Component.translatable("spidermon.screen.map_hint_left");
		String zoomText = Integer.toString(pixelsPerBlock);
		Component hintRight = Component.translatable("spidermon.screen.map_hint_right", zoomText);
		guiGraphics.drawString(font, hintLeft, BOTTOM_HINT_PAD, bottomY, HINT_COLOR, false);
		int rightX = imageWidth - BOTTOM_HINT_PAD - font.width(hintRight);
		guiGraphics.drawString(font, hintRight, rightX, bottomY, HINT_COLOR, false);
	}

	private record MapEdge(BlockPos a, BlockPos b) {
	}

	/** World columns that share the same map pixel (X/Z only; Y may differ). */
	private record MapColumn(int x, int z) {
		static MapColumn of(BlockPos p) {
			return new MapColumn(p.getX(), p.getZ());
		}
	}

	/** Inclusive min, exclusive max — matches {@link GuiGraphics#fill} pixel ranges. */
	private record IntRect(int x0, int y0, int x1, int y1) {
	}
}
