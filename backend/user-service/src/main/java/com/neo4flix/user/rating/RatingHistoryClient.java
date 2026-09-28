package com.neo4flix.user.rating;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RatingHistoryClient {

    private final RestClient client;

    public RatingHistoryClient(RestClient.Builder builder,
                               @Value("${neo4flix.rating-service.url:http://localhost:8083}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public RatingHistoryDtos.PageResponse findMine(String bearerToken, String requestId, int page, int size) {
        return client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/ratings/me")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .headers(headers -> {
                    headers.setBearerAuth(bearerToken);
                    if (requestId != null && !requestId.isBlank()) {
                        headers.set("X-Request-Id", requestId);
                    }
                })
                .retrieve()
                .body(RatingHistoryDtos.PageResponse.class);
    }
}
