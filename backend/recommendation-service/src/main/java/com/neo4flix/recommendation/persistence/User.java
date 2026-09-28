package com.neo4flix.recommendation.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Minimal endpoint for recommendation-share ownership mapping.
 * The User service remains the only module declaring the canonical User node label.
 */
public record User(@Id @Property("id") String id) {
}
