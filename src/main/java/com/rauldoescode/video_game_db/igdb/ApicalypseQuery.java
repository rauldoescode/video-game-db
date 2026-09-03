package com.rauldoescode.video_game_db.igdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApicalypseQuery {

    private String[] fields;
    private String searchTerm;
    private final List<String> whereClauses = new ArrayList<>();
    private Integer limit;
    private boolean limitExplicit;
    private Integer offset;

    /**
     * Sets the fields to be returned by the query.
     * @param fields fields to return
     * @return this ApicalypseQuery instance
     */
    public ApicalypseQuery fields(String... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("Fields must be non-null and non-empty");
        }

        for (String field : fields) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("Field must be non-null and non-empty");
            }
            if (field.strip().equals("*")) {
                throw new IllegalArgumentException("Field must not be \"*\"");
            }
        }

        this.fields = Arrays.copyOf(fields, fields.length);
        return this;
    }

    /**
     * Sets the search term for the query.
     * @param searchTerm search term to use
     * @return this ApicalypseQuery instance
     */
    public ApicalypseQuery search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            throw new IllegalArgumentException("Search term must be non-null and non-empty");
        }

        // If no limit is set, default to 20
        if (!limitExplicit) {
            this.limit = 20;
        }

        this.searchTerm = searchTerm;
        return this;
    }

    /**
     * Adds a where clause to the query.
     * @param clause where clause to add
     * @return this ApicalypseQuery instance
     */
    public ApicalypseQuery where(String clause) {
        if (clause == null || clause.isBlank()) {
            throw new IllegalArgumentException("Where clause must be non-null and non-empty");
        }

        whereClauses.add(clause);
        return this;
    }

    /**
     * Sets the limit for the query.
     * @param limit maximum number of results to return
     * @return this ApicalypseQuery instance
     */
    public ApicalypseQuery limit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        // Clamp limit to 500 (IGDB max)
        this.limit = Math.min(limit, 500);
        this.limitExplicit = true;
        return this;
    }

    /**
     * Sets the offset for the query.
     * @param offset number of results to skip
     * @return this ApicalypseQuery instance
     */
    public ApicalypseQuery offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }

        this.offset = offset;
        return this;
    }

    /**
     * Builds the Apicalypse query string.
     * @return the Apicalypse query string
     */
    public String build() {
        if (fields == null || fields.length == 0) {
            throw new IllegalStateException("Fields must be set before building the query");
        }

        StringBuilder query = new StringBuilder();
        appendClause(query, "fields " + String.join(",", fields));

        if (searchTerm != null) {
            appendClause(query, "search \"" + escapeSearchTerm(searchTerm) + "\"");
        }

        if (!whereClauses.isEmpty()) {
            appendClause(query, "where " + String.join(" & ", whereClauses));
        }

        if (limit != null) {
            appendClause(query, "limit " + limit);
        }

        if (offset != null) {
            appendClause(query, "offset " + offset);
        }

        return query.toString();
    }

    /**
     * Appends a clause to the query string, ensuring there's a space between clauses.
     * @param query the query string to append to
     * @param clause the clause to append
     */
    private static void appendClause(StringBuilder query, String clause) {
        if (!query.isEmpty()) {
            query.append(' ');
        }
        query.append(clause).append(';');
    }

    /**
     * Escapes a search term by replacing backslashes and double quotes with their escaped counterparts.
     * @param term the search term to escape
     * @return the escaped search term
     */
    private static String escapeSearchTerm(String term) {
        return term.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
