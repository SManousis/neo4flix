package com.neo4flix.movie.recommendation;

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

public class MovieRecommendationClient {

    private static final Set<String> ALLOWED_QUERY_KEYS = Set.of(
            "genre", "fromYear", "toYear", "minimumAverageRating", "sort", "page", "size");

    private final RestClient restClient;

    public MovieRecommendationClient(RestClient.Builder builder, MovieRecommendationProperties properties) {
        this(builder.baseUrl(properties.baseUrl()).build(), properties);
    }

    public MovieRecommendationClient(RestClient restClient, MovieRecommendationProperties properties) {
        this.restClient = restClient;
    }

    public ResponseEntity<String> fetch(HttpHeaders incomingHeaders, MultiValueMap<String, String> query) {
        var uriBuilder = UriComponentsBuilder.fromPath("/api/v1/recommendations/me");
        if (query != null) {
            query.forEach((key, values) -> {
                if (ALLOWED_QUERY_KEYS.contains(key)) {
                    values.forEach(value -> {
                        if (value != null && !value.isBlank()) {
                            uriBuilder.queryParam(key, value);
                        }
                    });
                }
            });
        }

        try {
            return restClient.get()
                    .uri(uriBuilder.build().encode().toUri())
                    .headers(headers -> copyForwardedHeaders(incomingHeaders, headers))
                    .exchange((request, response) -> {
                        var body = response.bodyTo(String.class);
                        if (response.getStatusCode().is5xxServerError()) {
                            throw new RecommendationUnavailableException(
                                    "Recommendation service returned " + response.getStatusCode().value());
                        }
                        return ResponseEntity.status(response.getStatusCode())
                                .headers(response.getHeaders())
                                .body(body);
                    });
        } catch (RecommendationUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RecommendationUnavailableException("Recommendation service is unavailable", exception);
        }
    }

    private static void copyForwardedHeaders(HttpHeaders source, HttpHeaders target) {
        if (source == null) {
            return;
        }
        copy(source, target, HttpHeaders.AUTHORIZATION);
        copy(source, target, "X-Request-Id");
    }

    private static void copy(HttpHeaders source, HttpHeaders target, String name) {
        String value = source.getFirst(name);
        if (value != null && !value.isBlank()) {
            target.set(name, value);
        }
    }
}
