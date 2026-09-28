package com.neo4flix.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 10, max = 128) String newPassword,
        @Pattern(regexp = "\\d{6}") String code) {
}
