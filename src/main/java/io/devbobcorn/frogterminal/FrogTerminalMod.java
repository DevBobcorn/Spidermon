package io.devbobcorn.frogterminal;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.devbobcorn.frogterminal.block.AdvancedStressometerBlock;
import io.devbobcorn.frogterminal.block.AdvancedStressometerBlockEntity;
import io.devbobcorn.frogterminal.block.FrogTerminalBlock;
import io.devbobcorn.frogterminal.block.FrogTerminalBlockEntity;
import io.devbobcorn.frogterminal.block.FrogTerminalItem;
import io.devbobcorn.frogterminal.block.FrogTerminalMenu;
import io.devbobcorn.frogterminal.network.FrogTerminalPlacementPacket;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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
@Mod(FrogTerminalMod.MODID)
public class FrogTerminalMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "frogterminal";
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

    // Advanced Stressometer — block, item, and block entity
    public static final DeferredBlock<AdvancedStressometerBlock> ADVANCED_STRESSOMETER_BLOCK =
            BLOCKS.register("advanced_stressometer",
                    () -> new AdvancedStressometerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PODZOL)
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(2.0F)
                            .sound(SoundType.WOOD)
                            .ignitedByLava()
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> ADVANCED_STRESSOMETER_ITEM =
            ITEMS.registerSimpleBlockItem("advanced_stressometer", ADVANCED_STRESSOMETER_BLOCK);

    public static final Supplier<BlockEntityType<AdvancedStressometerBlockEntity>> ADVANCED_STRESSOMETER_BE =
            BLOCK_ENTITY_TYPES.register("advanced_stressometer",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new AdvancedStressometerBlockEntity(
                                    FrogTerminalMod.ADVANCED_STRESSOMETER_BE.get(), pos, state),
                            ADVANCED_STRESSOMETER_BLOCK.get()).build(null));

    // Frog Terminal — block, item, and block entity
    public static final DeferredBlock<FrogTerminalBlock> FROG_TERMINAL_BLOCK =
            BLOCKS.register("frog_terminal",
                    () -> new FrogTerminalBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLUE)
                            .strength(3.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()));

    public static final DeferredItem<FrogTerminalItem> FROG_TERMINAL_ITEM =
            ITEMS.registerItem("frog_terminal",
                    props -> new FrogTerminalItem(FROG_TERMINAL_BLOCK.get(), props));

    public static final DeferredHolder<MenuType<?>, MenuType<FrogTerminalMenu>> FROG_TERMINAL_MENU =
            MENU_TYPES.register("frog_terminal",
                    () -> IMenuTypeExtension.create(FrogTerminalMenu::new));

    public static final Supplier<BlockEntityType<FrogTerminalBlockEntity>> FROG_TERMINAL_BE =
            BLOCK_ENTITY_TYPES.register("frog_terminal",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new FrogTerminalBlockEntity(
                                    FrogTerminalMod.FROG_TERMINAL_BE.get(), pos, state),
                            FROG_TERMINAL_BLOCK.get()).build(null));

    // Creates a creative tab with the id "frogterminal:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.frogterminal")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ADVANCED_STRESSOMETER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ADVANCED_STRESSOMETER_ITEM.get());
                output.accept(FROG_TERMINAL_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public FrogTerminalMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(FrogTerminalMod::registerPayloads);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (FrogTerminalMod) to respond directly to events.
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
        registrar.playToServer(FrogTerminalPlacementPacket.TYPE,
            FrogTerminalPlacementPacket.STREAM_CODEC, FrogTerminalPlacementPacket::handle);
        registrar.playToClient(FrogTerminalPlacementPacket.ClientBoundRequest.TYPE,
            FrogTerminalPlacementPacket.ClientBoundRequest.STREAM_CODEC, FrogTerminalPlacementPacket.ClientBoundRequest::handle);
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
