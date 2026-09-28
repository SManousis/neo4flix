package com.neo4flix.movie.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.neo4flix.movie.catalog.CatalogModels.*;

@RestController
@RequestMapping("/api/v1")
public class MovieCatalogController {
    private final MovieCatalogRepository repository;

    public MovieCatalogController(MovieCatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/movies")
    public PageResult<MovieSummary> movies(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "minYear", required = false) Integer minYear,
            @RequestParam(name = "maxYear", required = false) Integer maxYear,
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "desc") String direction,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size) {
        return repository.search(MovieQuery.from(title, genre, minYear, maxYear, fromDate, sort, direction, page, size));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<MovieDetail> movie(@PathVariable(name = "id") String id) {
        return ResponseEntity.of(repository.findMovie(id));
    }

    @GetMapping("/movies/{id}/related")
    public java.util.List<MovieSummary> related(@PathVariable(name = "id") String id,
                                                @RequestParam(name = "limit", defaultValue = "12") int limit) {
        return repository.findRelated(id, limit);
    }

    @GetMapping("/genres")
    public java.util.List<GenreSummary> genres() {
        return repository.findGenres();
    }

    @PostMapping("/movies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovieDetail> createMovie(@RequestBody MovieWrite movie) {
        return ResponseEntity.status(201).body(repository.createMovie(movie));
    }

    @PatchMapping("/movies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovieDetail> updateMovie(@PathVariable(name = "id") String id, @RequestBody MovieWrite movie) {
        return ResponseEntity.of(repository.updateMovie(id, movie));
    }

    @DeleteMapping("/movies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMovie(@PathVariable(name = "id") String id) {
        return repository.deleteMovie(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/genres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenreSummary> createGenre(@RequestParam(name = "name") String name) {
        return ResponseEntity.status(201).body(repository.createGenre(name));
    }

    @PatchMapping("/genres/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenreSummary> renameGenre(@PathVariable(name = "id") String id, @RequestParam(name = "name") String name) {
        return ResponseEntity.of(repository.renameGenre(id, name));
    }

    @DeleteMapping("/genres/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGenre(@PathVariable(name = "id") String id) {
        MovieCatalogRepository.DeleteGenreResult result = repository.deleteGenre(id);
        if (result.referenced()) return ResponseEntity.status(409).build();
        return result.deleted() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
