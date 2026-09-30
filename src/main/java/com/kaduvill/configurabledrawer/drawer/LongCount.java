package com.kaduvill.configurabledrawer.drawer;

/** Arithmetic only: never add quantities before checking the available space. */
public final class LongCount {
    private long count;
    private long limit;

    public LongCount(long limit) { setLimit(limit); }
    public long count() { return count; }
    public long limit() { return limit; }
    public long remaining() { return count >= limit ? 0 : limit - count; }
    public void setLimit(long value) {
        if (value < 1) throw new IllegalArgumentException("Capacity must be positive");
        limit = value;
    }
    public void restore(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative item count");
        count = value; // Existing contents survive capacity reductions.
    }
    public int insert(int requested, boolean simulate) {
        int accepted = requested <= 0 ? 0 : (int) Math.min((long) requested, remaining());
        if (!simulate) count += accepted;
        return accepted;
    }
    public int extract(int requested, boolean simulate) {
        int extracted = requested <= 0 ? 0 : (int) Math.min((long) requested, count);
        if (!simulate) count -= extracted;
        return extracted;
    }
    /** Apply an INT read/modify/write to the visible window, preserving the hidden remainder. */
    public void setVisibleCount(int requested) {
        if (requested < 0) return;
        long delta = (long) requested - visible(count);
        if (delta > 0) insert((int) delta, false);
        else if (delta < 0) extract((int) -delta, false);
    }
    public static int visible(long value) {
        return (int) Math.max(0L, Math.min(value, Integer.MAX_VALUE));
    }
}
