package com.neo4flix.movie.catalog;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.neo4flix.movie.catalog.CatalogModels.*;

public interface MovieCatalogRepository {
    String DELETE_MOVIE_QUERY = """
            MATCH (m:Movie {id:$id})
            OPTIONAL MATCH (s:RecommendationShare)-[:SHARES]->(m)
            WITH m, collect(s) AS shares
            FOREACH (share IN shares | DETACH DELETE share)
            DETACH DELETE m
            RETURN count(m) AS deleted
            """;

    PageResult<MovieSummary> search(MovieQuery query);
    Optional<MovieDetail> findMovie(String id);
    List<MovieSummary> findRelated(String id, int limit);
    List<GenreSummary> findGenres();
    MovieDetail createMovie(MovieWrite movie);
    Optional<MovieDetail> updateMovie(String id, MovieWrite movie);
    boolean deleteMovie(String id);
    GenreSummary createGenre(String name);
    Optional<GenreSummary> renameGenre(String id, String name);
    DeleteGenreResult deleteGenre(String id);

    record DeleteGenreResult(boolean deleted, boolean referenced) { }

    @Repository("movieCatalogRepository")
    class Neo4j implements MovieCatalogRepository {
        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) { this.client = client; }

        @Override
        public PageResult<MovieSummary> search(MovieQuery query) {
            Map<String, Object> params = new HashMap<>(query.parameters());
            List<String> predicates = new ArrayList<>();
            if (query.title() != null) { predicates.add("toLower(m.normalizedTitle) CONTAINS toLower($title)"); params.put("title", query.title()); }
            if (query.genre() != null) { predicates.add("EXISTS { MATCH (m)-[:IN_GENRE]->(:Genre {normalizedName: toLower($genre)}) }"); params.put("genre", query.genre()); }
            if (query.minYear() != null) { predicates.add("m.releaseYear >= $minYear"); params.put("minYear", query.minYear()); }
            if (query.maxYear() != null) { predicates.add("m.releaseYear <= $maxYear"); params.put("maxYear", query.maxYear()); }
            String where = predicates.isEmpty() ? "" : "WHERE " + String.join(" AND ", predicates);
            String order = query.sortCypher() + ("asc".equals(query.direction()) ? " ASC" : " DESC");
            String rows = "MATCH (m:Movie) " + where + " "
                    + "OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre) "
                    + "WITH m, collect(CASE WHEN g IS NULL THEN null ELSE {id:g.id, name:g.name} END) AS genres "
                    + "RETURN m AS movie, genres "
                    + "ORDER BY " + order + " SKIP " + ((long) query.page() * query.size()) + " LIMIT " + query.size();
            String count = "MATCH (m:Movie) " + where + " RETURN count(m) AS total";
            params.values().removeIf(java.util.Objects::isNull);
            List<MovieSummary> content = new ArrayList<>(client.query(rows).bindAll(params).fetchAs(MovieSummary.class)
                    .mappedBy((ts, record) -> mapSummary(record)).all());
            long total = client.query(count).bindAll(params).fetch().one()
                    .map(row -> ((Number) ((Map<?, ?>) row).get("total")).longValue()).orElse(0L);
            return new PageResult<>(content, query.page(), query.size(), total,
                    (int) Math.ceil((double) total / query.size()));
        }

        @Override
        public Optional<MovieDetail> findMovie(String id) {
            return client.query("MATCH (m:Movie {id:$id}) OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre) "
                            + "RETURN m AS movie, collect(CASE WHEN g IS NULL THEN null ELSE {id:g.id,name:g.name} END) AS genres")
                    .bind(id).to("id").fetchAs(MovieDetail.class)
                    .mappedBy((ts, record) -> mapDetail(record)).one();
        }

