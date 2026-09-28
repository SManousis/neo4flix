package com.neo4flix.platform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException exception, HttpServletRequest request) {
        var requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        var traceId = requestId instanceof String value ? value : RequestId.resolve(null);
        return ProblemDetails.badRequest("VALIDATION_FAILED", traceId);
    }
}
