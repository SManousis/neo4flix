package com.neo4flix.recommendation.share;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static com.neo4flix.recommendation.share.RecommendationShareModels.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationShareControllerTest {

    private final RecommendationShareApplicationService service = mock(RecommendationShareApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationShareController(service))
                .setCustomArgumentResolvers(new TestJwtResolver())
                .build();
    }

    @Test
    void createsOwnerShareFromJwtSubject() throws Exception {
        when(service.create("user-1", new CreateRequest("movie-1", 30)))
                .thenReturn(new CreatedView("share-1", "movie-1", "raw-token", "/share/raw-token",
                        Instant.parse("2026-09-15T12:00:00Z"), Instant.parse("2026-10-15T12:00:00Z"), false));

        mvc.perform(post("/api/v1/recommendation-shares")
                        .with(subject("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"movie-1\",\"expiresInDays\":30}"))
                .andExpect(status().isCreated());
    }

    @Test
    void servesPublicLookupWithoutAuthentication() throws Exception {
        when(service.publicLookup("raw-token")).thenReturn(new PublicView("share-1", "movie-1", null,
                new PublicMovie("movie-1", "Arrival", "First contact", 2016, null, null,
                        List.of(), 4.4, 10)));

        mvc.perform(get("/api/v1/shares/raw-token"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor subject(String subject) {
        return request -> {
            request.addHeader("X-Test-Subject", subject);
            return request;
        };
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            String subject = webRequest.getHeader("X-Test-Subject");
            return subject == null ? null : Jwt.withTokenValue("test").header("alg", "none").subject(subject).build();
        }
    }
}