        @Override
        public List<MovieSummary> findRelated(String id, int limit) {
            String cypher = "MATCH (m:Movie {id:$id})-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(related:Movie) "
                    + "WHERE related.id <> $id WITH related, count(DISTINCT g) AS overlap "
                    + "OPTIONAL MATCH (related)-[:IN_GENRE]->(rg:Genre) "
                    + "WITH related, overlap, collect(CASE WHEN rg IS NULL THEN null ELSE {id:rg.id,name:rg.name} END) AS genres "
                    + "RETURN related AS movie, genres ORDER BY overlap DESC, related.normalizedTitle LIMIT $limit";
            return new ArrayList<>(client.query(cypher).bindAll(Map.of("id", id, "limit", Math.min(Math.max(limit, 1), 50)))
                    .fetchAs(MovieSummary.class).mappedBy((ts, record) -> mapSummary(record)).all());
        }

        @Override
        public List<GenreSummary> findGenres() {
            return new ArrayList<>(client.query("MATCH (g:Genre) RETURN g.id AS id, g.name AS name ORDER BY g.normalizedName")
                    .fetchAs(GenreSummary.class)
                    .mappedBy((ts, record) -> new GenreSummary(record.get("id").asString(), record.get("name").asString()))
                    .all());
        }

        @Override
        public MovieDetail createMovie(MovieWrite movie) {
            String id = UUID.randomUUID().toString();
            Instant now = Instant.now();
            client.query("CREATE (m:Movie {id:$id,title:$title,normalizedTitle:$normalizedTitle,overview:$overview,releaseYear:$releaseYear,releaseDate:$releaseDate,runtimeMinutes:$runtimeMinutes,posterUrl:$posterUrl,externalSource:$externalSource,externalId:$externalId,createdAt:$createdAt,updatedAt:$updatedAt})")
                    .bindAll(movieProperties(id, movie, now.atZone(ZoneOffset.UTC))).run();
            replaceGenres(id, movie.genreIds());
            return findMovie(id).orElseThrow();
        }

        @Override
        public Optional<MovieDetail> updateMovie(String id, MovieWrite movie) {
            long updated = client.query("MATCH (m:Movie {id:$id}) SET m.title=$title,m.normalizedTitle=$normalizedTitle,m.overview=$overview,m.releaseYear=$releaseYear,m.releaseDate=$releaseDate,m.runtimeMinutes=$runtimeMinutes,m.posterUrl=$posterUrl,m.externalSource=$externalSource,m.externalId=$externalId,m.updatedAt=$updatedAt RETURN count(m) AS updated")
                    .bindAll(movieProperties(id, movie, Instant.now().atZone(ZoneOffset.UTC))).fetchAs(Long.class).mappedBy((ts, r) -> r.get("updated").asLong()).one().orElse(0L);
            if (updated == 0) return Optional.empty();
            replaceGenres(id, movie.genreIds());
            return findMovie(id);
        }

        @Override public boolean deleteMovie(String id) {
            return client.query(DELETE_MOVIE_QUERY).bind(id).to("id").fetchAs(Long.class).mappedBy((ts, r) -> r.get("deleted").asLong()).one().orElse(0L) == 1;
        }

        @Override public GenreSummary createGenre(String name) {
            String id = UUID.randomUUID().toString();
            client.query("CREATE (g:Genre {id:$id,name:$name,normalizedName:$normalizedName,createdAt:$now,updatedAt:$now})")
                    .bindAll(Map.of("id", id, "name", name.trim(), "normalizedName", name.trim().toLowerCase(), "now", Instant.now().atZone(ZoneOffset.UTC))).run();
            return new GenreSummary(id, name.trim());
        }

        @Override public Optional<GenreSummary> renameGenre(String id, String name) {
            return client.query("MATCH (g:Genre {id:$id}) SET g.name=$name,g.normalizedName=$normalizedName,g.updatedAt=$now RETURN g.id AS id,g.name AS name")
                    .bindAll(Map.of("id", id, "name", name.trim(), "normalizedName", name.trim().toLowerCase(), "now", Instant.now().atZone(ZoneOffset.UTC))).fetchAs(GenreSummary.class)
                    .mappedBy((ts, r) -> new GenreSummary(r.get("id").asString(), r.get("name").asString())).one();
        }

