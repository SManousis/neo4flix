package com.neo4flix.user.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AuthConfiguration {

    @Bean
    PasswordPolicy passwordPolicy(@Value("${neo4flix.security.password.maximum-length:128}") int maximumLength) {
        return new PasswordPolicy(maximumLength);
    }

    @Bean
    PasswordEncoder passwordEncoder(@Value("${neo4flix.security.password.bcrypt-strength:12}") int strength) {
        if (strength < 10 || strength > 16) {
            throw new IllegalArgumentException("BCrypt strength must be between 10 and 16");
        }
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    SecureRandom authSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
