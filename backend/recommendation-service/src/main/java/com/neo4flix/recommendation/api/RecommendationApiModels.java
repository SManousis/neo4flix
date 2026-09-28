package com.neo4flix.recommendation.api;

import com.neo4flix.recommendation.core.RecommendationDtos;

import java.util.List;

public final class RecommendationApiModels {
    private RecommendationApiModels() {
    }

    public record QueryParams(
            String genre,
            Integer fromYear,
            Integer toYear,
            Double minimumAverageRating,
            String sort,
            Integer page,
            Integer size) {
    }

    public record Genre(String id, String name) {
    }

    public record MovieSummary(
            String id,
            String title,
            String overview,
            Integer releaseYear,
            String releaseDate,
            String posterUrl,
            List<Genre> genres,
            double averageRating,
            long ratingCount) {
    }

    public record Signals(double collaborative, double content, double popularity) {
    }

    public enum ReasonType {
        SIMILAR_USERS,
        GENRE_MATCH,
        POPULAR
    }

    public record Reason(ReasonType type, String text) {
    }

    public record Item(
            MovieSummary movie,
            double recommendationScore,
            Signals signals,
            RecommendationDtos.Strategy strategy,
            Reason reason) {
    }

    public record Response(
            List<Item> items,
            RecommendationDtos.Strategy strategy,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
