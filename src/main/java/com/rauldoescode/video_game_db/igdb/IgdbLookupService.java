package com.rauldoescode.video_game_db.igdb;

import com.rauldoescode.video_game_db.config.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Reads games from the L2 Redis cache, calling IGDB only when the cache does not have them.
 * Every call still goes through the throttle, because the throttle lives in the interceptor
 * behind {@link IgdbClient} — a cache hit never reaches it and so never spends a request slot.
 * <p>
 * Two rules drive the shape of this class:
 * <ul>
 *   <li>Redis being down is not a request failure. Reads and writes are attempted, logged on
 *       failure, and otherwise ignored, which turns an unreachable cache into a plain miss.</li>
 *   <li>An expired key must not stampede IGDB. Callers that miss the same key at the same time
 *       collapse into one IGDB call and share its result.</li>
 * </ul>
 * The cache is driven by hand rather than with {@code @Cacheable} because the annotation cannot
 * satisfy both: the Redis cache writer Boot configures does not lock, so
 * {@code @Cacheable(sync = true)} lets every concurrent miss call IGDB, and {@code unless} —
 * which is how a not-found game would be kept out of the cache — is not allowed alongside
 * {@code sync}.
 */
@Service
public class IgdbLookupService {

    private static final Logger log = LoggerFactory.getLogger(IgdbLookupService.class);

    private final IgdbClient igdbClient;
    private final CacheManager cacheManager;

    /**
     * Loads that are running right now, keyed by cache name and key. An entry exists only while
     * its IGDB call is in flight; the caller that created it removes it.
     */
    private final ConcurrentMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    /**
     * @param igdbClient the throttled IGDB HTTP proxy
     * @param cacheManager Redis-backed cache manager from RedisConfig
     */
    public IgdbLookupService(IgdbClient igdbClient, CacheManager cacheManager) {
        this.igdbClient = igdbClient;
        this.cacheManager = cacheManager;
    }

    /**
     * Searches IGDB for games, serving the 24 hour cached page when there is one.
     * @param params the normalized search; its cacheKey() is the Redis key
     * @return matching games, empty when IGDB has none
     */
    public List<IgdbGame> search(IgdbSearchParams params) {
        return cached(RedisConfig.GAME_SEARCH, params.cacheKey(), () -> fetchSearch(params));
    }

    /**
     * Loads one game by IGDB id, serving the 7 day cached copy when there is one. A game IGDB
     * does not have is deliberately not cached, so a bad id does not hold the key for a week.
     * @param igdbId the IGDB game id
     * @return the game, or empty if IGDB has no game with that id
     */
    public Optional<IgdbGame> detail(long igdbId) {
        return Optional.ofNullable(
                cached(RedisConfig.GAME_DETAILS, Long.toString(igdbId), () -> fetchDetail(igdbId))
        );
    }

    /**
     * Loads the most popular games for a category, serving the 6 hour cached list when there is one.
     * The key includes the limit, so a size of 10 and a size of 20 do not share an entry.
     * @param category which IGDB popularity type to rank by
     * @param limit how many games to return
     * @return games in popularity order, empty when IGDB has none
     */
    public List<IgdbGame> popular(IgdbCategory category, int limit) {
        return cached(RedisConfig.GAME_POPULAR, category.name() + ":" + limit,
                () -> fetchPopular(category.popularityType(), limit));
    }

    /**
     * Returns the cached value for a key, or loads it once and caches it. A null from the loader
     * is returned but not stored.
     * @param cacheName which cache, and so which TTL, applies
     * @param key the key within that cache
     * @param loader how to get the value from IGDB when the cache does not have it
     * @param <T> the cached type
     * @return the cached or freshly loaded value
     */
    private <T> T cached(String cacheName, String key, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        T hit = read(cache, key);
        if (hit != null) {
            return hit;
        }
        // The write happens inside the single flight so only the caller that actually called
        // IGDB writes; the ones that waited already have the value.
        return singleFlight(cacheName + "::" + key, () -> {
            T loaded = loader.get();
            write(cache, key, loaded);
            return loaded;
        });
    }

    /**
     * Reads one key, treating any cache failure as a miss. That covers Redis being unreachable
     * and a stored value that no longer deserializes.
     * @param cache the cache to read, or null if no such cache is configured
     * @param key the key to read
     * @param <T> the cached type
     * @return the cached value, or null on a miss or a cache failure
     */
    @SuppressWarnings("unchecked")
    private <T> T read(Cache cache, String key) {
        if (cache == null) {
            return null;
        }
        try {
            Cache.ValueWrapper hit = cache.get(key);
            return hit == null ? null : (T) hit.get();
        } catch (RuntimeException e) {
            log.warn("IGDB L2 cache read failed for {}::{}; calling IGDB instead", cache.getName(), key, e);
            return null;
        }
    }

    /**
     * Stores a value, logging and ignoring a cache failure. Nulls are skipped because the Redis
     * cache is configured to reject them, and because a missing game should not be cached.
     * @param cache the cache to write to, or null if no such cache is configured
     * @param key the key to write
     * @param value the value to store, possibly null
     */
    private void write(Cache cache, String key, Object value) {
        if (cache == null || value == null) {
            return;
        }
        try {
            cache.put(key, value);
        } catch (RuntimeException e) {
            log.warn("IGDB L2 cache write failed for {}::{}; the value was not cached", cache.getName(), key, e);
        }
    }

