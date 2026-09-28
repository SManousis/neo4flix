CREATE INDEX movie_title_index IF NOT EXISTS FOR (n:Movie) ON (n.normalizedTitle);
CREATE INDEX movie_release_year_index IF NOT EXISTS FOR (n:Movie) ON (n.releaseYear);
CREATE INDEX genre_name_index IF NOT EXISTS FOR (n:Genre) ON (n.name);
