package com.neo4flix.user.api;

public record TwoFactorChallenge(
        boolean requiresTwoFactor,
        String challengeToken,
        long expiresIn) {
}
