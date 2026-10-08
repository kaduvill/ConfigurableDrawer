package com.kaduvill.configurabledrawer.drawer;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import com.jaquadro.minecraft.storagedrawers.api.storage.*;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.LockAttribute;
import com.jaquadro.minecraft.storagedrawers.capabilities.CapabilityDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.capabilities.CapabilityDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.capabilities.CapabilityItemRepository;

import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public final class TileConfigurableDrawer extends TileEntity implements IDrawerGroup {
    private boolean unloaded;
    private long revision;
    private final DrawerStorage storage = new DrawerStorage(this::onStorageChanged, this::syncRenderFilter);
    private boolean powered, renderFrontIcon = true;
    private ItemStack renderFilter = ItemStack.EMPTY;
    private final Group[] groups = new Group[SideRules.ENTRIES];
    private static final String[] MATERIAL_KEYS = {"MatS", "MatT", "MatF"};
    private NBTTagCompound materials = new NBTTagCompound();
    private long lastInsertClickTime;
    private UUID lastInsertClickPlayer;

    private final IDrawerAttributes attributes = new EmptyDrawerAttributes() {
        @Override public boolean isItemLocked(LockAttribute attr) { return true; }
        @Override public boolean isSealed() { return true; }
        @Override public boolean isVoid() { return storage.voidOverflow(); }
    };

    // Read-only snapshot. readMaterials replaces it rather than modifying it.
    public NBTTagCompound renderMaterials() { return materials; }
    public void readMaterials(NBTTagCompound tag) {
        materials = new NBTTagCompound();
        for (String key : MATERIAL_KEYS) {
            if (tag.hasKey(key, 10))
                materials.setTag(key, tag.getCompoundTag(key).copy());
        }
    }

    public void writeMaterials(NBTTagCompound tag) {
        for (String key : MATERIAL_KEYS) {
            if (materials.hasKey(key, 10))
                tag.setTag(key, materials.getCompoundTag(key).copy());
            else
                tag.removeTag(key);
        }
    }
    public boolean secondInsertClick(EntityPlayer player) {
        long now = world.getTotalWorldTime();
        boolean second = lastInsertClickPlayer != null
                && lastInsertClickPlayer.equals(player.getUniqueID())
                && now >= lastInsertClickTime && now - lastInsertClickTime < 10;
        lastInsertClickTime = now;
        lastInsertClickPlayer = player.getUniqueID();
        return second;
    }

    public void insertMatchingInventory(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || !active() || player.world != world || player.isSpectator()
                || player.getDistanceSq(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64)
            return;
        if (!storage.voidOverflow() && storage.remaining() <= 0) return;

        boolean moved = false;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!storage.matches(stack)) continue;

            ItemStack remainder = storage.insert(stack, false);
            if (remainder.getCount() != stack.getCount()) {
                player.inventory.setInventorySlotContents(i, remainder);
                moved = true;
                // The open drawer container has no armor/offhand slots. Window -2
                // addresses InventoryPlayer directly while that GUI is open.
                if (i >= player.inventory.mainInventory.size()
                        && serverPlayer.openContainer != serverPlayer.inventoryContainer)
                    serverPlayer.connection.sendPacket(new SPacketSetSlot(-2, i, remainder));
            }
            if (!storage.voidOverflow() && storage.remaining() <= 0) break;
        }

        if (moved) {
            player.inventory.markDirty();
            serverPlayer.openContainer.detectAndSendChanges();
        }
    }

    // Client rendering only; callers must not modify this stack.
    public ItemStack renderFilter() { return renderFilter; }
    public void syncRenderFilter() {
        if (!active()) return;
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 2);
    }
    private void readRenderFilter(NBTTagCompound tag) {
        renderFilter = tag.hasKey("RenderFilter", 10)
                ? new ItemStack(tag.getCompoundTag("RenderFilter")) : ItemStack.EMPTY;
        if (!renderFilter.isEmpty()) renderFilter.setCount(1);
        renderFrontIcon = !tag.hasKey("RenderFrontIcon") || tag.getBoolean("RenderFrontIcon");
    }
    private void onStorageChanged() {
        revision++;
        markDirty();
        refreshPower();
        if (renderFrontIcon != storage.frontIcon()) {
            renderFrontIcon = storage.frontIcon();
            syncRenderFilter();
        }
    }
    private void refreshPower() {
        boolean next = storage.powered();
        if (powered == next) return;
        powered = next;
        if (active())
            world.notifyNeighborsOfStateChange(pos, ConfigurableDrawer.BLOCK, false);
    }
    public void onItemRestored() {
        renderFrontIcon = storage.frontIcon();
        refreshPower();
        syncRenderFilter();
    }
    public boolean isPowered() { return powered; }
    public boolean renderFrontIcon() { return renderFrontIcon; }
    public DrawerStorage storage() { return storage; }
    public long revision() { return revision; }
    public boolean active() {
        return world != null && !world.isRemote && !unloaded && !isInvalid()
                && world.isBlockLoaded(pos, false) && world.getTileEntity(pos) == this;
    }
    @Override
    public void markDirty() {
        if (!active()) return;
        updateContainingBlockInfo();
        world.markChunkDirty(pos, this);
    }
    @Override public void onLoad() { super.onLoad(); unloaded = false; }
    @Override public void onChunkUnload() { unloaded = true; super.onChunkUnload(); }
    @Override public int getDrawerCount() { return 1; }
    @Nonnull @Override public IDrawer getDrawer(int slot) {return group(null).getDrawer(slot);}
    @Nonnull @Override public int[] getAccessibleDrawerSlots() { return new int[] {0}; }

    @Override public boolean hasCapability(@Nonnull Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || cap == CapabilityDrawerGroup.DRAWER_GROUP_CAPABILITY
                || cap == CapabilityDrawerAttributes.DRAWER_ATTRIBUTES_CAPABILITY
                || cap == CapabilityItemRepository.ITEM_REPOSITORY_CAPABILITY
                || super.hasCapability(cap, side);
    }

    @Nullable @Override public <T> T getCapability(@Nonnull Capability<T> cap, @Nullable EnumFacing side) {
        T value = group(side).getCapability(cap, side);
        return value != null ? value : super.getCapability(cap, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Drawer", storage.write());
        writeMaterials(tag);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        storage.read(tag.getCompoundTag("Drawer"));
        powered = storage.powered();
        renderFrontIcon = storage.frontIcon();
        readMaterials(tag);
        revision++;
    }
    @Nullable @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }
    // Nearby clients receive only the filter; quantities remain container-only.
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.writeToNBT(new NBTTagCompound());
        ItemStack filter = storage.filter();
        if (!filter.isEmpty()) tag.setTag("RenderFilter", filter.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("RenderFrontIcon", storage.frontIcon());
        writeMaterials(tag);
        return tag;
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readRenderFilter(tag);
        readMaterials(tag);
    }

    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.getNbtCompound();
        readRenderFilter(tag);
        readMaterials(tag);
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    private abstract class Port {
        @Nullable final EnumFacing side;

        Port(@Nullable EnumFacing side) { this.side = side; }

        int mode() {
            return SideRules.mode(storage.sides(),
                    side == null ? SideRules.UNSIDED : SideRules.entry(side, front()));
        }
        boolean input() {
            return active() && storage.filtered() && SideRules.inserts(mode());
        }
        boolean output() {
            return active() && storage.filtered() && SideRules.extracts(mode());
        }
        boolean matches(ItemStack stack, Predicate<ItemStack> predicate) {
            return active() && storage.matches(stack)
                    && (predicate == null || predicate.test(storage.filter()));
        }
    }

    private final class Group implements IDrawerGroup {
        private final IDrawer drawer;
        private final IItemHandler handler;
        private final IItemRepository repository;

        Group(@Nullable EnumFacing side) {
            drawer = new Drawer(side);
            handler = new Handler(side);
            repository = new Repository(side);
        }
        @Override public int getDrawerCount() { return 1; }
        @Nonnull @Override public IDrawer getDrawer(int slot) {
            return slot == 0 ? drawer : Drawers.DISABLED;
        }
        @Nonnull @Override public int[] getAccessibleDrawerSlots() { return new int[] {0}; }

        @Override public boolean hasCapability(@Nonnull Capability<?> cap, @Nullable EnumFacing side) {
            return getCapability(cap, side) != null;
        }
        @Nullable @Override public <T> T getCapability(@Nonnull Capability<T> cap, @Nullable EnumFacing side) {
            // Nested queries retain this group's original access side.
            if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
            if (cap == CapabilityDrawerGroup.DRAWER_GROUP_CAPABILITY)
                return CapabilityDrawerGroup.DRAWER_GROUP_CAPABILITY.cast(this);
            if (cap == CapabilityDrawerAttributes.DRAWER_ATTRIBUTES_CAPABILITY)
                return CapabilityDrawerAttributes.DRAWER_ATTRIBUTES_CAPABILITY.cast(attributes);
            if (cap == CapabilityItemRepository.ITEM_REPOSITORY_CAPABILITY)
                return CapabilityItemRepository.ITEM_REPOSITORY_CAPABILITY.cast(repository);
            return null;
        }
    }

    private final class Handler extends Port implements IItemHandler {
        Handler(@Nullable EnumFacing side) { super(side); }

        // Match SD's item handler: slot 0 is virtual insertion, slot 1 is drawer 0.
        @Override public int getSlots() { return 2; }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) {
            return slot == 1 && output() ? storage.publicStack() : ItemStack.EMPTY;
        }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return (slot == 0 || slot == 1) && input() && stack.getCount() > 0
                    ? storage.insert(stack, simulate) : stack;
        }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || amount <= 0 || !output()) return ItemStack.EMPTY;
            // Forge's handler contract limits extraction to one normal item stack.
            return storage.extract(Math.min(amount, storage.filter().getMaxStackSize()), simulate);
        }
        @Override public int getSlotLimit(int slot) {
            if (slot == 0) return Integer.MAX_VALUE;
            return slot == 1 && active() && storage.filtered()
                    ? storage.voidOverflow() ? Integer.MAX_VALUE : LongCount.visible(storage.capacity()) : 0;
        }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return (slot == 0 || slot == 1) && input() && storage.matches(stack);
        }
    }

    private final class Drawer extends Port implements IDrawer {
        Drawer(@Nullable EnumFacing side) { super(side); }

        @Nonnull @Override public ItemStack getStoredItemPrototype() {
            return active() ? storage.filter() : ItemStack.EMPTY;
        }
        @Nonnull @Override public ItemStack getPublicItemStack() {
            return output() ? storage.publicStack() : ItemStack.EMPTY;
        }
        @Override public int getStoredItemCount() {
            // SD's handler uses this directly for simulated extraction.
            return output() ? LongCount.visible(storage.count()) : 0;
        }
        @Override public boolean isEmpty() { return !active() || !storage.filtered(); }
        @Override public boolean isEnabled() { return active() && storage.filtered(); }

        @Nonnull @Override public IDrawer setStoredItem(@Nonnull ItemStack stack) {
            // Automation never assigns or clears the GUI's filter.
            return matches(stack, null) ? this : Drawers.DISABLED;
        }
        @Nonnull @Override public IDrawer setStoredItem(@Nonnull ItemStack stack, int amount) {
            IDrawer target = setStoredItem(stack);
            target.setStoredItemCount(amount);
            return target;
        }
        @Override public void setStoredItemCount(int amount) {
            if (!isEnabled() || amount < 0) return;
            // Delta against this access view, not against the hidden LONG quantity.
            // Insert-only exposes zero, so set(getCount() + n) inserts exactly n.
            long delta = (long) amount - getStoredItemCount();
            if (delta != 0) adjustStoredItemCount((int) delta);
        }
        @Override public int adjustStoredItemCount(int amount) {
            if (amount == Integer.MIN_VALUE)
                throw new IllegalArgumentException("Drawer transfer exceeds INT_MAX");
            if (amount > 0)
                return input() ? amount - storage.add(amount, false) : amount;
            if (amount < 0)
                return output() ? -amount - storage.extract(-amount, false).getCount() : -amount;
            return 0;
        }
        @Override public int getMaxCapacity(@Nonnull ItemStack stack) {
            return isEnabled() && (stack.isEmpty() || storage.matches(stack))
                    ? LongCount.visible(storage.capacity()) : 0;
        }
        @Override public int getAcceptingMaxCapacity(@Nonnull ItemStack stack) {
            return input() && (stack.isEmpty() || storage.matches(stack))
                    ? storage.voidOverflow() ? Integer.MAX_VALUE : LongCount.visible(storage.capacity()) : 0;
        }
        @Override public int getRemainingCapacity() {
            return input() ? LongCount.visible(storage.remaining()) : 0;
        }
        @Override public int getAcceptingRemainingCapacity() {
            return input() ? storage.voidOverflow() ? Integer.MAX_VALUE
                    : LongCount.visible(storage.remaining()) : 0;
        }
        @Override public boolean canItemBeStored(@Nonnull ItemStack stack, Predicate<ItemStack> predicate) {
            // SD 5.5.3 also calls this while extracting through its repository.
            // Capacity and mutation methods are the insertion authorization boundary.
            return matches(stack, predicate) && (input() || output());
        }
        @Override public boolean canItemBeExtracted(@Nonnull ItemStack stack, Predicate<ItemStack> predicate) {
            return matches(stack, predicate) && output();
        }
    }

    private final class Repository extends Port implements IItemRepository {
        Repository(@Nullable EnumFacing side) { super(side); }

        @Nonnull @Override public NonNullList<ItemRecord> getAllItems() {
            NonNullList<ItemRecord> result = NonNullList.create();
            if (output() && storage.count() > 0)
                result.add(new ItemRecord(storage.filter(), LongCount.visible(storage.count())));
            return result;
        }
        @Nonnull @Override public ItemStack insertItem(@Nonnull ItemStack stack, boolean simulate,
                                                       Predicate<ItemStack> predicate) {
            return stack.getCount() > 0 && matches(stack, predicate) && input()
                    ? storage.insert(stack, simulate) : stack;
        }
        @Nonnull @Override public ItemStack extractItem(@Nonnull ItemStack stack, int amount, boolean simulate,
                                                        Predicate<ItemStack> predicate) {
            return amount > 0 && matches(stack, predicate) && output()
                    ? storage.extract(amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getStoredItemCount(@Nonnull ItemStack stack, Predicate<ItemStack> predicate) {
            return matches(stack, predicate) && output() ? LongCount.visible(storage.count()) : 0;
        }
        @Override public int getRemainingItemCapacity(@Nonnull ItemStack stack, Predicate<ItemStack> predicate) {
            return matches(stack, predicate) && input()
                    ? storage.voidOverflow() ? Integer.MAX_VALUE : LongCount.visible(storage.remaining()) : 0;
        }
        @Override public int getItemCapacity(@Nonnull ItemStack stack, Predicate<ItemStack> predicate) {
            return matches(stack, predicate) && input()
                    ? storage.voidOverflow() ? Integer.MAX_VALUE : LongCount.visible(storage.capacity()) : 0;
        }
    }

    public TileConfigurableDrawer() {
        for (int i = 0; i < groups.length; i++)
            groups[i] = new Group(i == 6 ? null : EnumFacing.byIndex(i));
    }
    public EnumFacing front() {
        if (world == null) return EnumFacing.NORTH;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() == ConfigurableDrawer.BLOCK
                ? state.getValue(BlockConfigurableDrawer.FACING) : EnumFacing.NORTH;
    }
    private Group group(@Nullable EnumFacing side) {
        return groups[side == null ? 6 : side.getIndex()];
    }
}
