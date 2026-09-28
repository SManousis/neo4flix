package com.neo4flix.user.watchlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WatchlistApplicationServiceTest {

    private final WatchlistRepository repository = mock(WatchlistRepository.class);
    private WatchlistApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WatchlistApplicationService(repository);
    }

    @Test
    void addReturnsWhetherTheRelationshipWasCreated() {
        when(repository.add("user-1", "movie-1")).thenReturn(true);

        assertThat(service.add("user-1", "movie-1")).isTrue();
        verify(repository).add("user-1", "movie-1");
    }

    @Test
    void repeatedAddRemainsIdempotent() {
        when(repository.add("user-1", "movie-1")).thenReturn(false);

        assertThat(service.add("user-1", "movie-1")).isFalse();
    }

    @Test
    void removeAndListUseAuthenticatedSubject() {
        var response = new WatchlistDtos.PageResponse(
                List.of(new WatchlistDtos.MovieEntry("movie-1", "Arrival", "First contact", 2016, null,
                        Instant.parse("2026-09-14T00:00:00Z"))),
                0, 24, 1, 1);
        when(repository.findMine("user-1", 0, 24)).thenReturn(response);
        when(repository.remove("user-1", "movie-1")).thenReturn(true);

        assertThat(service.findMine("user-1", 0, 24)).isSameAs(response);
        assertThat(service.remove("user-1", "movie-1")).isTrue();
        verify(repository).findMine("user-1", 0, 24);
        verify(repository).remove("user-1", "movie-1");
    }

    @Test
    void rejectsInvalidPaging() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findMine("user-1", -1, 24))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findMine("user-1", 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
