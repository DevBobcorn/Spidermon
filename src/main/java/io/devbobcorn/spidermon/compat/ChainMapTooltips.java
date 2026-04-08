package io.devbobcorn.spidermon.compat;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Shared hover text for chain map UIs (package screen, Xaero world map overlay,
 * and JourneyMap overlay).
 */
public final class ChainMapTooltips {
	private static final double CHAIN_HOVER_PX = 3.0;
	private static final double CHAIN_HOVER_PX_SQ = CHAIN_HOVER_PX * CHAIN_HOVER_PX;
	/** Spaces before looping-package destination and content lines (under the package title). */
	private static final String LOOPING_DETAIL_INDENT = "    ";

	private ChainMapTooltips() {
	}

	public record FrogportEntry(BlockPos pos, String addressFilter) {
	}

	public static List<Component> frogportsHoverLines(List<FrogportEntry> frogports) {
		List<Component> lines = new ArrayList<>();
		boolean first = true;
		for (FrogportEntry fp : frogports) {
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

	public static List<Component> conveyorHoverLines(BlockPos p, ChainConveyorBlockEntity ccbe) {
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
	public static List<Component> chainEdgeHoverLines(Level level, BlockPos a, BlockPos b) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("spidermon.screen.map_tooltip_chain",
			a.getX(), a.getY(), a.getZ(),
			b.getX(), b.getY(), b.getZ()));

		if (level == null)
			return lines;

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

	public static boolean isNearChainEdgeScreen(
		double mouseX, double mouseY,
		int x0, int y0, int x1, int y1) {
		double mx = mouseX + 0.5;
		double my = mouseY + 0.5;
		return distSqPointSegment(mx, my, x0, y0, x1, y1) <= CHAIN_HOVER_PX_SQ;
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
}
