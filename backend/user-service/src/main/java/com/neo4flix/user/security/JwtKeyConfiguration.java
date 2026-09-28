package com.neo4flix.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class JwtKeyConfiguration {

    @Bean
    @Conditional(PrivateKeyConfigured.class)
    RSAPrivateKey jwtPrivateKey(@Value("${neo4flix.security.jwt.private-key}") String configuredValue) {
        try {
            String material = readConfiguredValue(configuredValue);
            String encoded = material
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load JWT private key", exception);
        }
    }

    @Bean
    @Conditional(PrivateKeyConfigured.class)
    JwtTokenService jwtTokenService(
            RSAPrivateKey jwtPrivateKey,
            @Value("${neo4flix.security.jwt.issuer:neo4flix-user-service}") String issuer,
            @Value("${neo4flix.security.jwt.audience:neo4flix-api}") String audience,
            @Value("${neo4flix.security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        return new JwtTokenService(jwtPrivateKey, issuer, audience, accessTokenTtl);
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

    public static final class PrivateKeyConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String value = context.getEnvironment().getProperty("neo4flix.security.jwt.private-key");
            return value != null && !value.isBlank();
        }
    }
}
