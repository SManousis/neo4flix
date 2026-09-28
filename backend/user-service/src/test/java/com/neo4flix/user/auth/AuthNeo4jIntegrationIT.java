package com.neo4flix.user.auth;

import com.neo4flix.user.api.LoginRequest;
import com.neo4flix.user.api.RegisterRequest;
import com.neo4flix.user.persistence.AuthSessionRepository;
import com.neo4flix.user.persistence.UserRepository;
import com.neo4flix.user.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthNeo4jIntegrationIT {

    @Test
    void concurrentRefreshOfOneTokenHasExactlyOneWinner() throws Exception {
        try (var neo4j = new Neo4jContainer<>(DockerImageName.parse("neo4j:2026.07.1-community"))
                .withAdminPassword("test-password")) {
            neo4j.start();
            try (var driver = GraphDatabase.driver(
                    neo4j.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
                driver.executableQuery("CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.normalizedEmail IS UNIQUE")
                        .execute();
                driver.executableQuery("CREATE CONSTRAINT session_id IF NOT EXISTS FOR (s:AuthSession) REQUIRE s.id IS UNIQUE")
                        .execute();
                var service = service(Neo4jClient.create(driver));
                service.register(new RegisterRequest("race@example.com", "Race", "StrongPass1!"));
                String token = ((AuthApplicationService.Authenticated) service.login(
                        new LoginRequest("race@example.com", "StrongPass1!"))).refreshToken();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                List<Boolean> outcomes;
                try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> refreshOutcome(service, token, ready, start));
                    var second = executor.submit(() -> refreshOutcome(service, token, ready, start));
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
                }

                assertThat(outcomes).containsExactlyInAnyOrder(true, false);
                long replacements = driver.executableQuery("""
                                MATCH (:User {normalizedEmail: $email})-[:HAS_SESSION]->(s:AuthSession)
                                WHERE s.rotatedFromSessionId IS NOT NULL
                                RETURN count(s) AS count
                                """)
                        .withParameters(java.util.Map.of("email", "race@example.com"))
                        .execute().records().getFirst().get("count").asLong();
                assertThat(replacements).isEqualTo(1L);
            }
        }
    }

    @Test
    void refreshTokensRotateOnceLogoutRevokesAndOnlyHashesReachTheGraph() throws Exception {
        try (var neo4j = new Neo4jContainer<>(DockerImageName.parse("neo4j:2026.07.1-community"))
                .withAdminPassword("test-password")) {
            neo4j.start();
            try (var driver = GraphDatabase.driver(
                    neo4j.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
                driver.executableQuery("CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.normalizedEmail IS UNIQUE")
                        .execute();
                driver.executableQuery("CREATE CONSTRAINT session_hash IF NOT EXISTS FOR (s:AuthSession) REQUIRE s.refreshTokenHash IS UNIQUE")
                        .execute();
                var service = service(Neo4jClient.create(driver));

                service.register(new RegisterRequest("alice@example.com", "Alice", "StrongPass1!"));
                var login = (AuthApplicationService.Authenticated) service.login(
                        new LoginRequest("alice@example.com", "StrongPass1!"));
                String tokenA = login.refreshToken();
                var refreshB = service.refresh(tokenA);
                String tokenB = refreshB.refreshToken();

                assertThat(tokenB).isNotEqualTo(tokenA);
                assertThatThrownBy(() -> service.refresh(tokenA))
                        .isInstanceOf(AuthApplicationService.InvalidRefreshTokenException.class);

                service.logout(tokenB);
                assertThatThrownBy(() -> service.refresh(tokenB))
                        .isInstanceOf(AuthApplicationService.InvalidRefreshTokenException.class);

                List<Object> values = driver.executableQuery("MATCH (n) UNWIND keys(n) AS key RETURN n[key] AS value")
                        .execute().records().stream()
                        .map(record -> record.get("value").asObject())
                        .toList();
                assertThat(values)
                        .doesNotContain("StrongPass1!", tokenA, tokenB)
                        .anySatisfy(value -> assertThat(value.toString()).startsWith("$2"));
                assertThat(driver.executableQuery("MATCH (s:AuthSession) RETURN s.refreshTokenHash AS hash")
                        .execute().records())
                        .isNotEmpty()
                        .allSatisfy(record -> assertThat(record.get("hash").asString())
                                .hasSize(43)
                                .isNotIn(tokenA, tokenB));
            }
        }
    }

    private static AuthApplicationService service(Neo4jClient client) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var jwt = new JwtTokenService(
                (RSAPrivateKey) generator.generateKeyPair().getPrivate(),
                "neo4flix-user-service", "neo4flix-api", Duration.ofMinutes(15));
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var twoFactor = new TotpAuthenticationService(client, new com.neo4flix.user.security.TotpService(),
                new com.neo4flix.user.security.SecretEncryptionService(java.util.Base64.getEncoder().encodeToString(key)), clock, new SecureRandom());
        return new AuthApplicationService(
                new UserRepository.Neo4j(client), new AuthSessionRepository.Neo4j(client), new PasswordPolicy(128), new BCryptPasswordEncoder(4), jwt,
                new SecureRandom(), clock, Duration.ofDays(30), 32, twoFactor);
    }

    private static boolean refreshOutcome(
            AuthApplicationService service,
            String token,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            service.refresh(token);
            return true;
        } catch (AuthApplicationService.InvalidRefreshTokenException exception) {
            return false;
        }
    }
}
