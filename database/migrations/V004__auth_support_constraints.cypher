CREATE CONSTRAINT auth_session_id_unique IF NOT EXISTS FOR (n:AuthSession) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT auth_challenge_id_unique IF NOT EXISTS FOR (n:AuthChallenge) REQUIRE n.id IS UNIQUE;
