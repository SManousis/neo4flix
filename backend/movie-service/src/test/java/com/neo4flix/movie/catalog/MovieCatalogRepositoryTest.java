package com.neo4flix.movie.catalog;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieCatalogRepositoryTest {

    @Test
    void queryNormalizesTextAndBoundsPageSize() {
        MovieQuery query = MovieQuery.from(
                "  Alien  ", null, null, null, null, "createdAt", "desc", 3, 500);

        assertThat(query.title()).isEqualTo("Alien");
        assertThat(query.page()).isEqualTo(3);
        assertThat(query.size()).isEqualTo(100);
        assertThat(query.sortCypher()).isEqualTo("m.createdAt");
        assertThat(query.parameters()).containsEntry("title", "Alien");
    }

    @Test
    void queryAlwaysProvidesNullableCypherParametersAndSafeSortDefaults() {
        MovieQuery query = MovieQuery.from(" ", " ", null, null, null, null, "sideways", -4, 0);

        assertThat(query.title()).isNull();
        assertThat(query.genre()).isNull();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(1);
        assertThat(query.direction()).isEqualTo("desc");
        assertThat(query.sortCypher()).isEqualTo("m.createdAt");
        assertThat(query.parameters()).containsKeys("title", "genre", "minYear", "maxYear", "skip", "limit");
        assertThat(query.parameters().get("title")).isNull();
    }

    @Test
    void queryRejectsUnknownSortFields() {
        assertThatThrownBy(() -> MovieQuery.from(null, null, null, null, null, "drop table", "desc", 0, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort");
    }

    @Test
    void movieDeletionRemovesRecommendationSharesBeforeDetachingMovie() {
        assertThat(MovieCatalogRepository.DELETE_MOVIE_QUERY)
                .contains("RecommendationShare")
                .contains("DETACH DELETE")
                .contains("$id");
    }
}
