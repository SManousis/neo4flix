package com.neo4flix.recommendation.core;

import com.neo4flix.recommendation.persistence.RecommendationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationApplicationServiceTest {

    private final RecommendationRepository repository = mock(RecommendationRepository.class);
    private final RecommendationConfiguration configuration = RecommendationConfiguration.defaults();
    private final RecommendationApplicationService service = new RecommendationApplicationService(
            repository, configuration, new RecommendationScoringService());

    @Test
    void exposesSafeDefaultsAndRejectsInvalidConfiguration() {
        assertThat(configuration.maxPageSize()).isEqualTo(50);
        assertThat(configuration.peerLimit()).isEqualTo(10);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecommendationConfiguration(0.6, 0.3, 0.2, 5.0, 2, 10, 50, 3, 50));
    }

    @Test
    void rejectsBlankUsersAndNonPositiveLimits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.recommend(new RecommendationDtos.Query(" ", 10, 2, 10, 50, null, null, null, null)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.recommend(new RecommendationDtos.Query("user-1", 0, 2, 10, 50, null, null, null, null)));
    }

    @Test
    void clampsOversizedLimitsBeforeCallingTheRepository() {
        when(repository.snapshot(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RecommendationRepository.Snapshot(0, false, List.of()));

        service.recommend(new RecommendationDtos.Query("user-1", 500, 2, 500, 500, null, null, null, null));

        verify(repository).snapshot(org.mockito.ArgumentMatchers.argThat(query ->
                query.limit() == 50 && query.peerLimit() == 10 && query.candidateLimit() == 50));
    }

    @Test
    void ranksResultsDeterministicallyAndKeepsPeerIdentityOutOfResults() {
        RecommendationDtos.SignalRow lower = row("movie-b", 0.2, 0.2, 0.8);
        RecommendationDtos.SignalRow higher = row("movie-a", 0.9, 0.2, 0.8);
        when(repository.snapshot(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RecommendationRepository.Snapshot(3, true, List.of(lower, higher)));

        List<RecommendationDtos.Result> results = service.recommend(
                new RecommendationDtos.Query("user-1", 10, 2, 10, 50, null, null, null, null));

        assertThat(results).extracting(RecommendationDtos.Result::movieId)
                .containsExactly("movie-a", "movie-b");
        assertThat(results).allSatisfy(result -> {
            assertThat(result.strategy()).isEqualTo(RecommendationDtos.Strategy.HYBRID);
            assertThat(result.reason()).doesNotContain("user-");
            assertThat(result.score()).isBetween(0.0, 1.0);
        });
    }

    private static RecommendationDtos.SignalRow row(String movieId, double collaborative, double content,
                                                     double popularity) {
        return new RecommendationDtos.SignalRow(
                movieId, movieId, null, null, null, List.of(), collaborative, content, popularity,
                1, 2, 4.0, 10);
    }
}
