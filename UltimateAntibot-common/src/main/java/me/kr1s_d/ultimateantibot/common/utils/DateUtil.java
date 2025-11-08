package me.kr1s_d.ultimateantibot.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static String getCurrentHour(){
        return TIME_FORMATTER.format(LocalDateTime.now());
    }

    public static String getCurrentDate(){
        return DATE_FORMATTER.format(LocalDateTime.now());
    }

    public static String getFullDateAndTime(){
        return FULL_FORMATTER.format(LocalDateTime.now());
    }
}
