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
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for {@link ETLService} against TODAY's schema (surrogate-id
 * keyed dim_step/dim_section, no Type 2 versioning anywhere). There were zero tests for
 * this class before this file. These lock in current behavior so that, once
 * research/Kimball_type_2.md's durable-key rewrite lands, an unmodified re-run of this
 * suite proves nothing regressed for pre-migration (epoch-dated) data — see that doc's
 * Migration Strategy step 7 and Open Question 1's testing checklist.
 * <p>
 * Driven by the V005/V005.5/V005.6 "Library Card Registration" fixture (survey_id=1,
 * Tess Tester = respondent_id=1, already finalized). {@code ETLService.init()} is
 * {@code @Startup}, so by the time any test method runs, dim_step/dim_section/the
 * dim_* tag tables/fact_sections have already been populated once for the whole test
 * JVM — these tests assert against that already-populated state and confirm re-running
 * the same operations is idempotent, rather than assuming an empty starting point.
 */
@QuarkusTest
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
                    JOIN survey.steps s ON a.step = s.id
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
        r.token = "etl_test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    // ── dim_step / dim_section ──────────────────────────────────────────────

    @Test
    void given_startupAlreadyRan_when_updateStepDimensionTable_then_idempotent() {
        long before = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_step");
        assertTrue(before > 0, "@Startup must have already populated dim_step");

        etlService.updateStepDimensionTable();

        long after = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_step");
        assertEquals(before, after, "Re-running updateStepDimensionTable() must not add rows");
    }

    @Test
    void given_startupAlreadyRan_when_updateSectionDimensionTable_then_idempotent() {
        long before = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_section");
        assertTrue(before > 0, "@Startup must have already populated dim_section");

        etlService.updateSectionDimensionTable();

        long after = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_section");
        assertEquals(before, after, "Re-running updateSectionDimensionTable() must not add rows");
    }

    @Test
    void given_stepRenamed_when_updateStepDimensionTable_then_sameRowUpdatedInPlace() {
        // Locks in today's SCD-Type-1-on-dim_step behavior (Kimball_type_2.md Gap ETL-5's
        // documented *intentional* current/future behavior) so the durable-key rekey can
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
        long countBefore = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_step");

        try {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.steps SET dimension_name = 'WelcomeRenamed' WHERE id = ?1")
                            .setParameter(1, WELCOME_STEP_ID)
                            .executeUpdate());

            etlService.updateStepDimensionTable();

            long countAfter = nativeCount("SELECT COUNT(*) FROM surveyreport.dim_step");
            String value = (String) em.createNativeQuery("SELECT value FROM surveyreport.dim_step WHERE id = ?1")
                    .setParameter(1, WELCOME_STEP_ID)
                    .getSingleResult();

            assertEquals(countBefore, countAfter, "Rename must update in place, not insert a new dim_step row");
            assertEquals("WelcomeRenamed", value, "dim_step.value must reflect the renamed dimension_name");
        } finally {
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("UPDATE survey.steps SET dimension_name = 'Welcome' WHERE id = ?1")
                            .setParameter(1, WELCOME_STEP_ID)
                            .executeUpdate());
            etlService.updateStepDimensionTable();
        }
    }

    // ── dimension table discovery ───────────────────────────────────────────

    @Test
    void given_allDimensionTablesAlreadyBuilt_when_buildDimensionTables_then_noNewTablesFound() {
        // @Startup already ran buildDimensionTables() once for the whole test JVM.
        String result = etlService.buildDimensionTables();
        assertEquals("new Dimesions tables = []", result,
                "Second call must find zero new dimension tables — the discovery query is idempotent");
    }

    @Test
    void given_metadataOntologyDimensionsChain_when_buildDimensionTables_then_expectedTablesExist() {
        // Spot-check a couple of tables the V005 fixture's metadata/ontology/dimensions
        // chain must have produced (PatronProfile dimension covers terms_consent +
        // digital_access; Branch is a tag-only-looking name but is dimensioned).
        long patronProfile = nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='surveyreport' AND table_name='dim_patronprofile'");
        long branch = nativeCount(ownerEm,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='surveyreport' AND table_name='dim_branch'");
        assertEquals(1, patronProfile, "dim_patronprofile must exist (PatronProfile dimension: terms_consent, digital_access)");
        assertEquals(1, branch, "dim_branch must exist (Branch dimension: pickup_branch)");
    }

    // ── fact_sections population ────────────────────────────────────────────

    @Test
    void given_tessFinalized_when_populateFactSectionTable_then_rowCountMatchesOracle() {
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        long actual = nativeCount(
                "SELECT COUNT(*) FROM surveyreport.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
                SURVEY_ID, TESS_RESPONDENT_ID);

        assertEquals(expectedFactSectionTupleCount(TESS_RESPONDENT_ID), actual,
                "fact_sections row count for Tess must match the distinct (step,instance,section,instance) "
                        + "tuple count among her non-deleted, saved, non-null answers");
    }

    @Test
    void given_tessAlreadyProcessed_when_populateFactSectionTableAgain_then_idempotent() {
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);
        long before = nativeCount(
                "SELECT COUNT(*) FROM surveyreport.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
                SURVEY_ID, TESS_RESPONDENT_ID);

        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        long after = nativeCount(
                "SELECT COUNT(*) FROM surveyreport.fact_sections WHERE survey_id = ?1 AND respondent_id = ?2",
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
                    "SELECT COUNT(*) FROM surveyreport.fact_sections WHERE respondent_id = ?1", r.id);
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
        // V005.6 fixture: step_section_id-path metadata row with a constant value —
        // exercises the previously-uncovered steps_sections join in FIND_DIMENSTION_VALUES_SQL
        // / FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL.
        etlService.populateFactSectionTable(TESS_RESPONDENT_ID);

        String value = dimensionValueForWelcomeFactRow("welcome_reached_key", "dim_welcome_reached");
        assertEquals("welcomestepreached", value,
                "welcome_reached_key must resolve via the step_section_id join path");
    }

    @Test
    void given_questionIdPathFixture_when_populateFactSectionTable_then_directProbeKeyResolvesToAnswerText() {
        // V005.6 fixture: question_id-direct-path metadata row with value=NULL — exercises
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
                "SELECT d.value FROM surveyreport.fact_sections f "
                        + "JOIN surveyreport." + dimTable + " d ON d.id = f." + keyColumn + " "
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
