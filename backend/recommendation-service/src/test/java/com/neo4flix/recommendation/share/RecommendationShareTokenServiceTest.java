package com.neo4flix.recommendation.share;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationShareTokenServiceTest {

    private final RecommendationShareTokenService service = new RecommendationShareTokenService();

    @Test
    void issuesDistinctUrlSafeTokensAndAHashThatDoesNotContainTheRawToken() {
        RecommendationShareTokenService.IssuedToken first = service.issue();
        RecommendationShareTokenService.IssuedToken second = service.issue();

        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.rawToken()).matches("[A-Za-z0-9_-]{40,}");
        assertThat(first.hash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.hash()).doesNotContain(first.rawToken());
        assertThat(service.hash(first.rawToken())).isEqualTo(first.hash());
    }

    @Test
    void producesCollisionResistantValuesAcrossManyIssues() {
        Set<String> rawTokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            rawTokens.add(service.issue().rawToken());
        }

        assertThat(rawTokens).hasSize(100);
    }
}
