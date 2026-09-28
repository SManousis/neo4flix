package com.neo4flix.rating.api;

public record RatingSummaryResponse(String movieId, Double averageRating, long ratingCount) {
}
