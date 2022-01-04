package dev.m8u.meshvoxelizer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;


@Mod("meshvoxelizer")
public class MeshVoxelizer {
    public static ByteArrayOutputStream baos;
    public PrintStream ps;

    public MeshVoxelizer() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        MinecraftForge.EVENT_BUS.register(this);

        File modelsDirectory = new File(Minecraft.getInstance().gameDir+"/mods/MeshVoxelizer/");
        if (!modelsDirectory.exists()) {
            modelsDirectory.mkdir();
        }
    }

    private void setup(final FMLCommonSetupEvent event) { }

    private void doClientStuff(final FMLClientSetupEvent event) { }

    private void enqueueIMC(final InterModEnqueueEvent event) { }

    private void processIMC(final InterModProcessEvent event) { }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        baos = new ByteArrayOutputStream();
        ps = new PrintStream(baos);
        System.setErr(ps);
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        static final VoxelizerOriginBlock voxelizerOriginBlock = new VoxelizerOriginBlock();
        static final BlockItem voxelizerOriginBlockItem = (BlockItem) new BlockItem(voxelizerOriginBlock,
                new Item.Properties().group(ItemGroup.SEARCH))
                    .setRegistryName(voxelizerOriginBlock.getRegistryName());

        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            blockRegistryEvent.getRegistry().register(voxelizerOriginBlock);
        }

        @SubscribeEvent
        public static void onItemsRegistry(final RegistryEvent.Register<Item> itemRegistryEvent) {
            ModelLoader.addSpecialModel(new ModelResourceLocation(voxelizerOriginBlockItem.getRegistryName(),"facing"));
            itemRegistryEvent.getRegistry().register(voxelizerOriginBlockItem);
        }
    }
}
