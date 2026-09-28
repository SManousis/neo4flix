package com.neo4flix.recommendation.core;

import org.springframework.stereotype.Component;

@Component
public final class RecommendationScoringService {

    private static final String COLLABORATIVE_REASON = "Users with similar ratings also liked this movie.";
    private static final String CONTENT_REASON = "Matches genres from movies you rated highly.";
    private static final String POPULARITY_REASON = "Highly rated by Neo4flix users.";

    public RecommendationDtos.Strategy selectStrategy(int ratingCount, boolean qualifyingPeer) {
        if (ratingCount <= 0) {
            return RecommendationDtos.Strategy.POPULARITY;
        }
        if (ratingCount < 3 || !qualifyingPeer) {
            return RecommendationDtos.Strategy.CONTENT_PLUS_POPULARITY;
        }
        return RecommendationDtos.Strategy.HYBRID;
    }

    public double preferenceFor(int score) {
        return switch (score) {
            case 1 -> -1.0;
            case 2 -> -0.5;
            case 3 -> 0.0;
            case 4 -> 0.5;
            case 5 -> 1.0;
            default -> throw new IllegalArgumentException("rating score must be between 1 and 5");
        };
    }

    public double popularityScore(double averageRating, long ratingCount, double priorCount) {
        if (!Double.isFinite(averageRating) || ratingCount <= 0 || !Double.isFinite(priorCount) || priorCount <= 0) {
            return 0.0;
        }
        double normalizedAverage = clamp(averageRating / 5.0);
        double confidence = ratingCount / (ratingCount + priorCount);
        return clamp(normalizedAverage * confidence);
    }

    public double score(RecommendationDtos.Strategy strategy, RecommendationDtos.SignalRow row,
                        RecommendationWeights weights) {
        double collaborative = clamp(row.collaborativeScore());
        double content = clamp(row.contentScore());
        double popularity = clamp(row.popularityScore());
        return switch (strategy) {
            case POPULARITY -> popularity;
            case CONTENT_PLUS_POPULARITY -> clamp(
                    (content * weights.content() + popularity * weights.popularity())
                            / (weights.content() + weights.popularity()));
            case HYBRID -> clamp(
                    weights.collaborative() * collaborative
                            + weights.content() * content
                            + weights.popularity() * popularity);
        };
    }

    public String reason(RecommendationDtos.Strategy strategy, RecommendationDtos.SignalRow row) {
        if (strategy == RecommendationDtos.Strategy.HYBRID && row.collaborativeScore() > 0) {
            return COLLABORATIVE_REASON;
        }
        if (strategy != RecommendationDtos.Strategy.POPULARITY && row.contentScore() > 0) {
            return CONTENT_REASON;
        }
        return POPULARITY_REASON;
    }

    public static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
