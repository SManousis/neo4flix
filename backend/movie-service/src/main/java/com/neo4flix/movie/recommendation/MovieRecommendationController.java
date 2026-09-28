package com.neo4flix.movie.recommendation;

import java.util.LinkedHashMap;

import jakarta.servlet.http.HttpServletRequest;
import com.neo4flix.platform.common.web.RequestId;
import com.neo4flix.platform.common.web.RequestIdFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieRecommendationController {

    private final MovieRecommendationClient client;

    public MovieRecommendationController(MovieRecommendationClient client) {
        this.client = client;
    }

    @GetMapping("/recommended")
    public ResponseEntity<?> recommend(@ModelAttribute MovieRecommendationQueryParams params,
                                       HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        copyHeader(request, headers, HttpHeaders.AUTHORIZATION);
        String requestId = requestId(request);
        if (requestId != null) {
            headers.set(RequestIdFilter.HEADER_NAME, requestId);
        }
        try {
            return client.fetch(headers, query(params));
        } catch (RecommendationUnavailableException exception) {
            var body = new LinkedHashMap<String, Object>();
            body.put("type", "about:blank");
            body.put("title", "Service Unavailable");
            body.put("status", 503);
            body.put("detail", "Recommendations are temporarily unavailable");
            body.put("instance", request.getRequestURI());
            body.put("code", "RECOMMENDATION_SERVICE_UNAVAILABLE");
            body.put("traceId", requestId);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set(RequestIdFilter.HEADER_NAME, requestId);
            return ResponseEntity.status(503)
                    .headers(responseHeaders)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(body);
        }
    }

    private static String requestId(HttpServletRequest request) {
        Object generated = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (generated instanceof String value && !value.isBlank()) {
            return value;
        }
        return RequestId.resolve(request.getHeader(RequestIdFilter.HEADER_NAME));
    }

    private static LinkedMultiValueMap<String, String> query(MovieRecommendationQueryParams params) {
        var query = new LinkedMultiValueMap<String, String>();
        add(query, "genre", params.genre());
        add(query, "fromYear", params.fromYear());
        add(query, "toYear", params.toYear());
        add(query, "minimumAverageRating", params.minimumAverageRating());
        add(query, "sort", params.sort());
        add(query, "page", params.page());
        add(query, "size", params.size());
        return query;
    }

    private static void add(LinkedMultiValueMap<String, String> query, String key, Object value) {
        if (value != null && (! (value instanceof String string) || !string.isBlank())) {
            query.add(key, value.toString());
        }
    }

    private static void copyHeader(HttpServletRequest request, HttpHeaders headers, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }
}
