package me.kr1s_d.ultimateantibot;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PacketAntibotManager {

    private final JavaPlugin plugin;
    private final ConnectionController controller;

    private final Map<String, Long> ipConnectionHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> handshakeTimestamps = new ConcurrentHashMap<>();
    private final Map<String, VerificationData> pendingVerification = new ConcurrentHashMap<>();

    private final Deque<Long> recentHandshakes = new ArrayDeque<>();
    private final AtomicBoolean aggressiveMode = new AtomicBoolean(false);

    private static final long BASE_CONNECTION_THROTTLE_MS = 2000L;
    private static final long BASE_SLOW_BOT_THRESHOLD_MS = 3000L;
    private static final int GLOBAL_RATE_THRESHOLD_PER_5S = 500;

    private static final long CLEANUP_TIMER_TICKS = 20L * 10;
    private static final long KEEPALIVE_SEND_TICKS = 40L;
    private static final long VERIFICATION_SWEEP_TICKS = 600L;

    public PacketAntibotManager(JavaPlugin plugin, ConnectionController controller) {
        this.plugin = plugin;
        this.controller = controller;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupAndEvaluate, CLEANUP_TIMER_TICKS, CLEANUP_TIMER_TICKS);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendKeepalivesToPending, 20L, KEEPALIVE_SEND_TICKS);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::finalVerificationSweep, VERIFICATION_SWEEP_TICKS, VERIFICATION_SWEEP_TICKS);
    }

    private void cleanupAndEvaluate() {
        long now = System.currentTimeMillis();
        ipConnectionHistory.entrySet().removeIf(e -> now - e.getValue() > 30_000L);
        handshakeTimestamps.entrySet().removeIf(e -> now - e.getValue() > 30_000L);
        pendingVerification.entrySet().removeIf(e -> now - e.getValue().createdAt > 60_000L);

        synchronized (recentHandshakes) {
            while (!recentHandshakes.isEmpty() && now - recentHandshakes.peekFirst() > 5_000L) recentHandshakes.pollFirst();
            boolean shouldAggressive = recentHandshakes.size() > GLOBAL_RATE_THRESHOLD_PER_5S;
            if (shouldAggressive != aggressiveMode.get()) {
                aggressiveMode.set(shouldAggressive);
                plugin.getLogger().log(Level.INFO, "PacketAntibotManager: aggressiveMode={0} (recentHandshakes={1})", new Object[]{shouldAggressive, recentHandshakes.size()});
            }
        }
    }

    public void notifyPacketSeen(String connId) {
        long now = System.currentTimeMillis();
        synchronized (recentHandshakes) { recentHandshakes.addLast(now); }
    }

    public void notifyHandshake(String connId, InetSocketAddress address) {
        String ip = address == null ? "unknown" : address.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        handleHandshake(connId, ip, now);
    }

    public void notifyLoginStart(String connId, String username) {
        long now = System.currentTimeMillis();
        handleLoginStart(connId, username, now);
    }

    public void notifyClientSettings(String connId) {
        handleSettings(connId);
    }

    public void notifyPluginMessage(String connId, String channel) {
        handlePluginMessage(connId, channel);
    }

    public void notifyClientKeepAlive(String connId) {
        handleKeepAlive(connId);
    }

    private void handleHandshake(String connId, String ip, long now) {
        long throttle = aggressiveMode.get() ? 800L : BASE_CONNECTION_THROTTLE_MS;
        Long last = ipConnectionHistory.get(ip);
        if (last != null && now - last < throttle) {
                pendingVerification.putIfAbsent(connId, new VerificationData());
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                    Long h = handshakeTimestamps.get(connId);
                    VerificationData d = pendingVerification.get(connId);
                    boolean handshakePassed = d != null && d.handshakePassed;
                    if ((h == null || !handshakePassed) && !aggressiveMode.get()) {
                        plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection after delayed throttle check conn={0} ip={1}", new Object[]{connId, ip});
                        try { controller.closeConnection(connId); } catch (Throwable ignored) {}
                        pendingVerification.remove(connId);
                    }
                });
                return;
            }
        ipConnectionHistory.put(ip, now);
        handshakeTimestamps.put(connId, now);
        pendingVerification.putIfAbsent(connId, new VerificationData());
    }

    private void handleLoginStart(String connId, String username, long now) {
        Long handshake = handshakeTimestamps.get(connId);
        if (handshake == null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                Long h = handshakeTimestamps.get(connId);
                if (h == null && !aggressiveMode.get()) {
                    plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection (missing-handshake after delay) conn={0}", new Object[]{connId});
                    try { controller.closeConnection(connId); } catch (Throwable ignored) {}
                }
            });
            return;
        }
        long delta = now - handshake;
        long slowThreshold = aggressiveMode.get() ? BASE_SLOW_BOT_THRESHOLD_MS * 2 : BASE_SLOW_BOT_THRESHOLD_MS;
        if (delta > slowThreshold) {
            if (!aggressiveMode.get()) {
                plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection (slow-handshake) conn={0} delta={1}", new Object[]{connId, delta});
                try { controller.closeConnection(connId); } catch (Throwable ignored) {}
            } else {
                pendingVerification.computeIfAbsent(connId, k -> new VerificationData()).markedSlow = true;
            }
            return;
        }

        String name = username;
        if (name == null || !name.matches("[a-zA-Z0-9_]{3,16}")) {
            plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection (invalid-username) conn={0} name={1}", new Object[]{connId, name});
            try { controller.closeConnection(connId); } catch (Throwable ignored) {}
            return;
        }

        VerificationData data = pendingVerification.computeIfAbsent(connId, k -> new VerificationData());
        data.handshakePassed = true;
        data.lastActivity = now;
    }

    private void handleSettings(String connId) {
        VerificationData data = pendingVerification.get(connId);
        if (data != null) {
            data.hasSentSettings = true;
            data.lastActivity = System.currentTimeMillis();
        }
    }

    private void handlePluginMessage(String connId, String channel) {
        VerificationData data = pendingVerification.get(connId);
        if (data == null) return;
        if (channel.equalsIgnoreCase("minecraft:brand") || channel.equalsIgnoreCase("MC|Brand")) {
            data.hasSentPluginMessage = true;
            data.lastActivity = System.currentTimeMillis();
        }
    }

    private void handleKeepAlive(String connId) {
        VerificationData data = pendingVerification.get(connId);
        if (data == null) return;
        long now = System.currentTimeMillis();
        long sentAt = data.lastKeepAliveSentAt;
        if (sentAt > 0) {
            long interval = now - sentAt;
            synchronized (data.keepAliveSamples) {
                if (data.keepAliveSamples.size() >= 5) data.keepAliveSamples.remove(0);
                data.keepAliveSamples.add(interval);
            }
            data.lastActivity = now;
        }
    }

    public void notifyDisconnect(String connId) {
        if (connId == null) return;
        handshakeTimestamps.remove(connId);
        pendingVerification.remove(connId);
    }

    private void sendKeepalivesToPending() {
        if (pendingVerification.isEmpty()) return;
        int sends = 0;
        for (Map.Entry<String, VerificationData> e : pendingVerification.entrySet()) {
            if (sends >= 1000) break;
            String connId = e.getKey();
            VerificationData data = e.getValue();
            if (data.verified) continue;
            if (!data.handshakePassed) continue;
            if (System.currentTimeMillis() - data.lastKeepAliveSentAt < 1000L) continue;
            if (controller.getAddress(connId) == null) continue;
            try {
                if (!controller.hasPlayer(connId)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            long id = ThreadLocalRandom.current().nextLong();
            try {
                boolean ok = controller.sendKeepAlive(connId, id);
                if (!ok) continue;
            } catch (Throwable ignored) {}
            data.lastKeepAliveSentAt = System.currentTimeMillis();
            sends++;
        }
    }

    private void finalVerificationSweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, VerificationData> e : pendingVerification.entrySet()) {
            String connId = e.getKey();
            VerificationData data = e.getValue();
            if (data.verified) { pendingVerification.remove(connId); continue; }

            List<Long> samples;
            synchronized (data.keepAliveSamples) { samples = new ArrayList<>(data.keepAliveSamples); }
            if (samples.size() >= 3) {
                double variance = calculateVariance(samples);
                InetSocketAddress addr1 = controller.getAddress(connId);
                boolean isLocal = addr1 != null && addr1.getAddress().isLoopbackAddress();
                    if (!isLocal && variance < 0.5D && !aggressiveMode.get() && !data.hasSentSettings && !data.hasSentPluginMessage) {
                        plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection (keepalive-variance+no-settings) conn={0} variance={1}", new Object[]{connId, variance});
                        try { controller.closeConnection(connId); } catch (Throwable ignored) {}
                        pendingVerification.remove(connId);
                        continue;
                    }
                if (data.hasSentSettings && (data.hasSentPluginMessage || samples.size() >= 3)) {
                    data.verified = true;
                    pendingVerification.remove(connId);
                    continue;
                }
            }

            long ageMs = now - data.createdAt;
            if (ageMs >= VERIFICATION_SWEEP_TICKS * 50L) {
                boolean lowSamples = samples.size() < 3;
                boolean shouldClose = !data.hasSentSettings && !data.hasSentPluginMessage && lowSamples && !aggressiveMode.get();
                boolean handshakePassed = data.handshakePassed;
                boolean hasPlayer = controller.hasPlayer(connId);

                if (shouldClose && !handshakePassed && !hasPlayer) {
                    plugin.getLogger().log(Level.INFO, "PacketAntibotManager: closing connection (verification-timeout-missing-settings) conn={0} ageMs={1}", new Object[]{connId, ageMs});
                    try { controller.closeConnection(connId); } catch (Throwable ignored) {}
                    pendingVerification.remove(connId);
                } else {
                    data.verified = true;
                    pendingVerification.remove(connId);
                }
            }
        }
    }

    private double calculateVariance(List<Long> samples) {
        if (samples.isEmpty()) return Double.MAX_VALUE;
        double sum = 0D;
        for (Long v : samples) sum += v;
        double mean = sum / samples.size();
        double sq = 0D;
        for (Long v : samples) { double diff = v - mean; sq += diff * diff; }
        return sq / samples.size();
    }

    

    private static class VerificationData {
        final long createdAt = System.currentTimeMillis();
        volatile boolean handshakePassed = false;
        volatile boolean hasSentSettings = false;
        volatile boolean hasSentPluginMessage = false;
        volatile boolean verified = false;
        @SuppressWarnings("unused")
        volatile boolean markedSlow = false;
        @SuppressWarnings("unused")
        volatile long lastActivity = System.currentTimeMillis();
        volatile long lastKeepAliveSentAt = 0L;
        final List<Long> keepAliveSamples = new ArrayList<>();
    }
}