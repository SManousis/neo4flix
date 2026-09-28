CREATE CONSTRAINT recommendation_share_id_unique IF NOT EXISTS FOR (n:RecommendationShare) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT recommendation_share_token_hash_unique IF NOT EXISTS FOR (n:RecommendationShare) REQUIRE n.publicTokenHash IS UNIQUE;
