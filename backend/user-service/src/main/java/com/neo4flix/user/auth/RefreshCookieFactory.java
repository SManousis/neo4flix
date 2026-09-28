package com.neo4flix.user.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public final class RefreshCookieFactory {

    private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");
    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final String path;
    private final Duration ttl;

    public RefreshCookieFactory(
            @Value("${neo4flix.security.refresh-cookie.name:neo4flix_refresh}") String name,
            @Value("${neo4flix.security.refresh-cookie.secure:true}") boolean secure,
            @Value("${neo4flix.security.refresh-cookie.same-site:Strict}") String sameSite,
            @Value("${neo4flix.security.refresh-cookie.path:/api/v1/auth}") String path,
            @Value("${neo4flix.security.refresh-token.ttl:P30D}") Duration ttl) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Refresh cookie name is invalid");
        }
        String normalizedSameSite = normalizeSameSite(sameSite);
        if (!SAME_SITE_VALUES.contains(normalizedSameSite)) {
            throw new IllegalArgumentException("Refresh cookie SameSite policy is invalid");
        }
        if (path == null || !path.startsWith("/") || path.length() > 128) {
            throw new IllegalArgumentException("Refresh cookie path is invalid");
        }
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("Refresh token TTL is outside allowed bounds");
        }
        this.name = name;
        this.secure = secure;
        this.sameSite = normalizedSameSite;
        this.path = path;
        this.ttl = ttl;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public String name() {
        return name;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .secure(secure)
                .httpOnly(true)
                .sameSite(sameSite)
                .path(path);
    }

    private static String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
