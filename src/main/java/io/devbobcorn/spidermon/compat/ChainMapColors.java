package io.devbobcorn.spidermon.compat;

/**
 * Shared color constants for chain conveyor map rendering, used by
 * {@link io.devbobcorn.spidermon.client.PackageSpidermonScreen},
 * {@link io.devbobcorn.spidermon.compat.xaero.XaeroChainMap}, and
 * {@link io.devbobcorn.spidermon.compat.journeymap.JourneyChainMap}.
 */
public final class ChainMapColors {
	public static final int EDGE_COLOR = 0xFF6eb8d4;
	public static final int EDGE_OUTLINE_COLOR = 0xFF00002a;
	public static final int CONVEYOR_COLOR = 0xFFe8c84a;
	public static final int CONVEYOR_OUTLINE_COLOR = 0xFFb8a000;
	public static final int CONVEYOR_LOOPING_COLOR = 0xFFe066b3;
	public static final int CONVEYOR_LOOPING_OUTLINE_COLOR = 0xFFa14980;
	public static final int FROGPORT_COLOR = 0xFF5577cc;
	public static final int PACKAGE_COLOR = 0xFFFFFFFF;

	public static final int UNLOADED_EDGE_COLOR = 0xFF888888;
	public static final int UNLOADED_EDGE_OUTLINE_COLOR = 0xFF444444;
	public static final int UNLOADED_CONVEYOR_COLOR = 0xFFaaaaaa;
	public static final int UNLOADED_CONVEYOR_OUTLINE_COLOR = 0xFF555555;

	private ChainMapColors() {}
}
