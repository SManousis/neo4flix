package com.neo4flix.recommendation.share;

import com.neo4flix.recommendation.persistence.RecommendationShareRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import static com.neo4flix.recommendation.share.RecommendationShareModels.*;

@Service
public final class RecommendationShareApplicationService {
    private static final int MAX_TOKEN_ATTEMPTS = 3;

    private final RecommendationShareRepository repository;
    private final RecommendationShareTokenService tokens;
    private final RecommendationShareProperties properties;
    private final Clock clock;

    @Autowired
    public RecommendationShareApplicationService(RecommendationShareRepository repository,
                                                  RecommendationShareTokenService tokens,
                                                  RecommendationShareProperties properties) {
        this(repository, tokens, properties, Clock.systemUTC());
    }

    public RecommendationShareApplicationService(RecommendationShareRepository repository,
                                                 RecommendationShareTokenService tokens,
                                                 RecommendationShareProperties properties,
                                                 Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreatedView create(String ownerId, CreateRequest request) {
        requireOwner(ownerId);
        Objects.requireNonNull(request, "request");
        String movieId = requireValue(request.movieId(), "movieId");
        int expiryDays = expiryDays(request.expiresInDays());
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plusSeconds(expiryDays * 86400L);
        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            RecommendationShareTokenService.IssuedToken issued = tokens.issue();
            try {
                OptionalOwner created = new OptionalOwner(repository.create(ownerId, movieId, UUID.randomUUID().toString(),
                        issued.hash(), createdAt, expiresAt));
                if (created.value().isPresent()) {
                    return toCreated(created.value().get(), issued.rawToken());
                }
            } catch (DataIntegrityViolationException collision) {
                // A unique token-hash collision is retriable; the next attempt gets a fresh token.
            }
        }
        throw new IllegalArgumentException("movie not found or share could not be created");
    }

    public List<OwnerView> list(String ownerId) {
        requireOwner(ownerId);
        return repository.listOwned(ownerId);
    }

    public OwnerView get(String ownerId, String shareId) {
        requireOwner(ownerId);
        return repository.findOwned(ownerId, requireValue(shareId, "shareId"))
                .orElseThrow(UnavailableShareException::new);
    }

    public OwnerView update(String ownerId, String shareId, UpdateRequest request) {
        requireOwner(ownerId);
        Objects.requireNonNull(request, "request");
        OwnerView existing = get(ownerId, shareId);
        if (existing.revoked() && !Boolean.TRUE.equals(request.revoke())) {
            throw new IllegalArgumentException("revoked shares cannot be reactivated");
        }
        Instant now = clock.instant();
        Instant expiresAt = request.expiresInDays() == null
                ? existing.expiresAt()
                : now.plusSeconds(expiryDays(request.expiresInDays()) * 86400L);
        return repository.updateOwned(ownerId, shareId, expiresAt, Boolean.TRUE.equals(request.revoke()), now)
                .orElseThrow(UnavailableShareException::new);
    }

    public void delete(String ownerId, String shareId) {
        requireOwner(ownerId);
        repository.deleteOwned(ownerId, requireValue(shareId, "shareId"));
    }

    public PublicView publicLookup(String rawToken) {
        String token = requireValue(rawToken, "publicToken");
        return repository.findPublic(tokens.hash(token), clock.instant())
                .orElseThrow(UnavailableShareException::new);
    }

    private int expiryDays(Integer requested) {
        int days = requested == null ? properties.defaultExpiryDays() : requested;
        if (days < properties.minExpiryDays() || days > properties.maxExpiryDays()) {
            throw new IllegalArgumentException("share expiry is out of range");
        }
        return days;
    }

    private static CreatedView toCreated(OwnerView ownerView, String rawToken) {
        return new CreatedView(ownerView.id(), ownerView.movieId(), rawToken, "/share/" + rawToken,
                ownerView.createdAt(), ownerView.expiresAt(), ownerView.revoked());
    }

    private static String requireOwner(String value) {
        return requireValue(value, "ownerId");
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private record OptionalOwner(java.util.Optional<OwnerView> value) {
    }
}
