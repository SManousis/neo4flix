package com.neo4flix.rating.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class RatingPersistenceMappingTest {

    @Test
    void ratedRelationshipDeclaresPersistedKeyProperty() throws NoSuchFieldException {
        Field key = RatedRelationship.class.getDeclaredField("key");

        assertThat(RatedRelationship.class.isAnnotationPresent(RelationshipProperties.class)).isTrue();
        assertThat(key.getType()).isEqualTo(String.class);
        assertThat(key.isAnnotationPresent(Property.class)).isTrue();
        Field movie = RatedRelationship.class.getDeclaredField("movie");
        assertThat(movie.isAnnotationPresent(TargetNode.class)).isTrue();
        assertThat(movie.getType()).isEqualTo(Movie.class);
    }

    @Test
    void ratingSourceReferenceDeclaresAnOutgoingRatedRelationship() throws NoSuchFieldException {
        Field rated = User.class.getDeclaredField("ratings");
        Relationship relationship = rated.getAnnotation(Relationship.class);

        assertThat(relationship.type()).isEqualTo("RATED");
        assertThat(relationship.direction()).isEqualTo(Relationship.Direction.OUTGOING);
        assertThat(rated.getGenericType().getTypeName()).contains(RatedRelationship.class.getName());
    }
}
