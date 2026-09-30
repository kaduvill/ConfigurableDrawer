package com.kaduvill.configurabledrawer.compat;

import java.util.function.Function;

import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.DrawerNumberFormat;
import com.kaduvill.configurabledrawer.drawer.DrawerStorage;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;

import mcjty.theoneprobe.api.IProbeConfig;
import mcjty.theoneprobe.api.IProbeConfigProvider;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

public final class TOPCompat implements
        Function<ITheOneProbe, Void>,
        IProbeInfoProvider,
        IProbeConfigProvider {

    public static void register() {
        FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe", TOPCompat.class.getName());
    }

    private static String loc(String key) {return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;}

    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(this);
        probe.registerProbeConfigProvider(this);
        return null;
    }

    @Override
    public String getID() {return ConfigurableDrawer.MODID + ":drawer";}

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState state, IProbeHitData data) {

        if (state.getBlock() != ConfigurableDrawer.BLOCK) {
            return;
        }

        TileEntity tile = world.getTileEntity(data.getPos());
        if (!(tile instanceof TileConfigurableDrawer)) {
            return;
        }

        DrawerStorage storage = ((TileConfigurableDrawer) tile).storage();
        ItemStack filter = storage.filter();

        if (filter.isEmpty()) {
            probeInfo.text(TextStyleClass.WARNING + loc("configurabledrawer.top.unconfigured"));
        } else {
            IProbeInfo itemBox = probeInfo.vertical(probeInfo.defaultLayoutStyle().borderColor(0xff006699).spacing(0));
            itemBox.horizontal(probeInfo.defaultLayoutStyle().spacing(0)).item(filter);
        }

        String stored = DrawerNumberFormat.compact(storage.count());
        String capacity = DrawerNumberFormat.compact(storage.capacity());

        probeInfo.text(TextStyleClass.LABEL + loc("configurabledrawer.top.stored") + " " + TextStyleClass.INFO + stored);

        if (storage.count() > storage.capacity()) {
            probeInfo.text(TextStyleClass.ERROR + loc(storage.voidOverflow()
                    ? "configurabledrawer.top.overcapacity_void"
                    : "configurabledrawer.top.overcapacity_blocked"));
        }

        if (mode == ProbeMode.EXTENDED) {
            probeInfo.text(TextStyleClass.LABEL + loc("configurabledrawer.top.capacity") + " "
                    + TextStyleClass.INFO + capacity);
        }
    }

    @Override
    public void getProbeConfig(IProbeConfig config, EntityPlayer player, World world, IBlockState state, IProbeHitData data) {
        if (state.getBlock() == ConfigurableDrawer.BLOCK) {
            config.showChestContents(IProbeConfig.ConfigMode.NOT);
            config.showChestContentsDetailed(IProbeConfig.ConfigMode.NOT);
        }
    }

    @Override
    public void getProbeConfig(IProbeConfig config, EntityPlayer player, World world, Entity entity, IProbeHitEntityData data) {}


}