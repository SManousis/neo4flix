package com.neo4flix.rating.api;

import com.neo4flix.rating.RatingApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/ratings")
@Validated
public class RatingController {

    private final RatingApplicationService service;

    public RatingController(RatingApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RatingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RatingWriteRequest request) {
        return ResponseEntity.status(201).body(service.create(subject(jwt), request));
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<RatingResponse> findOwn(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "movieId") String movieId) {
        return ResponseEntity.ok(service.findOwn(subject(jwt), movieId)
                .orElseThrow(RatingNotFoundException::new));
    }

    @PutMapping("/{movieId}")
    public ResponseEntity<RatingResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "movieId") String movieId,
            @Valid @RequestBody RatingUpdateRequest request) {
        return ResponseEntity.ok(service.updateOwn(subject(jwt), movieId, request.score())
                .orElseThrow(RatingNotFoundException::new));
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "movieId") String movieId) {
        service.deleteOwn(subject(jwt), movieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public RatingPageResponse history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "24") @Min(1) @Max(100) int size) {
        return service.findHistory(subject(jwt), page, size);
    }

    @GetMapping("/movies/{movieId}/summary")
    public RatingSummaryResponse summary(@PathVariable(name = "movieId") String movieId) {
        return service.findSummary(movieId);
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new UnauthenticatedException();
        }
        return jwt.getSubject();
    }

    public static final class UnauthenticatedException extends RuntimeException {
    }

    public static final class RatingNotFoundException extends RuntimeException {
    }
}
