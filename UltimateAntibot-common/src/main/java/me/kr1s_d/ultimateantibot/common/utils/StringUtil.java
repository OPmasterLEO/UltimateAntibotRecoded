package me.kr1s_d.ultimateantibot.common.utils;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class StringUtil {
    
    private static final Cache<String, Double> similarityCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();
    
    public static int similarChars(String word1, String word2) {
        int count = 0;
        int minLength = Math.min(word1.length(), word2.length());
        for (int i = 0; i < minLength; i++) {
            char c1 = word1.charAt(i);
            char c2 = word2.charAt(i);
            if (c1 == c2 || Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
                count++;
            }
        }
        return count;
    }

    public static double calculateSimilarity(String str1, String str2) {
        if (str1.equals(str2)) return 100.0;
        int lenDiff = Math.abs(str1.length() - str2.length());
        int maxLen = Math.max(str1.length(), str2.length());
        if (maxLen > 0 && lenDiff > maxLen * 0.5) return 0.0;
        
        String cacheKey = str1.length() < str2.length() ? (str1 + "|" + str2) : (str2 + "|" + str1);
        Double cached = similarityCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        
        // Calculate similarity
        str1 = str1.toLowerCase();
        str2 = str2.toLowerCase();

        // check carattere per carattere
        int characterMatchCount = countCharacterMatches(str1, str2);
        int totalCharacters = Math.max(str1.length(), str2.length());

        // world checks
        int wordMatchCount = countWordMatches(str1, str2);
        int totalWords = Math.max(countWords(str1), countWords(str2));

        //conteggio delle lettere
        int letterMatchCount = countLetterMatches(str1, str2);
        int totalLetters = Math.max(countLetters(str1), countLetters(str2));

        double wordSimilarity = totalWords > 0 ? (double) wordMatchCount / totalWords : 0.0;
        double letterSimilarity = totalLetters > 0 ? (double) letterMatchCount / totalLetters : 0.0;
        double characterSimilarity = totalCharacters > 0 ? (double) characterMatchCount / totalCharacters : 0.0;

        // Calcolo della similarità totale come media dei tre rapporti
        double totalSimilarity = (characterSimilarity + wordSimilarity + letterSimilarity) / 3.0;
        double result = totalSimilarity * 100.0;
        
        // Cache the result
        similarityCache.put(cacheKey, result);
        
        return result;
    }

    private static int countCharacterMatches(String str1, String str2) {
        int matchCount = 0;
        int minLength = Math.min(str1.length(), str2.length());
        for (int i = 0; i < minLength; i++) {
            if (Character.toLowerCase(str1.charAt(i)) == Character.toLowerCase(str2.charAt(i))) {
                matchCount++;
            }
        }
        return matchCount;
    }

    private static int countWordMatches(String str1, String str2) {
        java.util.Set<String> words2 = tokenizeToSet(str2);
        int matchCount = 0;
        final int len = str1.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(str1.charAt(i))) i++;
            if (i >= len) break;
            int start = i;
            while (i < len && !Character.isWhitespace(str1.charAt(i))) i++;
            String w = str1.substring(start, i);
            if (words2.contains(w)) {
                matchCount++;
            }
        }
        return matchCount;
    }

    public static boolean isValidIPv4(String s) {
        s = s.replace("/", "");
        String[] parts = s.split("\\.");

        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);

                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // If all checks passed, return true
        return true;
    }

    private static int countWords(String str) {
        int len = str.length();
        int i = 0;
        int words = 0;
        boolean inWord = false;
        while (i < len) {
            char c = str.charAt(i++);
            if (Character.isWhitespace(c)) {
                if (inWord) inWord = false;
            } else {
                if (!inWord) {
                    inWord = true;
                    words++;
                }
            }
        }
        return words;
    }

    private static int countLetterMatches(String str1, String str2) {
        java.util.BitSet present = new java.util.BitSet();
        for (int i = 0; i < str2.length(); i++) {
            present.set(str2.charAt(i));
        }
        int matchCount = 0;
        for (int i = 0; i < str1.length(); i++) {
            if (present.get(str1.charAt(i))) {
                matchCount++;
            }
        }
        return matchCount;
    }

    private static int countLetters(String str) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) {
                count++;
            }
        }
        return count;
    }

    public static int spaces(String message) {
        int count = 0;
        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) == ' ') {
                count++;
            }
        }
        return count;
    }

    private static java.util.Set<String> tokenizeToSet(String str) {
        java.util.Set<String> set = new java.util.HashSet<>();
        final int len = str.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(str.charAt(i))) i++;
            if (i >= len) break;
            int start = i;
            while (i < len && !Character.isWhitespace(str.charAt(i))) i++;
            set.add(str.substring(start, i));
        }
        return set;
    }
}
