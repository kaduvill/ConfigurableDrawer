package com.kaduvill.configurabledrawer.drawer;

import com.kaduvill.configurabledrawer.DrawerConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/** One exact, persistent ghost filter and one long quantity. Owned by the server tile. */
public final class DrawerStorage {
    private ItemStack filter = ItemStack.EMPTY;
    private NBTTagCompound unresolvedFilter;
    private final LongCount quantity = new LongCount(DrawerConfig.defaultCapacity);
    private final Runnable changed;
    private final Runnable filterChanged;
    private int sides;
    public static final int REDSTONE_DISABLED = 0, REDSTONE_EMPTY = 1, REDSTONE_HAS_ITEMS = 2,
            REDSTONE_FULL = 3, REDSTONE_AT_LEAST = 4, REDSTONE_AT_MOST = 5;
    private boolean voidOverflow, frontIcon = true;
    private int redstone;
    private long threshold;

    public DrawerStorage(Runnable changed) {this(changed, () -> {});}
    public DrawerStorage(Runnable changed, Runnable filterChanged) {
        this.changed = changed;
        this.filterChanged = filterChanged;
    }
    public ItemStack filter() { return filter.copy(); }
    public boolean filtered() { return !filter.isEmpty(); }
    public long count() { return quantity.count(); }
    public long capacity() { return quantity.limit(); }
    public long remaining() { return filtered() ? quantity.remaining() : 0; }
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && filtered() && ItemStack.areItemsEqual(filter, stack)
                && ItemStack.areItemStackTagsEqual(filter, stack);
    }
    public boolean setFilter(ItemStack stack) {
        if (count() != 0) return matches(stack);
        ItemStack next = stack.copy();
        if (!next.isEmpty()) next.setCount(1);
        if (ItemStack.areItemStacksEqual(filter, next)) return true;
        filter = next;
        unresolvedFilter = null;
        changed.run();
        filterChanged.run();
        return true;
    }
    public boolean setCapacity(long capacity) {
        if (capacity < 1) {return false;}

        long clamped = Math.min(capacity, DrawerConfig.maximumCapacity);
        if (clamped != capacity()) {
            quantity.setLimit(clamped);
            changed.run();
        }
        return true;
    }

    public int sides() { return sides; }
    public boolean cycleSide(int entry) {
        if (entry < 0 || entry >= SideRules.ENTRIES) return false;
        sides = SideRules.cycle(sides, entry);
        changed.run();
        return true;
    }
    public boolean voidOverflow() { return voidOverflow; }
    public boolean frontIcon() { return frontIcon; }
    public int redstone() { return redstone; }
    public long threshold() { return threshold; }

    public void toggleVoidOverflow() {
        voidOverflow = !voidOverflow;
        changed.run();
    }
    public void toggleFrontIcon() {
        frontIcon = !frontIcon;
        changed.run();
    }
    public void cycleRedstone(boolean reverse) {
        redstone = Math.floorMod(redstone + (reverse ? -1 : 1), REDSTONE_AT_MOST + 1);
        changed.run();
    }
    public boolean setThreshold(long value) {
        if (value < 0 || (redstone != REDSTONE_AT_LEAST && redstone != REDSTONE_AT_MOST))
            return false;
        long clamped = Math.min(value, DrawerConfig.maximumCapacity);
        if (threshold != clamped) {
            threshold = clamped;
            changed.run();
        }
        return true;
    }
    public boolean powered() {
        switch (redstone) {
            case REDSTONE_EMPTY: return count() == 0;
            case REDSTONE_HAS_ITEMS: return count() > 0;
            case REDSTONE_FULL: return count() >= capacity();
            case REDSTONE_AT_LEAST: return count() >= threshold;
            case REDSTONE_AT_MOST: return count() <= threshold;
            default: return false;
        }
    }
    public ItemStack publicStack() { return stack(LongCount.visible(count())); }
    private ItemStack stack(int amount) {
        if (!filtered() || amount <= 0) return ItemStack.EMPTY;
        ItemStack result = filter.copy();
        result.setCount(amount);
        return result;
    }
    public ItemStack insert(ItemStack input, boolean simulate) {
        if (!matches(input)) return input;
        int accepted = add(input.getCount(), simulate);
        if (accepted == 0) return input;
        if (accepted == input.getCount()) return ItemStack.EMPTY;
        ItemStack rest = input.copy();
        rest.shrink(accepted);
        return rest;
    }
    public int add(int amount, boolean simulate) {
        if (!filtered() || amount <= 0) return 0;
        int stored = quantity.insert(amount, simulate);
        if (!simulate && stored != 0) changed.run();
        return voidOverflow ? amount : stored;
    }
    public void setVisibleCount(int amount) {
        if (!filtered()) return;
        long before = count();
        quantity.setVisibleCount(amount);
        if (before != count()) changed.run();
    }
    public ItemStack extract(int amount, boolean simulate) {
        if (!filtered()) return ItemStack.EMPTY;
        int moved = quantity.extract(amount, simulate);
        if (!simulate && moved != 0) changed.run();
        return stack(moved);
    }
    /** Default item drops omit storage NBT so they stack with newly crafted drawers. */
    public boolean isDefault() {
        return !filtered() && unresolvedFilter == null && count() == 0
                && capacity() == DrawerConfig.defaultCapacity && sides == 0
                && !voidOverflow && frontIcon && redstone == REDSTONE_DISABLED && threshold == 0;
    }
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 1);
        tag.setInteger("Sides", sides);
        tag.setBoolean("VoidOverflow", voidOverflow);
        tag.setBoolean("FrontIcon", frontIcon);
        tag.setInteger("Redstone", redstone);
        tag.setLong("Threshold", threshold);
        tag.setLong("Capacity", capacity());
        tag.setLong("Count", count());
        if (filtered()) tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        else if (unresolvedFilter != null) tag.setTag("Filter", unresolvedFilter.copy());
        return tag;
    }
    public void read(NBTTagCompound tag) {
        voidOverflow = tag.getBoolean("VoidOverflow");
        frontIcon = !tag.hasKey("FrontIcon") || tag.getBoolean("FrontIcon");
        int savedRedstone = tag.getInteger("Redstone");
        redstone = savedRedstone >= REDSTONE_DISABLED && savedRedstone <= REDSTONE_AT_MOST
                ? savedRedstone : REDSTONE_DISABLED;
        threshold = Math.max(0L, tag.getLong("Threshold"));
        sides = tag.getInteger("Sides") & SideRules.MASK;
        unresolvedFilter = null;
        filter = tag.hasKey("Filter", Constants.NBT.TAG_COMPOUND)
                ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        if (!filter.isEmpty()) filter.setCount(1);
        long capacity = tag.hasKey("Capacity", Constants.NBT.TAG_ANY_NUMERIC)
                ? tag.getLong("Capacity") : DrawerConfig.defaultCapacity;
        quantity.setLimit(Math.max(1L, Math.min(capacity, DrawerConfig.maximumCapacity)));
        long count = tag.getLong("Count");
        // Preserve an unresolved registry identity verbatim; disable transfers until its mod returns.
        if (filter.isEmpty() && tag.hasKey("Filter", Constants.NBT.TAG_COMPOUND))
            unresolvedFilter = tag.getCompoundTag("Filter").copy();
        if (count < 0) throw new IllegalArgumentException("Invalid Configurable Drawer NBT: negative count");
        quantity.restore(count);
    }
}
