package com.kaduvill.configurabledrawer.client.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.kaduvill.configurabledrawer.drawer.BlockConfigurableDrawer;
import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BakedQuadRetextured;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.apache.commons.lang3.tuple.Pair;

public final class DrawerFrameModel implements IBakedModel {
    private static final String SIDE = ConfigurableDrawer.MODID + ":blocks/drawer_side";
    private static final String TRIM = ConfigurableDrawer.MODID + ":blocks/drawer_trim";
    private static final String FRONT = ConfigurableDrawer.MODID + ":blocks/drawer_front";
    private static final String[] KEYS = {"MatS", "MatT", "MatF"};

    private final IBakedModel base;
    private final IBlockState listedState;
    private final Cache<NBTTagCompound, DrawerFrameModel> cache;
    private final List<List<BakedQuad>> quads;
    private final TextureAtlasSprite particle;
    private final ItemOverrideList overrides;

    // Root wrapper: one for each block orientation, and one for the item.
    public DrawerFrameModel(IBakedModel base, @Nullable IBlockState listedState) {
        this.base = base;
        this.listedState = listedState;
        this.cache = CacheBuilder.newBuilder().maximumSize(256).build();
        this.quads = null;
        this.particle = base.getParticleTexture();
        this.overrides = new ItemOverrideList(Collections.emptyList()) {
            @Override
            public IBakedModel handleItemState(IBakedModel original, ItemStack stack,
                                               @Nullable World world, @Nullable EntityLivingBase entity) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null || !hasMaterials(tag)) return DrawerFrameModel.this;

                // Exclude DrawerData: counts/filter/capacity are not model-cache keys.
                // Borrow tags for lookup; resolve() copies retained keys.
                NBTTagCompound materials = new NBTTagCompound();
                for (String key : KEYS) {
                    if (tag.hasKey(key, 10))
                        materials.setTag(key, tag.getCompoundTag(key));
                }
                return resolve(materials);
            }
        };
    }

    // Cached model containing already-retextured geometry.
    private DrawerFrameModel(DrawerFrameModel source, NBTTagCompound materials) {
        this.base = source.base;
        this.listedState = source.listedState;
        this.cache = null;
        this.overrides = ItemOverrideList.NONE;

        TextureAtlasSprite side = texture(materials, "MatS");
        TextureAtlasSprite trim = texture(materials, "MatT");
        TextureAtlasSprite front = texture(materials, "MatF");

        // Same material fallback convention as framed drawers.
        if (trim == null) trim = side;
        if (front == null) front = side;
        this.particle = side != null ? side : base.getParticleTexture();

        List<List<BakedQuad>> result = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            EnumFacing face = i == 6 ? null : EnumFacing.byIndex(i);
            List<BakedQuad> faces = new ArrayList<>();
            for (BakedQuad quad : base.getQuads(listedState, face, 0)) {
                String name = quad.getSprite().getIconName();
                TextureAtlasSprite replacement = null;
                if (SIDE.equals(name)) replacement = side;
                else if (TRIM.equals(name)) replacement = trim;
                else if (FRONT.equals(name)) replacement = front;

                faces.add(replacement == null ? quad : new BakedQuadRetextured(quad, replacement));
            }
            result.add(Collections.unmodifiableList(faces));
        }
        this.quads = Collections.unmodifiableList(result);
    }

    private static boolean hasMaterials(NBTTagCompound tag) {
        for (String key : KEYS) {
            if (tag.hasKey(key, 10)) return true;
        }
        return false;
    }

    private DrawerFrameModel resolve(NBTTagCompound materials) {
        if (!hasMaterials(materials)) return this;

        DrawerFrameModel result = cache.getIfPresent(materials);
        if (result == null) {
            NBTTagCompound key = materials.copy();
            result = new DrawerFrameModel(this, key);
            cache.put(key, result);
        }
        return result;
    }

    @Nullable
    private static TextureAtlasSprite texture(NBTTagCompound materials, String key) {
        if (!materials.hasKey(key, 10)) return null;
        // Keep item initialization and model overrides isolated from the cache key.
        ItemStack material = new ItemStack(materials.getCompoundTag(key).copy());
        if (material.isEmpty()) return null;

        Minecraft minecraft = Minecraft.getMinecraft();
        TextureAtlasSprite sprite = minecraft.getRenderItem()
                .getItemModelWithOverrides(material, null, null).getParticleTexture();

        return sprite == minecraft.getTextureMapBlocks().getMissingSprite() ? null : sprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (quads != null) return quads.get(side == null ? 6 : side.getIndex());

        if (state instanceof IExtendedBlockState) {
            NBTTagCompound materials = ((IExtendedBlockState) state)
                    .getValue(BlockConfigurableDrawer.MATERIALS);
            if (materials != null) {
                DrawerFrameModel model = resolve(materials);
                if (model != this) return model.getQuads(null, side, rand);
            }
        }
        return base.getQuads(listedState, side, rand);
    }

    @Override public boolean isAmbientOcclusion() { return base.isAmbientOcclusion(); }
    @Override public boolean isGui3d() { return base.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return particle; }
    @Override public ItemOverrideList getOverrides() { return overrides; }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return base.getItemCameraTransforms(); }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return Pair.of(this, base.handlePerspective(type).getRight());
    }
}