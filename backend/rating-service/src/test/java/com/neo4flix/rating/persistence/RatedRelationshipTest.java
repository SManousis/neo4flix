package com.neo4flix.rating.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatedRelationshipTest {

    @Test
    void ratingKeyIsDeterministicAndNonNull() {
        assertThat(RatedRelationship.keyFor("u1", "m1")).isEqualTo("u1:m1");
        assertThatThrownBy(() -> new RatedRelationship(null, 4, Instant.parse("2026-09-09T00:00:00Z"), Instant.parse("2026-09-09T00:00:00Z"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
