package me.kr1s_d.ultimateantibot;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Clean, single-class PacketAntibotManager implementing PacketListener.
 * Updated for broader PacketEvents 2.x API compatibility.
 */
public class PacketAntibotManager extends PacketListenerAbstract {

    private final JavaPlugin plugin;

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
    private static final long VERIFICATION_SWEEP_TICKS = 100L;

    public PacketAntibotManager(JavaPlugin plugin) {
        this.plugin = plugin;

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
                plugin.getLogger().log(Level.INFO, "PacketAntibotManager: aggressiveMode=" + shouldAggressive + " (recentHandshakes=" + recentHandshakes.size() + ")");
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            final User user = event.getUser();
            if (user == null) return;
            final InetSocketAddress address = user.getAddress();
            if (address == null) return;

            final String ip = address.getAddress().getHostAddress();
            final long now = System.currentTimeMillis();

            synchronized (recentHandshakes) { recentHandshakes.addLast(now); }

            // Use PacketType constants for detection
            PacketTypeCommon type = event.getPacketType();

            if (type == PacketType.Handshaking.Client.HANDSHAKE) {
                handleHandshake(user, ip, now);
                return;
            }

            if (type == PacketType.Login.Client.LOGIN_START) {
                WrapperLoginClientLoginStart login = new WrapperLoginClientLoginStart(event);
                handleLoginStart(user, login, now);
                return;
            }

            if (type == PacketType.Play.Client.CLIENT_SETTINGS) {
                WrapperPlayClientSettings settings = new WrapperPlayClientSettings(event);
                handleSettings(user, settings);
                return;
            }

            if (type == PacketType.Play.Client.PLUGIN_MESSAGE) {
                WrapperPlayClientPluginMessage msg = new WrapperPlayClientPluginMessage(event);
                handlePluginMessage(user, msg);
                return;
            }

            if (type == PacketType.Play.Client.KEEP_ALIVE) {
                WrapperPlayClientKeepAlive ka = new WrapperPlayClientKeepAlive(event);
                handleKeepAlive(user, ka);
            }
        } catch (Throwable ignored) {
            // Ignored intentionally for safety
        }
    }

    private void handleHandshake(User user, String ip, long now) {
        long throttle = aggressiveMode.get() ? 800L : BASE_CONNECTION_THROTTLE_MS;
        Long last = ipConnectionHistory.get(ip);
        if (last != null && now - last < throttle) {
            if (!aggressiveMode.get()) {
                try { user.closeConnection(); } catch (Throwable ignored) {}
            } else {
                pendingVerification.putIfAbsent(getConnectionId(user), new VerificationData());
            }
            return;
        }
        ipConnectionHistory.put(ip, now);
        handshakeTimestamps.put(getConnectionId(user), now);
        pendingVerification.putIfAbsent(getConnectionId(user), new VerificationData());
    }

    private void handleLoginStart(User user, WrapperLoginClientLoginStart login, long now) {
        String connId = getConnectionId(user);
        Long handshake = handshakeTimestamps.get(connId);
        if (handshake == null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                Long h = handshakeTimestamps.get(connId);
                if (h == null && !aggressiveMode.get()) {
                    try { user.closeConnection(); } catch (Throwable ignored) {}
                }
            });
            return;
        }
        long delta = now - handshake;
        long slowThreshold = aggressiveMode.get() ? BASE_SLOW_BOT_THRESHOLD_MS * 2 : BASE_SLOW_BOT_THRESHOLD_MS;
        if (delta > slowThreshold) {
            if (!aggressiveMode.get()) {
                try { user.closeConnection(); } catch (Throwable ignored) {}
            } else {
                pendingVerification.computeIfAbsent(connId, k -> new VerificationData()).markedSlow = true;
            }
            return;
        }

        String name = login.getUsername();
        if (name == null || !name.matches("[a-zA-Z0-9_]{3,16}")) {
            try { user.closeConnection(); } catch (Throwable ignored) {}
            return;
        }

        VerificationData data = pendingVerification.computeIfAbsent(connId, k -> new VerificationData());
        data.handshakePassed = true;
        data.lastActivity = now;
    }

    private void handleSettings(User user, WrapperPlayClientSettings settings) {
        VerificationData data = pendingVerification.get(getConnectionId(user));
        if (data != null) {
            data.hasSentSettings = true;
            data.lastActivity = System.currentTimeMillis();
        }
    }

    private void handlePluginMessage(User user, WrapperPlayClientPluginMessage msg) {
        VerificationData data = pendingVerification.get(getConnectionId(user));
        if (data == null) return;
        String channel = msg.getChannelName();
        if (channel == null) return;
        if (channel.equalsIgnoreCase("minecraft:brand") || channel.equalsIgnoreCase("MC|Brand")) {
            data.hasSentPluginMessage = true;
            data.lastActivity = System.currentTimeMillis();
        }
    }

    private void handleKeepAlive(User user, WrapperPlayClientKeepAlive keepAlive) {
        VerificationData data = pendingVerification.get(getConnectionId(user));
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

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        User user = event.getUser();
        if (user == null) return;
        String connId = getConnectionId(user);
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
            if (System.currentTimeMillis() - data.lastKeepAliveSentAt < 1000L) continue;
            User user = findUserByConnectionId(connId);
            if (user == null) continue;
            long id = ThreadLocalRandom.current().nextLong();
            try {
                WrapperPlayServerKeepAlive keepAlive = new WrapperPlayServerKeepAlive(id);
                user.sendPacket(keepAlive);
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
                User user = findUserByConnectionId(connId);
                boolean isLocal = user != null && user.getAddress() != null && user.getAddress().getAddress().isLoopbackAddress();
                if (!isLocal && variance < 0.5D && !aggressiveMode.get()) {
                    if (user != null) try { user.closeConnection(); } catch (Throwable ignored) {}
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
                User user = findUserByConnectionId(connId);
                if (!data.hasSentSettings && !aggressiveMode.get()) {
                    if (user != null) try { user.closeConnection(); } catch (Throwable ignored) {}
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

    private User findUserByConnectionId(String connId) {
        try {
            // Updated PacketEvents 2.x API call for users
            Collection<User> users = PacketEvents.getAPI().getProtocolManager().getUsers();
            
            for (User u : users) {
                if (getConnectionId(u).equals(connId)) return u;
            }
        } catch (Throwable ignored) {
            // Fallback just in case
        }
        return null;
    }

    private String getConnectionId(User user) {
        if (user == null || user.getAddress() == null) return "unknown";
        InetSocketAddress a = user.getAddress();
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    private static class VerificationData {
        final long createdAt = System.currentTimeMillis();
        volatile boolean handshakePassed = false;
        volatile boolean hasSentSettings = false;
        volatile boolean hasSentPluginMessage = false;
        volatile boolean verified = false;
        volatile boolean markedSlow = false;
        volatile long lastActivity = System.currentTimeMillis();
        volatile long lastKeepAliveSentAt = 0L;
        final List<Long> keepAliveSamples = new ArrayList<>();
    }
}