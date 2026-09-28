package com.neo4flix.user.rating;

import java.time.Instant;
import java.util.List;

public final class RatingHistoryDtos {
    private RatingHistoryDtos() {
    }

    public record Entry(String movieId, String movieTitle, int score, Instant createdAt, Instant updatedAt) {
    }

    public record PageResponse(List<Entry> content, int page, int size, long totalElements, int totalPages) {
    }
}
