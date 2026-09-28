package com.neo4flix.movie.recommendation;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import com.neo4flix.platform.common.web.RequestIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovieRecommendationControllerTest {

    private final MovieRecommendationClient client = mock(MovieRecommendationClient.class);
    private final MovieRecommendationController controller = new MovieRecommendationController(client);

    @Test
    void delegatesAuthenticatedHeadersAndEquivalentFilters() {
        when(client.fetch(any(), any())).thenReturn(ResponseEntity.ok("{\"items\":[]}"));
        MockHttpServletRequest request = request("Bearer token", "request-1");

        ResponseEntity<?> response = controller.recommend(
                new MovieRecommendationQueryParams("Science Fiction", 1990, 2020, 3.5, "rating", 0, 20), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(client).fetch(eq(requestHeaders(request)), eq(query("Science Fiction", 1990, 2020, 3.5, "rating", 0, 20)));
    }

    @Test
    void turnsDownstreamFailureIntoStable503ProblemDetails() {
        when(client.fetch(any(), any())).thenThrow(new RecommendationUnavailableException("downstream unavailable"));

        ResponseEntity<?> response = controller.recommend(
                new MovieRecommendationQueryParams(null, null, null, null, null, null, null),
                request("Bearer token", "request-2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).startsWith("application/problem+json");
        assertThat(response.getBody().toString()).contains("RECOMMENDATION_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().toString()).contains("instance");
    }

    @Test
    void forwardsRequestIdGeneratedBySharedFilterWhenInboundHeaderIsAbsent() {
        when(client.fetch(any(), any())).thenReturn(ResponseEntity.ok("{\"items\":[]}"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/movies/recommended");
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "generated-1");

        controller.recommend(new MovieRecommendationQueryParams(null, null, null, null, null, null, null), request);

        HttpHeaders expected = new HttpHeaders();
        expected.set("X-Request-Id", "generated-1");
        verify(client).fetch(eq(expected), eq(new LinkedMultiValueMap<>()));
    }

    private static MockHttpServletRequest request(String authorization, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/movies/recommended");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        request.addHeader("X-Request-Id", requestId);
        return request;
    }

    private static HttpHeaders requestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, request.getHeader(HttpHeaders.AUTHORIZATION));
        headers.set("X-Request-Id", request.getHeader("X-Request-Id"));
        return headers;
    }

    private static LinkedMultiValueMap<String, String> query(String genre, Integer fromYear, Integer toYear,
                                                              Double minimumAverageRating, String sort,
                                                              Integer page, Integer size) {
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        if (genre != null) query.add("genre", genre);
        if (fromYear != null) query.add("fromYear", fromYear.toString());
        if (toYear != null) query.add("toYear", toYear.toString());
        if (minimumAverageRating != null) query.add("minimumAverageRating", minimumAverageRating.toString());
        if (sort != null) query.add("sort", sort);
        if (page != null) query.add("page", page.toString());
        if (size != null) query.add("size", size.toString());
        return query;
    }
}
