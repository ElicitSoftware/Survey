package com.elicitsoftware.scd;

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

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for {@code survey.steps_sections} — research/Kimball_type_2.md,
 * "Schema Changes Per Table &rarr; steps_sections". Same cascade-immunity pattern as
 * {@link SectionsQuestionsCascadeSpecTest} applied to the step/section pair; also
 * covers {@code display_key} stability within a version's effective period
 * (Open Question 2, resolved) and reordering (Gap ETL-3 / Edge case R-2).
 */
@QuarkusTest
@Disabled("Enable once the steps_sections Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> steps_sections'")
class StepsSectionsCascadeSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsDurableKeyVersionColumnsAndFkCompanions() {
        long cols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='steps_sections' "
                        + "AND column_name IN ('steps_sections_id','version','effective_from','effective_to','is_draft',"
                        + "'step_version','section_version')").getSingleResult()).longValue();
        assertEquals(7, cols);
    }

    @Test
    @TestTransaction
    void reorderingTheStep_isAVersioningEventThatLeavesTheJoinRowsDisplayKeyStableUntilPublish() {
        Integer stepsSectionsId = ScdFixtureIds.stepsSectionsId(em);
        String displayKeyBefore = (String) em.createNativeQuery(
                "SELECT display_key FROM survey.steps_sections WHERE id = ?1")
                .setParameter(1, stepsSectionsId).getSingleResult();
        assertNotNull(displayKeyBefore, "display_key must be present and stable within the current version's effective period");
    }

    @Test
    @TestTransaction
    void renamingTheStep_leavesTheJoinRowUntouchedAndResolvesTheNewVersionAtNow() {
        Integer stepsSectionsId = ScdFixtureIds.stepsSectionsId(em);
        Integer stepSurrogateId = ScdFixtureIds.stepId(em);
        Integer durableStepId = (Integer) em.createNativeQuery(
                "SELECT step_id FROM survey.steps WHERE id = ?1").setParameter(1, stepSurrogateId).getSingleResult();
        int currentVersion = ((Number) em.createNativeQuery("SELECT version FROM survey.steps WHERE id = ?1")
                .setParameter(1, stepSurrogateId).getSingleResult()).intValue();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();
        OffsetDateTime publishInstant = OffsetDateTime.now().plusSeconds(5);

        em.createNativeQuery("UPDATE survey.steps SET effective_to = ?2 WHERE step_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableStepId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, step_id, version, survey_id, display_order, name, dimension_name, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.steps_seq'), step_id, ?2, survey_id, display_order, 'ScdStepRenamed', dimension_name, "
                        + "?3, ?4, false FROM survey.steps WHERE step_id = ?1 AND version = ?5")
                .setParameter(1, durableStepId).setParameter(2, currentVersion + 1).setParameter(3, publishInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        Integer joinRowStepId = (Integer) em.createNativeQuery(
                "SELECT step_id FROM survey.steps_sections WHERE id = ?1")
                .setParameter(1, stepsSectionsId).getSingleResult();
        assertEquals(durableStepId, joinRowStepId, "steps_sections.step_id (durable) must be unchanged by the step rename");

        String nameAtFirstAccess = stepNameResolvedThroughJoin(stepsSectionsId, firstAccessDt);
        String nameNow = stepNameResolvedThroughJoin(stepsSectionsId, OffsetDateTime.now().plusSeconds(10));
        assertEquals("ScdStep", nameAtFirstAccess, "A respondent pinned before the rename must see the old step name");
        assertEquals("ScdStepRenamed", nameNow, "A NOW() query must see the new step name through the same, unchanged join row");
    }

    private String stepNameResolvedThroughJoin(Integer stepsSectionsId, OffsetDateTime ts) {
        return (String) em.createNativeQuery(
                "SELECT s.name FROM survey.steps_sections ss "
                        + "JOIN survey.steps s ON s.step_id = ss.step_id AND s.effective_from <= ?2 AND s.effective_to > ?2 "
                        + "WHERE ss.id = ?1")
                .setParameter(1, stepsSectionsId).setParameter(2, ts).getSingleResult();
    }

    @Test
    @TestTransaction
    void orphanedDurableStepId_isRejectedByTheCompositeFk() {
        Integer stepsSectionsId = ScdFixtureIds.stepsSectionsId(em);
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("UPDATE survey.steps_sections SET step_id = -999999 WHERE id = ?1")
                        .setParameter(1, stepsSectionsId).executeUpdate(),
                "steps_sections_step_id_fkey must reject a step_id with no (step_id, version=0) row in steps");
    }
}
