package com.neo4flix.user.rating;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RatingHistoryClientTest {

    @Test
    void forwardsIdentityAndRequestIdToRatingService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://rating.test/api/v1/ratings/me?page=1&size=10"))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(header("X-Request-Id", "request-42"))
                .andRespond(withSuccess("""
                        {"content":[],"page":1,"size":10,"totalElements":0,"totalPages":0}
                        """, MediaType.APPLICATION_JSON));

        RatingHistoryDtos.PageResponse response = new RatingHistoryClient(builder, "http://rating.test")
                .findMine("access-token", "request-42", 1, 10);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isZero();
        server.verify();
    }
}
