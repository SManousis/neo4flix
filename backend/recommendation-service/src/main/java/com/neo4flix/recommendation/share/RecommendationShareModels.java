package com.neo4flix.recommendation.share;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.List;

public final class RecommendationShareModels {
    private RecommendationShareModels() {
    }

    public record CreateRequest(
            @NotBlank String movieId,
            @Min(1) @Max(365) Integer expiresInDays) {
    }

    public record UpdateRequest(
            @Min(1) @Max(365) Integer expiresInDays,
            Boolean revoke) {
    }

    public record OwnerView(
            String id,
            String movieId,
            Instant createdAt,
            Instant expiresAt,
            boolean revoked) {
    }

    public record CreatedView(
            String id,
            String movieId,
            String publicToken,
            String publicPath,
            Instant createdAt,
            Instant expiresAt,
            boolean revoked) {
    }

    public record PublicMovie(
            String id,
            String title,
            String overview,
            Integer releaseYear,
            String releaseDate,
            String posterUrl,
            List<Genre> genres,
            double averageRating,
            long ratingCount) {
    }

    public record Genre(String id, String name) {
    }

    public record PublicView(
            String id,
            String movieId,
            Instant expiresAt,
            PublicMovie movie) {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static final class UnavailableShareException extends RuntimeException {
        public UnavailableShareException() {
            super("share unavailable");
        }
    }
}
