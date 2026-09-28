package com.neo4flix.recommendation.api;

import com.neo4flix.recommendation.core.RecommendationApplicationService;
import com.neo4flix.recommendation.core.RecommendationDtos;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationControllerTest {

    private final RecommendationApplicationService service = mock(RecommendationApplicationService.class);
    private final RecommendationController controller = new RecommendationController(service);
    private final Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-from-jwt")
            .claim("roles", List.of("USER"))
            .build();

    @Test
    void derivesIdentityFromJwtAndAppliesBoundedDefaults() {
        when(service.recommend(any())).thenReturn(List.of());

        RecommendationApiModels.Response response = controller.recommend(
                new RecommendationApiModels.QueryParams(null, null, null, null, null, null, null), jwt);

        verify(service).recommend(argThat(query ->
                query.userId().equals("user-from-jwt")
                        && query.limit() == 50
                        && query.candidateLimit() == 50));
        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void requestUserIdCannotOverrideJwtIdentity() {
        when(service.recommend(any())).thenReturn(List.of());

        controller.recommend(new RecommendationApiModels.QueryParams(
                "attacker", null, null, null, "rating", 0, 5), jwt);

        verify(service).recommend(argThat(query -> query.userId().equals("user-from-jwt")));
    }

    @Test
    void rejectsInvalidBoundsAndSortValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommend(
                new RecommendationApiModels.QueryParams("x", 1887, null, null, null, 0, 20), jwt));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommend(
                new RecommendationApiModels.QueryParams("x", null, 2201, null, null, 0, 20), jwt));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommend(
                new RecommendationApiModels.QueryParams("x", null, null, 5.1, null, 0, 20), jwt));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommend(
                new RecommendationApiModels.QueryParams("x", null, null, null, "unknown", 0, 20), jwt));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommend(
                new RecommendationApiModels.QueryParams("x", 2020, 2010, null, null, 0, 20), jwt));
    }

    @Test
    void rejectsMalformedHttpQueryNumbersAsValidationErrors() {
        assertThatIllegalArgumentException().isThrownBy(() -> controller.recommendHttp(
                "Science Fiction", "not-a-year", null, "NaN", "recommendation", "0", "20", jwt));
    }

    @Test
    void mapsStrategySignalsReasonsAndSlicesDeterministically() {
        when(service.recommend(any())).thenReturn(List.of(
                new RecommendationDtos.Result("movie-b", "B", null, 2000, null, .8,
                        RecommendationDtos.Strategy.HYBRID, "Users with similar ratings also liked this movie."),
                new RecommendationDtos.Result("movie-a", "A", null, 2001, null, .8,
                        RecommendationDtos.Strategy.HYBRID, "Users with similar ratings also liked this movie."),
                new RecommendationDtos.Result("movie-c", "C", null, 2002, null, .2,
                        RecommendationDtos.Strategy.HYBRID, "Highly rated by Neo4flix users.")));

        RecommendationApiModels.Response response = controller.recommend(
                new RecommendationApiModels.QueryParams(null, null, null, null, "recommendation", 1, 1), jwt);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().movie().id()).isEqualTo("movie-b");
        assertThat(response.items().getFirst().reason().type()).isEqualTo(RecommendationApiModels.ReasonType.SIMILAR_USERS);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items().getFirst().movie()).extracting("id", "title").containsExactly("movie-b", "B");
    }
}
