package com.neo4flix.rating;

import com.neo4flix.rating.api.RatingPageResponse;
import com.neo4flix.rating.api.RatingResponse;
import com.neo4flix.rating.api.RatingSummaryResponse;
import com.neo4flix.rating.api.RatingWriteRequest;
import com.neo4flix.rating.persistence.RatingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RatingApplicationService {

    private final RatingRepository repository;

    public RatingApplicationService(RatingRepository repository) {
        this.repository = repository;
    }

    public RatingResponse create(String userId, RatingWriteRequest request) {
        return repository.create(userId, request);
    }

    public Optional<RatingResponse> findOwn(String userId, String movieId) {
        return repository.findOwn(userId, movieId);
    }

    public Optional<RatingResponse> updateOwn(String userId, String movieId, int score) {
        return repository.updateOwn(userId, movieId, score);
    }

    public void deleteOwn(String userId, String movieId) {
        repository.deleteOwn(userId, movieId);
    }

    public RatingPageResponse findHistory(String userId, int page, int size) {
        return repository.findHistory(userId, page, size);
    }

    public RatingSummaryResponse findSummary(String movieId) {
        return repository.findSummary(movieId);
    }
}
