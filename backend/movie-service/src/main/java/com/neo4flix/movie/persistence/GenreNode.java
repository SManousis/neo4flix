package com.neo4flix.movie.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.Instant;

@Node("Genre")
public record GenreNode(
        @Id @Property("id") String id,
        @Property("name") String name,
        @Property("normalizedName") String normalizedName,
        @Property("createdAt") Instant createdAt,
        @Property("updatedAt") Instant updatedAt
) {
}
