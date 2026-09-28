package com.neo4flix.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyTwoFactorRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String challengeToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }
