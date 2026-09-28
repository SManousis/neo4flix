package com.neo4flix.user.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.neo4flix.user.persistence.UserNode;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JwtTokenService {

    private final NimbusJwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            RSAPrivateKey privateKey,
            String issuer,
            String audience,
            Duration accessTokenTtl) {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        RSAPublicKey publicKey = publicKeyFrom(privateKey);
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.accessTokenTtl = Objects.requireNonNull(accessTokenTtl, "accessTokenTtl must not be null");
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("accessTokenTtl must be positive");
        }
    }

    public IssuedAccessToken issue(UserNode user, Instant now) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(requireText(user.id(), "user.id"))
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(requireText(user.role(), "user.role")))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new IssuedAccessToken(token, accessTokenTtl.toSeconds());
    }

    private static RSAPublicKey publicKeyFrom(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalArgumentException("JWT private key must contain RSA CRT public parameters");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to derive JWT public key", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record IssuedAccessToken(String token, long expiresInSeconds) {
    }
}
