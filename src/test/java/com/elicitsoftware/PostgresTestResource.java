package com.elicitsoftware;

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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

/**
 * Spins up a throwaway Postgres container for the test run and points both the
 * {@code default} ({@code survey_user}) and {@code owner} ({@code elicit_owner}) datasources
 * at it. Replaces the previous fixed, manually-provisioned {@code survey_test} database
 * (see {@code CONTRIBUTING.md}) so every test run starts from a clean, identical database —
 * no state can leak in from a prior run.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String PASSWORD = "SURVEYPW";

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer("postgres:18")
                .withDatabaseName("survey")
                .withInitScript("db/testcontainers-init.sql");
        container.start();

        String jdbcUrl = container.getJdbcUrl();

        return Map.of(
                "quarkus.datasource.jdbc.url", jdbcUrl,
                "quarkus.datasource.username", "survey_user",
                "quarkus.datasource.password", PASSWORD,
                "quarkus.datasource.owner.jdbc.url", jdbcUrl,
                "quarkus.datasource.owner.username", "elicit_owner",
                "quarkus.datasource.owner.password", PASSWORD);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
