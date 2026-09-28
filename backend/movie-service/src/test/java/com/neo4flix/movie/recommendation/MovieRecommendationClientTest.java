package com.neo4flix.movie.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MovieRecommendationClientTest {

    @Test
    void forwardsBearerRequestIdAndAllowlistedFilters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MovieRecommendationClient client = new MovieRecommendationClient(
                builder, new MovieRecommendationProperties("http://recommendation.test", 2_000, 2_000));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("jwt-token");
        headers.set("X-Request-Id", "request-123");
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("genre", "Science Fiction");
        query.add("size", "20");
        query.add("userId", "must-not-forward");

        server.expect(requestTo("http://recommendation.test/api/v1/recommendations/me?genre=Science%20Fiction&size=20"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-token"))
                .andExpect(header("X-Request-Id", "request-123"))
                .andRespond(withStatus(HttpStatus.OK).body("{\"items\":[]}"));

        ResponseEntity<String> response = client.fetch(headers, query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"items\":[]}");
        server.verify();
    }

    @Test
    void preservesDownstreamValidationResponses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MovieRecommendationClient client = new MovieRecommendationClient(
                builder, new MovieRecommendationProperties("http://recommendation.test", 2_000, 2_000));
        server.expect(requestTo("http://recommendation.test/api/v1/recommendations/me"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":\"VALIDATION_FAILED\"}"));

        ResponseEntity<String> response = client.fetch(new HttpHeaders(), new LinkedMultiValueMap<>());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED");
        server.verify();
    }

    @Test
    void convertsDownstreamServerFailureToUnavailableException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MovieRecommendationClient client = new MovieRecommendationClient(
                builder, new MovieRecommendationProperties("http://recommendation.test", 2_000, 2_000));
        server.expect(requestTo("http://recommendation.test/api/v1/recommendations/me"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.fetch(new HttpHeaders(), new LinkedMultiValueMap<>()))
                .isInstanceOf(RecommendationUnavailableException.class);
        server.verify();
    }
}
