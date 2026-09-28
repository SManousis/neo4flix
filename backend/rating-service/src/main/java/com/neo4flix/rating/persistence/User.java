package com.neo4flix.rating.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.Set;

/**
 * Minimal source endpoint for the rating-owned RATED relationship.
 * The User service remains the only module declaring the canonical User node label.
 */
public record User(
        @Id @Property("id") String id,
        @Relationship(type = "RATED", direction = Relationship.Direction.OUTGOING) Set<RatedRelationship> ratings
) {
}
