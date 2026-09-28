package com.neo4flix.rating.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RatingDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void ratingWriteRequestAcceptsOnlyScoresFromOneThroughFiveAndNonBlankMovies() {
        assertThat(validator.validate(new RatingWriteRequest("movie-1", 1))).isEmpty();
        assertThat(validator.validate(new RatingWriteRequest("movie-1", 5))).isEmpty();
        assertThat(validator.validate(new RatingWriteRequest("movie-1", 0))).hasSize(1);
        assertThat(validator.validate(new RatingWriteRequest("movie-1", 6))).hasSize(1);
        assertThat(validator.validate(new RatingWriteRequest("", 3))).hasSize(1);
    }
}
