package com.neo4flix.user.user;

import com.neo4flix.user.auth.AuthApplicationService;
import com.neo4flix.user.security.JwtKeyConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Conditional(JwtKeyConfiguration.PrivateKeyConfigured.class)
public class AccountDeletionService {
    private final AuthApplicationService auth;
    private final Neo4jClient client;

    public AccountDeletionService(AuthApplicationService auth, Neo4jClient client) { this.auth = auth; this.client = client; }

    @Transactional
    public void delete(String subject, String password, String code) {
        String id = auth.reauthenticate(subject, password, code).id();
        client.query("""
                MATCH (u:User {id:$id})
                CALL (u) {
                  MATCH (u)-[:CREATED_SHARE]->(s:RecommendationShare)
                  DETACH DELETE s
                }
                CALL (u) {
                  MATCH (u)-[:HAS_SESSION]->(s:AuthSession)
                  DETACH DELETE s
                }
                CALL (u) {
                  MATCH (u)-[:HAS_AUTH_CHALLENGE]->(c:AuthChallenge)
                  DETACH DELETE c
                }
                DETACH DELETE u
                """).bind(id).to("id").run();
    }
}
