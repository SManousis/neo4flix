package com.neo4flix.user.persistence;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    Optional<UserNode> findByNormalizedEmail(String normalizedEmail);

    Optional<UserNode> findById(String id);

    boolean create(UserNode user);

    Optional<UserNode> updateDisplayName(String id, String displayName, Instant updatedAt);

    boolean updatePassword(String id, String passwordHash, Instant updatedAt);

    @Repository("userRepository")
    class Neo4j implements UserRepository {

        private static final String USER_PROJECTION = "u{.*} AS user";
        private static final String FIND_BY_EMAIL = """
                MATCH (u:User {normalizedEmail: $normalizedEmail})
                RETURN %s
                """.formatted(USER_PROJECTION);
        private static final String FIND_BY_ID = """
                MATCH (u:User {id: $id})
                RETURN %s
                """.formatted(USER_PROJECTION);
        private static final String CREATE = """
                MERGE (u:User {normalizedEmail: $normalizedEmail})
                ON CREATE SET
                  u.id = $id,
                  u.email = $email,
                  u.displayName = $displayName,
                  u.passwordHash = $passwordHash,
                  u.role = $role,
                  u.enabled = $enabled,
                  u.twoFactorEnabled = $twoFactorEnabled,
                  u.createdAt = $createdAt,
                  u.updatedAt = $updatedAt
                RETURN u.id = $id AS created
                """;
        private static final String UPDATE_DISPLAY_NAME = """
                MATCH (u:User {id: $id})
                SET u.displayName = $displayName, u.updatedAt = $updatedAt
                RETURN %s
                """.formatted(USER_PROJECTION);
        private static final String UPDATE_PASSWORD = """
                MATCH (u:User {id: $id})
                SET u.passwordHash = $passwordHash, u.updatedAt = $updatedAt
                RETURN count(u) = 1 AS updated
                """;

        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public Optional<UserNode> findByNormalizedEmail(String normalizedEmail) {
            return userQuery(FIND_BY_EMAIL, Map.of("normalizedEmail", normalizedEmail));
        }

        @Override
        public Optional<UserNode> findById(String id) {
            return userQuery(FIND_BY_ID, Map.of("id", id));
        }

        @Override
        public boolean create(UserNode user) {
            return client.query(CREATE)
                    .bindAll(Map.of(
                            "id", user.id(),
                            "email", user.email(),
                            "normalizedEmail", user.normalizedEmail(),
                            "displayName", user.displayName(),
                            "passwordHash", user.passwordHash(),
                            "role", user.role(),
                            "enabled", user.enabled(),
                            "twoFactorEnabled", user.twoFactorEnabled(),
                            "createdAt", utc(user.createdAt()),
                            "updatedAt", utc(user.updatedAt())))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("created").asBoolean())
                    .one()
                    .orElse(false);
        }

        @Override
        public Optional<UserNode> updateDisplayName(String id, String displayName, Instant updatedAt) {
            return userQuery(UPDATE_DISPLAY_NAME, Map.of(
                    "id", id,
                    "displayName", displayName,
                    "updatedAt", utc(updatedAt)));
        }

        @Override
        public boolean updatePassword(String id, String passwordHash, Instant updatedAt) {
            return client.query(UPDATE_PASSWORD)
                    .bindAll(Map.of(
                            "id", id,
                            "passwordHash", passwordHash,
                            "updatedAt", utc(updatedAt)))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("updated").asBoolean())
                    .one()
                    .orElse(false);
        }

        private Optional<UserNode> userQuery(String query, Map<String, Object> parameters) {
            return client.query(query)
                    .bindAll(parameters)
                    .fetchAs(UserNode.class)
                    .mappedBy((typeSystem, record) -> mapUser(record))
                    .one();
        }

        public static UserNode mapUser(Record record) {
            Value user = record.get("user");
            return new UserNode(
                    user.get("id").asString(),
                    user.get("email").asString(),
                    user.get("normalizedEmail").asString(),
                    user.get("displayName").asString(),
                    user.get("passwordHash").asString(),
                    user.get("role").asString(),
                    user.get("enabled").asBoolean(),
                    user.get("twoFactorEnabled").asBoolean(),
                    nullableString(user, "totpSecretEncrypted"),
                    nullableString(user, "pendingTotpSecretEncrypted"),
                    nullableInstant(user, "pendingTotpExpiresAt"),
                    user.get("createdAt").asZonedDateTime().toInstant(),
                    user.get("updatedAt").asZonedDateTime().toInstant(),
                    Set.of(), Set.of(), Set.of());
        }

        private static String nullableString(Value properties, String key) {
            return properties.get(key).isNull() ? null : properties.get(key).asString();
        }

        private static Instant nullableInstant(Value properties, String key) {
            return properties.get(key).isNull() ? null : properties.get(key).asZonedDateTime().toInstant();
        }

        private static Object utc(Instant value) {
            return value.atZone(ZoneOffset.UTC);
        }
    }
}
