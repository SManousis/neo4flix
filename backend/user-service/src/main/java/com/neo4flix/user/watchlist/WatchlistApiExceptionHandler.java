package com.neo4flix.user.watchlist;

import com.neo4flix.platform.common.web.RequestId;
import com.neo4flix.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice(assignableTypes = WatchlistController.class)
public class WatchlistApiExceptionHandler {

    @ExceptionHandler(WatchlistApplicationService.MovieNotFoundException.class)
    ProblemDetail movieNotFound(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND", "The requested movie is unavailable", request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        problem.setProperty("code", code);
        problem.setProperty("traceId", requestId instanceof String value
                ? value : RequestId.resolve(request.getHeader(RequestIdFilter.HEADER_NAME)));
        return problem;
    }
}
