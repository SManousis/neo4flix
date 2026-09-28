package com.neo4flix.user.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistedRelationshipTest {

    @Test
    void watchlistKeyIsDeterministicAndPersisted() throws NoSuchFieldException {
        assertThat(WatchlistedRelationship.keyFor("u1", "m1")).isEqualTo("u1:m1");
        assertThat(WatchlistedRelationship.class.isAnnotationPresent(RelationshipProperties.class)).isTrue();
        assertThat(WatchlistedRelationship.class.getDeclaredField("key").isAnnotationPresent(Property.class)).isTrue();
        assertThat(WatchlistedRelationship.class.getDeclaredField("movie").isAnnotationPresent(TargetNode.class)).isTrue();
        assertThat(WatchlistedRelationship.class.getDeclaredField("movie").getType()).isEqualTo(Movie.class);
        assertThatThrownBy(() -> new WatchlistedRelationship(null, Instant.parse("2026-09-09T00:00:00Z"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
