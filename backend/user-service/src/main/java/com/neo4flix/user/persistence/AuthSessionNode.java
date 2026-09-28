package com.neo4flix.user.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.Instant;

@Node("AuthSession")
public record AuthSessionNode(
        @Id @Property("id") String id,
        @Property("refreshTokenHash") String refreshTokenHash,
        @Property("createdAt") Instant createdAt,
        @Property("expiresAt") Instant expiresAt,
        @Property("revokedAt") Instant revokedAt,
        @Property("rotatedFromSessionId") String rotatedFromSessionId
) {
}
