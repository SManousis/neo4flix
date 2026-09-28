package com.neo4flix.recommendation.share;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public final class RecommendationShareTokenService {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public RecommendationShareTokenService() {
        this(new SecureRandom());
    }

    RecommendationShareTokenService(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public IssuedToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(rawToken, hash(rawToken));
    }

    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String rawToken, String hash) {
        public IssuedToken {
            Objects.requireNonNull(rawToken, "rawToken");
            Objects.requireNonNull(hash, "hash");
        }
    }
}
