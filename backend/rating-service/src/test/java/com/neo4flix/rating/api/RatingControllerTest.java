package com.neo4flix.rating.api;

import com.neo4flix.rating.RatingApplicationService;
import com.neo4flix.rating.persistence.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RatingControllerTest {

    private final RatingApplicationService service = mock(RatingApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new RatingController(service))
                .setControllerAdvice(new RatingApiExceptionHandler())
                .setCustomArgumentResolvers(new TestJwtResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsForTheJwtSubjectAndReturnsCreated() throws Exception {
        RatingResponse response = new RatingResponse("movie-1", 5,
                Instant.parse("2026-09-14T10:00:00Z"), Instant.parse("2026-09-14T10:00:00Z"));
        when(service.create(eq("user-1"), any(RatingWriteRequest.class))).thenReturn(response);

        mvc.perform(post("/api/v1/ratings")
                        .header("X-Test-Subject", "user-1")
                        .contentType("application/json")
                        .content("{\"movieId\":\"movie-1\",\"score\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movieId").value("movie-1"))
                .andExpect(jsonPath("$.score").value(5));
        verify(service).create(eq("user-1"), eq(new RatingWriteRequest("movie-1", 5)));
    }

    @Test
    void rejectsMalformedScoreWithValidationProblem() throws Exception {
        mvc.perform(post("/api/v1/ratings")
                        .header("X-Test-Subject", "user-1")
                        .contentType("application/json")
                        .content("{\"movieId\":\"movie-1\",\"score\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void mapsDuplicateCreateAndMissingOwnRating() throws Exception {
        when(service.create(eq("user-1"), any(RatingWriteRequest.class)))
                .thenThrow(new RatingRepository.DuplicateRatingException("already exists", null));
        when(service.findOwn("user-1", "missing")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/ratings")
                        .header("X-Test-Subject", "user-1")
                        .contentType("application/json")
                        .content("{\"movieId\":\"movie-1\",\"score\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATING_ALREADY_EXISTS"));
        mvc.perform(get("/api/v1/ratings/missing")
                        .header("X-Test-Subject", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATING_NOT_FOUND"));
    }

    @Test
    void servesHistoryAndPublicSummary() throws Exception {
        when(service.findHistory("user-1", 0, 24))
                .thenReturn(new RatingPageResponse(List.of(), 0, 24, 0, 0));
        when(service.findSummary("movie-1"))
                .thenReturn(new RatingSummaryResponse("movie-1", null, 0));

        mvc.perform(get("/api/v1/ratings/me").header("X-Test-Subject", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/ratings/movies/movie-1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.ratingCount").value(0));
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            String subject = request.getHeader("X-Test-Subject");
            return subject == null ? null : Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject(subject)
                    .build();
        }
    }
}
