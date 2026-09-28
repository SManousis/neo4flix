package com.neo4flix.recommendation.persistence;

import com.neo4flix.recommendation.core.RecommendationDtos;

import java.util.List;

public interface RecommendationRepository {

    Snapshot snapshot(RecommendationDtos.Query query);

    record Snapshot(int ratingCount, boolean qualifyingPeer, List<RecommendationDtos.SignalRow> rows) {
        public Snapshot {
            rows = List.copyOf(rows);
        }
    }
}
