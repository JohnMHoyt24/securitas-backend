package com.securitas.backend.graph;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Applies the graph's schema constraints on startup. A dedicated migration framework
 * (neo4j-migrations) would be preferable, but it requires executing dbms.components()
 * to detect the server version, which Aura does not allow for non-bootstrap users
 * (see plan Stage 2 notes). CREATE CONSTRAINT itself only needs ordinary schema-write
 * access, which the app's architect-role user has, so this runs fine.
 */
@Component
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private static final String CREATE_ACCOUNT_ID_CONSTRAINT =
            "CREATE CONSTRAINT account_id_unique IF NOT EXISTS FOR (a:Account) REQUIRE a.accountId IS UNIQUE";

    private final Neo4jClient neo4jClient;

    public Neo4jSchemaInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        neo4jClient.query(CREATE_ACCOUNT_ID_CONSTRAINT).run();
    }
}