        @Override public DeleteGenreResult deleteGenre(String id) {
            long references = client.query("MATCH (:Movie)-[r:IN_GENRE]->(:Genre {id:$id}) RETURN count(r) AS references").bind(id).to("id").fetchAs(Long.class).mappedBy((ts, r) -> r.get("references").asLong()).one().orElse(0L);
            if (references > 0) return new DeleteGenreResult(false, true);
            boolean deleted = client.query("MATCH (g:Genre {id:$id}) DELETE g RETURN count(g) AS deleted").bind(id).to("id").fetchAs(Long.class).mappedBy((ts, r) -> r.get("deleted").asLong()).one().orElse(0L) == 1;
            return new DeleteGenreResult(deleted, false);
        }

        private void replaceGenres(String movieId, List<String> genreIds) {
            client.query("MATCH (m:Movie {id:$id})-[r:IN_GENRE]->() DELETE r").bind(movieId).to("id").run();
            if (genreIds == null || genreIds.isEmpty()) return;
            client.query("MATCH (m:Movie {id:$movieId}) MATCH (g:Genre) WHERE g.id IN $genreIds MERGE (m)-[:IN_GENRE]->(g)")
                    .bindAll(Map.of("movieId", movieId, "genreIds", genreIds)).run();
        }

        private static Map<String, Object> movieProperties(String id, MovieWrite movie, Object timestamp) {
            Map<String, Object> values = new HashMap<>();
            values.put("id", id);
            values.put("title", movie.title());
            values.put("normalizedTitle", movie.title().trim().toLowerCase());
            values.put("overview", movie.overview());
            values.put("releaseYear", movie.releaseYear());
            values.put("releaseDate", movie.releaseDate());
            values.put("runtimeMinutes", movie.runtimeMinutes());
            values.put("posterUrl", movie.posterUrl());
            values.put("externalSource", movie.externalSource());
            values.put("externalId", movie.externalId());
            values.put("createdAt", timestamp);
            values.put("updatedAt", timestamp);
            return values;
        }

        private static MovieSummary mapSummary(Record record) {
            Value movie = record.get("movie");
            return new MovieSummary(movie.get("id").asString(), movie.get("title").asString(), movie.get("overview").asString(), movie.get("releaseYear").asInt(), nullableDate(movie, "releaseDate"), nullableString(movie, "posterUrl"), mapGenres(record.get("genres")), 0, 0);
        }

        private static MovieDetail mapDetail(Record record) {
            Value movie = record.get("movie");
            return new MovieDetail(movie.get("id").asString(), movie.get("title").asString(), movie.get("overview").asString(), movie.get("releaseYear").asInt(), nullableDate(movie, "releaseDate"), movie.get("runtimeMinutes").isNull() ? null : movie.get("runtimeMinutes").asInt(), nullableString(movie, "posterUrl"), nullableString(movie, "externalSource"), nullableString(movie, "externalId"), movie.get("createdAt").asZonedDateTime().toInstant(), movie.get("updatedAt").asZonedDateTime().toInstant(), mapGenres(record.get("genres")), 0, 0);
        }

        private static List<GenreSummary> mapGenres(Value value) {
            List<GenreSummary> result = new ArrayList<>();
            for (Value item : value.values()) if (!item.isNull()) result.add(new GenreSummary(item.get("id").asString(), item.get("name").asString()));
            return result;
        }
        private static String nullableString(Value value, String key) { return value.get(key).isNull() ? null : value.get(key).asString(); }
        private static LocalDate nullableDate(Value value, String key) { return value.get(key).isNull() ? null : value.get(key).asLocalDate(); }
    }
}
