package com.neo4flix.rating.persistence;

import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.Instant;
import java.util.Objects;

@RelationshipProperties
public final class RatedRelationship {

    @RelationshipId
    private String relationshipId;

    @Property("key")
    private final String key;

    @Property("score")
    private final int score;

    @Property("createdAt")
    private final Instant createdAt;

    @Property("updatedAt")
    private final Instant updatedAt;

    @TargetNode
    private final Movie movie;

    public RatedRelationship(String key, int score, Instant createdAt, Instant updatedAt, Movie movie) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.score = score;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.movie = Objects.requireNonNull(movie, "movie must not be null");
    }

    public String key() {
        return key;
    }

    public int score() {
        return score;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Movie movie() {
        return movie;
    }

    public static String keyFor(String userId, String movieId) {
        return userId + ":" + movieId;
    }
}
