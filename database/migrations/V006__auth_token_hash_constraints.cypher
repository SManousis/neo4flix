CREATE CONSTRAINT auth_session_refresh_hash_unique IF NOT EXISTS FOR (n:AuthSession) REQUIRE n.refreshTokenHash IS UNIQUE;
CREATE CONSTRAINT auth_challenge_token_hash_unique IF NOT EXISTS FOR (n:AuthChallenge) REQUIRE n.tokenHash IS UNIQUE;
