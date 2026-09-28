package com.neo4flix.user.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {

    @Test
    void issuedAndClearedCookiesKeepSecureHttpOnlySameSitePolicy() {
        var factory = new RefreshCookieFactory(
                "neo4flix_refresh", true, "Strict", "/api/v1/auth", Duration.ofDays(30));

        String issued = factory.issue("opaque-token").toString();
        String cleared = factory.clear().toString();

        assertThat(issued)
                .contains("neo4flix_refresh=opaque-token")
                .contains("Path=/api/v1/auth")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Max-Age=2592000");
        assertThat(cleared)
                .contains("neo4flix_refresh=")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }
}
