package com.kaduvill.configurabledrawer.menu;

import com.kaduvill.configurabledrawer.drawer.TileConfigurableDrawer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

public final class ContainerDrawer extends Container {
    public final TileConfigurableDrawer tile;
    public long shownCount, shownCapacity = 1, shownMaximum = Long.MAX_VALUE;
    public ItemStack shownFilter = ItemStack.EMPTY;
    private long lastRevision = Long.MIN_VALUE;
    public int shownSides;
    public boolean shownVoidOverflow, shownFrontIcon = true;
    public int shownRedstone;
    public long shownThreshold;

    public ContainerDrawer(InventoryPlayer inventory, TileConfigurableDrawer tile) {
        this.tile = tile;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 48 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(inventory, col, 48 + col * 18, 198));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return !player.isSpectator() && !tile.isInvalid() && player.world == tile.getWorld()
                && player.world.isBlockLoaded(tile.getPos(), false)
                && player.world.getTileEntity(tile.getPos()) == tile
                && player.getDistanceSq(tile.getPos().getX() + .5, tile.getPos().getY() + .5, tile.getPos().getZ() + .5) <= 64;
    }
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP) send((EntityPlayerMP) listener);
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorld().isRemote || lastRevision == tile.revision()) return;
        lastRevision = tile.revision();
        for (IContainerListener listener : listeners)
            if (listener instanceof EntityPlayerMP) send((EntityPlayerMP) listener);
    }
    public void send(EntityPlayerMP player) {
        DrawerNetwork.CHANNEL.sendTo(new DrawerNetwork.State(this), player);
    }
    public void setFilterFromCursor(EntityPlayer player, boolean clear) {
        if (tile.active()) tile.storage().setFilter(clear ? ItemStack.EMPTY : player.inventory.getItemStack());
    }
    public void takeStack(EntityPlayer player) {
        if (!tile.active()) return;
        ItemStack prototype = tile.storage().filter();
        if (prototype.isEmpty()) return;
        ItemStack offered = tile.storage().extract(prototype.getMaxStackSize(), true);
        int before = offered.getCount();
        // Vanilla player slots only. Commit exactly the amount actually accepted.
        mergeItemStack(offered, 0, inventorySlots.size(), false);
        tile.storage().extract(before - offered.getCount(), false);
        player.inventory.markDirty();
    }
    @Override public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (clickType != ClickType.QUICK_MOVE)
            return super.slotClick(slotId, dragType, clickType, player);

        // Both peers return EMPTY; only the server changes storage and sends the resulting slots.
        if (player.world.isRemote) return ItemStack.EMPTY;
        if (slotId < 0 || slotId >= inventorySlots.size()) return ItemStack.EMPTY;
        if (!canInteractWith(player) || !tile.active()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(slotId);
        if (dragType != 0 && dragType != 1 || !slot.canTakeStack(player)) return ItemStack.EMPTY;
        transferStackInSlot(player, slotId);
        return ItemStack.EMPTY;
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!tile.active() || index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack original = slot.getStack().copy();
        ItemStack remainder = tile.storage().insert(original, false);
        if (remainder.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.putStack(remainder);
        slot.onTake(player, original);
        detectAndSendChanges();
        return original;
    }
}
