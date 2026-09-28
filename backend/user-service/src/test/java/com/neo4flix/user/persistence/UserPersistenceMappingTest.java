package com.neo4flix.user.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UserPersistenceMappingTest {

    @Test
    void userOwnedNodesDeclareTheirCanonicalLabelsAndStringIds() throws NoSuchFieldException {
        assertThat(UserNode.class.getAnnotation(Node.class).value()).containsExactly("User");
        assertThat(AuthSessionNode.class.getAnnotation(Node.class).value()).containsExactly("AuthSession");
        assertThat(AuthChallengeNode.class.getAnnotation(Node.class).value()).containsExactly("AuthChallenge");

        assertStringId(UserNode.class);
        assertStringId(AuthSessionNode.class);
        assertStringId(AuthChallengeNode.class);
    }

    @Test
    void userNodeDeclaresOutgoingRatingAndWatchlistRelationships() throws NoSuchFieldException {
        assertRelationship(UserNode.class.getDeclaredField("watchlisted"), "WATCHLISTED", WatchlistedRelationship.class);
    }

    @Test
    void canonicalNodeLabelsAreDeclaredOnlyByTheirOwningModules() throws Exception {
        Path backend = Path.of(System.getProperty("user.dir")).resolve("../..").normalize().resolve("backend");

        Map<String, String> owners = Map.of(
                "User", "user-service",
                "Movie", "movie-service",
                "Genre", "movie-service",
                "RecommendationShare", "recommendation-service"
        );

        for (Map.Entry<String, String> labelOwner : owners.entrySet()) {
            assertThat(modulesDeclaring(backend, labelOwner.getKey())).containsExactly(labelOwner.getValue());
        }
    }

    private void assertRelationship(Field field, String type, Class<?> relationshipPropertiesType) {
        Relationship relationship = field.getAnnotation(Relationship.class);
        assertThat(relationship.type()).isEqualTo(type);
        assertThat(relationship.direction()).isEqualTo(Relationship.Direction.OUTGOING);
        assertThat(field.getGenericType().getTypeName()).contains(relationshipPropertiesType.getName());
    }

    private Set<String> modulesDeclaring(Path backend, String label) throws Exception {
        Pattern nodeLabel = Pattern.compile("@Node\\(\\\"" + Pattern.quote(label) + "\\\"\\)");

        try (var sourceFiles = Files.walk(backend)) {
            return sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src"))
                    .filter(path -> {
                        try {
                            return nodeLabel.matcher(Files.readString(path)).find();
                        } catch (java.io.IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> backend.relativize(path).getName(0).toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private void assertStringId(Class<?> type) throws NoSuchFieldException {
        Field id = type.getDeclaredField("id");
        assertThat(id.getType()).isEqualTo(String.class);
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
    }
}
