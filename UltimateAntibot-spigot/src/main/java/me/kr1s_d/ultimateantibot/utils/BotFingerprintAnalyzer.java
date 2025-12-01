package me.kr1s_d.ultimateantibot.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BotFingerprintAnalyzer {
    
    private static final Set<String> KNOWN_BOT_BRANDS = new HashSet<>(Arrays.asList(
        "",
        "MCProtocolLib",
        "MinecraftBot",
        "BotClient",
        "AutoClient"
    ));
    
    private static final Set<String> LEGITIMATE_BRANDS = new HashSet<>(Arrays.asList(
        "vanilla",
        "fabric",
        "forge",
        "lunar",
        "badlion",
        "feather",
        "pvplounge",
        "labymod"
    ));
    
    private static final long HANDSHAKE_TO_LOGIN_MIN = 50L;
    private static final long HANDSHAKE_TO_LOGIN_MAX = 2000L;
    private static final long LOGIN_TO_SETTINGS_MIN = 30L;
    private static final long LOGIN_TO_SETTINGS_MAX = 500L;
    private static final long KEEPALIVE_RESPONSE_MIN = 5L;
    private static final long KEEPALIVE_RESPONSE_HUMAN_MIN = 20L;
    private final Map<String, Deque<Long>> subnetConnections = new ConcurrentHashMap<>();
    private final Map<String, List<String>> usernamePatternCache = new ConcurrentHashMap<>();

    public double analyzeClientBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) {
            return 0.7;
        }
        
        String lowerBrand = brand.toLowerCase();
        
        if (KNOWN_BOT_BRANDS.contains(lowerBrand)) {
            return 0.9;
        }
        
        if (LEGITIMATE_BRANDS.contains(lowerBrand)) {
            return 0.0;
        }
        
        if (lowerBrand.equals("vanilla")) {
            return 0.3;
        }
        
        return 0.1;
    }

    public double analyzeHandshakeToLoginTiming(long deltaMs) {
        if (deltaMs < HANDSHAKE_TO_LOGIN_MIN) {
            return 0.95;
        }
        
        if (deltaMs > HANDSHAKE_TO_LOGIN_MAX) {
            return 0.4;
        }
        
        return 0.0;
    }
    
    /**
     * Analyze login to settings timing.
     */
    public double analyzeLoginToSettingsTiming(long deltaMs) {
        if (deltaMs < LOGIN_TO_SETTINGS_MIN) {
            return 0.85;
        }
        
        if (deltaMs > LOGIN_TO_SETTINGS_MAX) {
            return 0.3;
        }
        
        return 0.0;
    }

    public double analyzeKeepaliveResponseTimes(List<Long> samples) {
        if (samples == null || samples.size() < 3) {
            return 0.0;
        }
        
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        
        double p50 = getPercentile(sorted, 0.5);
        double p99 = getPercentile(sorted, 0.99);
        double min = sorted.get(0);
        
        if (p50 < KEEPALIVE_RESPONSE_MIN) {
            return 0.95;
        }
        
        if (p99 - p50 < 2.0) {
            return 0.85;
        }
        
        if (p50 < KEEPALIVE_RESPONSE_HUMAN_MIN) {
            return 0.6;
        }
        
        return 0.0;
    }
    
    /**
     * Calculate percentile from sorted list.
     */
    private double getPercentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index).doubleValue();
    }
    
    /**
     * Detect subnet-level burst attacks.
     * Returns bot probability if coordinated attack detected.
     */
    public double analyzeSubnetBurst(String ip, long timestamp) {
        String subnet = extractSubnet(ip);
        if (subnet == null) return 0.0;
        
        Deque<Long> connections = subnetConnections.computeIfAbsent(subnet, k -> new ArrayDeque<>());
        
        synchronized (connections) {
            long cutoff = timestamp - 10_000L;
            connections.removeIf(t -> t < cutoff);
            
            connections.add(timestamp);
            
            if (connections.size() >= 10) {
                return 0.9;
            }
            
            long recent = timestamp - 5_000L;
            long recentCount = connections.stream().filter(t -> t >= recent).count();
            if (recentCount >= 5) {
                return 0.7;
            }
        }
        
        return 0.0;
    }
    
    /**
     * Extract /24 subnet from IP address.
     */
    private String extractSubnet(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot == -1) return null;
        return ip.substring(0, lastDot) + ".0";
    }
    
    /**
     * Analyze username patterns for bot swarms.
     * Detects sequential names (Bot1, Bot2, Bot3) and similar patterns.
     */
    public double analyzeUsernamePattern(String username, List<String> recentUsernames) {
        if (username == null || username.length() < 3) return 0.0;
        if (recentUsernames == null || recentUsernames.size() < 3) return 0.0;
        
        int sequentialMatches = 0;
        String basePattern = username.replaceAll("[0-9]+$", ""); // Remove trailing numbers
        
        for (String other : recentUsernames) {
            if (other.equals(username)) continue;
            
            String otherBase = other.replaceAll("[0-9]+$", "");
            if (basePattern.equalsIgnoreCase(otherBase) && basePattern.length() >= 3) {
                sequentialMatches++;
            }
        }
        
        if (sequentialMatches >= 5) {
            return 0.95;
        }
        
        if (sequentialMatches >= 3) {
            return 0.75;
        }
        
        int similarNames = 0;
        for (String other : recentUsernames) {
            if (other.equals(username)) continue;
            if (calculateEditDistance(username, other) <= 2) {
                similarNames++;
            }
        }
        
        if (similarNames >= 5) {
            return 0.85;
        }
        
        return 0.0;
    }

    private int calculateEditDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        
        if (Math.abs(len1 - len2) > 3) return 999;
        
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        
        return prev[len2];
    }

    public double calculateWeightedScore(
            double brandScore,
            double handshakeTimingScore,
            double loginTimingScore,
            double keepaliveScore,
            double subnetScore,
            double usernameScore,
            boolean hasSettings,
            boolean hasPluginMessage
    ) {
        double highConfidence = 0.0;
        if (keepaliveScore > 0.8) highConfidence = Math.max(highConfidence, keepaliveScore);
        if (handshakeTimingScore > 0.8) highConfidence = Math.max(highConfidence, handshakeTimingScore);
        
        double mediumConfidence = 0.0;
        mediumConfidence += brandScore * 0.3;
        mediumConfidence += loginTimingScore * 0.3;
        mediumConfidence += subnetScore * 0.2;
        if (!hasSettings) mediumConfidence += 0.2;
        
        double lowConfidence = 0.0;
        lowConfidence += usernameScore * 0.5;
        if (!hasPluginMessage) lowConfidence += 0.5;
        
        return (highConfidence * 0.7) + (mediumConfidence * 0.2) + (lowConfidence * 0.1);
    }
    
    /**
     * Cleanup old subnet data (call periodically).
     */
    public void cleanupSubnetData(long olderThan) {
        subnetConnections.entrySet().removeIf(entry -> {
            Deque<Long> connections = entry.getValue();
            synchronized (connections) {
                connections.removeIf(t -> t < olderThan);
                return connections.isEmpty();
            }
        });
    }
}
