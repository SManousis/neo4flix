package com.neo4flix.movie.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.net.URI;
import java.util.List;

public final class CatalogModels {
    private CatalogModels() { }

    public record GenreSummary(String id, String name) { }

    public record MovieSummary(
            String id,
            String title,
            String overview,
            int releaseYear,
            LocalDate releaseDate,
            String posterUrl,
            List<GenreSummary> genres,
            double averageRating,
            long ratingCount) { }

    public record MovieDetail(
            String id,
            String title,
            String overview,
            int releaseYear,
            LocalDate releaseDate,
            Integer runtimeMinutes,
            String posterUrl,
            String externalSource,
            String externalId,
            Instant createdAt,
            Instant updatedAt,
            List<GenreSummary> genres,
            double averageRating,
            long ratingCount) { }

    public record MovieWrite(
            String title,
            String overview,
            int releaseYear,
            LocalDate releaseDate,
            Integer runtimeMinutes,
            String posterUrl,
            String externalSource,
            String externalId,
            List<String> genreIds) {
        public MovieWrite {
            posterUrl = validatePosterUrl(posterUrl);
        }
    }

    static String validatePosterUrl(String value) {
        if (value == null || value.isBlank()) return null;
        final URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("posterUrl must be a valid HTTPS URL", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = uri.getHost();
        boolean localHttp = "http".equals(scheme) && host != null &&
                (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
        if ((!"https".equals(scheme) && !localHttp) || host == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("posterUrl must use HTTPS (or explicit local HTTP development host)");
        }
        return uri.toString();
    }

    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
}
