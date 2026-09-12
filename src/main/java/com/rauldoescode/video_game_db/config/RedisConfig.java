package com.rauldoescode.video_game_db.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

/**
 * L2 cache wiring for IGDB lookups. Spring Cache is the API; Redis is the store. Spring Boot still
 * creates the {@code RedisCacheManager} and connection factory — this class turns caching on,
 * sets per-cache TTLs, writes JSON instead of JDK serialization, and treats Redis failures as
 * misses so a down cache never fails a user request.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    /** Spring cache name; Redis keys look like {@code game:details::{igdbId}}. */
    public static final String GAME_DETAILS = "game:details";

    /** Spring cache name; Redis keys look like {@code game:search::{sha256}}. */
    public static final String GAME_SEARCH = "game:search";

    /**
     * Shared Redis cache settings: Jackson 3 JSON values, and do not store nulls. Named caches
     * copy this and then set their own TTL.
     * <p>
     * Default typing writes an {@code @class} hint so a cached {@code List<IgdbGame>} can be
     * read back as that type rather than a raw JSON tree. The validator only allows this app's
     * types and {@code java.util.*} (lists/maps) - Redis is local, but the serializer still
     * refuses arbitrary classes.
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        GenericJacksonJsonRedisSerializer json = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.rauldoescode.video_game_db.")
                        .allowIfSubType("java.util.")
                        .build())
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
                .disableCachingNullValues();
    }

    /**
     * The cache manager, with a TTL per cache. Popular is not registered yet; that cache lands
     * with the popular endpoint.
     * <p>
     * Declared here rather than left to Boot because of {@code immediateWrites}. With Lettuce on
     * the classpath, Spring Data Redis writes cache entries asynchronously by default: put()
     * returns before Redis has the entry, so a lookup that immediately follows can still miss,
     * and a failed write is never reported to anyone. Immediate writes cost one local round trip
     * and make "the second identical request is a hit" true rather than likely.
     * @param connectionFactory Redis connection factory from Boot's auto-configuration
     * @param redisCacheConfiguration the shared serialization settings
     * @return the cache manager backing every {@code game:*} cache
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisCacheConfiguration redisCacheConfiguration) {
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory,
                configurer -> configurer.immediateWrites(true));
        return RedisCacheManager.builder(writer)
                .cacheDefaults(redisCacheConfiguration)
                .withCacheConfiguration(GAME_DETAILS, redisCacheConfiguration.entryTtl(Duration.ofDays(7)))
                .withCacheConfiguration(GAME_SEARCH, redisCacheConfiguration.entryTtl(Duration.ofHours(24)))
                .build();
    }

    /**
     * Redis get/put/evict failures are logged and swallowed, so a {@code @Cacheable} method runs
     * (or skips the put) instead of becoming a 500. {@code true} keeps the stack trace so a down
     * Redis is clear in logs.
     * <p>
     * {@code CacheErrorHandler} is Spring Cache's hook when the store throws, and it only applies
     * to annotation-driven caching. {@code IgdbLookupService} reads the cache directly and
     * handles its own failures, so this covers annotated caches added later rather than the IGDB
     * lookups. The default ({@code SimpleCacheErrorHandler}) rethrows, which would violate
     * "Redis down is not a request failure."
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(true);
    }
}
