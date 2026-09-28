package com.neo4flix.rating.api;

import java.time.Instant;

public record RatingResponse(String movieId, int score, Instant createdAt, Instant updatedAt) {
}
