package com.neo4flix.rating.api;

import java.util.List;

public record RatingPageResponse(
        List<RatingHistoryEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
