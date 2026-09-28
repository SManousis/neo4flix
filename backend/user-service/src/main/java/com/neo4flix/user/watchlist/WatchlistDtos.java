package com.neo4flix.user.watchlist;

import java.time.Instant;
import java.util.List;

public final class WatchlistDtos {

    private WatchlistDtos() {
    }

    public record MovieEntry(
            String movieId,
            String title,
            String overview,
            Integer releaseYear,
            String posterUrl,
            Instant createdAt) {
    }

    public record PageResponse(
            List<MovieEntry> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
