package com.neo4flix.recommendation.share;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationShareModelsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allowsOmittedExpiryForTheServiceDefaultAndRejectsBlankMovieIds() {
        var omittedExpiry = new RecommendationShareModels.CreateRequest("movie-1", null);
        var blankMovie = new RecommendationShareModels.CreateRequest(" ", 30);

        assertThat(validator.validate(omittedExpiry)).isEmpty();
        assertThat(validator.validate(blankMovie)).isNotEmpty();
    }

    @Test
    void acceptsOnlyBoundedExpiryValues() {
        var tooShort = new RecommendationShareModels.CreateRequest("movie-1", 0);
        var tooLong = new RecommendationShareModels.CreateRequest("movie-1", 366);

        assertThat(validator.validate(tooShort)).isNotEmpty();
        assertThat(validator.validate(tooLong)).isNotEmpty();
    }
}
