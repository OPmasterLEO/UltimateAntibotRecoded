package me.kr1s_d.ultimateantibot.netty;

import java.util.concurrent.TimeUnit;

public final class TokenBucket {
    private final long refillIntervalNanos;
    private final int maxTokens;
    private volatile int tokens;
    private volatile long lastRefillNanos;

    public TokenBucket(int maxTokens, long refillIntervalMillis) {
        this.maxTokens = Math.max(1, maxTokens);
        this.refillIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, refillIntervalMillis));
        this.tokens = this.maxTokens;
        this.lastRefillNanos = System.nanoTime();
    }

    public boolean tryConsume() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed >= refillIntervalNanos) {
            long intervals = elapsed / refillIntervalNanos;
            int refill = (int) Math.min(intervals, Integer.MAX_VALUE);
            int newTokens = Math.min(maxTokens, tokens + refill);
            tokens = newTokens;
            lastRefillNanos = now;
        }
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }
}
