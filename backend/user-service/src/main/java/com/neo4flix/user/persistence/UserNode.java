package com.neo4flix.user.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.Set;

@Node("User")
public record UserNode(
        @Id @Property("id") String id,
        @Property("email") String email,
        @Property("normalizedEmail") String normalizedEmail,
        @Property("displayName") String displayName,
        @Property("passwordHash") String passwordHash,
        @Property("role") String role,
        @Property("enabled") boolean enabled,
        @Property("twoFactorEnabled") boolean twoFactorEnabled,
        @Property("totpSecretEncrypted") String totpSecretEncrypted,
        @Property("pendingTotpSecretEncrypted") String pendingTotpSecretEncrypted,
        @Property("pendingTotpExpiresAt") Instant pendingTotpExpiresAt,
        @Property("createdAt") Instant createdAt,
        @Property("updatedAt") Instant updatedAt,
        @Relationship(type = "HAS_SESSION", direction = Relationship.Direction.OUTGOING) Set<AuthSessionNode> sessions,
        @Relationship(type = "HAS_AUTH_CHALLENGE", direction = Relationship.Direction.OUTGOING) Set<AuthChallengeNode> challenges,
        @Relationship(type = "WATCHLISTED", direction = Relationship.Direction.OUTGOING) Set<WatchlistedRelationship> watchlisted
) {
}
