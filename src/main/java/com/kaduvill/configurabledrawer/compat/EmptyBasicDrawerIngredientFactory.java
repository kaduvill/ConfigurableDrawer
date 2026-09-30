package com.kaduvill.configurabledrawer.compat;

import com.google.gson.JsonObject;
import com.jaquadro.minecraft.storagedrawers.core.ModBlocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.BlockPlanks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.crafting.IIngredientFactory;
import net.minecraftforge.common.crafting.JsonContext;

public final class EmptyBasicDrawerIngredientFactory implements IIngredientFactory {
    @Nonnull @Override public Ingredient parse(JsonContext context, JsonObject json) {
        return new EmptyBasicDrawer();
    }

    private static final class EmptyBasicDrawer extends Ingredient {
        private EmptyBasicDrawer() { super(displayStacks()); }

        private static ItemStack[] displayStacks() {
            BlockPlanks.EnumType[] woods = BlockPlanks.EnumType.values();
            ItemStack[] stacks = new ItemStack[woods.length];

            for (int i = 0; i < woods.length; i++) {
                ItemStack stack = new ItemStack(ModBlocks.basicDrawers, 1, 0);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("material", woods[i].getName());
                stack.setTagCompound(tag);
                stacks[i] = stack;
            }
            return stacks;
        }

        @Override public boolean apply(@Nullable ItemStack stack) {
            if (stack == null || stack.isEmpty()
                    || stack.getItem() != Item.getItemFromBlock(ModBlocks.basicDrawers)
                    || stack.getMetadata() != 0) return false;

            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || tag.getKeySet().isEmpty()) return true; // Untagged defaults to oak.
            if (tag.getKeySet().size() != 1 || !tag.hasKey("material", 8)) return false;

            for (BlockPlanks.EnumType wood : BlockPlanks.EnumType.values())
                if (wood.getName().equals(tag.getString("material"))) return true;
            return false;
        }

        @Override public boolean isSimple() { return false; }
    }
}