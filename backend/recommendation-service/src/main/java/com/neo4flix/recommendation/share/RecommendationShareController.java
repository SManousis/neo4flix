package com.neo4flix.recommendation.share;

import com.neo4flix.platform.common.security.JwtClaims;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Objects;

import static com.neo4flix.recommendation.share.RecommendationShareModels.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class RecommendationShareController {
    private final RecommendationShareApplicationService service;

    public RecommendationShareController(RecommendationShareApplicationService service) {
        this.service = service;
    }

    @PostMapping("/recommendation-shares")
    public ResponseEntity<CreatedView> create(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(201).body(service.create(subject(jwt), request));
    }

    @GetMapping("/recommendation-shares")
    public List<OwnerView> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(subject(jwt));
    }

    @GetMapping("/recommendation-shares/{id}")
    public OwnerView get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String id) {
        return service.get(subject(jwt), id);
    }

    @PatchMapping("/recommendation-shares/{id}")
    public OwnerView update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String id,
                            @Valid @RequestBody UpdateRequest request) {
        return service.update(subject(jwt), id, request);
    }

    @DeleteMapping("/recommendation-shares/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String id) {
        service.delete(subject(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shares/{publicToken}")
    public PublicView publicLookup(@PathVariable("publicToken") String publicToken) {
        return service.publicLookup(publicToken);
    }

    private static String subject(Jwt jwt) {
        return new JwtClaims(Objects.requireNonNull(jwt, "authenticated JWT is required")).subject();
    }
}
