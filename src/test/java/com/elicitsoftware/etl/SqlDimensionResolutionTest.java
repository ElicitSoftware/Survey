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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Query-level characterization of the three metadata join paths in {@link Sql}
 * (question_id-direct, section_question_id, step_section_id) — finer-grained than
 * {@link ETLServiceTest}: these tests run the SQL constants directly via native
 * queries and inspect the raw tuples, independent of ETLService's table-creation
 * plumbing. Both dimension-value-resolution SQL constants must be rewritten to use
 * durable keys per research/Kimball_type_2.md ("ETL Dimension-Discovery and Value
 * Queries — Time-Range Guards"); this is the query-level "before" picture the
 * rewrite must reproduce (modulo the join column itself).
 * <p>
 * Uses the V005/V005.5/V005.6 fixture (survey_id=1, Tess Tester = respondent_id=1).
 */
@QuarkusTest
class SqlDimensionResolutionTest {

    @Inject
    EntityManager em;

    static final int TESS_RESPONDENT_ID = 1;

    @SuppressWarnings("unchecked")
    private List<Object[]> findDimensionValuesForTess() {
        Query q = em.createNativeQuery(Sql.FIND_DIMENSTION_VALUES_SQL);
        q.setParameter("respondentId", TESS_RESPONDENT_ID);
        return q.getResultList();
    }

    private boolean containsTuple(List<Object[]> rows, String dim, String val) {
        return rows.stream().anyMatch(r -> dim.equals(r[0]) && val.equals(r[1]));
    }

    // ── FIND_DIMENSTION_VALUES_SQL — all three join paths ───────────────────

    @Test
    void given_tess_when_findDimensionValues_then_sectionQuestionIdPathResolvesConstant() {
        // Path: answers -> sections_questions -> metadata.section_question_id -> ontology (dimensioned).
        // metadata.value = 'Consented' is a hardcoded constant.
        List<Object[]> rows = findDimensionValuesForTess();
        assertTrue(containsTuple(rows, "dim_patronprofile", "consented"),
                "section_question_id join path must resolve terms_consent's constant metadata.value");
    }

    @Test
    void given_tess_when_findDimensionValues_then_stepSectionIdPathResolvesConstant() {
        // Path: answers -> steps_sections (display-order join) -> metadata.step_section_id
        // -> ontology (tag-only). V005.6 fixture-added coverage.
        List<Object[]> rows = findDimensionValuesForTess();
        assertTrue(containsTuple(rows, "dim_welcome_reached", "welcomestepreached"),
                "step_section_id join path must resolve the constant metadata.value for ss_welcome");
    }

    @Test
    void given_tess_when_findDimensionValues_then_questionIdDirectPathResolvesAnswerText() {
        // Path: answers.question_id -> metadata.question_id (direct) -> ontology (tag-only).
        // metadata.value IS NULL here, so resolution falls back to the raw answers.text_value
        // ('true' for Tess's Q37 CHECKBOX answer). V005.6 fixture-added coverage.
        List<Object[]> rows = findDimensionValuesForTess();
        assertTrue(containsTuple(rows, "dim_terms_consent_direct_probe", "true"),
                "question_id-direct join path must fall back to the respondent's raw answer text");
    }

    @Test
    void given_unfinalizedOrUnknownRespondent_when_findDimensionValues_then_empty() {
        Query q = em.createNativeQuery(Sql.FIND_DIMENSTION_VALUES_SQL);
        q.setParameter("respondentId", -1);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        assertTrue(rows.isEmpty(), "An unknown respondent id must resolve to zero dimension values");
    }

    // ── FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL — same three paths, against fact_sections ──

    @Test
    void given_tessFactSectionsPopulated_when_findMissingFactSectionDimensions_then_allThreePathsPresent() {
        // Relies on @Startup's ETLService.init() having already run addRespondentFactSections()
        // for every finalized respondent (Tess included) once for this test JVM.
        Query q = em.createNativeQuery(Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL);
        q.setParameter("respondent_id", TESS_RESPONDENT_ID);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        assertTrue(rows.stream().anyMatch(r -> "terms_consent_key".equals(r[0]) && "dim_patronprofile".equals(r[1]) && "consented".equals(r[2])),
                "section_question_id path must appear in the missing-fact-dimensions result set");
        assertTrue(rows.stream().anyMatch(r -> "welcome_reached_key".equals(r[0]) && "dim_welcome_reached".equals(r[1]) && "welcomestepreached".equals(r[2])),
                "step_section_id path must appear in the missing-fact-dimensions result set");
        assertTrue(rows.stream().anyMatch(r -> "terms_consent_direct_probe_key".equals(r[0]) && "dim_terms_consent_direct_probe".equals(r[1]) && "true".equals(r[2])),
                "question_id-direct path must appear in the missing-fact-dimensions result set");
    }
}
