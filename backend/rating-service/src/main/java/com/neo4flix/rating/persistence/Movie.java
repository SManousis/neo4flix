package com.neo4flix.rating.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Minimal relationship endpoint for the rating-owned RATED mapping.
 * The Movie service remains the only module declaring the canonical Movie node label.
 */
public record Movie(@Id @Property("id") String id) {
}
