package com.neo4flix.movie.catalog;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized, bounded catalog query values used by parameterized Cypher. */
public record MovieQuery(
        String title,
        String genre,
        Integer minYear,
        Integer maxYear,
        LocalDate fromDate,
        LocalDate toDate,
        Double minRating,
        String sortBy,
        String direction,
        int page,
        int size) {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "m.createdAt",
            "title", "m.normalizedTitle",
            "releaseDate", "m.releaseDate",
            "releaseYear", "m.releaseYear",
            "rating", "avgRating");

    public static MovieQuery from(
            String title,
            String genre,
            Integer minYear,
            Integer maxYear,
            LocalDate fromDate,
            String sortBy,
            String direction,
            int page,
            int size) {
        String normalizedTitle = title == null || title.isBlank() ? null : title.trim();
        String normalizedGenre = genre == null || genre.isBlank() ? null : genre.trim();
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        String normalizedSort;
        if (sortBy == null || sortBy.isBlank()) {
            normalizedSort = "createdAt";
        } else if (!SORT_FIELDS.containsKey(sortBy)) {
            throw new IllegalArgumentException("Unsupported sort field: " + sortBy);
        } else {
            normalizedSort = sortBy;
        }
        String normalizedDirection = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
        return new MovieQuery(normalizedTitle, normalizedGenre, minYear, maxYear, fromDate, null,
                null, normalizedSort, normalizedDirection, boundedPage, boundedSize);
    }

    public String sortCypher() {
        return SORT_FIELDS.getOrDefault(sortBy, SORT_FIELDS.get("createdAt"));
    }

    public Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("title", title);
        parameters.put("genre", genre);
        parameters.put("minYear", minYear);
        parameters.put("maxYear", maxYear);
        parameters.put("fromDate", fromDate);
        parameters.put("toDate", toDate);
        parameters.put("minRating", minRating);
        parameters.put("skip", (long) page * size);
        parameters.put("limit", size);
        return parameters;
    }
}
