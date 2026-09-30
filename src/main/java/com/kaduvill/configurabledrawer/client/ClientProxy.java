package com.kaduvill.configurabledrawer.client;

import com.kaduvill.configurabledrawer.*;
import com.kaduvill.configurabledrawer.client.render.DrawerFrameModel;
import com.kaduvill.configurabledrawer.client.render.DrawerRenderer;
import com.kaduvill.configurabledrawer.drawer.BlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import com.kaduvill.configurabledrawer.menu.ContainerDrawer;
import com.kaduvill.configurabledrawer.menu.DrawerNetwork;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = ConfigurableDrawer.MODID, value = Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ConfigurableDrawer.ITEM, 0,
                new ModelResourceLocation(ConfigurableDrawer.ITEM.getRegistryName(), "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileConfigurableDrawer.class, new DrawerRenderer());
    }
    @SubscribeEvent public static void bakeModels(ModelBakeEvent event) {
        ResourceLocation name = ConfigurableDrawer.BLOCK.getRegistryName();

        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            ModelResourceLocation location = new ModelResourceLocation(name, "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(location);
            if (base != null) {
                IBlockState state = ConfigurableDrawer.BLOCK.getDefaultState()
                        .withProperty(BlockConfigurableDrawer.FACING, facing);
                event.getModelRegistry().putObject(location, new DrawerFrameModel(base, state));
            }
        }

        ModelResourceLocation location = new ModelResourceLocation(name, "inventory");
        IBakedModel base = event.getModelRegistry().getObject(location);
        if (base != null)
            event.getModelRegistry().putObject(location, new DrawerFrameModel(base, null));
    }
    @Override public Object createGui(EntityPlayer player, TileConfigurableDrawer tile) {
        return new GuiDrawer(new ContainerDrawer(player.inventory, tile));
    }
    @Override public void receiveState(DrawerNetwork.State state) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> {
            if (minecraft.player == null || !(minecraft.player.openContainer instanceof ContainerDrawer)) return;
            ContainerDrawer container = (ContainerDrawer) minecraft.player.openContainer;
            if (container.windowId != state.window || !container.tile.getPos().equals(state.pos)) return;
            container.shownCount = state.count;
            container.shownCapacity = state.capacity;
            container.shownMaximum = state.maximum;
            container.shownFilter = state.filter.copy();
            container.shownSides = state.sides;
            container.shownVoidOverflow = state.voidOverflow;
            container.shownFrontIcon = state.frontIcon;
            container.shownRedstone = state.redstone;
            container.shownThreshold = state.threshold;
        });
    }
}
