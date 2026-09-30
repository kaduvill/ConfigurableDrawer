package com.kaduvill.configurabledrawer.drawer;

import java.util.Locale;

public final class DrawerNumberFormat {

    private static final long[] DIVISORS = {
            1_000_000_000_000_000_000L,
            1_000_000_000_000_000L,
            1_000_000_000_000L,
            1_000_000_000L,
            1_000_000L,
            1_000L
    };

    private static final String[] SUFFIXES = {"E", "Q", "T", "B", "M", "K"};

    private DrawerNumberFormat() {}

    public static String compact(long value) {
        for (int i = 0; i < DIVISORS.length; i++) {
            long divisor = DIVISORS[i];
            if (value >= divisor) {
                long tenths = value / (divisor / 10L);

                return (tenths / 10L) + "." + (tenths % 10L) + SUFFIXES[i];
            }
        }
        return Long.toString(value);
    }

    public static String exact(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}