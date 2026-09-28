package com.neo4flix.user.rating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RatingHistoryControllerTest {

    private final RatingHistoryClient client = mock(RatingHistoryClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RatingHistoryController(client))
                .setCustomArgumentResolvers(new TestJwtResolver())
                .build();
    }

    @Test
    void exposesAuthenticatedHistoryFacade() throws Exception {
        when(client.findMine("access-token", "request-42", 0, 24))
                .thenReturn(new RatingHistoryDtos.PageResponse(java.util.List.of(), 0, 24, 0, 0));

        mvc.perform(get("/api/v1/users/me/ratings")
                        .header("X-Test-Subject", "user-1")
                        .header("X-Test-Token", "access-token")
                        .header("X-Request-Id", "request-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(client).findMine("access-token", "request-42", 0, 24);
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue(request.getHeader("X-Test-Token"))
                    .header("alg", "none")
                    .subject(request.getHeader("X-Test-Subject"))
                    .build();
        }
    }
}
