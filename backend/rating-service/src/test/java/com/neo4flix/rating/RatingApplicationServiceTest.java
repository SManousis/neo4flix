package com.neo4flix.rating;

import com.neo4flix.rating.api.RatingPageResponse;
import com.neo4flix.rating.api.RatingResponse;
import com.neo4flix.rating.api.RatingSummaryResponse;
import com.neo4flix.rating.api.RatingWriteRequest;
import com.neo4flix.rating.persistence.RatingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RatingApplicationServiceTest {

    private final RatingRepository repository = mock(RatingRepository.class);
    private final RatingApplicationService service = new RatingApplicationService(repository);
    private final RatingResponse response = new RatingResponse(
            "movie-1", 5, Instant.parse("2026-09-14T10:00:00Z"), Instant.parse("2026-09-14T10:00:00Z"));

    @Test
    void delegatesCreateToTheAuthenticatedUser() {
        when(repository.create("user-1", new RatingWriteRequest("movie-1", 5))).thenReturn(response);

        assertThat(service.create("user-1", new RatingWriteRequest("movie-1", 5))).isEqualTo(response);
        verify(repository).create("user-1", new RatingWriteRequest("movie-1", 5));
    }

    @Test
    void delegatesOwnReadsUpdatesDeletesHistoryAndSummary() {
        RatingPageResponse page = new RatingPageResponse(java.util.List.of(), 0, 24, 0, 0);
        RatingSummaryResponse summary = new RatingSummaryResponse("movie-1", null, 0);
        when(repository.findOwn("user-1", "movie-1")).thenReturn(Optional.of(response));
        when(repository.updateOwn("user-1", "movie-1", 3)).thenReturn(Optional.of(response));
        when(repository.findHistory("user-1", 0, 24)).thenReturn(page);
        when(repository.findSummary("movie-1")).thenReturn(summary);

        assertThat(service.findOwn("user-1", "movie-1")).contains(response);
        assertThat(service.updateOwn("user-1", "movie-1", 3)).contains(response);
        service.deleteOwn("user-1", "movie-1");
        assertThat(service.findHistory("user-1", 0, 24)).isEqualTo(page);
        assertThat(service.findSummary("movie-1")).isEqualTo(summary);
        verify(repository).deleteOwn("user-1", "movie-1");
    }
}
