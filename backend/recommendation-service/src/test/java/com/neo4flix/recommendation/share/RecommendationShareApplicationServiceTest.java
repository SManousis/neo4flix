package com.neo4flix.recommendation.share;

import com.neo4flix.recommendation.persistence.RecommendationShareRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.neo4flix.recommendation.share.RecommendationShareModels.*;

class RecommendationShareApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private final RecommendationShareRepository repository = mock(RecommendationShareRepository.class);
    private final RecommendationShareApplicationService service = new RecommendationShareApplicationService(
            repository,
            new RecommendationShareTokenService(),
            RecommendationShareProperties.defaults(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsWithTheConfiguredDefaultExpiryAndReturnsRawTokenOnlyOnCreation() {
        when(repository.create(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Optional.of(new RecommendationShareModels.OwnerView(
                        invocation.getArgument(2), invocation.getArgument(1), NOW,
                        NOW.plusSeconds(30L * 86400), false)));

        RecommendationShareModels.CreatedView created = service.create(
                "owner-1", new RecommendationShareModels.CreateRequest("movie-1", null));

        assertThat(created.publicToken()).isNotBlank();
        assertThat(created.publicPath()).isEqualTo("/share/" + created.publicToken());
        assertThat(created.expiresAt()).isEqualTo(NOW.plusSeconds(30L * 86400));
        verify(repository).create(eq("owner-1"), eq("movie-1"), any(), any(), eq(NOW),
                eq(NOW.plusSeconds(30L * 86400)));
    }

    @Test
    void randomOrUnavailablePublicTokensResolveToTheSameUnavailableException() {
        when(repository.findPublic(any(), eq(NOW))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publicLookup("random-token"))
                .isInstanceOf(RecommendationShareModels.UnavailableShareException.class);
        verify(repository).findPublic(any(), eq(NOW));
    }

    @Test
    void retriesAUniqueTokenCollision() {
        OwnerView owner = new OwnerView("share-1", "movie-1", NOW, NOW.plusSeconds(30L * 86400), false);
        when(repository.create(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate token hash"))
                .thenReturn(Optional.of(owner));

        assertThat(service.create("owner-1", new CreateRequest("movie-1", null)).id()).isEqualTo("share-1");
        verify(repository, org.mockito.Mockito.times(2)).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updatesExpiryAndRevokesOnlyAnOwnedShare() {
        OwnerView existing = new OwnerView("share-1", "movie-1", NOW, NOW.plusSeconds(30L * 86400), false);
        OwnerView updated = new OwnerView("share-1", "movie-1", NOW, NOW.plusSeconds(60L * 86400), true);
        when(repository.findOwned("owner-1", "share-1")).thenReturn(Optional.of(existing));
        when(repository.updateOwned(eq("owner-1"), eq("share-1"), any(), eq(true), eq(NOW)))
                .thenReturn(Optional.of(updated));

        OwnerView result = service.update("owner-1", "share-1", new UpdateRequest(60, true));

        assertThat(result).isEqualTo(updated);
        verify(repository).updateOwned("owner-1", "share-1", NOW.plusSeconds(60L * 86400), true, NOW);
    }

    @Test
    void revokeOnlyUpdatePreservesTheExistingExpiry() {
        Instant existingExpiry = NOW.plusSeconds(30L * 86400);
        OwnerView existing = new OwnerView("share-1", "movie-1", NOW, existingExpiry, false);
        OwnerView updated = new OwnerView("share-1", "movie-1", NOW, existingExpiry, true);
        when(repository.findOwned("owner-1", "share-1")).thenReturn(Optional.of(existing));
        when(repository.updateOwned("owner-1", "share-1", existingExpiry, true, NOW))
                .thenReturn(Optional.of(updated));

        assertThat(service.update("owner-1", "share-1", new UpdateRequest(null, true))).isEqualTo(updated);
        verify(repository).updateOwned("owner-1", "share-1", existingExpiry, true, NOW);
    }

    @Test
    void listsAndDeletesOnlyThroughOwnerBoundRepositoryMethods() {
        OwnerView own = new OwnerView("share-1", "movie-1", NOW, NOW.plusSeconds(30L * 86400), false);
        when(repository.listOwned("owner-1")).thenReturn(List.of(own));

        assertThat(service.list("owner-1")).containsExactly(own);
        service.delete("owner-1", "share-1");

        verify(repository).deleteOwned("owner-1", "share-1");
    }
}
