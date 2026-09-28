package com.neo4flix.movie.recommendation;

public record MovieRecommendationQueryParams(
        String genre,
        Integer fromYear,
        Integer toYear,
        Double minimumAverageRating,
        String sort,
        Integer page,
        Integer size) {
}
