package com.neo4flix.platform.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceServerSecurityConfigTest {

    @Test
    void productionCorsAllowsConfiguredCredentialedRequestsAndRejectsHostileOrigins() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new org.springframework.mock.web.MockServletContext());
            context.register(ResourceServerSecurityConfig.class, TestEndpoints.class);
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("cors-test", Map.of(
                            "neo4flix.security.allowed-origins", "https://dev.example,http://localhost:4200")));
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/users/me")
                            .header("Origin", "https://dev.example")
                            .header("Access-Control-Request-Method", "PATCH")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Access-Control-Allow-Origin", "https://dev.example"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Access-Control-Allow-Credentials", "true"));
            mvc.perform(post("/api/v1/auth/login").header("Origin", "http://localhost:4200"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Access-Control-Allow-Origin", "http://localhost:4200"));
            mvc.perform(get("/protected").header("Origin", "https://dev.example"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Access-Control-Allow-Origin", "https://dev.example"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.type").value("about:blank"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").value("Forbidden"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(403))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.detail").isNotEmpty())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.instance").value("/api/v1/auth/login"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ORIGIN_REJECTED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId").isNotEmpty())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .doesNotExist("Access-Control-Allow-Origin"));
        }
    }

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
    void jwtClaimsExposeAuthenticatedIdentityContract() {
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(900),
                Map.of("alg", "RS256"),
                Map.of("sub", "user-42", "roles", List.of("USER", "ADMIN"),
                        "iss", ISSUER, "aud", List.of(AUDIENCE)));

        JwtClaims claims = new JwtClaims(jwt);

        assertThat(claims.subject()).isEqualTo("user-42");
        assertThat(claims.roles()).containsExactly("USER", "ADMIN");
        assertThat(claims.issuer()).isEqualTo(ISSUER);
        assertThat(claims.audience()).containsExactly(AUDIENCE);
    }

    @Test
    void roleConverterMapsRolesClaimWithSpringRolePrefix() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-42")
                .claim("roles", List.of("USER", "ADMIN"))
                .build();

        AbstractAuthenticationToken authentication =
                new ResourceServerSecurityConfig().jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void productionFilterValidatesRealBearerTokensAndEnforcesRolesWithoutSessions() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new org.springframework.mock.web.MockServletContext());
            context.register(ResourceServerSecurityConfig.class, TestEndpoints.class);
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("jwt-test", Map.of(
                            "neo4flix.security.jwt.public-key", pem(publicKey),
                            "neo4flix.security.jwt.issuer", ISSUER,
                            "neo4flix.security.jwt.audience", AUDIENCE)));
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            var publicResult = mvc.perform(post("/api/v1/auth/login"))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(publicResult.getRequest().getSession(false)).isNull();
            mvc.perform(get("/protected")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/movies")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/shares/random-token")).andExpect(status().isOk());
            mvc.perform(post("/api/v1/recommendation-shares")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/movies")).andExpect(status().isUnauthorized());
            Instant now = Instant.now();
            String validUser = token(privateKey, publicKey, ISSUER, AUDIENCE,
                    now, now.plusSeconds(900), List.of("USER"));
            String validAdmin = token(privateKey, publicKey, ISSUER, AUDIENCE,
                    now, now.plusSeconds(900), List.of("ADMIN"));

            mvc.perform(get("/protected").header("Authorization", "Bearer " + validUser))
                    .andExpect(status().isOk());
            mvc.perform(get("/admin").header("Authorization", "Bearer " + validUser))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/admin").header("Authorization", "Bearer " + validAdmin))
                    .andExpect(status().isOk());

            mvc.perform(get("/protected").header("Authorization", "Bearer " + token(
                            privateKey, publicKey, "other-issuer", AUDIENCE,
                            now, now.plusSeconds(900), List.of("USER"))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/protected").header("Authorization", "Bearer " + token(
                            privateKey, publicKey, ISSUER, "other-audience",
                            now, now.plusSeconds(900), List.of("USER"))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/protected").header("Authorization", "Bearer " + token(
                            privateKey, publicKey, ISSUER, AUDIENCE,
                            now.minusSeconds(1800), now.minusSeconds(900), List.of("USER"))))
                    .andExpect(status().isUnauthorized());

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair otherPair = generator.generateKeyPair();
            mvc.perform(get("/protected").header("Authorization", "Bearer " + token(
                            (RSAPrivateKey) otherPair.getPrivate(),
                            (RSAPublicKey) otherPair.getPublic(),
                            ISSUER, AUDIENCE, now, now.plusSeconds(900), List.of("USER"))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/protected").header("Authorization", "Bearer malformed-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String token(RSAPrivateKey signingKey, RSAPublicKey publicKey,
                                String issuer, String audience, Instant issuedAt,
                                Instant expiresAt, List<String> roles) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(signingKey).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user-42")
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("token-42")
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private static String pem(RSAPublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    @RestController
    static class TestEndpoints {
        @PostMapping("/api/v1/auth/login")
        String login() {
            return "public";
        }

        @GetMapping("/protected")
        String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/api/v1/movies")
        String publicMovies() {
            return "movies";
        }

        @GetMapping("/api/v1/shares/{token}")
        String publicShare() {
            return "share";
        }

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "admin";
        }
    }
}
