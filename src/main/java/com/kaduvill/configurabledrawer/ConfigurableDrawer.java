package com.kaduvill.configurabledrawer;

import com.kaduvill.configurabledrawer.compat.TOPCompat;
import com.kaduvill.configurabledrawer.drawer.BlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.ItemBlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import com.kaduvill.configurabledrawer.menu.DrawerGuiHandler;
import com.kaduvill.configurabledrawer.menu.DrawerNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid = ConfigurableDrawer.MODID, name = "Configurable Drawer", version = ConfigurableDrawer.VERSION,
        acceptedMinecraftVersions = "[" + Tags.MC_VERSION + "]",
        dependencies = "required-after:storagedrawers@[5.5.3,)")
@Mod.EventBusSubscriber(modid = ConfigurableDrawer.MODID)
public final class ConfigurableDrawer {
    public static final String MODID = "configurabledrawer";
    public static final String VERSION = Tags.VERSION;
    @Mod.Instance(MODID) public static ConfigurableDrawer INSTANCE;
    @SidedProxy(clientSide = "com.kaduvill.configurabledrawer.client.ClientProxy",
            serverSide = "com.kaduvill.configurabledrawer.CommonProxy")
    public static CommonProxy PROXY;
    public static final BlockConfigurableDrawer BLOCK = new BlockConfigurableDrawer();
    public static final ItemBlock ITEM = (ItemBlock) new ItemBlockConfigurableDrawer(BLOCK)
                    .setRegistryName(MODID, "configurable_drawer")
                    .setCreativeTab(CreativeTabs.DECORATIONS);

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        DrawerConfig.load(event.getSuggestedConfigurationFile());
        GameRegistry.registerTileEntity(TileConfigurableDrawer.class, new ResourceLocation(MODID, "configurable_drawer"));
        DrawerNetwork.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new DrawerGuiHandler());

        if (Loader.isModLoaded("theoneprobe")) {
            TOPCompat.register();
        }
    }
    @SubscribeEvent public static void registerBlocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(BLOCK); }
    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) { event.getRegistry().register(ITEM); }

    @SubscribeEvent public static void onDrawerRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND || !event.getEntityPlayer().isSneaking()
                || !event.getWorld().isBlockLoaded(event.getPos(), false)) return;
        IBlockState state = event.getWorld().getBlockState(event.getPos());
        if (state.getBlock() != BLOCK) return;
        // Allow our block's onBlockActivated to run while sneaking.
        if (event.getEntityPlayer().getHeldItemMainhand().isEmpty() || event.getFace() == state.getValue(BlockConfigurableDrawer.FACING))
            event.setUseBlock(Event.Result.ALLOW);
    }
}
