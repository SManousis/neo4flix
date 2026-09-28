package com.neo4flix.user.watchlist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistRepositoryTest {

    @Test
    void keyUsesAuthenticatedUserAndMovieIds() {
        assertThat(WatchlistRepository.keyFor("user-1", "movie-1")).isEqualTo("user-1:movie-1");
    }
}
