package com.neo4flix.user.auth;

import com.neo4flix.user.api.LoginRequest;
import com.neo4flix.user.api.RegisterRequest;
import com.neo4flix.user.api.UpdateProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestDtosRejectUnboundedOrMalformedInput() {
        assertThat(validator.validate(new RegisterRequest(
                "not-an-email", "", "a".repeat(129)))).hasSize(3);
        assertThat(validator.validate(new LoginRequest(
                "x".repeat(255) + "@example.com", "a".repeat(129)))).hasSize(3);
        assertThat(validator.validate(new UpdateProfileRequest("x".repeat(101)))).hasSize(1);
    }
}
