package com.neo4flix.platform.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void preservesValidRequestIdInResponseRequestAndMdcDuringChain() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request-42");
        var response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(MDC.get("requestId")).isEqualTo("request-42");

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-42");
        assertThat(request.getAttribute("neo4flix.requestId")).isEqualTo("request-42");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesInvalidRequestIdWithUuid() {
        assertThat(RequestId.resolve("bad id with spaces")).matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesMissingRequestIdWithUuid() {
        assertThat(RequestId.resolve(null)).matches("[0-9a-f-]{36}");
    }
}
