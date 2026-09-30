package com.kaduvill.configurabledrawer.drawer;

import javax.annotation.Nullable;
import net.minecraft.util.EnumFacing;

public final class SideRules {
    public static final int BOTH = 0, BLOCKED = 1, INSERT = 2, EXTRACT = 3;
    public static final int FRONT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3,
            LEFT = 4, BACK = 5, UNSIDED = 6;
    public static final int ENTRIES = 7, MASK = (1 << (ENTRIES * 2)) - 1;

    private SideRules() { }

    public static int mode(int packed, int entry) {
        return (packed >>> (entry * 2)) & 3;
    }
    public static int cycle(int packed, int entry) {
        int shift = entry * 2;
        return (packed & ~(3 << shift)) | (((mode(packed, entry) + 1) & 3) << shift);
    }
    public static boolean inserts(int mode) { return mode == BOTH || mode == INSERT; }
    public static boolean extracts(int mode) { return mode == BOTH || mode == EXTRACT; }

    @Nullable public static EnumFacing face(int entry, EnumFacing front) {
        switch (entry) {
            case FRONT: return front;
            case TOP: return EnumFacing.UP;
            case RIGHT: return front.rotateYCCW();
            case BOTTOM: return EnumFacing.DOWN;
            case LEFT: return front.rotateY();
            case BACK: return front.getOpposite();
            case UNSIDED: return null;
            default: throw new IllegalArgumentException("Invalid side entry");
        }
    }
    public static int entry(@Nullable EnumFacing side, EnumFacing front) {
        if (side == null) return UNSIDED;
        if (side == front) return FRONT;
        if (side == EnumFacing.UP) return TOP;
        if (side == EnumFacing.DOWN) return BOTTOM;
        if (side == front.rotateYCCW()) return RIGHT;
        if (side == front.rotateY()) return LEFT;
        return BACK;
    }
}