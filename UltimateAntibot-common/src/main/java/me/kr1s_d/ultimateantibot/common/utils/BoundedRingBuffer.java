package me.kr1s_d.ultimateantibot.common.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class BoundedRingBuffer {
    private final long[] buffer;
    private final int capacity;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger size = new AtomicInteger(0);
    
    public BoundedRingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new long[capacity];
    }

    public void add(long timestamp) {
        int index = writeIndex.getAndIncrement() % capacity;
        buffer[index] = timestamp;
        
        int currentSize;
        do {
            currentSize = size.get();
            if (currentSize >= capacity) break;
        } while (!size.compareAndSet(currentSize, currentSize + 1));
    }

    public int removeOlderThan(long cutoffTimestamp) {
        int removed = 0;
        int currentSize = size.get();
        
        for (int i = 0; i < currentSize; i++) {
            if (buffer[i] > 0 && buffer[i] < cutoffTimestamp) {
                buffer[i] = 0;
                removed++;
            }
        }
        
        size.addAndGet(-removed);
        return removed;
    }

    public int size() {
        return size.get();
    }

    public void clear() {
        writeIndex.set(0);
        size.set(0);
    }

    public long getOldest() {
        long oldest = Long.MAX_VALUE;
        int currentSize = size.get();
        
        for (int i = 0; i < currentSize; i++) {
            long val = buffer[i];
            if (val > 0 && val < oldest) {
                oldest = val;
            }
        }
        
        return oldest == Long.MAX_VALUE ? 0 : oldest;
    }
}
