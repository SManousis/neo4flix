package com.neo4flix.recommendation.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationShareRepositoryTest {

    @Test
    void shareQueriesKeepIdentityTokenAndClockValuesAsParameters() {
        assertThat(RecommendationShareRepository.CREATE_QUERY)
                .contains("$ownerId", "$movieId", "$shareId", "$tokenHash", "$createdAt", "$expiresAt")
                .contains("CREATED_SHARE", "SHARES");
        assertThat(RecommendationShareRepository.PUBLIC_QUERY)
                .contains("$tokenHash", "$now", "revokedAt IS NULL")
                .contains("expiresAt IS NULL OR s.expiresAt > datetime($now)");
        assertThat(RecommendationShareRepository.OWNER_LIST_QUERY)
                .contains("$ownerId")
                .contains("ORDER BY s.createdAt DESC");
    }

    @Test
    void parametersDoNotEmbedRequestValuesIntoCypher() {
        Instant createdAt = Instant.parse("2026-09-15T12:00:00Z");
        Map<String, Object> params = RecommendationShareRepository.parameters(
                "user-1", "movie-1", "share-1", "hash", createdAt, createdAt.plusSeconds(86400));

        assertThat(params).containsEntry("ownerId", "user-1")
                .containsEntry("movieId", "movie-1")
                .containsEntry("shareId", "share-1")
                .containsEntry("tokenHash", "hash")
                .containsEntry("createdAt", createdAt.atZone(ZoneOffset.UTC))
                .containsEntry("expiresAt", createdAt.plusSeconds(86400).atZone(ZoneOffset.UTC));
    }
}
