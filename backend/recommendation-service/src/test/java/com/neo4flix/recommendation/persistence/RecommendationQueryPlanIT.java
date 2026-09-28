package com.neo4flix.recommendation.persistence;

import com.neo4flix.migrator.seed.AuditSeedLoader;
import com.neo4flix.platform.common.test.Neo4jGdsContainer;
import com.neo4flix.recommendation.core.RecommendationDtos;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.neo4j.driver.summary.Plan;
import org.neo4j.driver.summary.ProfiledPlan;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationQueryPlanIT {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";

    @Test
    void explainsAndProfilesBoundedRecommendationQueriesWithoutExposingPrivateData() {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            new AuditSeedLoader().load(neo4j.driver(), "neo4j");
            RecommendationDtos.Query query = new RecommendationDtos.Query(
                    ALICE, 10, 2, 10, 50, null, null, null, null);
            Map<String, Object> parameters = RecommendationNeo4jRepository.parameters(query, 5.0);

            try (Session session = neo4j.driver().session()) {
                Plan peerPlan = session.run("EXPLAIN " + RecommendationNeo4jRepository.COLLABORATIVE_QUERY,
                                parameters)
                        .consume()
                        .plan();
                Plan candidatePlan = session.run("EXPLAIN " + RecommendationNeo4jRepository.CANDIDATE_QUERY,
                                parameters)
                        .consume()
                        .plan();
                assertThat(operatorTypes(peerPlan)).contains("ProduceResults");
                assertThat(operatorTypes(candidatePlan)).contains("ProduceResults");

                ProfiledPlan profile = session.run("PROFILE " + RecommendationNeo4jRepository.CANDIDATE_QUERY,
                                parameters)
                        .consume()
                        .profile();
                assertThat(profile).isNotNull();
                assertThat(profile.records()).isGreaterThanOrEqualTo(0L);
                assertThat(profile.dbHits()).isGreaterThanOrEqualTo(0L);
                assertThat(operatorTypes(profile)).contains("ProduceResults");
            }
        }
    }

    private static Set<String> operatorTypes(Plan plan) {
        return Stream.concat(Stream.of(operatorName(plan.operatorType())), plan.children().stream().flatMap(child -> operatorTypes(child).stream()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> operatorTypes(ProfiledPlan plan) {
        return Stream.concat(Stream.of(operatorName(plan.operatorType())), plan.children().stream().flatMap(child -> operatorTypes(child).stream()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String operatorName(String operatorType) {
        int separator = operatorType.indexOf('@');
        return separator < 0 ? operatorType : operatorType.substring(0, separator);
    }
}
