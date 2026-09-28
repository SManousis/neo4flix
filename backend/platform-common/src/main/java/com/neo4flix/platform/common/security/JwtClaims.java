package com.neo4flix.platform.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

public final class JwtClaims {

    private final Jwt jwt;

    public JwtClaims(Jwt jwt) {
        this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
    }

    public String subject() {
        return jwt.getSubject();
    }

    public List<String> roles() {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : List.copyOf(roles);
    }

    public String issuer() {
        return jwt.getClaimAsString("iss");
    }

    public List<String> audience() {
        List<String> audience = jwt.getAudience();
        return audience == null ? List.of() : List.copyOf(audience);
    }
}
