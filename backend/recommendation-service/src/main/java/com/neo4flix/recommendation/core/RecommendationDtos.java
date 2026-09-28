package com.neo4flix.recommendation.core;

import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public enum Strategy {
        POPULARITY,
        CONTENT_PLUS_POPULARITY,
        HYBRID
    }

    public record Query(
            String userId,
            int limit,
            int minimumOverlap,
            int peerLimit,
            int candidateLimit,
            String genre,
            Integer fromYear,
            Integer toYear,
            Double minimumAverageRating) {
    }

    public record SignalRow(
            String movieId,
            String title,
            String overview,
            Integer releaseYear,
            String posterUrl,
            List<String> genres,
            double collaborativeScore,
            double contentScore,
            double popularityScore,
            int peerCount,
            int commonMovies,
            double averageRating,
            long ratingCount,
            String releaseDate,
            List<String> genreIds) {
        public SignalRow(String movieId, String title, String overview, Integer releaseYear, String posterUrl,
                         List<String> genres, double collaborativeScore, double contentScore,
                         double popularityScore, int peerCount, int commonMovies,
                         double averageRating, long ratingCount) {
            this(movieId, title, overview, releaseYear, posterUrl, genres, collaborativeScore, contentScore,
                    popularityScore, peerCount, commonMovies, averageRating, ratingCount, null, List.of());
        }
    }

    public record Result(
            String movieId,
            String title,
            String overview,
            Integer releaseYear,
            String posterUrl,
            double score,
            Strategy strategy,
            String reason,
            List<String> genres,
            List<String> genreIds,
            String releaseDate,
            double averageRating,
            long ratingCount,
            double collaborativeScore,
            double contentScore,
            double popularityScore) {
        public Result(String movieId, String title, String overview, Integer releaseYear, String posterUrl,
                      double score, Strategy strategy, String reason) {
            this(movieId, title, overview, releaseYear, posterUrl, score, strategy, reason,
                    List.of(), List.of(), null, 0.0, 0L, 0.0, 0.0, 0.0);
        }
    }
}
