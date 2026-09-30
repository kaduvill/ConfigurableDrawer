package com.kaduvill.configurabledrawer.drawer;

import java.util.List;

import javax.annotation.Nullable;

import com.kaduvill.configurabledrawer.DrawerConfig;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.IFrameable;

public final class ItemBlockConfigurableDrawer extends ItemBlock implements IFrameable {

    public ItemBlockConfigurableDrawer(Block block) {
        super(block);
    }
    
    @Override
    public ItemStack decorate(ItemStack input, ItemStack matSide, ItemStack matTrim, ItemStack matFront) {
        ItemStack result = input.copy();
        result.setCount(1);
        NBTTagCompound tag = result.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        setMaterial(tag, "MatS", matSide);
        setMaterial(tag, "MatT", matTrim);
        setMaterial(tag, "MatF", matFront);
        result.setTagCompound(tag);
        return result;
    }

    private static void setMaterial(NBTTagCompound tag, String key, ItemStack material) {
        if (material.isEmpty()) {
            tag.removeTag(key);
            return;
        }
        ItemStack copy = material.copy();
        copy.setCount(1);
        tag.setTag(key, copy.writeToNBT(new NBTTagCompound()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(
            ItemStack stack,
            @Nullable World world,
            List<String> tooltip,
            ITooltipFlag flag) {

        super.addInformation(stack, world, tooltip, flag);

        NBTTagCompound stackTag = stack.getTagCompound();
        NBTTagCompound data = stackTag != null && stackTag.hasKey("DrawerData", Constants.NBT.TAG_COMPOUND)
                ? stackTag.getCompoundTag("DrawerData") : null;

        long stored = data != null && data.hasKey("Count", Constants.NBT.TAG_ANY_NUMERIC)
                ? Math.max(0L, data.getLong("Count")) : 0L;

        long capacity = data != null && data.hasKey("Capacity", Constants.NBT.TAG_ANY_NUMERIC)
                ? Math.max(1L, data.getLong("Capacity")) : DrawerConfig.defaultCapacity;

        ItemStack filter = ItemStack.EMPTY;

        if (data != null && data.hasKey("Filter", Constants.NBT.TAG_COMPOUND)) {
            filter = new ItemStack(data.getCompoundTag("Filter"));
        }

        if (filter.isEmpty()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("configurabledrawer.tooltip.unconfigured"));
        } else {
            tooltip.add(TextFormatting.GRAY + I18n.format("configurabledrawer.tooltip.filter", filter.getDisplayName()));
        }

        tooltip.add(TextFormatting.GRAY + I18n.format("configurabledrawer.tooltip.stored", DrawerNumberFormat.compact(stored), DrawerNumberFormat.compact(capacity)));

        if (stored > capacity) {
            tooltip.add(TextFormatting.RED + I18n.format(data != null && data.getBoolean("VoidOverflow")
                            ? "configurabledrawer.tooltip.overcapacity_void"
                            : "configurabledrawer.tooltip.overcapacity",
                    DrawerNumberFormat.compact(stored - capacity)));
        }

        if (GuiScreen.isShiftKeyDown()) {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("configurabledrawer.tooltip.exact_stored", DrawerNumberFormat.exact(stored)));
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("configurabledrawer.tooltip.exact_capacity", DrawerNumberFormat.exact(capacity)));
        } else {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("configurabledrawer.tooltip.shift"));
        }
    }
}