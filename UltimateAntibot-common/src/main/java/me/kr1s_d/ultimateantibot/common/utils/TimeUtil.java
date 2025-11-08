package me.kr1s_d.ultimateantibot.common.utils;

import java.util.concurrent.TimeUnit;

public class TimeUtil {
    private static final long SECONDS_PER_YEAR = 60L * 60 * 24 * 365;
    private static final long SECONDS_PER_MONTH = 60L * 60 * 24 * 30;
    private static final long SECONDS_PER_DAY = 60L * 60 * 24;
    private static final long SECONDS_PER_HOUR = 60L * 60;
    private static final long SECONDS_PER_MINUTE = 60L;

    public static String convertSeconds(long seconds) {
        long years = seconds / SECONDS_PER_YEAR;
        seconds %= SECONDS_PER_YEAR;
        long months = seconds / SECONDS_PER_MONTH;
        seconds %= SECONDS_PER_MONTH;
        long days = seconds / SECONDS_PER_DAY;
        seconds %= SECONDS_PER_DAY;
        long hours = seconds / SECONDS_PER_HOUR;
        seconds %= SECONDS_PER_HOUR;
        long minutes = seconds / SECONDS_PER_MINUTE;
        seconds %= SECONDS_PER_MINUTE;

        StringBuilder result = new StringBuilder(32);

        if (years > 0) {
            result.append(years).append("y ");
        }
        if (months > 0) {
            result.append(months).append("m ");
        }
        if (days > 0) {
            result.append(days).append("d ");
        }
        if (hours > 0) {
            result.append(hours).append("h ");
        }
        if (minutes > 0) {
            result.append(minutes).append("m ");
        }
        if (seconds > 0) {
            result.append(seconds).append("s");
        }

        if (result.length() == 0) {
            return "0s";
        }
        if (result.charAt(result.length() - 1) == ' ') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    public static String formatMilliseconds(long millis) {
        return convertSeconds(TimeUnit.MILLISECONDS.toSeconds(millis));
    }
}
