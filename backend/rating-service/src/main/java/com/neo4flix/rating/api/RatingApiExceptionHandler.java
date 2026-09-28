package com.neo4flix.rating.api;

import com.neo4flix.platform.common.web.RequestId;
import com.neo4flix.platform.common.web.RequestIdFilter;
import com.neo4flix.rating.persistence.RatingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RatingApiExceptionHandler {

    @ExceptionHandler(RatingRepository.DuplicateRatingException.class)
    ProblemDetail duplicate(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "RATING_ALREADY_EXISTS",
                "A rating already exists for this movie", request, Map.of());
    }

    @ExceptionHandler(RatingController.UnauthenticatedException.class)
    ProblemDetail unauthenticated(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "AUTHENTICATION_REQUIRED",
                "Authentication is required", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
                "One or more fields are invalid", request, fieldErrors);
    }

    @ExceptionHandler(RatingController.RatingNotFoundException.class)
    ProblemDetail notFound(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", "RATING_NOT_FOUND",
                "The requested rating does not exist", request, Map.of());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail,
                                         HttpServletRequest request, Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        problem.setProperty("code", code);
        problem.setProperty("traceId", requestId instanceof String value ? value : RequestId.resolve(null));
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }
}
