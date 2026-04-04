package io.devbobcorn.spidermon;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.devbobcorn.spidermon.block.PackageSpidermonBlock;
import io.devbobcorn.spidermon.block.PackageSpidermonBlockEntity;
import io.devbobcorn.spidermon.block.PackageSpidermonItem;
import io.devbobcorn.spidermon.block.PackageSpidermonMenu;
import io.devbobcorn.spidermon.network.SpidermonPlacementPacket;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SpidermonMod.MODID)
public class SpidermonMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "spidermon";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MODID);

    // Frog Terminal — block, item, and block entity
    public static final DeferredBlock<PackageSpidermonBlock> PACKAGE_SPIDERMON_BLOCK =
            BLOCKS.register("package_spidermon",
                    () -> new PackageSpidermonBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLUE)
                            .strength(3.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()));

    public static final DeferredItem<PackageSpidermonItem> PACKAGE_SPIDERMON_ITEM =
            ITEMS.registerItem("package_spidermon",
                    props -> new PackageSpidermonItem(PACKAGE_SPIDERMON_BLOCK.get(), props));

    public static final DeferredHolder<MenuType<?>, MenuType<PackageSpidermonMenu>> PACKAGE_SPIDERMON_MENU =
            MENU_TYPES.register("package_spidermon",
                    () -> IMenuTypeExtension.create(PackageSpidermonMenu::new));

    public static final Supplier<BlockEntityType<PackageSpidermonBlockEntity>> SPIDERMON_BE =
            BLOCK_ENTITY_TYPES.register("package_spidermon",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new PackageSpidermonBlockEntity(
                                    SpidermonMod.SPIDERMON_BE.get(), pos, state),
                            PACKAGE_SPIDERMON_BLOCK.get()).build(null));

    // Creates a creative tab with the id "spidermon:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spidermon")) //The language key for the title of your CreativeModeTab
            // .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PACKAGE_SPIDERMON_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PACKAGE_SPIDERMON_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SpidermonMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(SpidermonMod::registerPayloads);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (SpidermonMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(SpidermonPlacementPacket.TYPE,
            SpidermonPlacementPacket.STREAM_CODEC, SpidermonPlacementPacket::handle);
        registrar.playToClient(SpidermonPlacementPacket.ClientBoundRequest.TYPE,
            SpidermonPlacementPacket.ClientBoundRequest.STREAM_CODEC, SpidermonPlacementPacket.ClientBoundRequest::handle);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
