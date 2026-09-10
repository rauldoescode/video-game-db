package com.rauldoescode.video_game_db.igdb;

import io.github.bucket4j.BlockingStrategy;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgdbThrottleTest {

    /**
     * Stands in for both the clock the bucket reads and the way a caller waits, so the test never
     * sleeps on a real clock. Parking advances the fake clock by exactly the requested amount,
     * which is what a perfectly accurate park would do.
     */
    private static final class FakeClock implements TimeMeter, BlockingStrategy {

        private long nanos;
        private long parkedNanos;
        private int parks;

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        @Override
        public void park(long nanosToPark) {
            parkedNanos += nanosToPark;
            parks++;
            nanos += nanosToPark;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    /**
     * IGDB's real 4 requests per second, so the refill interval under test is the production one.
     * @param maxConcurrent requests allowed in flight at once
     * @param timeout how long a caller waits for capacity
     * @return properties for a throttle under test
     */
    private static IgdbProperties limits(int maxConcurrent, Duration timeout) {
        return new IgdbProperties("test-id", "test-secret",
                URI.create("http://localhost/oauth2/token"), Duration.ofSeconds(60),
                4, maxConcurrent, timeout);
    }

    @Test
    void firstFourCallsInASecondDoNotWait() throws IOException {
        FakeClock clock = new FakeClock();
        IgdbThrottle throttle = new IgdbThrottle(limits(8, Duration.ofSeconds(5)), clock, clock);
        AtomicInteger calls = new AtomicInteger();

        for (int i = 0; i < 4; i++) {
            throttle.execute(calls::incrementAndGet);
        }

        assertEquals(4, calls.get());
        assertEquals(0, clock.parks);
    }

    @Test
    void fifthCallWaitsForTheNextRefill() throws IOException {
        FakeClock clock = new FakeClock();
        IgdbThrottle throttle = new IgdbThrottle(limits(8, Duration.ofSeconds(5)), clock, clock);
        for (int i = 0; i < 4; i++) {
            throttle.execute(() -> null);
        }

        throttle.execute(() -> null);

        assertEquals(1, clock.parks);
        // 4 tokens per second refills one token every 250 ms.
        assertEquals(Duration.ofMillis(250).toNanos(), clock.parkedNanos);
    }

    @Test
    void fifthCallDoesNotWaitOnceTheBucketHasRefilled() throws IOException {
        FakeClock clock = new FakeClock();
        IgdbThrottle throttle = new IgdbThrottle(limits(8, Duration.ofSeconds(5)), clock, clock);
        for (int i = 0; i < 4; i++) {
            throttle.execute(() -> null);
        }

        clock.advance(Duration.ofSeconds(1));
        throttle.execute(() -> null);

        assertEquals(0, clock.parks);
    }

    @Test
    void callFailsWhenTheWaitWouldExceedTheTimeout() throws IOException {
        FakeClock clock = new FakeClock();
        // A 100 ms timeout cannot cover the 250 ms wait for the next token.
        IgdbThrottle throttle = new IgdbThrottle(limits(8, Duration.ofMillis(100)), clock, clock);
        for (int i = 0; i < 4; i++) {
            throttle.execute(() -> null);
        }

        IOException thrown = assertThrows(IOException.class, () -> throttle.execute(() -> null));

        assertTrue(thrown.getMessage().contains("no request slot"), thrown.getMessage());
    }

    @Test
    void concurrencyPermitIsReleasedWhenTheCallFails() throws IOException {
        FakeClock clock = new FakeClock();
        IgdbThrottle throttle = new IgdbThrottle(limits(1, Duration.ofMillis(100)), clock, clock);

        assertThrows(IOException.class, () -> throttle.execute(() -> {
            throw new IOException("IGDB exploded");
        }));

        // With only one permit, a leak here would make the next call fail instead of returning "ok".
        assertEquals("ok", throttle.execute(() -> "ok"));
    }
}
