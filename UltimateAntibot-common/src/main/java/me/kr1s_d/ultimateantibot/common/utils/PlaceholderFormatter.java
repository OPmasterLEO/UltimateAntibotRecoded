package me.kr1s_d.ultimateantibot.common.utils;

import java.util.Map;

public final class PlaceholderFormatter {
    private PlaceholderFormatter() {}

    public static String apply(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values.isEmpty()) {
            return template;
        }
        int len = template.length();
        StringBuilder out = new StringBuilder(len + 16);
        for (int i = 0; i < len; i++) {
            char c = template.charAt(i);
            if (c == '%') {
                int start = i;
                int end = template.indexOf('%', start + 1);
                if (end > start) {
                    String key = template.substring(start, end + 1);
                    String val = values.get(key);
                    if (val != null) {
                        out.append(val);
                        i = end;
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
