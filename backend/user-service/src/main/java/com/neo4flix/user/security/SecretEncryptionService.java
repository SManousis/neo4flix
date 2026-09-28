package com.neo4flix.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Conditional(JwtKeyConfiguration.PrivateKeyConfigured.class)
public class SecretEncryptionService {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretEncryptionService(@Value("${neo4flix.security.totp.encryption-key:}") String encodedKey) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedKey); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("A base64 AES-256 deployment key is required"); }
        if (bytes.length != 32) throw new IllegalArgumentException("A base64 AES-256 deployment key is required");
        key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String secret) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("Unable to encrypt enrollment material"); }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.US_ASCII);
        } catch (RuntimeException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt enrollment material");
        }
    }
}
