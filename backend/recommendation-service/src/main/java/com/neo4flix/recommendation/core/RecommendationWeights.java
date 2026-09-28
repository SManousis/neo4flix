package com.neo4flix.recommendation.core;

public record RecommendationWeights(
        double collaborative,
        double content,
        double popularity,
        double popularityPriorCount) {

    private static final double SUM_TOLERANCE = 1e-9;

    public RecommendationWeights {
        if (!Double.isFinite(collaborative) || !Double.isFinite(content) || !Double.isFinite(popularity)
                || collaborative < 0 || content < 0 || popularity < 0) {
            throw new IllegalArgumentException("recommendation weights must be finite and non-negative");
        }
        if (Math.abs(collaborative + content + popularity - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("recommendation weights must sum to 1.0");
        }
        if (!Double.isFinite(popularityPriorCount) || popularityPriorCount <= 0) {
            throw new IllegalArgumentException("popularity prior count must be positive");
        }
    }

    public static RecommendationWeights defaults() {
        return new RecommendationWeights(0.60, 0.30, 0.10, 5.0);
    }
}
