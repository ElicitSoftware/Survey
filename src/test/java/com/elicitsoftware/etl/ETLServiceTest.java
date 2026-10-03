package com.elicitsoftware.etl;

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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ETLService} against the Library fixture's own reporting schema (UC-008).
 * <p>
 * Driven by the V9005/V9005.5/V9005.6 "Library Card Registration" fixture (survey_id=1,
 * Tess Tester = respondent_id=1, already finalized). {@code ETLService.init()} is
 * {@code @Startup}, so by the time any test method runs every fixture survey has been built
 * once for the whole test JVM: the Library survey reports in {@value #SCHEMA} (BR-006), and
 * its dim_step/dim_section/dim_* tag tables/fact_sections are populated. These tests assert
 * against that state and confirm re-running the same operations is idempotent, rather than
 * assuming an empty starting point. The two-survey tests add a survey whose names collide
 * with the Library's and check it gets a schema of its own (BR-008, BR-009).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ETLServiceTest {

    @Inject
    ETLService etlService;

    @Inject
    EntityManager em;

    /**
     * Sql.CREATE_NEW_DIMENSION_TABLE_SQL only grants SELECT on a newly-created dim_*
     * table to REPORT_USER — the app's own default datasource (survey_user) never reads
     * these tables in production (ETLService/ETLRespondentService always use the "owner"
     * persistence unit). Reads against dynamically-created dim_* tables (and
     * information_schema lookups about them — Postgres hides catalog rows for objects
     * the connecting role has zero privilege on) must go through this EntityManager
     * instead of the plain one, matching what ETLService itself uses.
     */
    @PersistenceContext(unitName = "owner")
    EntityManager ownerEm;

    static final int SURVEY_ID = 1;
    /** UC-008 BR-006: "LibraryCardReg" lower-cased behind the prefix. */
    static final String SCHEMA = "report_librarycardreg";
    static final int TESS_RESPONDENT_ID = 1;
    static final int WELCOME_STEP_ID = 1;
    static final int WELCOME_SECTION_ID = 1;

    @SuppressWarnings("unchecked")
    private List<Object[]> nativeList(String sql, Object... params) {
        return nativeList(em, sql, params);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> nativeList(EntityManager entityManager, String sql, Object... params) {
        Query q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.getResultList();
    }

    private long nativeCount(String sql, Object... params) {
        return nativeCount(em, sql, params);
    }

    private long nativeCount(EntityManager entityManager, String sql, Object... params) {
        Query q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    /** Independently-derived expected fact_sections row count for a respondent, mirroring
     *  Sql.INSERT_MISSING_FACT_SECTION_SQL's own SELECT DISTINCT — an oracle rather than a
     *  hand-computed magic number, so it stays correct if the fixture ever grows. */
    private long expectedFactSectionTupleCount(int respondentId) {
        return nativeCount("""
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT a.step, a.step_instance, a.section, a.section_instance
                    FROM survey.answers a
                    JOIN survey.respondents r ON a.respondent_id = r.id
                    JOIN survey.steps s ON a.step = s.display_order AND s.survey_id = a.survey_id
                    WHERE a.deleted != true
                      AND a.text_value IS NOT NULL
                      AND a.saved_dt IS NOT NULL
                      AND r.finalized_dt IS NOT NULL
                      AND a.survey_id = ?1
                      AND a.respondent_id = ?2
                ) x
                """, SURVEY_ID, respondentId);
    }

    private Respondent createFreshUnfinalizedRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.accessCode = "etl_test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    // ── no-survey guard ─────────────────────────────────────────────────────

    /**
     * Ties {@link ETLService#countSurveys()} and {@link ETLService#listSurveys()} to
     * survey.surveys, so a schema change to that table surfaces here rather than silently
     * making {@code init()} build nothing.
     */
    @Test
    void countSurveysSeesTheFixtureSurvey() {
        long expected = nativeCount("SELECT COUNT(*) FROM survey.surveys");
        assertTrue(expected > 0, "fixture must install at least one survey");
        assertEquals(expected, etlService.countSurveys(),
                "countSurveys() must report the rows actually in survey.surveys");
        assertEquals(expected, etlService.listSurveys().size(),
                "listSurveys() must return every row of survey.surveys");
    }

    @Test
    // UC-008 BR-006: the startup build named the Library survey's schema after it and stored
    // the name on the survey; BR-007: the fixed tables and views are in that schema.
    void given_startupAlreadyRan_then_librarySurveyReportsInItsOwnSchema() {
        String schema = (String) em.createNativeQuery("SELECT report_schema FROM survey.surveys WHERE id = ?1")
                .setParameter(1, SURVEY_ID).getSingleResult();
        assertEquals(SCHEMA, schema);
        for (String table : List.of("dim_step", "dim_section", "dim_question", "dim_item", "fact_sections",
                "fact_respondents", "fact_respondents_view", "fact_sections_view")) {
            assertEquals(1, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?1 AND table_name = ?2", SCHEMA, table),
                    schema + "." + table + " must exist after the startup build");
        }
        assertEquals(0, nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'surveyreport' AND table_name = 'fact_sections'"),
                "the common schema holds no fact table any more");
    }

    /**
     * The javadoc's "the surrogate-keyed relationship this test pins" is history: BR-011 resolves
     * step_key through dim_step.step_id, so the assertion below checks the resolved step, not an
     * id coincidence.
     */
    @Test
    // UC-008 BR-011: a fact row's step_key and section_key are dimension ids, resolved as of the
    // respondent's anchor, so joining them back yields the step and section the answer was in.
    void given_tessWelcomeAnswer_when_populateFactSectionTable_then_keysResolveToWelcomeStepAndSection() {
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT ds.value, dsec.value FROM report_librarycardreg.fact_sections f "
                        + "JOIN report_librarycardreg.dim_step ds ON ds.id = f.step_key "
                        + "JOIN report_librarycardreg.dim_section dsec ON dsec.id = f.section_key "
                        + "WHERE f.respondent_id = ?1 AND f.name = 'Welcome'")
                .setParameter(1, TESS_RESPONDENT_ID).getResultList();
        assertFalse(rows.isEmpty(), "Tess has a Welcome fact row");
        assertEquals("Welcome", rows.get(0)[0], "step_key resolves to the Welcome step's dimension");
        assertEquals("Welcome", rows.get(0)[1], "section_key resolves to the Welcome section's dimension");
    }

    // ── two surveys (UC-008 BR-008, BR-009) ──────────────────────────────────

    /**
     * A second survey whose step, section and tag names are the Library's. Before UC-008 the
     * step upsert tripped the site-wide dim_step_un; now it gets a schema of its own.
     */
    private record Twin(int id, java.util.UUID key, int respondentId) {
    }

    private Twin installTwin() {
        return QuarkusTransaction.requiringNew().call(() -> {
            java.util.UUID key = java.util.UUID.randomUUID();
            Integer id = ((Number) em.createNativeQuery(
                    "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                            + "VALUES (NEXTVAL('survey.surveys_seq'), 'Etl Twin', 903, 'ETL twin fixture', "
                            + "'Second survey reusing the Library''s names', NULL, NULL, ?1) RETURNING id")
                    .setParameter(1, key).getSingleResult()).intValue();
            em.createNativeQuery(
                    "INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description, step_key) "
                            + "SELECT NEXTVAL('survey.steps_seq'), ?1, s.display_order, s.name, s.dimension_name, s.description, gen_random_uuid() "
                            + "FROM survey.steps s WHERE s.survey_id = ?2 AND s.id = ?3")
                    .setParameter(1, id).setParameter(2, SURVEY_ID).setParameter(3, WELCOME_STEP_ID).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description, section_key) "
                            + "SELECT NEXTVAL('survey.sections_seq'), ?1, s.display_order, s.name, s.dimension_name, s.description, gen_random_uuid() "
                            + "FROM survey.sections s WHERE s.survey_id = ?2 AND s.id = ?3")
                    .setParameter(1, id).setParameter(2, SURVEY_ID).setParameter(3, WELCOME_SECTION_ID).executeUpdate();
            // The same tag name as a Library tag, standalone (no dimension), so the twin asks
            // for a dim_terms_consent_direct_probe of its own.
            em.createNativeQuery(
                    "INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) "
                            + "VALUES (NEXTVAL('survey.ontology_seq'), ?1, 'Twin probe', 'terms_consent_direct_probe', NULL)")
                    .setParameter(1, id).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO survey.metadata (id, survey_id, steps_sections_id, ontology_id) "
                            + "SELECT NEXTVAL('survey.metadata_seq'), ?1, ss.steps_sections_id, o.id "
                            + "FROM survey.steps_sections ss, survey.ontology o "
                            + "WHERE ss.survey_id = ?2 AND o.survey_id = ?1 LIMIT 1")
                    .setParameter(1, id).setParameter(2, SURVEY_ID).executeUpdate();
            Integer respondentId = ((Number) em.createNativeQuery(
                    "INSERT INTO survey.respondents(id, survey_id, access_code, active, logins, created_dt, first_access_dt, finalized_dt) "
                            + "VALUES (NEXTVAL('survey.respondents_seq'), ?1, ?2, false, 1, NOW(), NOW(), NOW()) RETURNING id")
                    .setParameter(1, id).setParameter(2, "twin_" + System.nanoTime()).getSingleResult()).intValue();
            return new Twin(id, key, respondentId);
        });
    }

    private void removeTwin(Twin twin) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("DELETE FROM survey.respondents WHERE survey_id = ?1").setParameter(1, twin.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.metadata WHERE survey_id = ?1").setParameter(1, twin.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.ontology WHERE survey_id = ?1").setParameter(1, twin.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.sections WHERE survey_id = ?1").setParameter(1, twin.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.steps WHERE survey_id = ?1").setParameter(1, twin.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.surveys WHERE id = ?1").setParameter(1, twin.id()).executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() ->
                ownerEm.createNativeQuery("DROP SCHEMA IF EXISTS report_etl_twin CASCADE").executeUpdate());
    }

    @Test
    // UC-008 BR-006/BR-008/BR-009: a survey reusing the Library's step and section dimension
    // names builds without a dim_step_un failure, in a schema named after it, and nothing of
    // it lands in the Library's schema -- its tag column is on its own fact_sections only.
    void given_secondSurveyReusingNames_when_rebuild_then_ownSchemaAndNothingShared() {
        long libraryStepsBefore = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step");
        long libraryColumnsBefore = nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'report_librarycardreg' AND table_name = 'fact_sections'");
        Twin twin = installTwin();
        try {
            ETLService.RebuildResult result = etlService.rebuildReportingSchema(java.util.Optional.of(twin.key()));

            assertEquals(ETLService.RebuildStatus.OK, result.status(), result.message());
            String schema = (String) em.createNativeQuery("SELECT report_schema FROM survey.surveys WHERE id = ?1")
                    .setParameter(1, twin.id()).getSingleResult();
            assertEquals("report_etl_twin", schema, "BR-006: the name is derived from the survey's name");
            assertEquals(1, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM report_etl_twin.dim_step WHERE value = (SELECT dimension_name FROM survey.steps WHERE id = ?1)",
                    WELCOME_STEP_ID), "BR-008: the colliding step name is fine in the twin's own dim_step");
            assertEquals(1, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'report_etl_twin' AND table_name = 'dim_terms_consent_direct_probe'"),
                    "the twin's tag gets a dimension table in the twin's schema");
            assertEquals(1, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'report_etl_twin' AND table_name = 'fact_sections' AND column_name = 'terms_consent_direct_probe_key'"));
            assertEquals(0, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'report_etl_twin' AND table_name = 'fact_sections' AND column_name = 'terms_consent_key'"),
                    "BR-009: the Library's other tags are not columns of the twin's fact table");
            assertEquals(libraryStepsBefore, nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step"),
                    "BR-009: the Library's dim_step is untouched by the twin's build");
            assertEquals(libraryColumnsBefore, nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'report_librarycardreg' AND table_name = 'fact_sections'"),
                    "BR-009: the Library's fact_sections gained no column from the twin");
            // The twin's finalized respondent has no answers, so the back-fill selects it and
            // inserts nothing; a second build must still answer OK (the work list is per build).
            assertEquals(ETLService.RebuildStatus.OK, etlService.rebuildReportingSchema(java.util.Optional.of(twin.key())).status());
            assertEquals(0, nativeCount(ownerEm, "SELECT COUNT(*) FROM report_etl_twin.fact_sections"));
            assertEquals(1, nativeCount(ownerEm, "SELECT COUNT(*) FROM report_etl_twin.fact_respondents WHERE id = ?1 AND status = 2", twin.respondentId()),
                    "BR-010: the finalized respondent shows as finished in the twin's fact_respondents view");
        } finally {
            removeTwin(twin);
        }
    }

    @Test
    // UC-008 A6 / BR-003: an unknown key is reported, not thrown, and builds nothing.
    void given_unknownSurveyKey_when_rebuild_then_unknown() {
        ETLService.RebuildResult result = etlService.rebuildReportingSchema(java.util.Optional.of(java.util.UUID.randomUUID()));
        assertEquals(ETLService.RebuildStatus.UNKNOWN, result.status());
    }

    @Test
    // UC-008 BR-010: fact_respondents is a view, so a respondent's status moves without any
    // build, and a created date outside dim_date still appears in fact_respondents_view.
    void given_respondentProgresses_then_factRespondentsViewFollowsWithoutABuild() {
        Respondent r = QuarkusTransaction.requiringNew().call(this::createFreshUnfinalizedRespondent);
        try {
            assertEquals(0, statusOf(r.id), "not started");
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.respondents SET first_access_dt = NOW() WHERE id = ?1")
                            .setParameter(1, r.id).executeUpdate());
            assertEquals(1, statusOf(r.id), "in progress");
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.respondents SET finalized_dt = NOW(), created_dt = '2031-03-04' WHERE id = ?1")
                            .setParameter(1, r.id).executeUpdate());
            assertEquals(2, statusOf(r.id), "finished");
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT created, status FROM report_librarycardreg.fact_respondents_view WHERE id = ?1")
                    .setParameter(1, r.id).getResultList();
            assertEquals(1, rows.size(), "a date beyond dim_date must not hide the respondent (LEFT JOIN)");
            assertNull(rows.get(0)[0], "the date label is null, not a missing row");
            assertEquals("Finished", rows.get(0)[1]);
        } finally {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1")
                            .setParameter(1, r.id).executeUpdate());
        }
    }

    private int statusOf(int respondentId) {
        return ((Number) em.createNativeQuery("SELECT status FROM report_librarycardreg.fact_respondents WHERE id = ?1")
                .setParameter(1, respondentId).getSingleResult()).intValue();
    }

    // ── dim_step / dim_section ──────────────────────────────────────────────

    @Test
    void given_startupAlreadyRan_when_updateStepDimensionTable_then_idempotent() {
        long before = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step");
        assertTrue(before > 0, "@Startup must have already populated dim_step");

        etlService.updateStepDimensionTable(SCHEMA, SURVEY_ID);

        long after = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step");
        assertEquals(before, after, "Re-running updateStepDimensionTable() must not add rows");
    }

    @Test
    void given_startupAlreadyRan_when_updateSectionDimensionTable_then_idempotent() {
        long before = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_section");
        assertTrue(before > 0, "@Startup must have already populated dim_section");

        etlService.updateSectionDimensionTable(SCHEMA, SURVEY_ID);

        long after = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_section");
        assertEquals(before, after, "Re-running updateSectionDimensionTable() must not add rows");
    }

    @Test
    void given_stepRenamed_when_updateStepDimensionTable_then_sameRowUpdatedInPlace() {
        // Locks in the intentional SCD-Type-1-on-dim_step behavior (a rename is a clarification
        // and every report shows the current label) so the durable-key rekey can
        // be compared against it later: renaming updates the existing dim_step row, it
        // does not insert a second row for the same steps.id.
        //
        // No @TestTransaction here: ETLService's entityManager is bound to the "owner"
        // persistence unit, a different datasource from this test's default one.
        // Narayana cannot enlist that connection into an already-active @TestTransaction
        // ("Failed to enlist. Check if a connection from another datasource is already
        // enlisted to the same transaction") — each write below runs in its own
        // short-lived transaction instead, and the rename is reverted in `finally` since
        // nothing here auto-rolls-back.
        long countBefore = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step");

        try {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.steps SET dimension_name = 'WelcomeRenamed' WHERE id = ?1")
                            .setParameter(1, WELCOME_STEP_ID)
                            .executeUpdate());

            etlService.updateStepDimensionTable(SCHEMA, SURVEY_ID);

            long countAfter = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_step");
            String value = (String) em.createNativeQuery("SELECT value FROM report_librarycardreg.dim_step WHERE id = ?1")
                    .setParameter(1, WELCOME_STEP_ID)
                    .getSingleResult();

            assertEquals(countBefore, countAfter, "Rename must update in place, not insert a new dim_step row");
            assertEquals("WelcomeRenamed", value, "dim_step.value must reflect the renamed dimension_name");
        } finally {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.steps SET dimension_name = 'Welcome' WHERE id = ?1")
                            .setParameter(1, WELCOME_STEP_ID)
                            .executeUpdate());
            etlService.updateStepDimensionTable(SCHEMA, SURVEY_ID);
        }
    }

    @Test
    void given_sectionRenamed_when_updateSectionDimensionTable_then_sameRowUpdatedInPlace() {
        // Mirrors given_stepRenamed_when_updateStepDimensionTable_then_sameRowUpdatedInPlace —
        // locks in the intentional SCD-Type-1-on-dim_section behavior
        // for sections, which only had an idempotency test before this.
        long countBefore = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_section");

        try {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.sections SET dimension_name = 'WelcomeRenamed' WHERE id = ?1")
                            .setParameter(1, WELCOME_SECTION_ID)
                            .executeUpdate());

            etlService.updateSectionDimensionTable(SCHEMA, SURVEY_ID);

            long countAfter = nativeCount("SELECT COUNT(*) FROM report_librarycardreg.dim_section");
            String value = (String) em.createNativeQuery("SELECT value FROM report_librarycardreg.dim_section WHERE id = ?1")
                    .setParameter(1, WELCOME_SECTION_ID)
                    .getSingleResult();

            assertEquals(countBefore, countAfter, "Rename must update in place, not insert a new dim_section row");
            assertEquals("WelcomeRenamed", value, "dim_section.value must reflect the renamed dimension_name");
        } finally {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.sections SET dimension_name = 'Welcome' WHERE id = ?1")
                            .setParameter(1, WELCOME_SECTION_ID)
                            .executeUpdate());
            etlService.updateSectionDimensionTable(SCHEMA, SURVEY_ID);
        }
    }

    // ── dimension table discovery ───────────────────────────────────────────

    @Test
    void given_allDimensionTablesAlreadyBuilt_when_buildDimensionTables_then_noNewTablesFound() {
        // @Startup already ran buildDimensionTables() once for the whole test JVM.
        String result = etlService.buildDimensionTables(SCHEMA, SURVEY_ID);
        assertEquals("new Dimesions tables = []", result,
                "Second call must find zero new dimension tables — the discovery query is idempotent");
    }

    @Test
    void given_metadataOntologyDimensionsChain_when_buildDimensionTables_then_expectedTablesExist() {
        // Spot-check a couple of tables the V9005 fixture's metadata/ontology/dimensions
        // chain must have produced (PatronProfile dimension covers terms_consent +
        // digital_access; Branch is a tag-only-looking name but is dimensioned).
        long patronProfile = nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='report_librarycardreg' AND table_name='dim_patronprofile'");
        long branch = nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='report_librarycardreg' AND table_name='dim_branch'");
        assertEquals(1, patronProfile, "dim_patronprofile must exist (PatronProfile dimension: terms_consent, digital_access)");
        assertEquals(1, branch, "dim_branch must exist (Branch dimension: pickup_branch)");
    }

    @Test
    void given_freshOntologyDimensionMetadataRow_when_buildDimensionTables_then_newTableIsDiscoveredAndCreated() {
        // Every other discovery test only observes state after @Startup already ran once
        // (idempotent re-run, or tables that already exist). This test captures the actual
        // create-a-new-table transition FIND_NEW_DIMENSION_TABLES_SQL exists for, via the
        // section_question_id-path / standalone-tag branch (dimension IS NULL).
        String tag = "gap_probe_" + System.nanoTime();
        String tableName = "dim_" + tag;
        Integer ontologyId = QuarkusTransaction.requiringNew().call(() -> {
            Integer newOntologyId = ((Number) em.createNativeQuery(
                    "INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) "
                            + "VALUES (NEXTVAL('survey.ontology_seq'), ?1, ?2, ?3, NULL) RETURNING id")
                    .setParameter(1, SURVEY_ID).setParameter(2, "Gap Probe " + tag).setParameter(3, tag)
                    .getSingleResult()).intValue();
            em.createNativeQuery(
                    "INSERT INTO survey.metadata (id, survey_id, sections_question_id, ontology_id) "
                            + "SELECT NEXTVAL('survey.metadata_seq'), ?1, sq.sections_question_id, ?2 "
                            + "FROM survey.sections_questions sq WHERE sq.section_id = ?3 LIMIT 1")
                    .setParameter(1, SURVEY_ID).setParameter(2, newOntologyId).setParameter(3, WELCOME_SECTION_ID)
                    .executeUpdate();
            return newOntologyId;
        });

        try {
            long existsBefore = nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='report_librarycardreg' AND table_name=?1",
                    tableName);
            assertEquals(0, existsBefore, tableName + " must not exist before buildDimensionTables() discovers it");

            String result = etlService.buildDimensionTables(SCHEMA, SURVEY_ID);

            assertTrue(result.contains(tableName), "buildDimensionTables() return value must name the newly discovered table: " + result);
            long existsAfter = nativeCount(ownerEm,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='report_librarycardreg' AND table_name=?1",
                    tableName);
            assertEquals(1, existsAfter, tableName + " must exist after buildDimensionTables()");
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                em.createNativeQuery("DELETE FROM survey.metadata WHERE ontology_id = ?1")
                        .setParameter(1, ontologyId).executeUpdate();
                em.createNativeQuery("DELETE FROM survey.ontology WHERE id = ?1")
                        .setParameter(1, ontologyId).executeUpdate();
            });
            QuarkusTransaction.requiringNew().run(() ->
                    ownerEm.createNativeQuery("DROP TABLE IF EXISTS report_librarycardreg." + tableName).executeUpdate());
            QuarkusTransaction.requiringNew().run(() ->
                    ownerEm.createNativeQuery("DROP SEQUENCE IF EXISTS report_librarycardreg." + tableName + "_seq").executeUpdate());
        }
    }

    // ── fact_sections population ────────────────────────────────────────────

    @Test
    void given_tessFinalized_when_populateFactSectionTable_then_rowCountMatchesOracle() {
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        long actual = nativeCount(
                "SELECT COUNT(*) FROM report_librarycardreg.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
                SURVEY_ID, TESS_RESPONDENT_ID);

        assertEquals(expectedFactSectionTupleCount(TESS_RESPONDENT_ID), actual,
                "fact_sections row count for Tess must match the distinct (step,instance,section,instance) "
                        + "tuple count among her non-deleted, saved, non-null answers");
    }

    @Test
    void given_tessAlreadyProcessed_when_populateFactSectionTableAgain_then_idempotent() {
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);
        long before = nativeCount(
                "SELECT COUNT(*) FROM report_librarycardreg.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
                SURVEY_ID, TESS_RESPONDENT_ID);

        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        long after = nativeCount(
                "SELECT COUNT(*) FROM report_librarycardreg.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
                SURVEY_ID, TESS_RESPONDENT_ID);
        assertEquals(before, after,
                "The NOT EXISTS guard in INSERT_MISSING_FACT_SECTION_SQL must make a second "
                        + "populateFactSectionTable() call a no-op");
    }

    @Test
    void given_freshUnfinalizedRespondent_when_populateFactSectionTable_then_noFactRowsCreated() {
        // No @TestTransaction — see the comment on given_stepRenamed_when_... above for why
        // mixing it with an ETLService call (owner-datasource) fails to enlist.
        Respondent r = QuarkusTransaction.requiringNew().call(this::createFreshUnfinalizedRespondent);
        try {
            etlService.populateFactSectionTable(r.id);

            long count = nativeCount(
                    "SELECT COUNT(*) FROM report_librarycardreg.fact_sections WHERE respondent_id = ?1", r.id);
            assertEquals(0, count,
                    "INSERT_MISSING_FACT_SECTION_SQL filters on r.finalized_dt IS NOT NULL — "
                            + "an in-progress respondent must produce zero fact_sections rows");
        } finally {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1")
                            .setParameter(1, r.id).executeUpdate());
        }
    }

    // ── dimension value resolution (spot checks tied to metadata constants, not guesses) ──

    @Test
    void given_tessWelcomeSection_when_populateFactSectionTable_then_termsConsentKeyResolvesToConstant() {
        // metadata.value = 'Consented' is a hardcoded constant regardless of Tess's
        // actual Q37 answer text — the safest possible spot check.
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        String value = dimensionValueForWelcomeFactRow("terms_consent_key", "dim_patronprofile");
        assertEquals("consented", value,
                "terms_consent_key must resolve via the section_question_id path's constant metadata.value");
    }

    @Test
    void given_stepSectionIdPathFixture_when_populateFactSectionTable_then_welcomeReachedKeyResolves() {
        // V9005.6 fixture: step_section_id-path metadata row with a constant value —
        // exercises the previously-uncovered steps_sections join in FIND_DIMENSTION_VALUES_SQL
        // / FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL.
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        String value = dimensionValueForWelcomeFactRow("welcome_reached_key", "dim_welcome_reached");
        assertEquals("welcomestepreached", value,
                "welcome_reached_key must resolve via the step_section_id join path");
    }

    @Test
    void given_questionIdPathFixture_when_populateFactSectionTable_then_directProbeKeyResolvesToAnswerText() {
        // V9005.6 fixture: question_id-direct-path metadata row with value=NULL — exercises
        // the previously-uncovered "a.question_id = m.question_id" join, and the raw
        // answers.text_value ('true' for Tess's Q37) fallback branch of the CASE.
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        String value = dimensionValueForWelcomeFactRow("terms_consent_direct_probe_key", "dim_terms_consent_direct_probe");
        assertEquals("true", value,
                "terms_consent_direct_probe_key must resolve via the question_id-direct join path "
                        + "to Tess's raw Q37 answer text");
    }

    private String dimensionValueForWelcomeFactRow(String keyColumn, String dimTable) {
        // ownerEm: the dim_* table here was created by CREATE_NEW_DIMENSION_TABLE_SQL,
        // which only grants SELECT to REPORT_USER, not the default datasource's survey_user.
        // Single-column native queries return the scalar type directly (List<String>),
        // not List<Object[]> — unlike the multi-column queries elsewhere in this class.
        Query q = ownerEm.createNativeQuery(
                "SELECT d.value FROM report_librarycardreg.fact_sections f "
                        + "JOIN report_librarycardreg." + dimTable + " d ON d.id = f." + keyColumn + " "
                        + "WHERE f.survey_id = ?1 AND f.respondent_id = ?2 AND f.step_key = ?3 AND f.section_key = ?4");
        q.setParameter(1, SURVEY_ID).setParameter(2, TESS_RESPONDENT_ID)
                .setParameter(3, WELCOME_STEP_ID).setParameter(4, WELCOME_SECTION_ID);
        @SuppressWarnings("unchecked")
        List<String> rows = q.getResultList();
        assertFalse(rows.isEmpty(), "Expected exactly one Welcome-section fact_sections row for Tess with " + keyColumn + " set");
        return rows.get(0);
    }

    @Test
    void contextLoads() {
        assertNotNull(etlService);
    }
}
