package com.neo4flix.movie.catalog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogModelsTest {
    @Test
    void acceptsHttpsPosterUrlsAndExplicitLocalDevelopmentExceptions() {
        assertThatCode(() -> movie("https://cdn.example/poster.jpg")).doesNotThrowAnyException();
        assertThatCode(() -> movie("http://localhost:4200/poster.jpg")).doesNotThrowAnyException();
        assertThatCode(() -> movie("http://127.0.0.1:8080/poster.jpg")).doesNotThrowAnyException();
    }

    @Test
    void rejectsDangerousOrAmbiguousPosterSchemes() {
        for (String value : List.of("javascript:alert(1)", "file:///etc/passwd", "data:text/html,evil", "http://evil.example/poster.jpg")) {
            assertThatThrownBy(() -> movie(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static CatalogModels.MovieWrite movie(String posterUrl) {
        return new CatalogModels.MovieWrite("Title", "Overview", 2020, LocalDate.of(2020, 1, 1), 100,
                posterUrl, null, null, List.of());
    }
}
