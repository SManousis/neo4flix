package com.neo4flix.user.security;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    private final TotpService totp = new TotpService();

    @Test void matchesRfc6238Sha1VectorsWithSixDigitTruncation() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(totp.verify(secret, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(totp.verify(secret, "081804", Instant.ofEpochSecond(1111111109))).isTrue();
        assertThat(totp.verify(secret, "050471", Instant.ofEpochSecond(1111111111))).isTrue();
        assertThat(totp.verify(secret, "005924", Instant.ofEpochSecond(1234567890))).isTrue();
        assertThat(totp.verify(secret, "287082", Instant.ofEpochSecond(119))).isFalse();
    }

    @Test void rejectsMalformedCodesAndGeneratesIndependentSecrets() {
        String first = totp.generatePending();
        assertThat(first).matches("[A-Z2-7]{32}").isNotEqualTo(totp.generatePending());
        for (String code : new String[]{null, "12345", "1234567", " 287082", "１２３４５６", "abcdef"}) {
            assertThat(totp.verify(first, code, Instant.now())).isFalse();
        }
    }
}
