package com.kaduvill.configurabledrawer;

import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import com.kaduvill.configurabledrawer.menu.DrawerNetwork;
import net.minecraft.entity.player.EntityPlayer;

public class CommonProxy {
    public Object createGui(EntityPlayer player, TileConfigurableDrawer tile) { return null; }
    public void receiveState(DrawerNetwork.State state) { }
}
