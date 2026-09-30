package com.kaduvill.configurabledrawer.compat;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import com.kaduvill.configurabledrawer.client.GuiDrawer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.item.ItemStack;

@JEIPlugin
public final class DrawerJeiPlugin implements IModPlugin {
    @Override public void register(IModRegistry registry) {
        registry.addGhostIngredientHandler(GuiDrawer.class, new IGhostIngredientHandler<GuiDrawer>() {
            @Override public <I> List<Target<I>> getTargets(GuiDrawer gui, I ingredient, boolean doStart) {
                if (!gui.isStorageView() || !(ingredient instanceof ItemStack) || ((ItemStack) ingredient).isEmpty() || gui.drawer.shownCount != 0)
                    return Collections.emptyList();
                return Collections.singletonList(new Target<I>() {
                    @Override public Rectangle getArea() { return gui.ghostArea(); }
                    @Override public void accept(I value) { if (value instanceof ItemStack) gui.setGhost((ItemStack) value); }
                });
            }
            @Override public void onComplete() { }
        });
    }
}
