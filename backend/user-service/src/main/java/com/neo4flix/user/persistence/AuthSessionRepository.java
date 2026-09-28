package com.neo4flix.user.persistence;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

public interface AuthSessionRepository {

    void create(String userId, AuthSessionNode session);

    Optional<UserNode> rotate(String currentHash, Instant now, AuthSessionNode replacement);

    boolean revoke(String refreshTokenHash, Instant revokedAt);

    @Repository("authSessionRepository")
    class Neo4j implements AuthSessionRepository {

        private static final String CREATE = """
                MATCH (u:User {id: $userId})
                CREATE (u)-[:HAS_SESSION]->(:AuthSession {
                  id: $id,
                  refreshTokenHash: $refreshTokenHash,
                  createdAt: $createdAt,
                  expiresAt: $expiresAt
                })
                """;
        private static final String ROTATE = """
                MATCH (u:User)-[:HAS_SESSION]->(current:AuthSession {refreshTokenHash: $currentHash})
                SET current.rotatedToSessionId = CASE
                  WHEN current.rotatedToSessionId IS NULL
                    AND current.revokedAt IS NULL
                    AND current.expiresAt > $now
                    AND u.enabled = true
                  THEN $replacementId
                  ELSE current.rotatedToSessionId
                END
                WITH u, current
                WHERE current.rotatedToSessionId = $replacementId
                SET current.revokedAt = $now
                CREATE (u)-[:HAS_SESSION]->(:AuthSession {
                  id: $replacementId,
                  refreshTokenHash: $replacementHash,
                  createdAt: $now,
                  expiresAt: $replacementExpiresAt,
                  rotatedFromSessionId: current.id
                })
                RETURN u{.*} AS user
                """;
        private static final String REVOKE = """
                MATCH (:User)-[:HAS_SESSION]->(session:AuthSession {refreshTokenHash: $refreshTokenHash})
                WHERE session.revokedAt IS NULL AND session.expiresAt > $revokedAt
                SET session.revokedAt = $revokedAt
                RETURN count(session) = 1 AS revoked
                """;

        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public void create(String userId, AuthSessionNode session) {
            client.query(CREATE)
                    .bindAll(Map.of(
                            "userId", userId,
                            "id", session.id(),
                            "refreshTokenHash", session.refreshTokenHash(),
                            "createdAt", utc(session.createdAt()),
                            "expiresAt", utc(session.expiresAt())))
                    .run();
        }

        @Override
        public Optional<UserNode> rotate(String currentHash, Instant now, AuthSessionNode replacement) {
            return client.query(ROTATE)
                    .bindAll(Map.of(
                            "currentHash", currentHash,
                            "now", utc(now),
                            "replacementId", replacement.id(),
                            "replacementHash", replacement.refreshTokenHash(),
                            "replacementExpiresAt", utc(replacement.expiresAt())))
                    .fetchAs(UserNode.class)
                    .mappedBy((typeSystem, record) -> UserRepository.Neo4j.mapUser(record))
                    .one();
        }

        @Override
        public boolean revoke(String refreshTokenHash, Instant revokedAt) {
            return client.query(REVOKE)
                    .bindAll(Map.of(
                            "refreshTokenHash", refreshTokenHash,
                            "revokedAt", utc(revokedAt)))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("revoked").asBoolean())
                    .one()
                    .orElse(false);
        }

        private static Object utc(Instant value) {
            return value.atZone(ZoneOffset.UTC);
        }
    }
}
