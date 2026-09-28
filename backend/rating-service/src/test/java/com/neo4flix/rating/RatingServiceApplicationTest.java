package com.neo4flix.rating;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.health.neo4j.enabled=false"}
)
@AutoConfigureTestRestTemplate
class RatingServiceApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUpAndIncludesRequestId() {
        var response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }
}
