package com.neo4flix.user.security;

import com.neo4flix.user.persistence.UserNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String ISSUER = "neo4flix-user-service";
    private static final String AUDIENCE = "neo4flix-api";

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @BeforeEach
    void generateEphemeralRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    @Test
    void issuesRs256AccessTokenWithRequiredClaimsAndNineHundredSecondLifetime() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserNode user = user("user-42", "ADMIN");
        JwtTokenService service = new JwtTokenService(
                privateKey, ISSUER, AUDIENCE, Duration.ofSeconds(900));

        JwtTokenService.IssuedAccessToken issued = service.issue(user, now);
        Jwt decoded = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(issued.token());

        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getSubject()).isEqualTo("user-42");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getIssuedAt()).isEqualTo(now);
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plusSeconds(900));
        assertThat(decoded.getId()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    private static UserNode user(String id, String role) {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new UserNode(
                id,
                "user@example.com",
                "user@example.com",
                "User",
                "bcrypt-hash",
                role,
                true,
                false,
                null,
                null,
                null,
                createdAt,
                createdAt,
                Set.of(),
                Set.of(),
                Set.of());
    }
}
