package com.neo4flix.recommendation.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RecommendationWeightsTest {

    @Test
    void exposesTheApprovedDefaultWeights() {
        RecommendationWeights weights = RecommendationWeights.defaults();

        assertThat(weights.collaborative()).isEqualTo(0.60);
        assertThat(weights.content()).isEqualTo(0.30);
        assertThat(weights.popularity()).isEqualTo(0.10);
        assertThat(weights.popularityPriorCount()).isEqualTo(5.0);
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecommendationWeights(0.6, 0.3, 0.2, 5.0))
                .withMessageContaining("sum");
    }

    @Test
    void rejectsNegativeWeightsAndNonPositivePopularityPrior() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecommendationWeights(-0.1, 0.8, 0.3, 5.0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecommendationWeights(0.6, 0.3, 0.1, 0.0));
    }
}
