package com.neo4flix.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthProductionContextIT {

    private static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>(
            DockerImageName.parse("neo4j:2026.07.1-community"))
            .withAdminPassword("test-password");
    private static final KeyPair KEY_PAIR = keyPair();
    private static final String ENCRYPTION_KEY = encryptionKey();
    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    static {
        NEO4J.start();
        try (var driver = GraphDatabase.driver(
                NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
            driver.executableQuery("CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.normalizedEmail IS UNIQUE")
                    .execute();
            driver.executableQuery("CREATE CONSTRAINT session_id IF NOT EXISTS FOR (s:AuthSession) REQUIRE s.id IS UNIQUE")
                    .execute();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @DynamicPropertySource
    static void productionProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "test-password");
        registry.add("neo4flix.security.jwt.private-key", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("neo4flix.security.jwt.public-key", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
        registry.add("neo4flix.security.password.bcrypt-strength", () -> "10");
        registry.add("neo4flix.security.totp.encryption-key", () -> ENCRYPTION_KEY);
        registry.add("neo4flix.security.rate-limit.requests", () -> "1000");
        registry.add("neo4flix.security.allowed-origins", () -> "https://app.example");
    }

    @Test
    void productionFilterOrderingPreservesCorsAndCorrelationOnFailures() {
        var headers = new HttpHeaders();
        headers.setOrigin("https://app.example");
        headers.set("X-Request-Id", "cors-error-42");
        var response = restTemplate.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("https://app.example");
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getBody()).containsEntry("traceId", "cors-error-42");

        headers.remove(HttpHeaders.ORIGIN);
        var rejected = restTemplate.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getHeaders().getFirst("X-Request-Id")).isEqualTo("cors-error-42");
        assertThat(rejected.getBody()).containsEntry("type", "about:blank")
                .containsEntry("code", "ORIGIN_REJECTED").containsEntry("traceId", "cors-error-42");
    }

    @Test
    @Order(1)
    void configuredProductionContextMapsAndServesCoreAuthAndProfileRoutes() {
        var registration = restTemplate.postForEntity(
                "/api/v1/auth/register",
                Map.of("email", "wired@example.com", "displayName", "Wired", "password", "StrongPass1!"),
                Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "wired@example.com", "password", "StrongPass1!"),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        String accessToken = login.getBody().get("accessToken").toString();
        String loginCookie = cookiePair(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(accessToken).isNotBlank();
        assertThat(loginCookie).startsWith("neo4flix_refresh=");

        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(accessToken);
        var authMe = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        var usersMe = restTemplate.exchange(
                "/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        assertThat(authMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usersMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authMe.getBody()).isEqualTo(usersMe.getBody());

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, loginCookie);
        refreshHeaders.add(HttpHeaders.ORIGIN, "https://app.example");
        var refresh = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshHeaders), Map.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody()).isNotNull();
        assertThat(refresh.getBody().get("accessToken").toString()).isNotBlank();
        assertThat(cookiePair(refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE)))
                .startsWith("neo4flix_refresh=")
                .isNotEqualTo(loginCookie);
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setOrigin("https://app.example");
        logoutHeaders.add(HttpHeaders.COOKIE, cookiePair(refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE)));
        var logout = restTemplate.exchange("/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Map.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(restTemplate.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "password policy violation {index} returns field validation problem")
    @MethodSource("invalidPasswords")
    @Order(2)
    void everyPasswordPolicyViolationReturnsFieldLevelBadRequest(String password) throws Exception {
        String email = "weak-" + EMAIL_SEQUENCE.incrementAndGet() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","displayName":"Weak","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(
                "Short1!",
                "lowercase1!",
                "UPPERCASE1!",
                "NoDigitsHere!",
                "NoSpecial123",
                "Strong Pass1!",
                "A1!" + "a".repeat(126));
    }

    @Test
    @Order(3)
    void totpEnrollmentChallengeReauthenticationAndDeletionAreEnforcedByLiveService() throws Exception {
        String email = "totp@example.com";
        var registered = restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "displayName", "Totp", "password", "StrongPass1!"), Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = registered.getBody().get("id").toString();
        var login = restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", "StrongPass1!"), Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().get("accessToken").toString());
        headers.setOrigin("https://app.example");

        var setup = postAuth("/2fa/setup", Map.of(), headers);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secret = secret(setup.getBody().get("otpauthUri").toString());
        assertThat(setup.getBody().get("qrCodeDataUrl").toString()).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(setup.getBody().get("qrCodeDataUrl").toString().split(",", 2)[1]);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png))).isNotNull();
        try (var driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
            var pending = driver.executableQuery("MATCH (u:User {id:$id}) RETURN u{.*} AS u").withParameters(Map.of("id", id)).execute().records().getFirst().get("u");
            assertThat(pending.get("pendingTotpSecretEncrypted").asString()).doesNotContain(secret);
            assertThat(pending.get("twoFactorEnabled").asBoolean()).isFalse();
            assertThat(pending.get("totpSecretEncrypted").isNull()).isTrue();
            driver.executableQuery("MATCH (u:User {id:$id}) SET u.pendingTotpExpiresAt=datetime()-duration('PT1S')").withParameters(Map.of("id", id)).execute();
        }
        assertThat(postAuth("/2fa/confirm", Map.of("code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        setup = postAuth("/2fa/setup", Map.of(), headers);
        secret = secret(setup.getBody().get("otpauthUri").toString());
        assertThat(postAuth("/2fa/confirm", Map.of("code", wrongCode(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/2fa/confirm", Map.of("code", "12345"), headers).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postAuth("/2fa/confirm", Map.of("code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(postAuth("/2fa/setup", Map.of(), headers).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(postAuth("/2fa/confirm", Map.of("code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var challenge = restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", "StrongPass1!"), Map.class);
        assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(challenge.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(challenge.getBody()).doesNotContainKeys("accessToken", "refreshToken", "userId");
        String token = challenge.getBody().get("challengeToken").toString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(postAuth("/2fa/verify", Map.of("challengeToken", "x".repeat(43), "code", code(secret)), new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/2fa/verify", Map.of("challengeToken", token, "code", wrongCode(secret)), new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        try (var driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
            driver.executableQuery("MATCH (:User {id:$id})-[:HAS_AUTH_CHALLENGE]->(c) SET c.expiresAt=datetime()-duration('PT1S')").withParameters(Map.of("id", id)).execute();
            assertThat(driver.executableQuery("MATCH (:User {id:$id})-[:HAS_SESSION]->(s) RETURN count(s) AS n").withParameters(Map.of("id", id)).execute().records().getFirst().get("n").asInt()).isEqualTo(1);
        }
        assertThat(postAuth("/2fa/verify", Map.of("challengeToken", token, "code", code(secret)), new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        challenge = restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", "StrongPass1!"), Map.class);
        String freshToken = challenge.getBody().get("challengeToken").toString();
        Map<String, String> verification = Map.of("challengeToken", freshToken, "code", code(secret));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return postAuth("/2fa/verify", verification, new HttpHeaders()); });
            var b = executor.submit(() -> { start.await(); return postAuth("/2fa/verify", verification, new HttpHeaders()); });
            start.countDown();
            var responses = java.util.List.of(a.get(), b.get());
            assertThat(responses.stream().map(org.springframework.http.ResponseEntity::getStatusCode).toList())
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
            var rejected = responses.stream().filter(r -> r.getStatusCode().equals(HttpStatus.UNAUTHORIZED)).findFirst().orElseThrow();
            assertThat(rejected.getBody()).containsEntry("code", "AUTHENTICATION_FAILED");
        }
        assertThat(postAuth("/2fa/verify", verification, new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/2fa/disable", Map.of("password", "wrong", "code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/2fa/disable", Map.of("password", "StrongPass1!", "code", wrongCode(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/change-password", Map.of("currentPassword", "StrongPass1!", "newPassword", "NewStrongPass2!", "code", wrongCode(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postAuth("/change-password", Map.of("currentPassword", "StrongPass1!", "newPassword", "NewStrongPass2!", "code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(postAuth("/2fa/disable", Map.of("password", "NewStrongPass2!", "code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        setup = postAuth("/2fa/setup", Map.of(), headers);
        secret = secret(setup.getBody().get("otpauthUri").toString());
        assertThat(postAuth("/2fa/confirm", Map.of("code", code(secret)), headers).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        try (var driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
            driver.executableQuery("MATCH (u:User {id:$id}) CREATE (u)-[:CREATED_SHARE]->(:RecommendationShare {id:'owned-share'})").withParameters(Map.of("id", id)).execute();
            var active = driver.executableQuery("MATCH (u:User {id:$id}) RETURN u{.*} AS u").withParameters(Map.of("id", id)).execute().records().getFirst().get("u");
            assertThat(active.get("pendingTotpSecretEncrypted").isNull()).isTrue();
            assertThat(active.get("pendingTotpExpiresAt").isNull()).isTrue();
            assertThat(active.get("twoFactorEnabled").asBoolean()).isTrue();
            var values = driver.executableQuery("MATCH (n) UNWIND keys(n) AS k RETURN n[k] AS v").execute().records().stream().map(r -> r.get("v").asObject()).toList();
            assertThat(values).doesNotContain(secret, token, freshToken, "StrongPass1!", "NewStrongPass2!");
        }
        assertThat(restTemplate.exchange("/api/v1/users/me", HttpMethod.DELETE, new HttpEntity<>(Map.of("password", "NewStrongPass2!"), headers), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/v1/users/me", HttpMethod.DELETE, new HttpEntity<>(Map.of("password", "wrong", "code", code(secret)), headers), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/v1/users/me", HttpMethod.DELETE, new HttpEntity<>(Map.of("password", "NewStrongPass2!", "code", code(secret)), headers), Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        try (var driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"))) {
            assertThat(driver.executableQuery("MATCH (u:User {id:$id}) RETURN count(u) AS n").withParameters(Map.of("id", id)).execute().records().getFirst().get("n").asInt()).isZero();
            assertThat(driver.executableQuery("MATCH (n) WHERE (n:RecommendationShare OR n:AuthSession OR n:AuthChallenge) AND NOT EXISTS { MATCH (:User)-->(n) } RETURN count(n) AS n").execute().records().getFirst().get("n").asInt()).isZero();
        }
    }

    @Test @Order(4)
    void productionCookieRoutesRejectMissingAndHostileOrigins() {
        assertThat(postAuth("/refresh", Map.of(), new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        HttpHeaders headers = new HttpHeaders(); headers.setOrigin("https://evil.example");
        assertThat(postAuth("/refresh", Map.of(), headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postAuth("/logout", Map.of(), headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @Order(5)
    void repositoryMigrationsEnforceOpaqueHashUniqueness() throws Exception {
        try (var driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"));
             var files = java.nio.file.Files.list(java.nio.file.Path.of("../../database/migrations"))) {
            for (var file : files.sorted().toList()) {
                for (String statement : java.nio.file.Files.readString(file).split(";")) {
                    if (!statement.isBlank()) driver.executableQuery(statement).execute();
                }
            }
            for (String query : java.util.List.of(
                    "CREATE (:AuthSession {id:randomUUID(), refreshTokenHash:'duplicate-test-hash'})",
                    "CREATE (:AuthChallenge {id:randomUUID(), tokenHash:'duplicate-test-hash'})")) {
                driver.executableQuery(query).execute();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> driver.executableQuery(query).execute())
                        .isInstanceOf(org.neo4j.driver.exceptions.ClientException.class);
            }
        }
    }

    @Test @Order(6)
    void accountWithoutTwoFactorCanDeleteWithPasswordAndOtherUsersRemain() {
        var registration = restTemplate.postForEntity("/api/v1/auth/register", Map.of("email", "delete@example.com", "displayName", "Delete", "password", "StrongPass1!"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var login = restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", "delete@example.com", "password", "StrongPass1!"), Map.class);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(login.getBody().get("accessToken").toString());
        assertThat(restTemplate.exchange("/api/v1/users/me", HttpMethod.DELETE, new HttpEntity<>(Map.of("password", "StrongPass1!"), headers), Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", "wired@example.com", "password", "StrongPass1!"), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private org.springframework.http.ResponseEntity<Map> postAuth(String path, Map<String, String> body, HttpHeaders headers) {
        return restTemplate.exchange("/api/v1/auth" + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private static String secret(String uri) {
        return java.util.Arrays.stream(java.net.URI.create(uri).getQuery().split("&"))
                .filter(part -> part.startsWith("secret=")).findFirst().orElseThrow().substring(7);
    }

    private static String wrongCode(String secret) throws Exception {
        return "%06d".formatted((Integer.parseInt(code(secret)) + 1) % 1_000_000);
    }

    private static String code(String secret) throws Exception {
        StringBuilder binary = new StringBuilder();
        for (char value : secret.toCharArray()) binary.append("%5s".formatted(Integer.toBinaryString("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(value))).replace(' ', '0'));
        byte[] key = new byte[binary.length() / 8];
        for (int i = 0; i < key.length; i++) key[i] = (byte) Integer.parseInt(binary.substring(i * 8, i * 8 + 8), 2);
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(java.time.Instant.now().getEpochSecond() / 30).array());
        int offset = hash[19] & 15;
        return "%06d".formatted((java.nio.ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff) % 1_000_000);
    }

    @AfterAll
    static void stopNeo4j() {
        NEO4J.stop();
    }

    private static String cookiePair(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encryptionKey() {
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
