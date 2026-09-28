package com.neo4flix.user.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.Instant;

@Node("AuthChallenge")
public record AuthChallengeNode(
        @Id @Property("id") String id,
        @Property("tokenHash") String tokenHash,
        @Property("purpose") String purpose,
        @Property("createdAt") Instant createdAt,
        @Property("expiresAt") Instant expiresAt,
        @Property("usedAt") Instant usedAt
) {
}
