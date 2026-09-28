package com.neo4flix.user.api;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        PublicUser user) {
}
