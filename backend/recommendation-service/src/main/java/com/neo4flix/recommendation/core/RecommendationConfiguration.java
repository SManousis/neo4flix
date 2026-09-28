package com.neo4flix.recommendation.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4flix.recommendation")
public record RecommendationConfiguration(
        double collaborativeWeight,
        double contentWeight,
        double popularityWeight,
        double popularityPriorCount,
        int minimumOverlap,
        int peerLimit,
        int candidateLimit,
        int matureRatingThreshold,
        int maxPageSize) {

    public RecommendationConfiguration {
        new RecommendationWeights(collaborativeWeight, contentWeight, popularityWeight, popularityPriorCount);
        if (minimumOverlap < 1 || peerLimit < 1 || candidateLimit < 1 || maxPageSize < 1) {
            throw new IllegalArgumentException("recommendation bounds must be positive");
        }
        if (matureRatingThreshold < 3) {
            throw new IllegalArgumentException("mature rating threshold must be at least 3");
        }
    }

    public static RecommendationConfiguration defaults() {
        return new RecommendationConfiguration(0.60, 0.30, 0.10, 5.0, 2, 10, 50, 3, 50);
    }

    public RecommendationWeights weights() {
        return new RecommendationWeights(collaborativeWeight, contentWeight, popularityWeight, popularityPriorCount);
    }
}
