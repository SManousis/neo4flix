package com.neo4flix.user.security;

import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
public class TotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    public String generatePending() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        StringBuilder encoded = new StringBuilder(32);
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) { bits -= 5; encoded.append(BASE32.charAt((buffer >> bits) & 31)); }
        }
        return encoded.toString();
    }

    public boolean verify(String secret, String code, Instant now) {
        if (secret == null || !secret.matches("[A-Z2-7]{32}") || code == null || !code.matches("[0-9]{6}")) return false;
        try {
            byte[] key = new byte[20];
            int buffer = 0, bits = 0, index = 0;
            for (char value : secret.toCharArray()) {
                buffer = (buffer << 5) | BASE32.indexOf(value);
                bits += 5;
                if (bits >= 8) { bits -= 8; key[index++] = (byte) (buffer >> bits); }
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            boolean valid = false;
            for (long step = -1; step <= 1; step++) {
                byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(now.getEpochSecond() / 30 + step).array());
                int offset = hash[hash.length - 1] & 15;
                int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
                String expected = String.format(Locale.ROOT, "%06d", binary % 1_000_000);
                valid |= MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII));
            }
            return valid;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP algorithm unavailable", exception);
        }
    }
}
