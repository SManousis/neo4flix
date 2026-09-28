package com.neo4flix.recommendation.core;

import com.neo4flix.recommendation.persistence.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public final class RecommendationApplicationService {

    private final RecommendationRepository repository;
    private final RecommendationConfiguration configuration;
    private final RecommendationScoringService scoring;

    public RecommendationApplicationService(RecommendationRepository repository,
                                            RecommendationConfiguration configuration,
                                            RecommendationScoringService scoring) {
        this.repository = repository;
        this.configuration = configuration;
        this.scoring = scoring;
    }

    public List<RecommendationDtos.Result> recommend(RecommendationDtos.Query query) {
        RecommendationDtos.Query bounded = bounded(query);
        RecommendationRepository.Snapshot snapshot = repository.snapshot(bounded);
        RecommendationDtos.Strategy strategy = scoring.selectStrategy(snapshot.ratingCount(), snapshot.qualifyingPeer());
        RecommendationWeights weights = configuration.weights();
        return snapshot.rows().stream()
                .map(row -> new RecommendationDtos.Result(
                        row.movieId(),
                        row.title(),
                        row.overview(),
                        row.releaseYear(),
                        row.posterUrl(),
                        scoring.score(strategy, row, weights),
                        strategy,
                        scoring.reason(strategy, row),
                        row.genres(),
                        row.genreIds(),
                        row.releaseDate(),
                        row.averageRating(),
                        row.ratingCount(),
                        row.collaborativeScore(),
                        row.contentScore(),
                        row.popularityScore()))
                .sorted(Comparator.comparingDouble(RecommendationDtos.Result::score).reversed()
                        .thenComparing(RecommendationDtos.Result::movieId))
                .limit(bounded.limit())
                .toList();
    }

    private RecommendationDtos.Query bounded(RecommendationDtos.Query query) {
        if (query == null || query.userId() == null || query.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (query.limit() <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (query.fromYear() != null && query.toYear() != null && query.fromYear() > query.toYear()) {
            throw new IllegalArgumentException("fromYear must not exceed toYear");
        }
        if (query.minimumAverageRating() != null
                && (query.minimumAverageRating() < 0 || query.minimumAverageRating() > 5)) {
            throw new IllegalArgumentException("minimumAverageRating must be between 0 and 5");
        }
        return new RecommendationDtos.Query(
                query.userId().trim(),
                Math.min(query.limit(), configuration.maxPageSize()),
                Math.max(1, Math.min(query.minimumOverlap(), configuration.minimumOverlap())),
                Math.max(1, Math.min(query.peerLimit(), configuration.peerLimit())),
                Math.max(1, Math.min(query.candidateLimit(), configuration.candidateLimit())),
                query.genre() == null || query.genre().isBlank() ? null : query.genre().trim(),
                query.fromYear(),
                query.toYear(),
                query.minimumAverageRating());
    }
}
