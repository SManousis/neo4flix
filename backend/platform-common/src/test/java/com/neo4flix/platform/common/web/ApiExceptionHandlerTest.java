package com.neo4flix.platform.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void buildsSafeBadRequestProblemDetail() {
        var problemDetail = ProblemDetails.badRequest("VALIDATION_FAILED", "request-42");

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
        assertThat(problemDetail.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("traceId", "request-42")
                .doesNotContainKey("detail");
    }

    @Test
    void mapsIllegalArgumentExceptionToSafeBadRequestProblemDetail() {
        var request = new MockHttpServletRequest();
        request.setAttribute("neo4flix.requestId", "request-42");

        var problemDetail = new ApiExceptionHandler()
                .handleIllegalArgumentException(new IllegalArgumentException("do not disclose me"), request);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
        assertThat(problemDetail.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("traceId", "request-42")
                .doesNotContainKey("detail");
        assertThat(problemDetail.getDetail()).isNull();
    }

    @Test
    void suppliesRequestIdWhenExceptionRequestHasNoAttribute() {
        var problemDetail = new ApiExceptionHandler()
                .handleIllegalArgumentException(new IllegalArgumentException("do not disclose me"), new MockHttpServletRequest());

        assertThat(problemDetail.getProperties().get("traceId").toString()).matches("[0-9a-f-]{36}");
    }
}
