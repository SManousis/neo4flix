package com.neo4flix.rating.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RatingWriteRequest(
        @NotBlank String movieId,
        @Min(1) @Max(5) int score) {
}
