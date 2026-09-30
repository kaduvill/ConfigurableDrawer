package com.kaduvill.configurabledrawer.client.render;

import java.awt.Rectangle;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

import com.kaduvill.configurabledrawer.*;
import com.kaduvill.configurabledrawer.drawer.BlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.drawer.SideRules;
import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

public final class DrawerPreview {
    private static final int GHOST_BANDS = 6, MAX_GHOST_QUADS = 64;
    private static final DoubleBuffer CLIP = BufferUtils.createDoubleBuffer(4);

    private DrawerPreview() { }

    public static int color(int mode) {
        switch (mode) {
            case SideRules.BLOCKED: return 0xe34b4b;
            case SideRules.INSERT: return 0x4285ed;
            case SideRules.EXTRACT: return 0xf39a35;
            default: return 0;
        }
    }

    public static void draw(TileConfigurableDrawer tile, ItemStack filter, int sides,
                            boolean frontIcon, Rectangle area, float yaw, float pitch) {
        if (!tile.hasWorld()) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (state.getBlock() != ConfigurableDrawer.BLOCK) return;

        Minecraft mc = Minecraft.getMinecraft();
        EnumFacing front = state.getValue(BlockConfigurableDrawer.FACING);
        IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
        IBlockState extended = state.getBlock().getExtendedState(state, tile.getWorld(), tile.getPos());
        int scale = new ScaledResolution(mc).getScaleFactor();
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;

        // Scissor has no GlStateManager cache. Preserve it without disturbing that cache.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(area.x * scale, mc.displayHeight - (area.y + area.height) * scale,
                area.width * scale, area.height * scale);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.depthMask(true);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);

            GlStateManager.translate(area.x + area.width / 2F, area.y + area.height / 2F, 150);
            GlStateManager.scale(28, -28, 28);
            GlStateManager.rotate(pitch, 1, 0, 0);
            GlStateManager.rotate(yaw, 0, 1, 0);
            GlStateManager.translate(-.5, -.5, -.5);

            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            mc.getBlockRendererDispatcher().getBlockModelRenderer()
                    .renderModelBrightnessColor(extended, model, 1, 1, 1, 1);

            GlStateManager.translate(.5, .5, .5);
            if (frontIcon && !filter.isEmpty()) {
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.rotate(-front.getHorizontalAngle(), 0, 1, 0);
                    GlStateManager.translate(0, 0, .4385);
                    GlStateManager.scale(.5F, .5F, .001F);
                    mc.getRenderItem().renderItem(filter, ItemCameraTransforms.TransformType.GUI);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
            GlStateManager.pushMatrix();
            try {
                // The current origin is the drawer's center; models use local 0..1 coordinates.
                GlStateManager.translate(-.5, -.5, -.5);
                renderNeighbors(tile);
            } finally {
                GlStateManager.popMatrix();
            }

            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);

            for (EnumFacing face : EnumFacing.values()) {
                int mode = SideRules.mode(sides, SideRules.entry(face, front));
                if (mode == SideRules.BOTH) continue;
                GlStateManager.pushMatrix();
                try {
                    orient(face);
                    overlay(color(mode));
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            GlStateManager.depthMask(true);
            // Do not let preview depth obscure the later GUI cursor or tooltips.
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static void orient(EnumFacing face) {
        if (face == EnumFacing.UP) GlStateManager.rotate(-90, 1, 0, 0);
        else if (face == EnumFacing.DOWN) GlStateManager.rotate(90, 1, 0, 0);
        else GlStateManager.rotate(-face.getHorizontalAngle(), 0, 1, 0);
    }
    private static void overlay(int color) {
        float r = ((color >>> 16) & 255) / 255F;
        float g = ((color >>> 8) & 255) / 255F;
        float b = (color & 255) / 255F;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-.5, -.5, .502).color(r, g, b, .35F).endVertex();
        buffer.pos(.5, -.5, .502).color(r, g, b, .35F).endVertex();
        buffer.pos(.5, .5, .502).color(r, g, b, .35F).endVertex();
        buffer.pos(-.5, .5, .502).color(r, g, b, .35F).endVertex();
        Tessellator.getInstance().draw();
    }

    private static void renderNeighbors(TileConfigurableDrawer tile) {
        if (!GLContext.getCapabilities().OpenGL14) return;
        World world = tile.getWorld();
        Minecraft mc = Minecraft.getMinecraft();
        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        // Preserve clip equations, clip enables, and the constant blend color.
        GL11.glPushAttrib(GL11.GL_TRANSFORM_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            GL11.glEnable(GL11.GL_CLIP_PLANE1);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.CONSTANT_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);

            for (EnumFacing face : EnumFacing.values()) {
                BlockPos pos = tile.getPos().offset(face);
                if (!world.isBlockLoaded(pos, false)) continue;

                try {
                    IBlockState state = world.getBlockState(pos);
                    if (state.getRenderType() != EnumBlockRenderType.MODEL) continue;

                    BlockRenderLayer layer = state.getBlock().getRenderLayer();
                    if (layer == BlockRenderLayer.TRANSLUCENT
                            || !state.getBlock().canRenderInLayer(state, layer)) continue;

                    IBlockState actual = state.getActualState(world, pos);
                    IBlockState extended = actual.getBlock().getExtendedState(actual, world, pos);
                    IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(actual);
                    if (model.isBuiltInRenderer()) continue;

                    ForgeHooksClient.setRenderLayer(layer);
                    List<BakedQuad> quads = ghostQuads(model, extended, face.getOpposite());
                    if (quads.isEmpty() && layer != BlockRenderLayer.CUTOUT_MIPPED
                            && state.getBlock().canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)) {
                        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);
                        quads = ghostQuads(model, extended, face.getOpposite());
                    }
                    if (quads.isEmpty() && layer != BlockRenderLayer.CUTOUT
                            && state.getBlock().canRenderInLayer(state, BlockRenderLayer.CUTOUT)) {
                        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);
                        quads = ghostQuads(model, extended, face.getOpposite());
                    }
                    if (quads.isEmpty()) continue;

                    int[] tint = new int[quads.size()];
                    for (int i = 0; i < tint.length; i++) {
                        BakedQuad quad = quads.get(i);
                        tint[i] = quad.hasTintIndex()
                                ? mc.getBlockColors().colorMultiplier(actual, world, pos, quad.getTintIndex())
                                : -1;
                    }

                    GlStateManager.pushMatrix();
                    try {
                        GlStateManager.translate(face.getXOffset(), face.getYOffset(), face.getZOffset());
                        for (int band = 0; band < GHOST_BANDS; band++) {
                            double near = band / (2.0 * GHOST_BANDS);
                            double far = (band + 1) / (2.0 * GHOST_BANDS);
                            clip(face, near, false);
                            clip(face, far, true);
                            float opacity = .28F * (1F - (band + .5F) / GHOST_BANDS);
                            GL14.glBlendColor(1, 1, 1, opacity);
                            renderGhostQuads(quads, tint);
                        }
                    } finally {
                        GlStateManager.popMatrix();
                    }
                } catch (RuntimeException ignored) {
                    // An incompatible third-party model must not close the drawer GUI.
                }
            }
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
            GL11.glPopAttrib();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(true);
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static List<BakedQuad> ghostQuads(IBakedModel model, IBlockState state,
                                              EnumFacing towardDrawer) {
        List<BakedQuad> result = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            EnumFacing face = i == 6 ? null : EnumFacing.byIndex(i);
            if (face == towardDrawer) continue;

            for (BakedQuad quad : model.getQuads(state, face, 0)) {
                if (!DefaultVertexFormats.ITEM.equals(quad.getFormat())
                        || quad.getVertexData().length != 4 * DefaultVertexFormats.ITEM.getIntegerSize())
                    return new ArrayList<>();

                // Also catch contact faces placed in the model's unculled quad list.
                if (quad.getFace() == towardDrawer || onContactPlane(quad, towardDrawer))
                    continue;

                result.add(quad);
                if (result.size() > MAX_GHOST_QUADS) return new ArrayList<>();
            }
        }
        return result;
    }

    private static boolean onContactPlane(BakedQuad quad, EnumFacing towardDrawer) {
        int axis = towardDrawer.getAxis() == EnumFacing.Axis.X ? 0
                : towardDrawer.getAxis() == EnumFacing.Axis.Y ? 1 : 2;
        float boundary = towardDrawer.getAxisDirection().getOffset() < 0 ? 0F : 1F;
        int[] vertices = quad.getVertexData();
        int stride = quad.getFormat().getIntegerSize();

        for (int vertex = 0; vertex < 4; vertex++) {
            float coordinate = Float.intBitsToFloat(vertices[vertex * stride + axis]);
            if (Math.abs(coordinate - boundary) > 1F / 1024F) return false;
        }
        return true;
    }

    private static void clip(EnumFacing face, double distance, boolean upper) {
        double sign = face.getAxisDirection().getOffset();
        double base = sign < 0 ? 1 : 0;
        double coefficient = upper ? -sign : sign;
        double constant = upper ? distance - base : base - distance;

        CLIP.clear();
        CLIP.put(face.getAxis() == EnumFacing.Axis.X ? coefficient : 0);
        CLIP.put(face.getAxis() == EnumFacing.Axis.Y ? coefficient : 0);
        CLIP.put(face.getAxis() == EnumFacing.Axis.Z ? coefficient : 0);
        CLIP.put(constant);
        CLIP.flip();
        GL11.glClipPlane(upper ? GL11.GL_CLIP_PLANE1 : GL11.GL_CLIP_PLANE0, CLIP);
    }

    private static void renderGhostQuads(List<BakedQuad> quads, int[] tint) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        boolean submitted = false;
        try {
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                buffer.addVertexData(quad.getVertexData());
                if (tint[i] != -1) {
                    float r = ((tint[i] >>> 16) & 255) / 255F;
                    float g = ((tint[i] >>> 8) & 255) / 255F;
                    float b = (tint[i] & 255) / 255F;
                    buffer.putColorRGB_F4(r, g, b);
                }
            }
            submitted = true;
            tessellator.draw();
        } finally {
            if (!submitted) {
                buffer.finishDrawing();
                buffer.reset();
            }
        }
    }
}