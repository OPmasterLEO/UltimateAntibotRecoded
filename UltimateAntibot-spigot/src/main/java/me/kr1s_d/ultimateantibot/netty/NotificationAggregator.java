package me.kr1s_d.ultimateantibot.netty;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.kr1s_d.ultimateantibot.PacketAntibotManager;


public final class NotificationAggregator {
    private static final int MAX_QUEUE_SIZE = 10000;
    private final PacketAntibotManager manager;
    private final Queue<String> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> keepAliveQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>(512, 0.75f, 4);
    private final AtomicInteger packetQueueSize = new AtomicInteger(0);
    private final AtomicInteger keepAliveQueueSize = new AtomicInteger(0);

    public NotificationAggregator(PacketAntibotManager manager) {
        this.manager = manager;
    }

    public void submitPacketSeen(String connId) {
        if (connId == null) return;
        int size = packetQueueSize.get();
        if (size >= MAX_QUEUE_SIZE) return;
        if (packetQueue.offer(connId)) {
            packetQueueSize.incrementAndGet();
        }
    }

    public void submitClientKeepAlive(String connId) {
        if (connId == null) return;
        int size = keepAliveQueueSize.get();
        if (size >= MAX_QUEUE_SIZE) return;
        if (keepAliveQueue.offer(connId)) {
            keepAliveQueueSize.incrementAndGet();
        }
    }

    public void flush() {
        try {
            if (packetQueueSize.get() == 0 && keepAliveQueueSize.get() == 0) return;

            String id;
            int processed = 0;
            while ((id = packetQueue.poll()) != null && processed < MAX_QUEUE_SIZE) {
                packetQueueSize.decrementAndGet();
                if (seen.putIfAbsent(id, Boolean.TRUE) == null) {
                    try { manager.notifyPacketSeen(id); } catch (Throwable ignored) {}
                }
                processed++;
            }

            processed = 0;
            while ((id = keepAliveQueue.poll()) != null && processed < MAX_QUEUE_SIZE) {
                keepAliveQueueSize.decrementAndGet();
                try { manager.notifyClientKeepAlive(id); } catch (Throwable ignored) {}
                processed++;
            }

            if (!seen.isEmpty()) {
                seen.clear();
            }
        } catch (Throwable t) {
        }
    }
}
