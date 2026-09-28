package com.neo4flix.user.api;

import java.time.Instant;

public record PublicUser(
        String id,
        String email,
        String displayName,
        String role,
        boolean twoFactorEnabled,
        Instant createdAt) {
}
