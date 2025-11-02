package me.kr1s_d.ultimateantibot.common.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Formatter {
    private static final String[] SUFFIXES = {"", "k", "M", "B", "T", "Q", "KQ"};
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ITALIAN);
        DecimalFormat df = new DecimalFormat("###.##", symbols);
        df.setGroupingUsed(false);
        return df;
    });

    public static String format(float value) {
        int idx = 0;
        while ((value / 1000) >= 1) {
            value /= 1000f;
            idx++;
        }
        if (idx >= SUFFIXES.length) {
            idx = 5;
        }
        String formatted = DECIMAL_FORMAT.get().format(value);
        return formatted + SUFFIXES[idx];
    }

    public static String format(double value) {
        int idx = 0;
        while ((value / 1000) >= 1) {
            value /= 1000d;
            idx++;
        }
        if (idx >= SUFFIXES.length) {
            idx = 5;
        }
        String formatted = DECIMAL_FORMAT.get().format(value);
        return formatted + SUFFIXES[idx];
    }
}
