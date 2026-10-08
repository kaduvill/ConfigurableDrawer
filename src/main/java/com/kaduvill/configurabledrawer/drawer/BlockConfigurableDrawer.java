package com.kaduvill.configurabledrawer.drawer;

import com.jaquadro.minecraft.storagedrawers.api.storage.INetworked;
import javax.annotation.Nullable;

import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class BlockConfigurableDrawer extends Block implements INetworked {
    public BlockConfigurableDrawer() {
        super(Material.WOOD);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        setRegistryName(ConfigurableDrawer.MODID, "configurable_drawer");
        setTranslationKey(ConfigurableDrawer.MODID + ".configurable_drawer");
        setCreativeTab(CreativeTabs.DECORATIONS);
        setHardness(2.5F);
        setResistance(10.0F);
        setSoundType(SoundType.WOOD);
    }
    public static final IUnlistedProperty<NBTTagCompound> MATERIALS = new IUnlistedProperty<NBTTagCompound>() {
        @Override public String getName() { return "materials"; }
        @Override public boolean isValid(NBTTagCompound value) { return value != null; }
        @Override public Class<NBTTagCompound> getType() { return NBTTagCompound.class; }
        @Override public String valueToString(NBTTagCompound value) { return value.toString(); }
    };
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return false; // Both ordinary and silk-touch mining must use our NBT-preserving getDrops.
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileConfigurableDrawer && ((TileConfigurableDrawer) tile).isPowered() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileConfigurableDrawer(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND || player.isSpectator()) return false;

        ItemStack held = player.getHeldItemMainhand();

        if (player.isSneaking() && held.isEmpty()) {
            if (!world.isRemote)
                player.openGui(ConfigurableDrawer.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        if (side != state.getValue(FACING)) return false;

        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileConfigurableDrawer && ((TileConfigurableDrawer) tile).active()) {
                TileConfigurableDrawer drawer = (TileConfigurableDrawer) tile;
                DrawerStorage storage = drawer.storage();
                if (drawer.secondInsertClick(player)) {
                    drawer.insertMatchingInventory(player);
                } else if (storage.matches(held)) {
                    ItemStack remainder = storage.insert(held, false);
                    if (remainder.getCount() != held.getCount()) {
                        player.setHeldItem(EnumHand.MAIN_HAND, remainder);
                        player.inventory.markDirty();
                        ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
                    }
                }
            }
        }
        return true;
    }

    @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote || player.isSpectator()) return;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == this && isFrontHit(pos, state, player))
            extractForPlayer(world, pos, player);
    }

    private boolean isFrontHit(BlockPos pos, IBlockState state, EntityPlayer player) {
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1.0D;
        RayTraceResult hit = ForgeHooks.rayTraceEyes(player, reach);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                && pos.equals(hit.getBlockPos()) && hit.sideHit == state.getValue(FACING);
    }

    public void extractForPlayer(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote || player.isSpectator()
                || player.getDistanceSq(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64)
            return;

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileConfigurableDrawer) || !((TileConfigurableDrawer) tile).active()) return;

        DrawerStorage storage = ((TileConfigurableDrawer) tile).storage();
        ItemStack filter = storage.filter();
        if (filter.isEmpty()) return;

        int slotLimit = Math.min(filter.getMaxStackSize(), player.inventory.getInventoryStackLimit());
        if (slotLimit <= 0) return;

        int space = 0;
        for (ItemStack slot : player.inventory.mainInventory) {
            if (slot.isEmpty()) space += slotLimit;
            else if (storage.matches(slot))
                space += Math.max(0, Math.min(slotLimit, slot.getMaxStackSize()) - slot.getCount());
        }
        if (space <= 0) return;

        int requested = player.isSneaking() ? slotLimit : 1;
        ItemStack offered = storage.extract(Math.min(requested, space), true);
        if (offered.isEmpty()) return;

        int before = offered.getCount();
        player.inventory.addItemStackToInventory(offered);
        int inserted = before - offered.getCount();
        if (inserted <= 0) return;

        storage.extract(inserted, false);
        player.inventory.markDirty();
        ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
    }

    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        world.setBlockState(pos, state.withProperty(FACING, facing), 3);

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileConfigurableDrawer)) return;

        TileConfigurableDrawer drawer = (TileConfigurableDrawer) tile;
        if (world.isRemote) {
            if (stack.hasTagCompound()) {
                drawer.readMaterials(stack.getTagCompound());
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
            return;
        }

        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("DrawerData", 10))
                drawer.storage().read(tag.getCompoundTag("DrawerData"));
            drawer.readMaterials(tag);
        }
        drawer.markDirty();
        drawer.onItemRestored();
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(ConfigurableDrawer.ITEM);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileConfigurableDrawer) {
            TileConfigurableDrawer drawer = (TileConfigurableDrawer) tile;
            NBTTagCompound tag = new NBTTagCompound();
            if (!drawer.storage().isDefault())
                tag.setTag("DrawerData", drawer.storage().write());
            drawer.writeMaterials(tag);
            if (!tag.isEmpty()) stack.setTagCompound(tag);
        }
        drops.add(stack);
    }
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                             EntityPlayer player, boolean willHarvest) {
        // Creative attacks skip onBlockClicked and go straight to removal.
        if (player.capabilities.isCreativeMode && state.getBlock() == this && isFrontHit(pos, state, player)) {
            if (!world.isRemote) extractForPlayer(world, pos, player);
            return false;
        }

        // Keep the tile alive until harvestBlock/getDrops have serialized its contents.
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }
    @Override public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
            @Nullable TileEntity tile, ItemStack tool) {
        super.harvestBlock(world, player, pos, state, tile, tool);
        world.setBlockToAir(pos);
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[] {FACING},
                new IUnlistedProperty<?>[] {MATERIALS});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (state instanceof IExtendedBlockState && tile instanceof TileConfigurableDrawer) {
            return ((IExtendedBlockState) state).withProperty(MATERIALS,
                    ((TileConfigurableDrawer) tile).renderMaterials());
        }
        return state;
    }
    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        if (facing.getAxis() == EnumFacing.Axis.Y) facing = EnumFacing.NORTH;
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return withRotation(state, mirror.toRotation(state.getValue(FACING)));
    }

}
