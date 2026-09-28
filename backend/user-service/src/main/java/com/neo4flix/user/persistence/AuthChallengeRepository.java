package com.neo4flix.user.persistence;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

public interface AuthChallengeRepository {

    void create(String userId, AuthChallengeNode challenge);

    Optional<AuthChallengeNode> findUsableByHash(String tokenHash, String purpose, Instant now);

    boolean consume(String tokenHash, String purpose, Instant usedAt);

    @Repository("authChallengeRepository")
    class Neo4j implements AuthChallengeRepository {

        private static final String CREATE = """
                MATCH (u:User {id: $userId})
                CREATE (u)-[:HAS_AUTH_CHALLENGE]->(:AuthChallenge {
                  id: $id,
                  tokenHash: $tokenHash,
                  purpose: $purpose,
                  createdAt: $createdAt,
                  expiresAt: $expiresAt
                })
                """;
        private static final String FIND_USABLE = """
                MATCH (:User)-[:HAS_AUTH_CHALLENGE]->(challenge:AuthChallenge {
                  tokenHash: $tokenHash,
                  purpose: $purpose
                })
                WHERE challenge.usedAt IS NULL AND challenge.expiresAt > $now
                RETURN challenge.id AS id,
                       challenge.tokenHash AS tokenHash,
                       challenge.purpose AS purpose,
                       challenge.createdAt AS createdAt,
                       challenge.expiresAt AS expiresAt,
                       challenge.usedAt AS usedAt
                """;
        private static final String CONSUME = """
                MATCH (:User)-[:HAS_AUTH_CHALLENGE]->(challenge:AuthChallenge {
                  tokenHash: $tokenHash,
                  purpose: $purpose
                })
                WHERE challenge.usedAt IS NULL AND challenge.expiresAt > $usedAt
                SET challenge.usedAt = $usedAt
                RETURN count(challenge) = 1 AS consumed
                """;

        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public void create(String userId, AuthChallengeNode challenge) {
            client.query(CREATE)
                    .bindAll(Map.of(
                            "userId", userId,
                            "id", challenge.id(),
                            "tokenHash", challenge.tokenHash(),
                            "purpose", challenge.purpose(),
                            "createdAt", utc(challenge.createdAt()),
                            "expiresAt", utc(challenge.expiresAt())))
                    .run();
        }

        @Override
        public Optional<AuthChallengeNode> findUsableByHash(String tokenHash, String purpose, Instant now) {
            return client.query(FIND_USABLE)
                    .bindAll(Map.of("tokenHash", tokenHash, "purpose", purpose, "now", utc(now)))
                    .fetchAs(AuthChallengeNode.class)
                    .mappedBy((typeSystem, record) -> new AuthChallengeNode(
                            record.get("id").asString(),
                            record.get("tokenHash").asString(),
                            record.get("purpose").asString(),
                            record.get("createdAt").asZonedDateTime().toInstant(),
                            record.get("expiresAt").asZonedDateTime().toInstant(),
                            record.get("usedAt").isNull()
                                    ? null : record.get("usedAt").asZonedDateTime().toInstant()))
                    .one();
        }

        @Override
        public boolean consume(String tokenHash, String purpose, Instant usedAt) {
            return client.query(CONSUME)
                    .bindAll(Map.of("tokenHash", tokenHash, "purpose", purpose, "usedAt", utc(usedAt)))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("consumed").asBoolean())
                    .one()
                    .orElse(false);
        }

        private static Object utc(Instant value) {
            return value.atZone(ZoneOffset.UTC);
        }
    }
}
