package com.kaduvill.configurabledrawer.menu;

import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class DrawerGuiHandler implements IGuiHandler {
    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return id == 0 && tile instanceof TileConfigurableDrawer
                ? new ContainerDrawer(player.inventory, (TileConfigurableDrawer) tile) : null;
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return id == 0 && tile instanceof TileConfigurableDrawer
                ? ConfigurableDrawer.PROXY.createGui(player, (TileConfigurableDrawer) tile) : null;
    }
}
