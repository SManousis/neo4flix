package com.neo4flix.user.auth;

import com.neo4flix.user.api.AuthResponse;
import com.neo4flix.user.api.ChangePasswordRequest;
import com.neo4flix.user.api.LoginRequest;
import com.neo4flix.user.api.PublicUser;
import com.neo4flix.user.api.RegisterRequest;
import com.neo4flix.user.api.UpdateProfileRequest;
import com.neo4flix.user.persistence.AuthSessionNode;
import com.neo4flix.user.persistence.AuthSessionRepository;
import com.neo4flix.user.persistence.UserNode;
import com.neo4flix.user.persistence.UserRepository;
import com.neo4flix.user.security.JwtKeyConfiguration;
import com.neo4flix.user.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Conditional(JwtKeyConfiguration.PrivateKeyConfigured.class)
public class AuthApplicationService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String DUMMY_PASSWORD = "NotARealPassword1!";

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration refreshTtl;
    private final int refreshTokenBytes;
    private final String dummyPasswordHash;
    private final TotpAuthenticationService twoFactor;

    public AuthApplicationService(
            UserRepository users,
            AuthSessionRepository sessions,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            SecureRandom secureRandom,
            Clock clock,
            @Value("${neo4flix.security.refresh-token.ttl:P30D}") Duration refreshTtl,
            @Value("${neo4flix.security.refresh-token.bytes:32}") int refreshTokenBytes,
            TotpAuthenticationService twoFactor) {
        this.users = Objects.requireNonNull(users);
        this.sessions = Objects.requireNonNull(sessions);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.clock = Objects.requireNonNull(clock);
        this.twoFactor = Objects.requireNonNull(twoFactor);
        if (refreshTtl == null || refreshTtl.compareTo(Duration.ofMinutes(1)) < 0
                || refreshTtl.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("Refresh token TTL is outside allowed bounds");
        }
        if (refreshTokenBytes < 32 || refreshTokenBytes > 64) {
            throw new IllegalArgumentException("Refresh token size must be between 32 and 64 bytes");
        }
        this.refreshTtl = refreshTtl;
        this.refreshTokenBytes = refreshTokenBytes;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public PublicUser register(RegisterRequest request) {
        String email = boundedEmail(request.email());
        String normalizedEmail = normalizeEmail(email);
        String displayName = boundedDisplayName(request.displayName());
        passwordPolicy.validate(request.password());
        Instant now = clock.instant();
        UserNode user = new UserNode(
                UUID.randomUUID().toString(), email, normalizedEmail, displayName,
                passwordEncoder.encode(request.password()), "USER", true, false,
                null, null, null, now, now, Set.of(), Set.of(), Set.of());
        if (!users.create(user)) {
            throw new DuplicateEmailException();
        }
        return toPublicUser(user);
    }

    @Transactional
    public LoginOutcome login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(boundedEmail(request.email()));
        UserNode user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (user != null) user = twoFactor.lockUser(user.id());
        String candidateHash = user == null ? dummyPasswordHash : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), candidateHash);
        if (user == null || !user.enabled() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        if (user.twoFactorEnabled()) {
            return twoFactor.challenge(user);
        }
        return issueSession(user, clock.instant());
    }

    @Transactional
    public Authenticated refresh(String refreshToken) {
        String currentHash = hashRefreshToken(requireRefreshToken(refreshToken));
        Instant now = clock.instant();
        TokenMaterial replacement = newRefreshToken();
        AuthSessionNode replacementSession = new AuthSessionNode(
                UUID.randomUUID().toString(), replacement.hash(), now, now.plus(refreshTtl), null, null);
        UserNode user = sessions.rotate(currentHash, now, replacementSession)
                .orElseThrow(InvalidRefreshTokenException::new);
        JwtTokenService.IssuedAccessToken access = jwtTokenService.issue(user, now);
        return authenticated(user, access, replacement.raw(), replacementSession.expiresAt());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.revoke(hashRefreshToken(refreshToken), clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public PublicUser currentUser(String subject) {
        return toPublicUser(requireUser(subject));
    }

    @Transactional
    public PublicUser updateProfile(String subject, UpdateProfileRequest request) {
        String displayName = boundedDisplayName(request.displayName());
        return users.updateDisplayName(requireSubject(subject), displayName, clock.instant())
                .map(AuthApplicationService::toPublicUser)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public void changePassword(String subject, ChangePasswordRequest request) {
        UserNode user = reauthenticate(subject, request.currentPassword(), request.code());
        passwordPolicy.validate(request.newPassword());
        if (!users.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()), clock.instant())) {
            throw new UserNotFoundException();
        }
    }

    @Transactional
    public UserNode reauthenticate(String subject, String password, String code) {
        UserNode user = twoFactor.lockUser(requireSubject(subject));
        if (password == null || !passwordEncoder.matches(password, user.passwordHash())) throw new InvalidCredentialsException();
        twoFactor.requireCode(user, code);
        return user;
    }

    @Transactional
    public com.neo4flix.user.api.TotpSetupResponse setupTwoFactor(String subject) { return twoFactor.setup(requireSubject(subject)); }

    @Transactional
    public void confirmTwoFactor(String subject, String code) { twoFactor.confirm(requireSubject(subject), code); }

    @Transactional
    public Authenticated verifyTwoFactor(String token, String code) { return issueSession(twoFactor.verifyChallenge(token, code), clock.instant()); }

    @Transactional
    public void disableTwoFactor(String subject, String password, String code) { twoFactor.disable(reauthenticate(subject, password, code)); }

    private Authenticated issueSession(UserNode user, Instant now) {
        TokenMaterial refresh = newRefreshToken();
        AuthSessionNode session = new AuthSessionNode(
                UUID.randomUUID().toString(), refresh.hash(), now, now.plus(refreshTtl), null, null);
        JwtTokenService.IssuedAccessToken access = jwtTokenService.issue(user, now);
        sessions.create(user.id(), session);
        return authenticated(user, access, refresh.raw(), session.expiresAt());
    }

    private static Authenticated authenticated(
            UserNode user,
            JwtTokenService.IssuedAccessToken access,
            String refreshToken,
            Instant refreshExpiresAt) {
        return new Authenticated(
                new AuthResponse(access.token(), "Bearer", access.expiresInSeconds(), toPublicUser(user)),
                refreshToken,
                refreshExpiresAt);
    }

    private TokenMaterial newRefreshToken() {
        byte[] bytes = new byte[refreshTokenBytes];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenMaterial(raw, hashRefreshToken(raw));
    }

    static String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UserNode requireUser(String subject) {
        return users.findById(requireSubject(subject)).orElseThrow(UserNotFoundException::new);
    }

    private static String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new UserNotFoundException();
        }
        return subject;
    }

    private static String requireRefreshToken(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    private static String boundedEmail(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String email = value.trim();
        if (email.isEmpty() || email.length() > 254 || !email.contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        return email;
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static String boundedDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Display name is required");
        }
        String displayName = value.trim();
        if (displayName.isEmpty() || displayName.length() > 100) {
            throw new IllegalArgumentException("Display name is invalid");
        }
        return displayName;
    }

    private static PublicUser toPublicUser(UserNode user) {
        return new PublicUser(
                user.id(), user.email(), user.displayName(), user.role(),
                user.twoFactorEnabled(), user.createdAt());
    }

    public sealed interface LoginOutcome permits Authenticated, RequiresTwoFactor {
    }

    public record Authenticated(
            AuthResponse response,
            String refreshToken,
            Instant refreshExpiresAt) implements LoginOutcome {
    }

    public record RequiresTwoFactor(String challengeToken, long expiresIn) implements LoginOutcome {
    }

    private record TokenMaterial(String raw, String hash) {
    }

    public static final class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("An account with that email already exists");
        }
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super(INVALID_CREDENTIALS);
        }
    }

    public static final class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Refresh token is invalid or expired");
        }
    }

    public static final class UserNotFoundException extends RuntimeException {
        public UserNotFoundException() {
            super("User not found");
        }
    }

    public static final class SecondFactorRequiredException extends RuntimeException {
        public SecondFactorRequiredException() {
            super("Second-factor verification is required");
        }
    }
}
