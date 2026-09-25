package com.rauldoescode.video_game_db.dto.response;

import java.util.List;

/**
 * Page response for paginated queries
 * @param content list of objects
 * @param page page number
 * @param size page size
 * @param totalElements total number of elements
 * @param totalPages total number of pages
 * @param <T> type of objects in the list
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
