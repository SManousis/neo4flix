package com.neo4flix.recommendation.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.Set;

@Node("RecommendationShare")
public record RecommendationShareNode(
        @Id @Property("id") String id,
        @Property("publicTokenHash") String publicTokenHash,
        @Property("createdAt") Instant createdAt,
        @Property("expiresAt") Instant expiresAt,
        @Property("revokedAt") Instant revokedAt,
        @Relationship(type = "CREATED_SHARE", direction = Relationship.Direction.INCOMING) Set<User> creators,
        @Relationship(type = "SHARES", direction = Relationship.Direction.OUTGOING) Set<Movie> sharedMovies
) {
}
