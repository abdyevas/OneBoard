package com.oneboard.vacancy.source;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Keeps a minimum interval between requests to the same host, so we honour each site's
 * robots.txt Crawl-delay (e.g. 30 s for jobsearch.az's API host).
 */
@Component
public class PoliteHttp {

    private final Map<String, Long> nextAllowedAt = new HashMap<>();

    /** Blocks until a request to {@code host} is allowed, then reserves the slot. */
    public void await(String host, Duration delay) {
        long waitMs;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextAllowedAt.getOrDefault(host, 0L));
            nextAllowedAt.put(host, slot + delay.toMillis());
            waitMs = slot - now;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + host, e);
            }
        }
    }

    /** Non-blocking variant: reserves the slot and returns true only if a request is allowed right now. */
    public synchronized boolean tryAcquire(String host, Duration delay) {
        long now = System.currentTimeMillis();
        if (nextAllowedAt.getOrDefault(host, 0L) > now) return false;
        nextAllowedAt.put(host, now + delay.toMillis());
        return true;
    }
}
