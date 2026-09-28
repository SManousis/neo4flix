CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (n:User) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT user_normalized_email_unique IF NOT EXISTS FOR (n:User) REQUIRE n.normalizedEmail IS UNIQUE;
CREATE CONSTRAINT movie_id_unique IF NOT EXISTS FOR (n:Movie) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT genre_id_unique IF NOT EXISTS FOR (n:Genre) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT genre_normalized_name_unique IF NOT EXISTS FOR (n:Genre) REQUIRE n.normalizedName IS UNIQUE;
