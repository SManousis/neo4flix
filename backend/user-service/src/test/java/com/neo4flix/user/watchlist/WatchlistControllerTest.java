package com.neo4flix.user.watchlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WatchlistControllerTest {

    private final WatchlistApplicationService service = mock(WatchlistApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WatchlistController(service))
                .setCustomArgumentResolvers(new TestJwtResolver())
                .setControllerAdvice(new WatchlistApiExceptionHandler())
                .build();
    }

    @Test
    void listsOnlyTheAuthenticatedSubject() throws Exception {
        when(service.findMine("user-1", 0, 24)).thenReturn(new WatchlistDtos.PageResponse(
                List.of(new WatchlistDtos.MovieEntry("movie-1", "Arrival", "First contact", 2016, null,
                        Instant.parse("2026-09-14T00:00:00Z"))), 0, 24, 1, 1));

        mvc.perform(get("/api/v1/users/me/watchlist")
                        .header("X-Test-Subject", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movieId").value("movie-1"));
        verify(service).findMine("user-1", 0, 24);
    }

    @Test
    void addIsCreatedThenIdempotent() throws Exception {
        when(service.add("user-1", "movie-1")).thenReturn(true, false);

        mvc.perform(post("/api/v1/users/me/watchlist/movie-1").header("X-Test-Subject", "user-1"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/users/me/watchlist/movie-1").header("X-Test-Subject", "user-1"))
                .andExpect(status().isNoContent());
        verify(service, times(2)).add("user-1", "movie-1");
    }

    @Test
    void removeIsIdempotentAndSubjectBound() throws Exception {
        when(service.remove("user-1", "movie-1")).thenReturn(true, false);

        mvc.perform(delete("/api/v1/users/me/watchlist/movie-1").header("X-Test-Subject", "user-1"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/users/me/watchlist/movie-1").header("X-Test-Subject", "user-1"))
                .andExpect(status().isNoContent());
        verify(service, times(2)).remove("user-1", "movie-1");
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .subject(request.getHeader("X-Test-Subject"))
                    .build();
        }
    }
}
