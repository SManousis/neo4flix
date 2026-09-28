package com.neo4flix.rating.api;

import java.time.Instant;

public record RatingHistoryEntry(
        String movieId,
        String movieTitle,
        int score,
        Instant createdAt,
        Instant updatedAt) {
}
