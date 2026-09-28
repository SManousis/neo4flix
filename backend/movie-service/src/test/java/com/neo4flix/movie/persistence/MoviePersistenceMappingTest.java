package com.neo4flix.movie.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MoviePersistenceMappingTest {

    @Test
    void movieOwnedNodesDeclareCanonicalLabelsStringIdsAndGenreDirection() throws NoSuchFieldException {
        assertThat(MovieNode.class.getAnnotation(Node.class).value()).containsExactly("Movie");
        assertThat(GenreNode.class.getAnnotation(Node.class).value()).containsExactly("Genre");
        assertStringId(MovieNode.class);
        assertStringId(GenreNode.class);

        Relationship genres = MovieNode.class.getDeclaredField("genres").getAnnotation(Relationship.class);
        assertThat(genres.type()).isEqualTo("IN_GENRE");
        assertThat(genres.direction()).isEqualTo(Relationship.Direction.OUTGOING);
    }

    private void assertStringId(Class<?> type) throws NoSuchFieldException {
        Field id = type.getDeclaredField("id");
        assertThat(id.getType()).isEqualTo(String.class);
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
    }
}
