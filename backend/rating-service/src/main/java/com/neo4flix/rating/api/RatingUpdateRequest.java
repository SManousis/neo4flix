package com.neo4flix.rating.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RatingUpdateRequest(@Min(1) @Max(5) int score) {
}
