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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Times {@code db/migration-v3}'s V010 (the ALTER-based Kimball Type 2 SCD migration) against
 * a synthetically large database, addressing the "Migration lock/duration risk" flagged in
 * research/Kimball_type_2.md's Implementation Readiness Notes: "Test the actual migration
 * against a production-sized data snapshot before committing to a single-transaction rollout;
 * batch it if it's too slow or holds locks too long."
 * <p>
 * <b>This is a synthetic proxy, not a substitute for testing against a real (anonymised)
 * production snapshot.</b> No such snapshot is available in this environment. The row counts
 * below are chosen to be large enough to surface an {@code O(n^2)} or full-table-rewrite
 * problem (Postgres rewrites the whole table for {@code ALTER COLUMN ... TYPE NUMERIC}, which
 * is exactly why {@code answers} — the table most likely to be large in a real deployment,
 * since it grows with respondent volume rather than survey complexity — gets the largest
 * synthetic volume here) without making this test impractically slow to run on demand.
 * Before a real production rollout, re-run an equivalent script against a real (anonymised)
 * snapshot or a synthetic dataset sized to match it, and adjust {@link #ANSWER_COUNT} etc. up
 * to match real scale.
 * <p>
 * <b>Measured (2026-09-12, local dev machine, Testcontainers postgres:18):</b> V010 completed
 * in ~2.1s against this dataset — no O(n^2) blowup or excessive lock hold time observed at
 * this scale. Re-measure and update this note if row counts or the migration itself change
 * significantly.
 * <p>
 * {@code @Disabled} by default — this seeds hundreds of thousands of rows and is too slow for
 * routine {@code mvn test} runs. Run explicitly on demand:
 * {@code ./mvnw -Dtest=ManualSchemaMigratorScaleTest -Dsurefire.failIfNoSpecifiedTests=false test}
 * (remove or comment out the {@code @Disabled} annotation first).
 */
@Disabled("Seeds hundreds of thousands of rows; run explicitly on demand, see class Javadoc")
class ManualSchemaMigratorScaleTest {

    private static final String PASSWORD = "SURVEYPW";
    private static final String OWNER_USER = "elicit_owner";

    // Structural tables stay small — real surveys rarely exceed a few thousand elements.
    private static final int QUESTION_COUNT = 3_000;
    // Respondent volume is the actual production risk factor for answers' size.
    private static final int RESPONDENT_COUNT = 20_000;
    private static final int ANSWER_COUNT = 300_000;

    // Generous but not unbounded — a real regression (e.g. an accidentally-quadratic backfill
    // join) should blow well past this on a synthetic dataset two orders of magnitude smaller
    // than a large real deployment; this is a smoke-scale guard, not a hard SLA.
    private static final Duration MAX_ACCEPTABLE_DURATION = Duration.ofMinutes(5);

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
    void upgradeMigration_atScale_completesInAReasonableTime() throws SQLException {
        // Seed a "v2.x" pre-Kimball schema (V001-V009 only), same as ManualSchemaMigratorUpgradeTest.
        flywayFor("classpath:db/migration-v3", "9").migrate();

        seedSyntheticData();

        Instant start = Instant.now();
        flywayFor("classpath:db/migration-v3", null).migrate();
        Duration elapsed = Duration.between(start, Instant.now());

        System.out.printf(
                "ManualSchemaMigratorScaleTest: V010 upgrade migration against %,d questions / "
                        + "%,d respondents / %,d answers took %d.%03ds%n",
                QUESTION_COUNT, RESPONDENT_COUNT, ANSWER_COUNT, elapsed.toSeconds(), elapsed.toMillisPart());

        assertTrue(elapsed.compareTo(MAX_ACCEPTABLE_DURATION) < 0,
                "V010 took " + elapsed + " against a synthetic dataset (" + ANSWER_COUNT + " answers) -- "
                        + "investigate before rolling out against a real production-sized database, "
                        + "and consider batching the offending ALTER/backfill statement");
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

    /**
     * Generates a synthetic survey, {@link #QUESTION_COUNT} questions/sections_questions rows,
     * {@link #RESPONDENT_COUNT} respondents, and {@link #ANSWER_COUNT} answers, entirely via
     * {@code generate_series}-driven bulk SQL (no row-by-row JDBC inserts) so the seeding step
     * itself doesn't dominate the timing.
     */
    private void seedSyntheticData() throws SQLException {
        try (Connection conn = DriverManager.getConnection(container.getJdbcUrl(), OWNER_USER, PASSWORD);
             Statement st = conn.createStatement()) {

            st.execute("INSERT INTO survey.surveys (id, name, display_order, title) "
                    + "VALUES (nextval('survey.surveys_seq'), 'ScaleTestSurvey', 1, 'Scale Test Survey')");

            st.execute("INSERT INTO survey.steps (id, survey_id, display_order, dimension_name) "
                    + "SELECT nextval('survey.steps_seq'), s.id, 1, 'scale_step' "
                    + "FROM survey.surveys s WHERE s.name = 'ScaleTestSurvey'");

            st.execute("INSERT INTO survey.sections (id, survey_id, display_order, dimension_name) "
                    + "SELECT nextval('survey.sections_seq'), s.id, 1, 'scale_section' "
                    + "FROM survey.surveys s WHERE s.name = 'ScaleTestSurvey'");

            st.execute("INSERT INTO survey.steps_sections "
                    + "(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) "
                    + "SELECT nextval('survey.steps_sections_seq'), su.id, st2.id, 1, se.id, 1, '0001-0001-0000-0001-0000-0000-0000' "
                    + "FROM survey.surveys su, survey.steps st2, survey.sections se "
                    + "WHERE su.name = 'ScaleTestSurvey' AND st2.survey_id = su.id AND se.survey_id = su.id");

            // QUESTION_COUNT questions, all referencing the one section via sections_questions.
            st.execute("INSERT INTO survey.questions (id, survey_id, type_id, text, required) "
                    + "SELECT nextval('survey.questions_seq'), su.id, "
                    + "       (SELECT id FROM survey.question_types WHERE name = 'TEXT'), "
                    + "       'Scale test question ' || gs, false "
                    + "FROM survey.surveys su, generate_series(1, " + QUESTION_COUNT + ") gs "
                    + "WHERE su.name = 'ScaleTestSurvey'");

            st.execute("INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) "
                    + "SELECT nextval('survey.sections_questions_seq'), q.survey_id, q.id, se.id, "
                    + "       row_number() OVER (ORDER BY q.id) "
                    + "FROM survey.questions q "
                    + "JOIN survey.surveys su ON su.id = q.survey_id AND su.name = 'ScaleTestSurvey' "
                    + "JOIN survey.sections se ON se.survey_id = su.id");

            // RESPONDENT_COUNT respondents against the same survey.
            st.execute("INSERT INTO survey.respondents (id, survey_id, token) "
                    + "SELECT nextval('survey.respondents_seq'), su.id, 'scale-token-' || gs "
                    + "FROM survey.surveys su, generate_series(1, " + RESPONDENT_COUNT + ") gs "
                    + "WHERE su.name = 'ScaleTestSurvey'");

            // ANSWER_COUNT answers spread evenly across respondents, all against the one
            // step/section -- this is the table the migration's NUMERIC conversion (a full
            // table rewrite in Postgres) and question_version backfill touch directly.
            st.execute("INSERT INTO survey.answers "
                    + "(id, survey_id, respondent_id, step, section, display_key, display_text) "
                    + "SELECT nextval('survey.answers_seq'), r.survey_id, r.id, "
                    + "       st2.id, se.id, 'GENSYN-' || gs, 'Scale test answer ' || gs "
                    + "FROM generate_series(1, " + ANSWER_COUNT + ") gs "
                    + "JOIN survey.respondents r ON r.id = (SELECT min(id) FROM survey.respondents) "
                    + "    + (gs % " + RESPONDENT_COUNT + "), "
                    + "     survey.steps st2, survey.sections se, survey.surveys su "
                    + "WHERE su.name = 'ScaleTestSurvey' AND st2.survey_id = su.id AND se.survey_id = su.id");
        }
    }
}
