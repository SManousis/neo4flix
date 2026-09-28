package com.neo4flix.user.rating;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/ratings")
@Validated
public class RatingHistoryController {

    private final RatingHistoryClient client;

    public RatingHistoryController(RatingHistoryClient client) {
        this.client = client;
    }

    @GetMapping
    public RatingHistoryDtos.PageResponse findMine(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "24") @Min(1) @Max(100) int size) {
        return client.findMine(jwt.getTokenValue(), request.getHeader("X-Request-Id"), page, size);
    }
}
