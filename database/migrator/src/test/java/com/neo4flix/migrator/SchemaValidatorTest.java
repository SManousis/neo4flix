package com.neo4flix.migrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidatorTest {

    private static final SchemaValidator.SchemaObject USER_ID = new SchemaValidator.SchemaObject(
            "user_id_unique",
            "NODE_PROPERTY_UNIQUENESS",
            "NODE",
            List.of("User"),
            List.of("id"));
    private static final SchemaValidator.SchemaObject MOVIE_TITLE = new SchemaValidator.SchemaObject(
            "movie_title_index",
            "RANGE",
            "NODE",
            List.of("Movie"),
            List.of("normalizedTitle"));

    @Test
    void acceptsExactConstraintMetadata() {
        assertThatCode(() -> SchemaValidator.verifyConstraints(List.of(USER_ID), List.of(USER_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsConstraintWithWrongType() {
        var actual = new SchemaValidator.SchemaObject(
                USER_ID.name(), "NODE_KEY", USER_ID.entityType(), USER_ID.labelsOrTypes(), USER_ID.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyConstraints(List.of(USER_ID), List.of(actual)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_id_unique");
    }

    @Test
    void rejectsConstraintWithWrongEntityType() {
        var actual = new SchemaValidator.SchemaObject(
                USER_ID.name(), USER_ID.type(), "RELATIONSHIP", USER_ID.labelsOrTypes(), USER_ID.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyConstraints(List.of(USER_ID), List.of(actual)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_id_unique");
    }

    @Test
    void rejectsConstraintWithWrongLabel() {
        var actual = new SchemaValidator.SchemaObject(
                USER_ID.name(), USER_ID.type(), USER_ID.entityType(), List.of("Account"), USER_ID.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyConstraints(List.of(USER_ID), List.of(actual)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_id_unique");
    }

    @Test
    void rejectsConstraintWithWrongProperty() {
        var actual = new SchemaValidator.SchemaObject(
                USER_ID.name(), USER_ID.type(), USER_ID.entityType(), USER_ID.labelsOrTypes(), List.of("legacyId"));

        assertThatThrownBy(() -> SchemaValidator.verifyConstraints(List.of(USER_ID), List.of(actual)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_id_unique");
    }

    @Test
    void acceptsExactOnlineIndexMetadata() {
        var actual = new SchemaValidator.IndexMetadata(MOVIE_TITLE, "ONLINE");

        assertThatCode(() -> SchemaValidator.verifyIndexes(List.of(MOVIE_TITLE), List.of(actual)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsIndexWithWrongType() {
        var definition = new SchemaValidator.SchemaObject(
                MOVIE_TITLE.name(), "TEXT", MOVIE_TITLE.entityType(), MOVIE_TITLE.labelsOrTypes(), MOVIE_TITLE.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyIndexes(
                List.of(MOVIE_TITLE), List.of(new SchemaValidator.IndexMetadata(definition, "ONLINE"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movie_title_index");
    }

    @Test
    void rejectsIndexWithWrongEntityType() {
        var definition = new SchemaValidator.SchemaObject(
                MOVIE_TITLE.name(), MOVIE_TITLE.type(), "RELATIONSHIP", MOVIE_TITLE.labelsOrTypes(), MOVIE_TITLE.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyIndexes(
                List.of(MOVIE_TITLE), List.of(new SchemaValidator.IndexMetadata(definition, "ONLINE"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movie_title_index");
    }

    @Test
    void rejectsIndexWithWrongLabel() {
        var definition = new SchemaValidator.SchemaObject(
                MOVIE_TITLE.name(), MOVIE_TITLE.type(), MOVIE_TITLE.entityType(), List.of("Film"), MOVIE_TITLE.properties());

        assertThatThrownBy(() -> SchemaValidator.verifyIndexes(
                List.of(MOVIE_TITLE), List.of(new SchemaValidator.IndexMetadata(definition, "ONLINE"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movie_title_index");
    }

    @Test
    void rejectsIndexWithWrongProperty() {
        var definition = new SchemaValidator.SchemaObject(
                MOVIE_TITLE.name(), MOVIE_TITLE.type(), MOVIE_TITLE.entityType(), MOVIE_TITLE.labelsOrTypes(), List.of("title"));

        assertThatThrownBy(() -> SchemaValidator.verifyIndexes(
                List.of(MOVIE_TITLE), List.of(new SchemaValidator.IndexMetadata(definition, "ONLINE"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movie_title_index");
    }

    @Test
    void rejectsIndexThatIsNotOnline() {
        var actual = new SchemaValidator.IndexMetadata(MOVIE_TITLE, "POPULATING");

        assertThatThrownBy(() -> SchemaValidator.verifyIndexes(List.of(MOVIE_TITLE), List.of(actual)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schema index is not ONLINE: movie_title_index (state=POPULATING)");
    }
}
