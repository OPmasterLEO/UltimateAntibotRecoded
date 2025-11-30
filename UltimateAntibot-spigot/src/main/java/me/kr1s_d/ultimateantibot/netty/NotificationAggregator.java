package me.kr1s_d.ultimateantibot.netty;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.kr1s_d.ultimateantibot.PacketAntibotManager;

public final class NotificationAggregator {
    private final PacketAntibotManager manager;
    private final Queue<String> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> keepAliveQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

    public NotificationAggregator(PacketAntibotManager manager) {
        this.manager = manager;
    }

    public void submitPacketSeen(String connId) {
        if (connId == null) return;
        packetQueue.offer(connId);
    }

    public void submitClientKeepAlive(String connId) {
        if (connId == null) return;
        keepAliveQueue.offer(connId);
    }

    public void flush() {
        try {
            String id;
            while ((id = packetQueue.poll()) != null) {
                if (seen.putIfAbsent(id, Boolean.TRUE) == null) {
                    try { manager.notifyPacketSeen(id); } catch (Throwable ignored) {}
                }
            }

            while ((id = keepAliveQueue.poll()) != null) {
                try { manager.notifyClientKeepAlive(id); } catch (Throwable ignored) {}
            }

            if (!seen.isEmpty()) {
                seen.clear();
            }
        } catch (Throwable t) {
        }
    }
}
