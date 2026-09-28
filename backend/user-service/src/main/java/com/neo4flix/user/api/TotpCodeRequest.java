package com.neo4flix.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpCodeRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }
