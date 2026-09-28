package com.neo4flix.recommendation.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPersistenceMappingTest {

    @Test
    void recommendationShareDeclaresItsCanonicalLabelAndStringId() throws NoSuchFieldException {
        assertThat(RecommendationShareNode.class.getAnnotation(Node.class).value())
                .containsExactly("RecommendationShare");

        Field id = RecommendationShareNode.class.getDeclaredField("id");
        assertThat(id.getType()).isEqualTo(String.class);
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
    }

    @Test
    void recommendationShareDeclaresOwnedCreatorAndMovieRelationshipDirections() throws NoSuchFieldException {
        assertRelationship(RecommendationShareNode.class.getDeclaredField("creators"), "CREATED_SHARE",
                Relationship.Direction.INCOMING, User.class);
        assertRelationship(RecommendationShareNode.class.getDeclaredField("sharedMovies"), "SHARES",
                Relationship.Direction.OUTGOING, Movie.class);
    }

    private void assertRelationship(Field field, String type, Relationship.Direction direction, Class<?> targetType) {
        Relationship relationship = field.getAnnotation(Relationship.class);
        assertThat(relationship.type()).isEqualTo(type);
        assertThat(relationship.direction()).isEqualTo(direction);
        assertThat(field.getGenericType().getTypeName()).contains(targetType.getName());
    }
}