    /**
     * Runs the loader once per key at a time. The first caller loads; callers that arrive while
     * it is running wait and return its value, so an expired key costs one IGDB call rather than
     * one per caller. Waiting is bounded by the loader itself, which is capped by the throttle
     * timeout and the HTTP client timeouts.
     * <p>
     * Locking is in this JVM only. One instance is the deployed topology, and a cross-instance
     * lock would mean extra Redis round-trips plus another way for a down Redis to break a
     * request.
     * @param flightKey cache name and key, so two caches cannot collide on the same key
     * @param loader the load to run at most once for this key
     * @param <T> the loaded type
     * @return the value the winning caller loaded
     */
    @SuppressWarnings("unchecked")
    private <T> T singleFlight(String flightKey, Supplier<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inFlight.putIfAbsent(flightKey, mine);

        if (running != null) {
            try {
                return (T) running.join();
            } catch (CompletionException e) {
                // Surface the original failure rather than the wrapper join() adds.
                throw e.getCause() instanceof RuntimeException cause ? cause : e;
            }
        }

        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (Throwable t) {
            // Catch everything: a waiting caller that is never completed would hang.
            mine.completeExceptionally(t);
            throw t;
        } finally {
            inFlight.remove(flightKey, mine);
        }
    }

    /**
     * Runs the search against IGDB.
     * @param params the normalized search
     * @return the games IGDB returned, never null
     */
    private List<IgdbGame> fetchSearch(IgdbSearchParams params) {
        ApicalypseQuery query = new ApicalypseQuery()
                .fields(IgdbFields.GAME)
                .search(params.q())
                .limit(params.limit())
                .offset(params.offset());

        // Params are lowercased, so filters use ~, which matches case-insensitively.
        if (params.genre() != null) {
            query.where("genres.name ~ *\"" + escape(params.genre()) + "\"*");
        }
        if (params.platform() != null) {
            query.where("platforms.name ~ *\"" + escape(params.platform()) + "\"*");
        }
        if (params.yearFrom() != null) {
            query.where("first_release_date >= " + startOfYear(params.yearFrom()));
        }
        if (params.yearTo() != null) {
            query.where("first_release_date < " + startOfYear(params.yearTo() + 1));
        }

        List<IgdbGame> games = igdbClient.games(query.build());
        // A fresh ArrayList, because the cached JSON carries the concrete class name and Jackson
        // has to be able to construct it again. List.of(...) and Arrays.asList(...) cannot be.
        return games == null ? new ArrayList<>() : new ArrayList<>(games);
    }

    /**
     * Loads one game by id from IGDB.
     * @param igdbId the IGDB game id
     * @return the game, or null if IGDB has no game with that id
     */
    private IgdbGame fetchDetail(long igdbId) {
        String query = new ApicalypseQuery()
                .fields(IgdbFields.GAME)
                .where("id = " + igdbId)
                .limit(1)
                .build();

        List<IgdbGame> games = igdbClient.games(query);
        return games == null || games.isEmpty() ? null : games.getFirst();
    }

    /**
     * Loads the top games for one popularity type. IGDB ranks ids on
     * {@code /popularity_primitives}; a second call loads those games. {@code /games} does not
     * keep the {@code where} order, so the primitives list is what puts the games back in rank.
     * An id the second call does not return is dropped.
     * @param type IGDB's {@code popularity_type} id
     * @param limit how many ranked ids to ask for
     * @return the games in rank order, never null
     */
    private List<IgdbGame> fetchPopular(int type, int limit) {
        String query = new ApicalypseQuery()
                .fields("game_id", "value")
                .where("popularity_type = " + type)
                .sort("value desc")
                .limit(limit)
                .build();

        List<IgdbPopularity> ranked = igdbClient.popularityPrimitives(query);
        if (ranked == null || ranked.isEmpty()) {
            // A fresh ArrayList, same reason as fetchSearch: the cached JSON names the concrete
            // class, and an empty list is an answer worth caching. A null would not be stored.
            return new ArrayList<>();
        }

        // Build the string of IDs from the ranked list
        StringBuilder ids = new StringBuilder();
        for (IgdbPopularity row : ranked) {
            if (!ids.isEmpty()) {
                ids.append(',');
            }
            ids.append(row.gameId());
        }

        // Build the query for /games call
        String gamesQuery = new ApicalypseQuery()
                .fields(IgdbFields.GAME)
                .where("id = (" + ids + ")")
                .limit(ranked.size())
                .build();

        // Store each returned game in a map by id
        Map<Long, IgdbGame> byId = new HashMap<>();
        List<IgdbGame> games = igdbClient.games(gamesQuery);
        if (games != null) {
            for (IgdbGame game : games) {
                byId.put(game.id(), game);
            }
        }

        // Load the games in the same order as the primitives
        List<IgdbGame> ordered = new ArrayList<>();
        for (IgdbPopularity row : ranked) {
            IgdbGame game = byId.get(row.gameId());
            if (game != null) {
                ordered.add(game);
            }
        }
        return ordered;
    }

    /**
     * IGDB stores release dates as Unix seconds, so a year filter becomes a timestamp.
     * @param year the calendar year
     * @return midnight UTC on January 1st of that year, in seconds
     */
    private static long startOfYear(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    /**
     * Escapes a filter value so a quote in a genre or platform name cannot end the string early.
     * @param value the filter value
     * @return the value with backslashes and quotes escaped
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
