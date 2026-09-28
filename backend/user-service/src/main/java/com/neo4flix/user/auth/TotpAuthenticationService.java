package com.neo4flix.user.auth;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.neo4flix.user.api.TotpSetupResponse;
import com.neo4flix.user.persistence.UserNode;
import com.neo4flix.user.persistence.UserRepository;
import com.neo4flix.user.security.JwtKeyConfiguration;
import com.neo4flix.user.security.SecretEncryptionService;
import com.neo4flix.user.security.TotpService;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@Conditional(JwtKeyConfiguration.PrivateKeyConfigured.class)
@Transactional
public class TotpAuthenticationService {
    private final Neo4jClient client;
    private final TotpService totp;
    private final SecretEncryptionService encryption;
    private final Clock clock;
    private final SecureRandom random;

    public TotpAuthenticationService(Neo4jClient client, TotpService totp, SecretEncryptionService encryption, Clock clock, SecureRandom random) {
        this.client = client; this.totp = totp; this.encryption = encryption; this.clock = clock; this.random = random;
    }

    public UserNode lockUser(String id) {
        // A dummy write obtains the User lock before reading credential state. The
        // transaction retains it after REMOVE, serializing enrollment/login/deletion.
        return client.query("""
                MATCH (u:User {id:$id})
                SET u.authLock = true
                REMOVE u.authLock
                RETURN u{.*} AS user
                """).bind(id).to("id").fetchAs(UserNode.class)
                .mappedBy((types, record) -> UserRepository.Neo4j.mapUser(record)).one()
                .filter(UserNode::enabled).orElseThrow(AuthApplicationService.InvalidCredentialsException::new);
    }

    public TotpSetupResponse setup(String id) {
        UserNode user = lockUser(id);
        if (user.twoFactorEnabled()) throw new AlreadyEnabledException();
        String secret = totp.generatePending();
        Instant expires = clock.instant().plusSeconds(600);
        String encrypted = encryption.encrypt(secret);
        client.query("""
                MATCH (u:User {id:$id})
                SET u.pendingTotpSecretEncrypted=$encrypted, u.pendingTotpExpiresAt=$expires, u.updatedAt=$now
                """).bindAll(Map.of("id", id, "encrypted", encrypted, "expires", utc(expires), "now", utc(clock.instant()))).run();
        String uri = "otpauth://totp/" + encode("Neo4flix:" + user.email()) + "?secret=" + secret + "&issuer=Neo4flix&algorithm=SHA1&digits=6&period=30";
        try {
            var matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 320, 320);
            var output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return new TotpSetupResponse(uri, "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()), expires);
        } catch (Exception exception) { throw new IllegalStateException("Unable to render enrollment QR"); }
    }

    public void confirm(String id, String code) {
        UserNode user = lockUser(id);
        Instant now = clock.instant();
        if (user.twoFactorEnabled() || user.pendingTotpSecretEncrypted() == null || user.pendingTotpExpiresAt() == null
                || !user.pendingTotpExpiresAt().isAfter(now)
                || !totp.verify(encryption.decrypt(user.pendingTotpSecretEncrypted()), code, now)) invalid();
        client.query("""
                MATCH (u:User {id:$id})
                SET u.totpSecretEncrypted=u.pendingTotpSecretEncrypted, u.twoFactorEnabled=true, u.updatedAt=$now
                REMOVE u.pendingTotpSecretEncrypted, u.pendingTotpExpiresAt
                """).bindAll(Map.of("id", id, "now", utc(now))).run();
    }

    public AuthApplicationService.RequiresTwoFactor challenge(UserNode user) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        client.query("""
                MATCH (u:User {id:$id})
                CREATE (u)-[:HAS_AUTH_CHALLENGE]->(:AuthChallenge {
                  id:$challengeId, tokenHash:$hash, purpose:'LOGIN_2FA', createdAt:$now, expiresAt:$expires
                })
                """).bindAll(Map.of("id", user.id(), "challengeId", UUID.randomUUID().toString(),
                        "hash", AuthApplicationService.hashRefreshToken(token), "now", utc(now), "expires", utc(now.plusSeconds(300)))).run();
        return new AuthApplicationService.RequiresTwoFactor(token, 300);
    }

    public UserNode verifyChallenge(String token, String code) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) invalid();
        String hash = AuthApplicationService.hashRefreshToken(token);
        String id = client.query("""
                MATCH (u:User)-[:HAS_AUTH_CHALLENGE]->(:AuthChallenge {tokenHash:$hash, purpose:'LOGIN_2FA'})
                RETURN u.id AS id
                """).bind(hash).to("hash").fetchAs(String.class).one().orElseThrow(AuthApplicationService.InvalidCredentialsException::new);
        UserNode user = lockUser(id);
        if (!user.twoFactorEnabled()) invalid();
        requireCode(user, code);
        // The owning User lock serializes attempts; only the first transaction can
        // consume this challenge and create its session before releasing the lock.
        boolean consumed = client.query("""
                MATCH (:User {id:$id})-[:HAS_AUTH_CHALLENGE]->(c:AuthChallenge {tokenHash:$hash, purpose:'LOGIN_2FA'})
                WHERE c.usedAt IS NULL AND c.expiresAt > $now
                SET c.usedAt=$now
                RETURN count(c)=1 AS consumed
                """).bindAll(Map.of("id", id, "hash", hash, "now", utc(clock.instant())))
                .fetchAs(Boolean.class).one().orElse(false);
        if (!consumed) invalid();
        return user;
    }

    public void requireCode(UserNode user, String code) {
        if (user.twoFactorEnabled() && (user.totpSecretEncrypted() == null
                || !totp.verify(encryption.decrypt(user.totpSecretEncrypted()), code, clock.instant()))) invalid();
    }

    public void disable(UserNode user) {
        client.query("""
                MATCH (u:User {id:$id})
                SET u.twoFactorEnabled=false, u.updatedAt=$now
                REMOVE u.totpSecretEncrypted, u.pendingTotpSecretEncrypted, u.pendingTotpExpiresAt
                WITH u
                OPTIONAL MATCH (u)-[:HAS_AUTH_CHALLENGE]->(c:AuthChallenge)
                DETACH DELETE c
                """).bindAll(Map.of("id", user.id(), "now", utc(clock.instant()))).run();
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static Object utc(Instant value) { return value.atZone(ZoneOffset.UTC); }
    private static void invalid() { throw new AuthApplicationService.InvalidCredentialsException(); }
    public static class AlreadyEnabledException extends RuntimeException { }
}
