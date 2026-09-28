package com.neo4flix.migrator;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchemaValidator {

    private SchemaValidator() {
    }

    static void verifyConstraints(Collection<SchemaObject> expected, Collection<SchemaObject> actual) {
        verifyDefinitions("constraint", expected, actual);
    }

    static void verifyIndexes(Collection<SchemaObject> expected, Collection<IndexMetadata> actual) {
        verifyDefinitions("index", expected, actual.stream().map(IndexMetadata::definition).toList());

        Map<String, IndexMetadata> actualByName = indexMetadataByName(actual);
        for (SchemaObject expectedIndex : expected) {
            IndexMetadata actualIndex = actualByName.get(expectedIndex.name());
            if (actualIndex != null && !"ONLINE".equals(actualIndex.state())) {
                throw new IllegalStateException(
                        "Schema index is not ONLINE: " + expectedIndex.name() + " (state=" + actualIndex.state() + ")");
            }
        }
    }

    private static void verifyDefinitions(
            String kind, Collection<SchemaObject> expected, Collection<SchemaObject> actual) {
        Map<String, SchemaObject> actualByName = definitionsByName(kind, actual);
        for (SchemaObject expectedObject : expected) {
            SchemaObject actualObject = actualByName.get(expectedObject.name());
            if (actualObject == null) {
                throw new IllegalStateException("Missing schema " + kind + ": " + expectedObject.name());
            }
            if (!expectedObject.equals(actualObject)) {
                throw new IllegalStateException("Schema " + kind + " metadata mismatch for "
                        + expectedObject.name() + ": expected=" + expectedObject + " actual=" + actualObject);
            }
        }
    }

    private static Map<String, SchemaObject> definitionsByName(
            String kind, Collection<SchemaObject> definitions) {
        Map<String, SchemaObject> byName = new LinkedHashMap<>();
        for (SchemaObject definition : definitions) {
            if (byName.put(definition.name(), definition) != null) {
                throw new IllegalStateException("Duplicate schema " + kind + " metadata: " + definition.name());
            }
        }
        return byName;
    }

    private static Map<String, IndexMetadata> indexMetadataByName(Collection<IndexMetadata> indexes) {
        Map<String, IndexMetadata> byName = new LinkedHashMap<>();
        for (IndexMetadata index : indexes) {
            if (byName.put(index.definition().name(), index) != null) {
                throw new IllegalStateException("Duplicate schema index metadata: " + index.definition().name());
            }
        }
        return byName;
    }

    record SchemaObject(
            String name,
            String type,
            String entityType,
            List<String> labelsOrTypes,
            List<String> properties) {

        SchemaObject {
            labelsOrTypes = List.copyOf(labelsOrTypes);
            properties = List.copyOf(properties);
        }
    }

    record IndexMetadata(SchemaObject definition, String state) {
    }
}
