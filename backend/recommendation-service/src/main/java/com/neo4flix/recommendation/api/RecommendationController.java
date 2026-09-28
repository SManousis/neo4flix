package com.neo4flix.recommendation.api;

import com.neo4flix.platform.common.security.JwtClaims;
import com.neo4flix.recommendation.core.RecommendationApplicationService;
import com.neo4flix.recommendation.core.RecommendationDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.neo4flix.recommendation.api.RecommendationApiModels.*;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private static final int MAX_CANDIDATES = 50;
    private static final int DEFAULT_SIZE = 20;

    private final RecommendationApplicationService service;

    public RecommendationController(RecommendationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public Response recommendHttp(
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "fromYear", required = false) String fromYear,
            @RequestParam(value = "toYear", required = false) String toYear,
            @RequestParam(value = "minimumAverageRating", required = false) String minimumAverageRating,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size,
            @AuthenticationPrincipal Jwt jwt) {
        return recommend(new QueryParams(genre, parseInteger("fromYear", fromYear), parseInteger("toYear", toYear),
                parseDouble("minimumAverageRating", minimumAverageRating), sort,
                parseInteger("page", page), parseInteger("size", size)), jwt);
    }

    public Response recommend(QueryParams params, Jwt jwt) {
        QueryParams normalized = normalize(params);
        String userId = new JwtClaims(Objects.requireNonNull(jwt, "authenticated JWT is required")).subject();
        List<RecommendationDtos.Result> ranked = service.recommend(new RecommendationDtos.Query(
                userId, MAX_CANDIDATES, 2, 10, MAX_CANDIDATES, normalized.genre(), normalized.fromYear(),
                normalized.toYear(), normalized.minimumAverageRating()));
        List<RecommendationDtos.Result> sorted = ranked.stream()
                .sorted(comparator(normalized.sort()))
                .toList();
        int from = Math.min((long) normalized.page() * normalized.size() > Integer.MAX_VALUE
                ? sorted.size() : normalized.page() * normalized.size(), sorted.size());
        int to = Math.min(from + normalized.size(), sorted.size());
        List<Item> items = sorted.subList(from, to).stream().map(RecommendationController::mapItem).toList();
        RecommendationDtos.Strategy strategy = sorted.isEmpty()
                ? RecommendationDtos.Strategy.POPULARITY : sorted.getFirst().strategy();
        return new Response(items, strategy, normalized.page(), normalized.size(), sorted.size(),
                (int) Math.ceil((double) sorted.size() / normalized.size()));
    }

    private static Integer parseInteger(String name, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static Double parseDouble(String name, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a number", exception);
        }
    }

    static QueryParams normalize(QueryParams params) {
        QueryParams value = params == null ? new QueryParams(null, null, null, null, null, null, null) : params;
        String genre = value.genre() == null || value.genre().isBlank() ? null : value.genre().trim();
        if (genre != null && genre.length() > 80) throw new IllegalArgumentException("genre is too long");
        if (value.fromYear() != null && (value.fromYear() < 1888 || value.fromYear() > 2200)) {
            throw new IllegalArgumentException("fromYear is out of range");
        }
        if (value.toYear() != null && (value.toYear() < 1888 || value.toYear() > 2200)) {
            throw new IllegalArgumentException("toYear is out of range");
        }
        if (value.fromYear() != null && value.toYear() != null && value.fromYear() > value.toYear()) {
            throw new IllegalArgumentException("fromYear must not exceed toYear");
        }
        if (value.minimumAverageRating() != null
                && (!Double.isFinite(value.minimumAverageRating())
                || value.minimumAverageRating() < 0 || value.minimumAverageRating() > 5)) {
            throw new IllegalArgumentException("minimumAverageRating is out of range");
        }
        String sort = value.sort() == null || value.sort().isBlank()
                ? "recommendation" : value.sort().trim().toLowerCase(Locale.ROOT);
        if (!List.of("recommendation", "rating", "newest").contains(sort)) {
            throw new IllegalArgumentException("sort is not supported");
        }
        int page = value.page() == null ? 0 : value.page();
        int size = value.size() == null ? DEFAULT_SIZE : value.size();
        if (page < 0 || page > 10_000) throw new IllegalArgumentException("page is out of range");
        if (size < 1 || size > MAX_CANDIDATES) throw new IllegalArgumentException("size is out of range");
        return new QueryParams(genre, value.fromYear(), value.toYear(), value.minimumAverageRating(), sort, page, size);
    }

    private static Comparator<RecommendationDtos.Result> comparator(String sort) {
        Comparator<RecommendationDtos.Result> base = switch (sort) {
            case "rating" -> Comparator.comparingDouble(RecommendationDtos.Result::averageRating).reversed();
            case "newest" -> Comparator.comparing(RecommendationDtos.Result::releaseYear,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparingDouble(RecommendationDtos.Result::score).reversed();
        };
        return base.thenComparing(RecommendationDtos.Result::movieId);
    }

    private static Item mapItem(RecommendationDtos.Result result) {
        List<String> names = result.genres();
        List<String> ids = result.genreIds();
        List<Genre> genres = java.util.stream.IntStream.range(0, names.size())
                .mapToObj(index -> new Genre(index < ids.size() ? ids.get(index) : null, names.get(index)))
                .toList();
        return new Item(
                new MovieSummary(result.movieId(), result.title(), result.overview(), result.releaseYear(),
                        result.releaseDate(), result.posterUrl(), genres, result.averageRating(), result.ratingCount()),
                result.score(),
                new Signals(result.collaborativeScore(), result.contentScore(), result.popularityScore()),
                result.strategy(),
                new Reason(reasonType(result.reason()), result.reason()));
    }

    private static ReasonType reasonType(String reason) {
        if (reason != null && reason.startsWith("Users with similar ratings")) return ReasonType.SIMILAR_USERS;
        if (reason != null && reason.startsWith("Matches")) return ReasonType.GENRE_MATCH;
        return ReasonType.POPULAR;
    }
}
