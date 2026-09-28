package com.neo4flix.recommendation;

import com.neo4flix.platform.common.test.Neo4jGdsContainer;
import com.neo4flix.recommendation.core.RecommendationApplicationService;
import com.neo4flix.recommendation.core.RecommendationConfiguration;
import com.neo4flix.recommendation.core.RecommendationDtos;
import com.neo4flix.recommendation.core.RecommendationScoringService;
import com.neo4flix.recommendation.persistence.RecommendationNeo4jRepository;
import com.neo4flix.recommendation.persistence.RecommendationRepository;
import com.neo4flix.migrator.seed.AuditSeedLoader;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationGoldenFixtureIT {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
    private static final String FRESH = "44444444-4444-4444-4444-444444444444";
    private static final String SPARSE = "55555555-5555-5555-5555-555555555555";
    private static final String NEGATIVE = "66666666-6666-6666-6666-666666666666";
    private static final String BLADE_RUNNER = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
    private static final String MAD_MAX = "gggggggg-gggg-gggg-gggg-gggggggggggg";

    @Test
    void provesGoldenRankingStrategiesFiltersExclusionAndGdsExecution() {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            new AuditSeedLoader().load(neo4j.driver(), "neo4j");
            RecommendationApplicationService service = service(neo4j);

            List<RecommendationDtos.Result> alice = service.recommend(query(ALICE, null));
            assertThat(alice).isNotEmpty();
            assertThat(alice).extracting(RecommendationDtos.Result::movieId)
                    .contains(BLADE_RUNNER)
                    .doesNotContain("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                            "cccccccc-cccc-cccc-cccc-cccccccccccc",
                            "dddddddd-dddd-dddd-dddd-dddddddddddd");
            assertThat(alice.getFirst().strategy()).isEqualTo(RecommendationDtos.Strategy.HYBRID);
            assertThat(alice).allSatisfy(result -> assertThat(result.score()).isBetween(0.0, 1.0));

            List<RecommendationDtos.Result> fresh = service.recommend(query(FRESH, null));
            assertThat(fresh).isNotEmpty().allSatisfy(result -> {
                assertThat(result.strategy()).isEqualTo(RecommendationDtos.Strategy.POPULARITY);
                assertThat(result.reason()).isEqualTo("Highly rated by Neo4flix users.");
            });

            List<RecommendationDtos.Result> sparse = service.recommend(query(SPARSE, null));
            assertThat(sparse).isNotEmpty().allSatisfy(result ->
                    assertThat(result.strategy()).isEqualTo(RecommendationDtos.Strategy.CONTENT_PLUS_POPULARITY));

            List<RecommendationDtos.Result> filtered = service.recommend(query(ALICE, "Science Fiction"));
            assertThat(filtered).allSatisfy(result -> assertThat(result.title()).isIn("Blade Runner", "The Matrix", "Inception", "Arrival"));

            List<RecommendationDtos.Result> repeated = service.recommend(query(ALICE, null));
            assertThat(repeated).isEqualTo(alice);

            assertThat(neo4j.runCypher(
                    "RETURN gds.similarity.cosine([1.0, 0.0], [1.0, 0.0]) AS similarity", Map.of())
                    .getFirst().get("similarity").asDouble()).isEqualTo(1.0);
        }
    }

    @Test
    void negativeOnlyHistoryDoesNotCreatePositiveGenreAffinity() {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            new AuditSeedLoader().load(neo4j.driver(), "neo4j");
            List<RecommendationDtos.Result> results = service(neo4j).recommend(query(NEGATIVE, "Action"));

            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(result ->
                    assertThat(result.reason()).isEqualTo("Highly rated by Neo4flix users."));
            assertThat(results).extracting(RecommendationDtos.Result::movieId).doesNotContain(MAD_MAX);
        }
    }

    private static RecommendationApplicationService service(Neo4jGdsContainer neo4j) {
        RecommendationNeo4jRepository repository = new RecommendationNeo4jRepository(
                Neo4jClient.create(neo4j.driver()));
        return new RecommendationApplicationService(
                repository, RecommendationConfiguration.defaults(), new RecommendationScoringService());
    }

    private static RecommendationDtos.Query query(String userId, String genre) {
        return new RecommendationDtos.Query(userId, 10, 2, 10, 50, genre, null, null, null);
    }
}
