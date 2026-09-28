package com.neo4flix.recommendation.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoringServiceTest {

    private final RecommendationScoringService service = new RecommendationScoringService();
    private final RecommendationWeights weights = RecommendationWeights.defaults();

    @Test
    void selectsDocumentedColdStartStrategies() {
        assertThat(service.selectStrategy(0, false)).isEqualTo(RecommendationDtos.Strategy.POPULARITY);
        assertThat(service.selectStrategy(2, true)).isEqualTo(RecommendationDtos.Strategy.CONTENT_PLUS_POPULARITY);
        assertThat(service.selectStrategy(3, true)).isEqualTo(RecommendationDtos.Strategy.HYBRID);
        assertThat(service.selectStrategy(3, false)).isEqualTo(RecommendationDtos.Strategy.CONTENT_PLUS_POPULARITY);
    }

    @Test
    void mapsRatingsSoLowScoresAreNotPositiveAffinity() {
        assertThat(service.preferenceFor(1)).isEqualTo(-1.0);
        assertThat(service.preferenceFor(2)).isEqualTo(-0.5);
        assertThat(service.preferenceFor(3)).isEqualTo(0.0);
        assertThat(service.preferenceFor(4)).isEqualTo(0.5);
        assertThat(service.preferenceFor(5)).isEqualTo(1.0);
    }

    @Test
    void appliesPopularityConfidenceToSmallRatingCounts() {
        assertThat(service.popularityScore(4.0, 5, 5.0)).isEqualTo(0.4);
        assertThat(service.popularityScore(5.0, 0, 5.0)).isEqualTo(0.0);
    }

    @Test
    void clampsSignalsAndFinalScoreToTheDocumentedRange() {
        RecommendationDtos.SignalRow row = row(1.2, -0.4, 0.5);

        assertThat(service.score(RecommendationDtos.Strategy.HYBRID, row, weights)).isEqualTo(0.65);
        assertThat(service.score(RecommendationDtos.Strategy.POPULARITY, row, weights)).isEqualTo(0.5);
    }

    @Test
    void choosesReasonsFromObservedSignals() {
        assertThat(service.reason(RecommendationDtos.Strategy.HYBRID, row(0.8, 0.2, 0.9)))
                .isEqualTo("Users with similar ratings also liked this movie.");
        assertThat(service.reason(RecommendationDtos.Strategy.CONTENT_PLUS_POPULARITY, row(0.0, 0.8, 0.9)))
                .isEqualTo("Matches genres from movies you rated highly.");
        assertThat(service.reason(RecommendationDtos.Strategy.POPULARITY, row(0.0, 0.0, 0.9)))
                .isEqualTo("Highly rated by Neo4flix users.");
    }

    private static RecommendationDtos.SignalRow row(double collaborative, double content, double popularity) {
        return new RecommendationDtos.SignalRow(
                "movie-1", "Arrival", "First contact", 2016, null, List.of("Science Fiction"),
                collaborative, content, popularity, 2, 3, 4.5, 10);
    }
}
