package com.kaduvill.configurabledrawer.client.render;

import com.kaduvill.configurabledrawer.drawer.BlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public final class DrawerRenderer extends TileEntitySpecialRenderer<TileConfigurableDrawer> {
    @Override
    public void render(TileConfigurableDrawer tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null || !tile.hasWorld() || !tile.renderFrontIcon()) return;

        ItemStack filter = tile.renderFilter();
        if (filter.isEmpty()) return;

        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (state.getBlock() != ConfigurableDrawer.BLOCK) return;

        EnumFacing facing = state.getValue(BlockConfigurableDrawer.FACING);
        int light = tile.getWorld().getCombinedLight(tile.getPos().offset(facing), 0);
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
            GlStateManager.rotate(-facing.getHorizontalAngle(), 0, 1, 0);

            // The drawer front is recessed 1/16 block; sit just ahead of it.
            GlStateManager.translate(0, 0, 0.4385D);

            // Half-block icon, flattened against the front.
            GlStateManager.scale(0.5F, 0.5F, 0.001F);
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableAlpha();
            GlStateManager.disableLighting();

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    light & 65535, light >>> 16);

            Minecraft.getMinecraft().getRenderItem().renderItem(
                    filter, ItemCameraTransforms.TransformType.GUI);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    previousLightX, previousLightY);
            GlStateManager.enableLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }
}