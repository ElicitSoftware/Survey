package com.elicitsoftware.flyway;

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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link ManualSchemaMigrator}'s upgrade branch — the one path in
 * research/Kimball_type_2.md's "Migration Strategy for Existing Data" that no other test
 * in the suite touches. Every {@code @QuarkusTest} in this project boots a genuinely fresh
 * Testcontainers Postgres (see {@code PostgresTestResource}), so {@code ManualSchemaMigrator}
 * always takes the greenfield {@code db/migration} branch before any test method gets to
 * run — {@code db/migration-v3} (the ALTER-based upgrade path for existing v2.x databases)
 * has had zero automated coverage.
 * <p>
 * This is a plain JUnit test, not {@code @QuarkusTest} — {@code ManualSchemaMigrator} calls
 * the Flyway Java API directly (see its own header comment on why Quarkus's build-time
 * classpath-location config can't be mutated at runtime), so its exact branching logic can
 * be replicated here against a throwaway container without needing CDI at all.
 * <p>
 * Seeds a "v2.x" pre-Kimball database by running only {@code db/migration-v3}'s V001-V009 —
 * those are verbatim copies of the original pre-Kimball V001-V009 (see V010's own header
 * comment in that location) — then drives {@code ManualSchemaMigrator}'s exact sequence:
 * validate against {@code db/migration} (must fail, since its V001 now has Kimball baked in
 * and therefore a different checksum), migrate via {@code db/migration-v3} (applies V010),
 * repair against {@code db/migration}, then validate against {@code db/migration} again
 * (must now succeed, proving every future boot converges there directly).
 */
class ManualSchemaMigratorUpgradeTest {

    private static final String PASSWORD = "SURVEYPW";
    private static final String OWNER_USER = "elicit_owner";

    private PostgreSQLContainer container;

    @BeforeEach
    void startContainer() {
        container = new PostgreSQLContainer("postgres:18")
                .withDatabaseName("survey")
                .withInitScript("db/testcontainers-init.sql");
        container.start();
    }

    @AfterEach
    void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void upgradeTrack_appliedToPreV010History_convergesOnGreenfieldSchema() throws SQLException {
        // Seed a "v2.x" pre-Kimball database: apply ONLY V001-V009 from the upgrade
        // location, matching how a real existing production database's Flyway history
        // looks before ManualSchemaMigrator's upgrade path has ever run against it.
        flywayFor("classpath:db/migration-v3", "9").migrate();

        // ManualSchemaMigrator.validatesCleanly(): db/migration's V001 now has Kimball baked
        // in from the start, so its checksum no longer matches this "v2.x" history's V001 —
        // this must fail validation, exactly like a real un-upgraded database would.
        assertThrows(FlywayValidateException.class,
                () -> flywayFor("classpath:db/migration", null).validate(),
                "A pre-V010 history must NOT validate cleanly against db/migration -- its V001 checksum "
                        + "differs now that Kimball is baked in");

        // ManualSchemaMigrator's upgrade branch: migrate via db/migration-v3 (applies V010,
        // the ALTER-based Kimball migration on top of the existing V001-V009 history), then
        // repair against db/migration so every future boot's validate() succeeds there directly.
        flywayFor("classpath:db/migration-v3", null).migrate();
        flywayFor("classpath:db/migration", null).repair();

        // This is the whole point of repair(): the NEXT boot must validate cleanly against
        // db/migration with no further routing through db/migration-v3.
        assertDoesNotThrow(() -> flywayFor("classpath:db/migration", null).validate(),
                "After repair(), db/migration must validate cleanly so every future boot uses it directly");

        assertKimballSchemaPresent();
    }

    private Flyway flywayFor(String location, String targetVersion) {
        Map<String, String> placeholders = Map.of(
                "survey_user", "survey_user",
                "surveyadmin_user", "surveyadmin_user",
                "surveyreport_user", "surveyreport_user");

        var configuration = Flyway.configure()
                .dataSource(container.getJdbcUrl(), OWNER_USER, PASSWORD)
                .schemas("survey")
                .table("flyway_history")
                .locations(location)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .baselineDescription("Empty Database")
                .validateOnMigrate(true)
                .connectRetries(10)
                .placeholders(placeholders);

        if (targetVersion != null) {
            configuration = configuration.target(targetVersion);
        }
        return configuration.load();
    }

    private void assertKimballSchemaPresent() throws SQLException {
        try (Connection conn = DriverManager.getConnection(container.getJdbcUrl(), OWNER_USER, PASSWORD)) {
            assertHasColumns(conn, "questions", "question_id", "version", "effective_from", "effective_to",
                    "is_draft", "published_by", "published_comment", "select_group_version");
            assertHasColumns(conn, "select_groups", "select_group_id", "version", "effective_from", "effective_to");
            assertHasColumns(conn, "select_items", "select_item_id", "select_group_id", "select_group_version");
            assertHasColumns(conn, "sections", "section_id", "version", "effective_from", "effective_to");
            assertHasColumns(conn, "steps", "step_id", "version", "effective_from", "effective_to");
            assertHasColumns(conn, "sections_questions", "sections_question_id", "question_id", "section_id",
                    "question_version", "section_version");
            assertHasColumns(conn, "steps_sections", "steps_sections_id", "step_id", "section_id",
                    "step_version", "section_version");
            assertHasColumns(conn, "relationships", "relationship_id", "upstream_step_id", "upstream_sq_id",
                    "downstream_step_id", "downstream_ss_id", "downstream_sq_id");
            assertHasColumns(conn, "answers", "question_version");

            assertEquals(1, countColumn(conn, "surveyreport", "dim_step", "step_id"),
                    "surveyreport.dim_step must gain the durable step_id column via the upgrade path");
            assertEquals(1, countColumn(conn, "surveyreport", "dim_section", "section_id"),
                    "surveyreport.dim_section must gain the durable section_id column via the upgrade path");
        }
    }

    private void assertHasColumns(Connection conn, String table, String... columns) throws SQLException {
        for (String column : columns) {
            assertEquals(1, countColumn(conn, "survey", table, column),
                    "survey." + table + " must have column " + column + " after the upgrade path runs");
        }
    }

    private long countColumn(Connection conn, String schema, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
