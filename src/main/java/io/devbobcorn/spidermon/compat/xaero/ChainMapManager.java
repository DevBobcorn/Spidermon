package io.devbobcorn.spidermon.compat.xaero;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectedPort;

import io.devbobcorn.spidermon.SpidermonMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Client-side cache of chain conveyor network data, rebuilt periodically by
 * scanning loaded chunks. This avoids depending on a specific
 * {@link io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity} to walk
 * the network, which wouldn't exist when viewing Xaero's World Map.
 */
public class ChainMapManager {
	public static final ChainMapManager INSTANCE = new ChainMapManager();

	private static final int REFRESH_INTERVAL_TICKS = 20;

	private List<ChainEdge> edges = List.of();
	private Set<BlockPos> conveyorPositions = Set.of();
	private List<ChainFrogport> frogports = List.of();
	private long lastRefreshTick = -REFRESH_INTERVAL_TICKS;

	public record ChainEdge(BlockPos a, BlockPos b) {
	}

	public record ChainFrogport(BlockPos pos, String addressFilter) {
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}
		long gameTick = mc.level.getGameTime();
		if (gameTick - lastRefreshTick < REFRESH_INTERVAL_TICKS)
			return;
		lastRefreshTick = gameTick;
		rebuild(mc.level);
	}

	public void clear() {
		edges = List.of();
		conveyorPositions = Set.of();
		frogports = List.of();
		lastRefreshTick = -REFRESH_INTERVAL_TICKS;
	}

	private void rebuild(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return;

		int renderDist = mc.options.renderDistance().get();
		int pcx = mc.player.chunkPosition().x;
		int pcz = mc.player.chunkPosition().z;

		Set<BlockPos> found = new HashSet<>();
		Map<BlockPos, ChainConveyorBlockEntity> beMap = new LinkedHashMap<>();

		int chunksChecked = 0;
		int chunksLoaded = 0;
		int totalBlockEntities = 0;

		for (int cx = pcx - renderDist; cx <= pcx + renderDist; cx++) {
			for (int cz = pcz - renderDist; cz <= pcz + renderDist; cz++) {
				chunksChecked++;
				ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
				if (!(chunk instanceof LevelChunk lc))
					continue;
				chunksLoaded++;
				Set<BlockPos> bePositions = lc.getBlockEntitiesPos();
				totalBlockEntities += bePositions.size();
				for (BlockPos bePos : bePositions) {
					if (lc.getBlockEntity(bePos) instanceof ChainConveyorBlockEntity ccbe) {
						BlockPos immutable = bePos.immutable();
						found.add(immutable);
						beMap.put(immutable, ccbe);
					}
				}
			}
		}

		List<ChainEdge> newEdges = new ArrayList<>();
		for (Map.Entry<BlockPos, ChainConveyorBlockEntity> entry : beMap.entrySet()) {
			BlockPos pos = entry.getKey();
			ChainConveyorBlockEntity ccbe = entry.getValue();
			for (BlockPos off : ccbe.connections) {
				BlockPos neighbor = pos.offset(off);
				if (found.contains(neighbor) && pos.compareTo(neighbor) < 0)
					newEdges.add(new ChainEdge(pos, neighbor));
			}
		}

		Map<BlockPos, Set<String>> frogportMap = new LinkedHashMap<>();
		for (Map.Entry<BlockPos, ChainConveyorBlockEntity> entry : beMap.entrySet()) {
			BlockPos pos = entry.getKey();
			ChainConveyorBlockEntity ccbe = entry.getValue();
			collectFrogports(frogportMap, pos, ccbe.loopPorts);
			collectFrogports(frogportMap, pos, ccbe.travelPorts);
		}
		List<ChainFrogport> newFrogports = new ArrayList<>();
		for (Map.Entry<BlockPos, Set<String>> e : frogportMap.entrySet()) {
			for (String filter : e.getValue())
				newFrogports.add(new ChainFrogport(e.getKey(), filter));
		}

		this.edges = List.copyOf(newEdges);
		this.conveyorPositions = Set.copyOf(found);
		this.frogports = List.copyOf(newFrogports);

		SpidermonMod.LOGGER.info("[Spidermon/Xaero] ChainMapManager rebuild: {} conveyors, {} edges, {} frogports | chunks checked={}, loaded={}, totalBEs={} (±{} around [{},{}])",
			found.size(), newEdges.size(), newFrogports.size(),
			chunksChecked, chunksLoaded, totalBlockEntities, renderDist, pcx, pcz);
	}

	private static void collectFrogports(Map<BlockPos, Set<String>> out, BlockPos conveyorPos,
		Map<BlockPos, ConnectedPort> ports) {
		for (Map.Entry<BlockPos, ConnectedPort> e : ports.entrySet()) {
			BlockPos frogPos = conveyorPos.offset(e.getKey());
			String filter = e.getValue().filter() != null ? e.getValue().filter() : "";
			out.computeIfAbsent(frogPos, k -> new LinkedHashSet<>()).add(filter);
		}
	}

	public List<ChainEdge> getEdges() {
		return edges;
	}

	public Set<BlockPos> getConveyorPositions() {
		return conveyorPositions;
	}

	public List<ChainFrogport> getFrogports() {
		return frogports;
	}
}
