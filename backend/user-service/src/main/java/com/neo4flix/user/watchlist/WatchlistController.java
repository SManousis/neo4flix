package com.neo4flix.user.watchlist;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/watchlist")
@Validated
public class WatchlistController {

    private final WatchlistApplicationService service;

    public WatchlistController(WatchlistApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public WatchlistDtos.PageResponse findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "24") @Min(1) @Max(100) int size) {
        return service.findMine(jwt.getSubject(), page, size);
    }

    @PostMapping("/{movieId}")
    public ResponseEntity<Void> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "movieId") String movieId) {
        return service.add(jwt.getSubject(), movieId)
                ? ResponseEntity.status(201).build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "movieId") String movieId) {
        service.remove(jwt.getSubject(), movieId);
        return ResponseEntity.noContent().build();
    }
}
