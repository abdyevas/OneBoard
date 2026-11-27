package com.oneboard.vacancy.source;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PoliteHttpTest {

    @Test
    void spacesRequestsToTheSameHost() {
        PoliteHttp polite = new PoliteHttp();
        long t0 = System.nanoTime();
        polite.await("a.example", Duration.ofMillis(300));
        polite.await("a.example", Duration.ofMillis(300));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs >= 280, "second request must wait for the delay, waited " + elapsedMs + " ms");
    }

    @Test
    void hostsAreIndependentAndTryAcquireDoesNotBlock() {
        PoliteHttp polite = new PoliteHttp();
        assertTrue(polite.tryAcquire("a.example", Duration.ofSeconds(30)));
        assertFalse(polite.tryAcquire("a.example", Duration.ofSeconds(30)), "within the crawl delay");
        assertTrue(polite.tryAcquire("b.example", Duration.ofSeconds(30)), "another host is unaffected");
    }
}
