package com.neo4flix.user.auth;

import com.neo4flix.user.api.ChangePasswordRequest;
import com.neo4flix.user.api.LoginRequest;
import com.neo4flix.user.api.RegisterRequest;
import com.neo4flix.user.api.UpdateProfileRequest;
import com.neo4flix.user.persistence.AuthSessionNode;
import com.neo4flix.user.persistence.AuthSessionRepository;
import com.neo4flix.user.persistence.UserNode;
import com.neo4flix.user.persistence.UserRepository;
import com.neo4flix.user.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemorySessions sessions = new InMemorySessions();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var jwt = new JwtTokenService(
                (RSAPrivateKey) generator.generateKeyPair().getPrivate(),
                "neo4flix-user-service",
                "neo4flix-api",
                Duration.ofMinutes(15));
        var twoFactor = org.mockito.Mockito.mock(TotpAuthenticationService.class);
        org.mockito.Mockito.when(twoFactor.lockUser(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> users.findById(invocation.getArgument(0)).orElseThrow());
        org.mockito.Mockito.when(twoFactor.challenge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AuthApplicationService.RequiresTwoFactor("test-challenge", 300));
        service = new AuthApplicationService(
                users,
                sessions,
                new PasswordPolicy(128),
                encoder,
                jwt,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(30),
                32, twoFactor);
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyBcryptHashWithoutImplicitSession() {
        var registered = service.register(new RegisterRequest(
                " Alice@Example.COM ", "Alice", "StrongPass1!"));

        UserNode stored = users.findById(registered.id()).orElseThrow();
        assertThat(stored.normalizedEmail()).isEqualTo("alice@example.com");
        assertThat(stored.passwordHash()).isNotEqualTo("StrongPass1!");
        assertThat(encoder.matches("StrongPass1!", stored.passwordHash())).isTrue();
        assertThat(sessions.size()).isZero();
    }

    @Test
    void duplicateRegistrationUsesNormalizedEmail() {
        service.register(new RegisterRequest("alice@example.com", "Alice", "StrongPass1!"));

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "ALICE@example.com", "Other Alice", "AnotherPass2@")))
                .isInstanceOf(AuthApplicationService.DuplicateEmailException.class);
    }

    @Test
    void unknownEmailAndWrongPasswordUseSameGenericFailure() {
        service.register(new RegisterRequest("alice@example.com", "Alice", "StrongPass1!"));

        Throwable unknown = catchFailure(() -> service.login(
                new LoginRequest("missing@example.com", "StrongPass1!")));
        Throwable wrong = catchFailure(() -> service.login(
                new LoginRequest("alice@example.com", "WrongPass1!")));

        assertThat(unknown).isInstanceOf(AuthApplicationService.InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(AuthApplicationService.InvalidCredentialsException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
    }

    @Test
    void activeTwoFactorAccountDoesNotReceiveAccessOrRefreshSession() {
        UserNode user = user("u-2", "two@example.com", encoder.encode("StrongPass1!"), true);
        users.create(user);

        AuthApplicationService.LoginOutcome outcome = service.login(
                new LoginRequest("two@example.com", "StrongPass1!"));

        assertThat(outcome).isInstanceOf(AuthApplicationService.RequiresTwoFactor.class);
        assertThat(sessions.size()).isZero();
    }

    @Test
    void profileMutationUsesAuthenticatedSubjectOnly() {
        users.create(user("u-1", "alice@example.com", encoder.encode("StrongPass1!"), false));
        users.create(user("u-2", "bob@example.com", encoder.encode("StrongPass1!"), false));

        var updated = service.updateProfile("u-1", new UpdateProfileRequest("Alice Smith"));

        assertThat(updated.id()).isEqualTo("u-1");
        assertThat(updated.displayName()).isEqualTo("Alice Smith");
        assertThat(users.findById("u-2").orElseThrow().displayName()).isEqualTo("User u-2");
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndPersistsNewBcryptHash() {
        users.create(user("u-1", "alice@example.com", encoder.encode("StrongPass1!"), false));

        assertThatThrownBy(() -> service.changePassword("u-1",
                new ChangePasswordRequest("WrongPass1!", "NewStrong2@", null)))
                .isInstanceOf(AuthApplicationService.InvalidCredentialsException.class);

        service.changePassword("u-1",
                new ChangePasswordRequest("StrongPass1!", "NewStrong2@", null));
        String hash = users.findById("u-1").orElseThrow().passwordHash();
        assertThat(hash).isNotEqualTo("NewStrong2@");
        assertThat(encoder.matches("NewStrong2@", hash)).isTrue();
    }

    private static Throwable catchFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected authentication failure");
        } catch (AuthApplicationService.InvalidCredentialsException exception) {
            return exception;
        }
    }

    private static UserNode user(String id, String email, String hash, boolean twoFactorEnabled) {
        return new UserNode(
                id, email, email.toLowerCase(), "User " + id, hash, "USER", true,
                twoFactorEnabled, twoFactorEnabled ? "encrypted" : null, null, null,
                NOW, NOW, Set.of(), Set.of(), Set.of());
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<String, UserNode> values = new LinkedHashMap<>();

        @Override
        public Optional<UserNode> findByNormalizedEmail(String normalizedEmail) {
            return values.values().stream()
                    .filter(user -> user.normalizedEmail().equals(normalizedEmail))
                    .findFirst();
        }

        @Override
        public Optional<UserNode> findById(String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public boolean create(UserNode user) {
            if (findByNormalizedEmail(user.normalizedEmail()).isPresent()) {
                return false;
            }
            values.put(user.id(), user);
            return true;
        }

        @Override
        public Optional<UserNode> updateDisplayName(String id, String displayName, Instant updatedAt) {
            return findById(id).map(user -> {
                UserNode updated = copy(user, displayName, user.passwordHash(), updatedAt);
                values.put(id, updated);
                return updated;
            });
        }

        @Override
        public boolean updatePassword(String id, String passwordHash, Instant updatedAt) {
            return findById(id).map(user -> {
                values.put(id, copy(user, user.displayName(), passwordHash, updatedAt));
                return true;
            }).orElse(false);
        }

        private static UserNode copy(UserNode user, String displayName, String passwordHash, Instant updatedAt) {
            return new UserNode(
                    user.id(), user.email(), user.normalizedEmail(), displayName, passwordHash,
                    user.role(), user.enabled(), user.twoFactorEnabled(), user.totpSecretEncrypted(),
                    user.pendingTotpSecretEncrypted(), user.pendingTotpExpiresAt(), user.createdAt(),
                    updatedAt, user.sessions(), user.challenges(), user.watchlisted());
        }
    }

    private static final class InMemorySessions implements AuthSessionRepository {
        private final Map<String, AuthSessionNode> values = new LinkedHashMap<>();

        @Override
        public void create(String userId, AuthSessionNode session) {
            values.put(session.refreshTokenHash(), session);
        }

        @Override
        public Optional<UserNode> rotate(
                String currentHash, Instant now, AuthSessionNode replacement) {
            return Optional.empty();
        }

        @Override
        public boolean revoke(String refreshTokenHash, Instant revokedAt) {
            return values.remove(refreshTokenHash) != null;
        }

        int size() {
            return values.size();
        }
    }
}
