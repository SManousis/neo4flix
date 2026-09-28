package com.neo4flix.user.auth;

import com.neo4flix.platform.common.web.RequestId;
import com.neo4flix.platform.common.web.RequestIdFilter;
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
public class AuthApiExceptionHandler {

    @ExceptionHandler(TotpAuthenticationService.AlreadyEnabledException.class)
    ProblemDetail twoFactorAlreadyEnabled(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already enabled", request, Map.of());
    }

    @ExceptionHandler(PasswordPolicy.PasswordPolicyViolationException.class)
    ProblemDetail invalidPassword(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
                "One or more fields are invalid", request,
                Map.of("password", "must satisfy the password policy"));
    }

    @ExceptionHandler(AuthApplicationService.DuplicateEmailException.class)
    ProblemDetail duplicateEmail(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "EMAIL_ALREADY_REGISTERED",
                "The requested account cannot be created", request, Map.of());
    }

    @ExceptionHandler({
            AuthApplicationService.InvalidCredentialsException.class,
            AuthApplicationService.InvalidRefreshTokenException.class
    })
    ProblemDetail authenticationFailed(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "AUTHENTICATION_FAILED",
                "Authentication failed", request, Map.of());
    }

    @ExceptionHandler(AuthApplicationService.UserNotFoundException.class)
    ProblemDetail userNotFound(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", "USER_NOT_FOUND",
                "The requested user is unavailable", request, Map.of());
    }

    @ExceptionHandler(AuthApplicationService.SecondFactorRequiredException.class)
    ProblemDetail secondFactorRequired(HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "SECOND_FACTOR_REQUIRED",
                "Second-factor verification is required", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
                "One or more fields are invalid", request, fieldErrors);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        problem.setProperty("traceId", requestId instanceof String value ? value : RequestId.resolve(null));
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }
}
