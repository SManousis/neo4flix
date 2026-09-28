package com.neo4flix.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitFilterTest {
    @Test void trustedNginxSeparatesClientsAndIgnoresSpoofedForwardingChains() throws Exception {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("proxy-test", java.util.Map.of(
                            "neo4flix.security.rate-limit.requests", "1",
                            "neo4flix.security.rate-limit.trusted-proxies", "10.20.0.4/32")));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(RateLimitFilter.class);
            context.refresh();
            var filter = context.getBean(RateLimitFilter.class);
            assertThat(proxyCall(filter, "10.20.0.4", "198.51.100.1").getStatus()).isEqualTo(204);
            assertThat(proxyCall(filter, "10.20.0.4", "198.51.100.2").getStatus()).isEqualTo(204);
            assertThat(proxyCall(filter, "10.20.0.4", "198.51.100.1").getStatus()).isEqualTo(429);
            assertThat(proxyCall(filter, "203.0.113.9", "198.51.100.3").getStatus()).isEqualTo(204);
            assertThat(proxyCall(filter, "203.0.113.9", "198.51.100.4").getStatus()).isEqualTo(429);
            assertThat(proxyCall(filter, "10.20.0.4", "not-an-ip").getStatus()).isEqualTo(204);
            assertThat(proxyCall(filter, "10.20.0.4", "198.51.100.5, 198.51.100.6").getStatus()).isEqualTo(429);
        }
    }

    @Test void rejectionResponsesExposeCompleteProblemContractAndRequestId() throws Exception {
        var filter = new RateLimitFilter(Clock.systemUTC(), 1, 60, 100, "https://app.example");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new com.neo4flix.platform.common.web.RequestIdFilter(), filter).build();
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isNoContent());
        assertProblem(mvc, "/api/v1/auth/login", 429, "Too Many Requests", "RATE_LIMITED");
        assertProblem(mvc, "/api/v1/auth/refresh", 403, "Forbidden", "ORIGIN_REJECTED");
    }

    private void assertProblem(MockMvc mvc, String path, int statusCode, String title, String code) throws Exception {
        mvc.perform(post(path).header("X-Request-Id", "review-fix-42"))
                .andExpect(status().is(statusCode))
                .andExpect(header().string("X-Request-Id", "review-fix-42"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("application/problem+json"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.type").value("about:blank"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").value(title))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(statusCode))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.detail").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.instance").value(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(code))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId").value("review-fix-42"));
    }

    private MockHttpServletResponse proxyCall(RateLimitFilter filter, String peer, String client) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(peer);
        request.addHeader("X-Real-IP", client);
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        return response;
    }

    @Test void capsRequestsAndIdentitiesWithoutTrustingForwardedHeaders() throws Exception {
        var filter = new RateLimitFilter(Clock.systemUTC(), 2, 60, 1, "https://app.example");
        assertThat(call(filter, "/api/v1/auth/login", null, null, "a").getStatus()).isEqualTo(204);
        assertThat(call(filter, "/api/v1/auth/login", null, null, "a").getStatus()).isEqualTo(204);
        var limited = call(filter, "/api/v1/auth/login", null, null, "a");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Integer.parseInt(limited.getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(call(filter, "/api/v1/auth/login", null, null, "b").getStatus()).isEqualTo(429);
        assertThat(call(filter, "/api/v1/users/me", null, null, "a").getStatus()).isEqualTo(204);
    }

    @Test void cookieEndpointsRequireExactAllowlistedOriginOrReferer() throws Exception {
        var filter = new RateLimitFilter(Clock.systemUTC(), 100, 60, 100, "https://app.example");
        for (String endpoint : new String[]{"refresh", "logout"}) {
            String path = "/api/v1/auth/" + endpoint;
            assertThat(call(filter, path, null, null, "a").getStatus()).isEqualTo(403);
            assertThat(call(filter, path, "https://evil.example", "https://app.example/profile", "a").getStatus()).isEqualTo(403);
            assertThat(call(filter, path, "null", null, "a").getStatus()).isEqualTo(403);
            assertThat(call(filter, path, "https://app.example.evil", null, "a").getStatus()).isEqualTo(403);
            assertThat(call(filter, path, "https://app.example", null, "a").getStatus()).isEqualTo(204);
            assertThat(call(filter, path, null, "https://app.example/profile", "a").getStatus()).isEqualTo(204);
        }
    }

    @Test void permitsNewWindowAfterExpiry() throws Exception {
        var clock = new Clock() {
            Instant now = Instant.parse("2026-09-13T12:00:00Z");
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        };
        var filter = new RateLimitFilter(clock, 1, 60, 1, "https://app.example");
        assertThat(call(filter, "/api/v1/auth/register", null, null, "a").getStatus()).isEqualTo(204);
        assertThat(call(filter, "/api/v1/auth/register", null, null, "a").getStatus()).isEqualTo(429);
        clock.now = clock.now.plusSeconds(60);
        assertThat(call(filter, "/api/v1/auth/register", null, null, "b").getStatus()).isEqualTo(204);
    }

    @Test void percentEncodedLoginAndTwoFactorPathsShareCanonicalRateLimits() throws Exception {
        MockMvc mvc = mvc(new RateLimitFilter(Clock.systemUTC(), 1, 60, 100, "https://app.example"));

        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isNoContent());
        mvc.perform(post(URI.create("/api/v1/auth/%6cogin")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        mvc.perform(post("/api/v1/auth/2fa/setup")).andExpect(status().isNoContent());
        mvc.perform(post(URI.create("/api/v1/auth/2fa/%63onfirm")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test void percentEncodedRefreshAndLogoutPathsStillRequireAllowedOrigin() throws Exception {
        MockMvc mvc = mvc(new RateLimitFilter(Clock.systemUTC(), 100, 60, 100, "https://app.example"));

        mvc.perform(post(URI.create("/api/v1/auth/%72efresh"))).andExpect(status().isForbidden());
        mvc.perform(post(URI.create("/api/v1/auth/%6cogout"))).andExpect(status().isForbidden());
    }

    @Test void rejectsAmbiguousEncodedTraversalAndSeparatorPaths() throws Exception {
        MockMvc mvc = mvc(new RateLimitFilter(Clock.systemUTC(), 100, 60, 100, "https://app.example"));

        for (String path : new String[]{
                "/api/v1/auth/%2e%2e/login",
                "/api/v1/auth/%2flogin",
                "/api/v1/auth/%5clogin",
                "/api/v1/auth//login",
                "/api/v1/auth/login;ignored=true"}) {
            mvc.perform(post(URI.create(path))).andExpect(status().isBadRequest());
        }
    }

    private static MockMvc mvc(RateLimitFilter filter) {
        return MockMvcBuilders.standaloneSetup(new ProbeController()).addFilters(filter).build();
    }

    @RestController
    private static final class ProbeController {
        @PostMapping({"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                "/api/v1/auth/2fa/setup", "/api/v1/auth/2fa/confirm"})
        ResponseEntity<Void> probe() {
            return ResponseEntity.noContent().build();
        }
    }

    private MockHttpServletResponse call(RateLimitFilter filter, String path, String origin, String referer, String address) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        if (origin != null) request.addHeader("Origin", origin);
        if (referer != null) request.addHeader("Referer", referer);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        return response;
    }
}
