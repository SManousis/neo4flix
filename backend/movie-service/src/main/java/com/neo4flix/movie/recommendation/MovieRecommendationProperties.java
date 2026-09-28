package com.neo4flix.movie.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4flix.recommendation")
public record MovieRecommendationProperties(
        String baseUrl,
        long connectTimeoutMillis,
        long readTimeoutMillis) {

    public MovieRecommendationProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "http://recommendation-service:8084" : baseUrl;
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 2_000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 5_000 : readTimeoutMillis;
    }
}
