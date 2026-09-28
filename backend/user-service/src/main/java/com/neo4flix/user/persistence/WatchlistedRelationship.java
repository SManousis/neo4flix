package com.neo4flix.user.persistence;

import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.Instant;
import java.util.Objects;

@RelationshipProperties
public final class WatchlistedRelationship {

    @RelationshipId
    private String relationshipId;

    @Property("key")
    private final String key;

    @Property("createdAt")
    private final Instant createdAt;

    @TargetNode
    private final Movie movie;

    public WatchlistedRelationship(String key, Instant createdAt, Movie movie) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.movie = Objects.requireNonNull(movie, "movie must not be null");
    }

    public String key() {
        return key;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Movie movie() {
        return movie;
    }

    public static String keyFor(String userId, String movieId) {
        return userId + ":" + movieId;
    }
}
