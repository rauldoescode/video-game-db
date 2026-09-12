package com.rauldoescode.video_game_db.igdb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * One IGDB game search, normalized so that two requests meaning the same thing land on the same
 * Redis entry. Values are normalized in the constructor, so the key, the equality of two params,
 * and the Apicalypse query built from them all agree.
 *
 * @param q search term; required
 * @param genre genre name filter, or null when not filtering
 * @param platform platform name filter, or null when not filtering
 * @param yearFrom earliest release year, inclusive, or null
 * @param yearTo latest release year, inclusive, or null
 * @param limit page size, clamped to IGDB's maximum
 * @param offset rows to skip
 */
public record IgdbSearchParams(
        String q,
        String genre,
        String platform,
        Integer yearFrom,
        Integer yearTo,
        int limit,
        int offset
) {

    /** IGDB's page size when the caller does not ask for one. */
    public static final int DEFAULT_LIMIT = 20;

    /** IGDB rejects a limit above this, so clamp rather than let the call fail. */
    public static final int MAX_LIMIT = 500;

    public IgdbSearchParams {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("Search term must be non-null and non-empty");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw new IllegalArgumentException("yearFrom must not be after yearTo");
        }

        q = q.strip().toLowerCase(Locale.ROOT);
        genre = normalizeFilter(genre);
        platform = normalizeFilter(platform);
        limit = Math.min(limit, MAX_LIMIT);
    }

    /**
     * A first page of results for a bare search term.
     * @param q search term
     * @return params with no filters, the default limit, and no offset
     */
    public static IgdbSearchParams of(String q) {
        return new IgdbSearchParams(q, null, null, null, null, DEFAULT_LIMIT, 0);
    }

    /**
     * Cache key for this search: SHA-256 of {@link #canonical()} as lowercase hex. Hashing keeps
     * the Redis key a fixed length and free of the characters a raw query string would carry.
     * @return 64 hex characters
     */
    public String cacheKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256, so this is unreachable rather than a case to handle.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * The normalized form that gets hashed. Absent filters are left out entirely so adding a
     * filter later cannot collide with a search that never had one, and the order is fixed so
     * the same search always produces the same string. Package-private so tests can read it.
     * @return the canonical representation of these params
     */
    String canonical() {
        StringJoiner canonical = new StringJoiner("|");
        canonical.add("q=" + q);
        if (genre != null) {
            canonical.add("genre=" + genre);
        }
        if (platform != null) {
            canonical.add("platform=" + platform);
        }
        if (yearFrom != null) {
            canonical.add("yearFrom=" + yearFrom);
        }
        if (yearTo != null) {
            canonical.add("yearTo=" + yearTo);
        }
        // Always present: two pages of the same search are different results.
        canonical.add("limit=" + limit);
        canonical.add("offset=" + offset);
        return canonical.toString();
    }

    /**
     * Treats a blank filter as no filter so {@code genre=""} and an omitted genre share a key.
     * @param value the raw filter value
     * @return the trimmed lowercase value, or null if there was nothing to filter on
     */
    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
