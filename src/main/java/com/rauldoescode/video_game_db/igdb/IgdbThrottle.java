package com.rauldoescode.video_game_db.igdb;

import io.github.bucket4j.BlockingStrategy;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Outbound rate limiter for IGDB. IGDB allows 4 requests per second and 8 in flight at once,
 * counted per client id rather than per caller, so the limit has to be enforced here rather
 * than by the inbound API rate limits. Callers block until there is capacity or the timeout expires.
 */
@Component
public class IgdbThrottle {

    /**
     * A call to IGDB. Declares IOException so this composes with ClientHttpRequestInterceptor,
     * which is where the throttle is applied.
     * @param <T> what the call returns
     */
    @FunctionalInterface
    public interface IgdbCall<T> {
        T call() throws IOException;
    }

    private final Bucket bucket;
    private final Semaphore concurrency;
    private final Duration timeout;
    private final BlockingStrategy blockingStrategy;

    /**
     * Creates the throttle used at runtime: a real monotonic clock, and parking the calling thread
     * while it waits for a token. Annotated because the class has a second constructor, so Spring
     * cannot infer which one to inject into.
     * @param properties limits from the igdb.* configuration
     */
    @Autowired
    public IgdbThrottle(IgdbProperties properties) {
        this(properties, TimeMeter.SYSTEM_NANOTIME, BlockingStrategy.PARKING);
    }

    /**
     * Creates the throttle with an injected clock and waiting strategy so tests can drive time
     * forward instead of sleeping.
     * @param properties limits from the igdb.* configuration
     * @param timeMeter clock the token bucket reads
     * @param blockingStrategy how a caller waits when the bucket is empty
     */
    IgdbThrottle(IgdbProperties properties, TimeMeter timeMeter, BlockingStrategy blockingStrategy) {
        long perSecond = properties.requestsPerSecond();
        // Greedy refill trickles tokens back continuously. Intervally would release all four at
        // once every second, which is a burst IGDB can reject.
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(perSecond).refillGreedy(perSecond, Duration.ofSeconds(1)))
                .withCustomTimePrecision(timeMeter)
                .build();
        this.concurrency = new Semaphore(properties.maxConcurrent());
        this.timeout = properties.throttleTimeout();
        this.blockingStrategy = blockingStrategy;
    }

    /**
     * Runs the call once there is throttle capacity for it.
     * @param call the IGDB call to run
     * @param <T> what the call returns
     * @return whatever the call returned
     * @throws IOException if the call fails, or if capacity did not free up within the timeout
     */
    public <T> T execute(IgdbCall<T> call) throws IOException {
        acquire();
        try {
            return call.call();
        } finally {
            concurrency.release();
        }
    }

    /**
     * Takes one token, then one concurrency permit. Tokens come first so a request that will never
     * be allowed to start does not occupy a permit while it waits.
     * @throws IOException if either wait exceeds the timeout, or the thread is interrupted
     */
    private void acquire() throws IOException {
        try {
            if (!bucket.asBlocking().tryConsume(1, timeout.toNanos(), blockingStrategy)) {
                throw new IOException("IGDB throttle: no request slot within " + timeout);
            }
            if (!concurrency.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IOException("IGDB throttle: no concurrency permit within " + timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for IGDB throttle capacity", e);
        }
    }
}
