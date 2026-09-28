package com.neo4flix.movie.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Node("Movie")
public record MovieNode(
        @Id @Property("id") String id,
        @Property("title") String title,
        @Property("normalizedTitle") String normalizedTitle,
        @Property("overview") String overview,
        @Property("releaseYear") int releaseYear,
        @Property("releaseDate") LocalDate releaseDate,
        @Property("runtimeMinutes") Integer runtimeMinutes,
        @Property("posterUrl") String posterUrl,
        @Property("externalSource") String externalSource,
        @Property("externalId") String externalId,
        @Property("createdAt") Instant createdAt,
        @Property("updatedAt") Instant updatedAt,
        @Relationship(type = "IN_GENRE", direction = Relationship.Direction.OUTGOING) Set<GenreNode> genres
) {
}
