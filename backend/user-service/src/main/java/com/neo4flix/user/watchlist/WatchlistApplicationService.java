package com.neo4flix.user.watchlist;

import org.springframework.stereotype.Service;

@Service
public class WatchlistApplicationService {

    private final WatchlistRepository repository;

    public WatchlistApplicationService(WatchlistRepository repository) {
        this.repository = repository;
    }

    public boolean add(String userId, String movieId) {
        requireIdentity(userId);
        requireMovieId(movieId);
        try {
            return repository.add(userId, movieId);
        } catch (WatchlistRepository.MovieNotFoundException exception) {
            throw new MovieNotFoundException();
        }
    }

    public boolean remove(String userId, String movieId) {
        requireIdentity(userId);
        requireMovieId(movieId);
        return repository.remove(userId, movieId);
    }

    public WatchlistDtos.PageResponse findMine(String userId, int page, int size) {
        requireIdentity(userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return repository.findMine(userId, page, size);
    }

    private static void requireIdentity(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("authenticated user is required");
    }

    private static void requireMovieId(String movieId) {
        if (movieId == null || movieId.isBlank()) throw new IllegalArgumentException("movieId is required");
    }

    public static final class MovieNotFoundException extends RuntimeException {
    }
}
