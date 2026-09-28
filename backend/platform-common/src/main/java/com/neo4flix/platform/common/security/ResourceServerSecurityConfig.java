package com.neo4flix.platform.common.security;

import com.neo4flix.platform.common.web.ProblemDetails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerSecurityConfig {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The token audience is invalid", null);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        var processor = new DefaultCorsProcessor() {
            @Override protected void rejectRequest(ServerHttpResponse ignored) {
                // The shared writer below supplies the complete API error contract.
            }
        };
        corsFilter.setCorsProcessor((configuration, request, response) -> {
            boolean accepted = processor.processRequest(configuration, request, response);
            if (!accepted) ProblemDetails.write(response, request, HttpStatus.FORBIDDEN,
                    "ORIGIN_REJECTED", "Cross-origin request rejected");
            return accepted;
        });
        return http
                .addFilterBefore(corsFilter, CsrfFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/2fa/verify")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies/recommended")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies", "/api/v1/movies/**", "/api/v1/genres", "/api/v1/genres/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ratings/movies/*/summary")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shares/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${neo4flix.security.allowed-origins:${NEO4FLIX_ALLOWED_ORIGINS:http://localhost:4200,http://localhost:8080}}") String origins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        configuration.setAllowCredentials(true);
        configuration.validateAllowCredentials();
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${neo4flix.security.jwt.public-key:}") String publicKeyMaterial,
            @Value("${neo4flix.security.jwt.issuer:neo4flix-user-service}") String issuer,
            @Value("${neo4flix.security.jwt.audience:neo4flix-api}") String audience) {
        if (publicKeyMaterial == null || publicKeyMaterial.isBlank()) {
            return token -> {
                throw new JwtException("JWT verification key is not configured");
            };
        }
        return jwtDecoder(parsePublicKey(publicKeyMaterial), issuer, audience);
    }

    public JwtDecoder jwtDecoder(RSAPublicKey publicKey, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    static RSAPublicKey parsePublicKey(String configuredValue) {
        try {
            String material = readConfiguredValue(configuredValue);
            String encoded = material
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load JWT public key", exception);
        }
    }

    private static String readConfiguredValue(String configuredValue) throws IOException {
        String value = configuredValue.trim();
        if (value.startsWith("-----BEGIN")) {
            return value;
        }
        try {
            Path path = value.startsWith("file:")
                    ? Path.of(java.net.URI.create(value))
                    : Path.of(value);
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.US_ASCII);
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // A base64 key can contain characters that are not valid in a platform path.
        }
        return value;
    }
}
