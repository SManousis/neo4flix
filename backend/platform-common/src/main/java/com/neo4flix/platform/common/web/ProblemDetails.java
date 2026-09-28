package com.neo4flix.platform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;

public final class ProblemDetails {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProblemDetails() {
    }

    public static void write(HttpServletResponse response, HttpServletRequest request,
                             HttpStatus status, String code, String detail) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        Object existing = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        String traceId = existing instanceof String value
                ? value : RequestId.resolve(request.getHeader(RequestIdFilter.HEADER_NAME));
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId);
        response.setHeader(RequestIdFilter.HEADER_NAME, traceId);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        var body = new LinkedHashMap<String, Object>();
        body.put("type", problem.getType());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance());
        body.putAll(problem.getProperties());
        JSON.writeValue(response.getWriter(), body);
    }

    public static ProblemDetail badRequest(String code, String requestId) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("traceId", requestId);
        return problemDetail;
    }
}
