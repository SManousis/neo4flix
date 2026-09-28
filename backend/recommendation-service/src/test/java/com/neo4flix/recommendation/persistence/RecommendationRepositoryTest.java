package com.neo4flix.recommendation.persistence;

import com.neo4flix.recommendation.core.RecommendationDtos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRepositoryTest {

    @Test
    void collaborativeQueryUsesAlignedVectorsAndGdsWithBoundedParameters() {
        assertThat(RecommendationNeo4jRepository.COLLABORATIVE_QUERY)
                .contains("gds.similarity.cosine")
                .contains("$userId", "$minimumOverlap", "$peerLimit")
                .contains("ORDER BY movie.id");
        assertThat(RecommendationNeo4jRepository.QUALIFYING_PEER_QUERY)
                .contains("$userId", "$minimumOverlap")
                .contains("count(peer)");
    }

    @Test
    void candidateQueryExcludesAlreadyRatedMoviesAndUsesBoundedFilters() {
        assertThat(RecommendationNeo4jRepository.CANDIDATE_QUERY)
                .contains("NOT (me)-[:RATED]->(movie)")
                .contains("$candidateLimit", "$genre", "$fromYear", "$toYear", "$minimumAverageRating")
                .contains("movie.id ASC");
    }

    @Test
    void parametersKeepUserAndLimitsOutOfCypherText() {
        RecommendationDtos.Query query = new RecommendationDtos.Query(
                "user-1", 12, 2, 10, 50, "Science Fiction", 1990, 2025, 3.5);

        assertThat(RecommendationNeo4jRepository.parameters(query, 5.0))
                .containsEntry("userId", "user-1")
                .containsEntry("minimumOverlap", 2)
                .containsEntry("peerLimit", 10)
                .containsEntry("candidateLimit", 50)
                .containsEntry("priorCount", 5.0)
                .containsEntry("genre", "Science Fiction");
    }

    @Test
    void mapsNullableMovieFieldsWithoutExposingPeerDetails() {
        RecommendationDtos.SignalRow row = RecommendationNeo4jRepository.mapSignalRow(Map.of(
                "movieId", "movie-1",
                "title", "Arrival",
                "genres", java.util.List.of("Science Fiction"),
                "collaborativeScore", 0.25,
                "contentScore", 0.75,
                "popularityScore", 0.5,
                "peerCount", 2L,
                "commonMovies", 3L,
                "averageRating", 4.5,
                "ratingCount", 10L));

        assertThat(row.movieId()).isEqualTo("movie-1");
        assertThat(row.overview()).isNull();
        assertThat(row.releaseYear()).isNull();
        assertThat(row.posterUrl()).isNull();
        assertThat(row.peerCount()).isEqualTo(2);
        assertThat(row.commonMovies()).isEqualTo(3);
    }

    @Test
    void contentScorePreservesNegativeAndNeutralGenreAffinity() {
        assertThat(RecommendationNeo4jRepository.contentScore(
                java.util.List.of("Action"), Map.of("Action", -1.0))).isEqualTo(0.0);
        assertThat(RecommendationNeo4jRepository.contentScore(
                java.util.List.of("Drama"), Map.of("Drama", 0.0))).isEqualTo(0.5);
        assertThat(RecommendationNeo4jRepository.contentScore(
                java.util.List.of("Science Fiction"), Map.of("Science Fiction", 1.0))).isEqualTo(1.0);
    }
}
