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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Client-side cache of chain conveyor network data, rebuilt periodically by
 * scanning loaded chunks. This avoids depending on a specific
 * {@link io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity} to walk
 * the network, which wouldn't exist when viewing Xaero's World Map.
 *
 * Conveyors in unloaded chunks are kept in a persistent cache so they remain
 * visible on the map (rendered in dimmer colors by the overlay).
 */
public class ChainMapManager {
	public static final ChainMapManager INSTANCE = new ChainMapManager();

	private static final int REFRESH_INTERVAL_TICKS = 20;

	private List<ChainEdge> edges = List.of();
	private List<ChainEdge> unloadedEdges = List.of();
	private Set<BlockPos> conveyorPositions = Set.of();
	private Set<BlockPos> unloadedConveyorPositions = Set.of();
	private List<ChainFrogport> frogports = List.of();
	private long lastRefreshTick = -REFRESH_INTERVAL_TICKS;

	/**
	 * Persistent cache: maps each known conveyor to its absolute neighbor
	 * positions. Survives chunk unloads; pruned when the conveyor's chunk is
	 * loaded but the block entity is gone (i.e. the conveyor was broken).
	 */
	private final Map<BlockPos, Set<BlockPos>> connectionCache = new LinkedHashMap<>();
	private final Map<BlockPos, Map<BlockPos, Set<String>>> frogportCache = new LinkedHashMap<>();

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
		unloadedEdges = List.of();
		conveyorPositions = Set.of();
		unloadedConveyorPositions = Set.of();
		frogports = List.of();
		connectionCache.clear();
		frogportCache.clear();
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
		Set<Long> loadedChunkKeys = new HashSet<>();

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
				loadedChunkKeys.add(ChunkPos.asLong(cx, cz));
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

		// Update cache with freshly loaded conveyor data
		for (Map.Entry<BlockPos, ChainConveyorBlockEntity> entry : beMap.entrySet()) {
			BlockPos pos = entry.getKey();
			ChainConveyorBlockEntity ccbe = entry.getValue();
			Set<BlockPos> neighbors = new HashSet<>();
			for (BlockPos off : ccbe.connections) {
				neighbors.add(pos.offset(off));
			}
			connectionCache.put(pos, neighbors);
		}

		// Prune cache entries whose chunk IS loaded but conveyor no longer exists
		connectionCache.keySet().removeIf(pos -> {
			long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
			return loadedChunkKeys.contains(chunkKey) && !found.contains(pos);
		});

		// Determine unloaded conveyors: in cache but not currently loaded
		Set<BlockPos> unloaded = new HashSet<>(connectionCache.keySet());
		unloaded.removeAll(found);

		// Build edges from the unified cache, categorizing by loaded state
		List<ChainEdge> newEdges = new ArrayList<>();
		List<ChainEdge> newUnloadedEdges = new ArrayList<>();

		for (Map.Entry<BlockPos, Set<BlockPos>> entry : connectionCache.entrySet()) {
			BlockPos pos = entry.getKey();
			for (BlockPos neighbor : entry.getValue()) {
				if (!connectionCache.containsKey(neighbor))
					continue;
				if (pos.compareTo(neighbor) >= 0)
					continue;

				if (found.contains(pos) && found.contains(neighbor)) {
					newEdges.add(new ChainEdge(pos, neighbor));
				} else {
					newUnloadedEdges.add(new ChainEdge(pos, neighbor));
				}
			}
		}

		// Update frogport cache for loaded conveyors
		for (Map.Entry<BlockPos, ChainConveyorBlockEntity> entry : beMap.entrySet()) {
			BlockPos pos = entry.getKey();
			ChainConveyorBlockEntity ccbe = entry.getValue();
			Map<BlockPos, Set<String>> fpMap = new LinkedHashMap<>();
			collectFrogports(fpMap, pos, ccbe.loopPorts);
			collectFrogports(fpMap, pos, ccbe.travelPorts);
			frogportCache.put(pos, fpMap);
		}

		// Prune frogport cache in sync with connection cache
		frogportCache.keySet().retainAll(connectionCache.keySet());

		// Build frogports list (loaded conveyors only — we can't inspect
		// unloaded block entities for live frogport state)
		List<ChainFrogport> newFrogports = new ArrayList<>();
		for (Map.Entry<BlockPos, ChainConveyorBlockEntity> entry : beMap.entrySet()) {
			BlockPos pos = entry.getKey();
			Map<BlockPos, Set<String>> fpMap = frogportCache.get(pos);
			if (fpMap == null)
				continue;
			for (Map.Entry<BlockPos, Set<String>> e : fpMap.entrySet()) {
				for (String filter : e.getValue())
					newFrogports.add(new ChainFrogport(e.getKey(), filter));
			}
		}

		this.edges = List.copyOf(newEdges);
		this.unloadedEdges = List.copyOf(newUnloadedEdges);
		this.conveyorPositions = Set.copyOf(found);
		this.unloadedConveyorPositions = Set.copyOf(unloaded);
		this.frogports = List.copyOf(newFrogports);

		SpidermonMod.LOGGER.info("[Spidermon/Xaero] ChainMapManager rebuild: {} conveyors ({} cached unloaded), {} edges ({} unloaded), {} frogports | chunks checked={}, loaded={}, totalBEs={} (±{} around [{},{}])",
			found.size(), unloaded.size(), newEdges.size(), newUnloadedEdges.size(), newFrogports.size(),
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

	public List<ChainEdge> getUnloadedEdges() {
		return unloadedEdges;
	}

	public Set<BlockPos> getConveyorPositions() {
		return conveyorPositions;
	}

	public Set<BlockPos> getUnloadedConveyorPositions() {
		return unloadedConveyorPositions;
	}

	public List<ChainFrogport> getFrogports() {
		return frogports;
	}
}
