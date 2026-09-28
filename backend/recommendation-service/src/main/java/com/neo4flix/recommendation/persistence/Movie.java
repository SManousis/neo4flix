package com.neo4flix.recommendation.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Minimal endpoint for recommendation-share ownership mapping.
 * The Movie service remains the only module declaring the canonical Movie node label.
 */
public record Movie(@Id @Property("id") String id) {
}
