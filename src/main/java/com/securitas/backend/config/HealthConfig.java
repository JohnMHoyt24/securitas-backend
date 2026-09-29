package com.securitas.backend.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's built-in Neo4j health indicator calls dbms.components(), which requires
 * the admin role. Aura doesn't allow granting admin to additional users (it stays reserved
 * for the bootstrap account), so this replaces it with a plain "RETURN 1" check that only
 * needs ordinary read access.
 */
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator neo4jConnectivityHealthIndicator(Driver driver) {
        return () -> {
            try (var session = driver.session()) {
                session.run("RETURN 1").consume();
                return Health.up().build();
            } catch (Exception e) {
                return Health.down(e).build();
            }
        };
    }
}
