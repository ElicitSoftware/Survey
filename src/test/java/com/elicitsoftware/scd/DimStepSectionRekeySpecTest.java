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

import com.elicitsoftware.etl.ETLService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for research/Kimball_type_2.md "dim_step and dim_section — Rekey by
 * Durable UUID" [sic — durable INTEGER in the final design]. Contrasts directly with
 * {@code ETLServiceTest.given_stepRenamed_when_updateStepDimensionTable_then_sameRowUpdatedInPlace}
 * (Track A, today's surrogate-`id`-keyed behavior): today a rename mutates the existing
 * row in place only because the surrogate `id` never changes on an in-place UPDATE. Once
 * a rename becomes "close current row, insert a new version row with a new surrogate
 * `id`", that same in-place behavior can only be preserved by upserting on the new
 * durable `step_id`/`section_id` column instead of the surrogate `id` — this is the fix
 * this migration provides.
 * <p>
 * Uses {@code ETLService} directly (not hand-copied SQL): {@code Sql.java}'s dimension
 * constants are rewritten in place by this migration, so calling the real bean exercises
 * the actual rewritten query.
 */
@QuarkusTest
@Disabled("Enable once dim_step/dim_section are rekeyed by durable step_id/section_id — see research/Kimball_type_2.md 'dim_step and dim_section — Rekey'")
class DimStepSectionRekeySpecTest {

    @Inject
    ETLService etlService;

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsUniqueStepIdAndSectionIdColumnsToDimTables() {
        long dimStepCol = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='surveyreport' "
                        + "AND table_name='dim_step' AND column_name='step_id'").getSingleResult()).longValue();
        long dimSectionCol = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='surveyreport' "
                        + "AND table_name='dim_section' AND column_name='section_id'").getSingleResult()).longValue();
        assertEquals(1, dimStepCol);
        assertEquals(1, dimSectionCol);
    }

    @Test
    @TestTransaction
    void renamingAStep_versionEventUpsertsTheSameDimStepRowByDurableId_notASecondRow() {
        Integer stepSurrogateIdBefore = ScdFixtureIds.stepId(em);
        Integer durableStepId = (Integer) em.createNativeQuery(
                "SELECT step_id FROM survey.steps WHERE id = ?1").setParameter(1, stepSurrogateIdBefore).getSingleResult();

        etlService.updateStepDimensionTable();
        long dimRowCountBefore = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM surveyreport.dim_step WHERE step_id = ?1")
                .setParameter(1, durableStepId).getSingleResult()).longValue();
        assertEquals(1, dimRowCountBefore, "The durable step_id must already map to exactly one dim_step row");

        // Rename = close current, insert new version row (new surrogate id, same durable step_id).
        int currentVersion = ((Number) em.createNativeQuery(
                "SELECT version FROM survey.steps WHERE step_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableStepId).setParameter(2, MAX_SENTINEL).getSingleResult()).intValue();
        em.createNativeQuery("UPDATE survey.steps SET effective_to = ?2 WHERE step_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableStepId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, step_id, version, survey_id, display_order, name, dimension_name, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.steps_seq'), step_id, ?2, survey_id, display_order, name, 'ScdStepRekeyed', "
                        + "?3, ?4, false FROM survey.steps WHERE step_id = ?1 AND version = ?5")
                .setParameter(1, durableStepId).setParameter(2, currentVersion + 1).setParameter(3, OffsetDateTime.now())
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        etlService.updateStepDimensionTable();

        long dimRowCountAfter = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM surveyreport.dim_step WHERE step_id = ?1")
                .setParameter(1, durableStepId).getSingleResult()).longValue();
        String valueAfter = (String) em.createNativeQuery(
                "SELECT value FROM surveyreport.dim_step WHERE step_id = ?1")
                .setParameter(1, durableStepId).getSingleResult();

        assertEquals(1, dimRowCountAfter,
                "Rekeying by durable step_id (not the surrogate id, which just changed) must upsert the SAME dim_step row");
        assertEquals("ScdStepRekeyed", valueAfter, "dim_step.value must reflect the new version's dimension_name");
    }
}
