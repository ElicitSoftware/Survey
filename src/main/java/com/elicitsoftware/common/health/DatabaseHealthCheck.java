package com.elicitsoftware.common.health;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.logging.Log;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    /** What a failed check answers, whatever the cause (NFR-014). */
    static final String DOWN = "Database connection failed";

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            // Simple connectivity test
            statement.execute("SELECT 1");
            
            return HealthCheckResponse.up("Database connection healthy");
        } catch (Exception e) {
            // NFR-014: the answer is a status and a fixed text; the cause is for the operator's log.
            Log.warn("Database health check failed", e);
            return HealthCheckResponse.down(DOWN);
        }
    }
}
