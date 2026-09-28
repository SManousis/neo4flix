package com.neo4flix.recommendation.share;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4flix.recommendation.share")
public record RecommendationShareProperties(int defaultExpiryDays, int minExpiryDays, int maxExpiryDays) {

    public RecommendationShareProperties {
        if (defaultExpiryDays < 1 || minExpiryDays < 1 || maxExpiryDays < minExpiryDays
                || defaultExpiryDays < minExpiryDays || defaultExpiryDays > maxExpiryDays) {
            throw new IllegalArgumentException("share expiry bounds are invalid");
        }
    }

    public static RecommendationShareProperties defaults() {
        return new RecommendationShareProperties(30, 1, 365);
    }
}
