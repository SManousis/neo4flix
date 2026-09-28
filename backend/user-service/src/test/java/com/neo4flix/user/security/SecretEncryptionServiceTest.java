package com.neo4flix.user.security;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class SecretEncryptionServiceTest {
    private static String key() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test void encryptsWithIndependentNoncesAndAuthenticatesCiphertext() {
        var service = new SecretEncryptionService(key());
        String encrypted = service.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(encrypted).doesNotContain("JBSWY3DPEHPK3PXP")
                .isNotEqualTo(service.encrypt("JBSWY3DPEHPK3PXP"));
        assertThat(service.decrypt(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
        byte[] damaged = Base64.getDecoder().decode(encrypted);
        damaged[damaged.length - 1] ^= 1;
        assertThatThrownBy(() -> service.decrypt(Base64.getEncoder().encodeToString(damaged)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretEncryptionService(key()).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsMissingOrNon256BitDeploymentKey() {
        assertThatThrownBy(() -> new SecretEncryptionService("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretEncryptionService(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
