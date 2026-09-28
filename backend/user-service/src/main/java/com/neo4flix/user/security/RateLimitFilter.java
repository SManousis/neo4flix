package com.neo4flix.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import com.neo4flix.platform.common.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final int limit, windowSeconds, capacity;
    private final Set<String> origins;
    private final Map<String, Window> windows = new HashMap<>();
    private List<IpAddressMatcher> trustedProxies = List.of();

    @org.springframework.beans.factory.annotation.Autowired
    void configureTrustedProxies(
            @Value("${neo4flix.security.rate-limit.trusted-proxies:}") String proxies) {
        trustedProxies = Arrays.stream(proxies.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public RateLimitFilter(Clock clock,
            @Value("${neo4flix.security.rate-limit.requests:10}") int limit,
            @Value("${neo4flix.security.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${neo4flix.security.rate-limit.capacity:10000}") int capacity,
            @Value("${neo4flix.security.allowed-origins:http://localhost:4200,http://localhost:8080}") String origins) {
        if (limit < 1 || limit > 1000 || windowSeconds < 1 || windowSeconds > 3600 || capacity < 1 || capacity > 100000)
            throw new IllegalArgumentException("Rate limits are outside allowed bounds");
        this.clock = clock; this.limit = limit; this.windowSeconds = windowSeconds; this.capacity = capacity;
        Set<String> configured = new HashSet<>();
        for (String value : origins.split(",")) {
            String origin = origin(value.trim(), false);
            if (origin == null) throw new IllegalArgumentException("An explicit HTTP(S) origin allowlist is required");
            configured.add(origin);
        }
        this.origins = Set.copyOf(configured);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = normalizedPath(request);
        if (path == null) { reject(request, response, 400, "INVALID_REQUEST_PATH", "Invalid request path"); return; }
        if (request.getMethod().equals("POST")) {
            if (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")) {
                String supplied = request.getHeader("Origin");
                String actual = supplied != null ? origin(supplied, false) : origin(request.getHeader("Referer"), true);
                if (actual == null || !origins.contains(actual)) { reject(request, response, 403, "ORIGIN_REJECTED", "Origin rejected"); return; }
            }
            String endpoint = endpoint(path);
            if (endpoint != null) {
                long retry = acquire(endpoint + "|" + clientIdentity(request));
                if (retry > 0) {
                    response.setHeader("Retry-After", Long.toString(retry));
                    reject(request, response, 429, "RATE_LIMITED", "Too many requests"); return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String clientIdentity(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trustedProxies.stream().noneMatch(proxy -> proxy.matches(peer))) return peer;
        // Nginx overwrites this header with its socket peer; never trust a supplied X-Forwarded-For chain.
        String client = request.getHeader("X-Real-IP");
        if (client == null || Collections.list(request.getHeaders("X-Real-IP")).size() != 1
                || !client.matches("[0-9a-fA-F:.]+")
                || (!client.contains(":") && !client.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) return peer;
        try {
            return java.net.InetAddress.getByName(client).getHostAddress();
        } catch (java.net.UnknownHostException exception) {
            return peer;
        }
    }

    private static String normalizedPath(HttpServletRequest request) {
        try {
            PathContainer path = ServletRequestPathUtils.parse(request).pathWithinApplication();
            String raw = path.value();
            if (raw.indexOf('\\') >= 0 || raw.contains("//")) return null;
            StringBuilder normalized = new StringBuilder(raw.length());
            for (PathContainer.Element element : path.elements()) {
                if (element instanceof PathContainer.PathSegment segment) {
                    String value = segment.valueToMatch();
                    if (!segment.parameters().isEmpty() || value.equals(".") || value.equals("..")
                            || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                            || value.chars().anyMatch(character -> character < 32 || character == 127)) return null;
                    normalized.append(value);
                } else if (element.value().equals("/")) {
                    normalized.append('/');
                } else {
                    return null;
                }
            }
            return normalized.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private synchronized long acquire(String key) {
        long now = clock.instant().getEpochSecond();
        windows.values().removeIf(window -> window.expiresAt <= now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= capacity) return windowSeconds;
            window = new Window(now + windowSeconds); windows.put(key, window);
        }
        if (window.count >= limit) return Math.max(1, window.expiresAt - now);
        window.count++; return 0;
    }

    private static String endpoint(String path) {
        return switch (path) {
            case "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh" -> path;
            case "/api/v1/auth/2fa/setup", "/api/v1/auth/2fa/confirm", "/api/v1/auth/2fa/verify", "/api/v1/auth/2fa/disable" -> "/api/v1/auth/2fa";
            default -> null;
        };
    }

    private static String origin(String value, boolean referer) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || (!referer && (uri.getRawQuery() != null || !uri.getRawPath().isEmpty()))) return null;
            int port = uri.getPort();
            if (port == (uri.getScheme().equals("https") ? 443 : 80)) port = -1;
            return uri.getScheme() + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
        } catch (IllegalArgumentException exception) { return null; }
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, int status, String code, String detail) throws IOException {
        ProblemDetails.write(response, request, HttpStatus.valueOf(status), code, detail);
    }
    private static final class Window {
        final long expiresAt; int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
