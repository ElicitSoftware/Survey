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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for Type 2 versioning of {@code survey.steps} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; steps". Covers the same
 * versioning/constraint pattern as {@link QuestionsVersioningSpecTest} plus the
 * {@code display_order INTEGER -> NUMERIC} conversion ("Adding a new question" &rarr;
 * decimal midpoint insertion).
 */
@QuarkusTest
@Disabled("Enable once the steps Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> steps'")
class StepsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsExpectedColumnsAndSequence() {
        long columns = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'survey' AND table_name = 'steps' "
                        + "AND column_name IN ('step_id','version','effective_from','effective_to','is_draft','published_by','published_comment')")
                .getSingleResult()).longValue();
        assertEquals(7, columns);
    }

    @Test
    void displayOrder_isConvertedToNumeric() {
        String dataType = (String) em.createNativeQuery(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='survey' "
                        + "AND table_name='steps' AND column_name='display_order'").getSingleResult();
        assertEquals("numeric", dataType, "display_order must be NUMERIC to support decimal midpoint insertion");
    }

    @Test
    @TestTransaction
    void preExistingIntegerDisplayOrder_survivesTheNumericConversionLosslessly() {
        Integer stepId = ScdFixtureIds.stepId(em);
        BigDecimal displayOrder = (BigDecimal) em.createNativeQuery(
                "SELECT display_order FROM survey.steps WHERE id = ?1").setParameter(1, stepId).getSingleResult();
        assertEquals(1, displayOrder.intValueExact(),
                "fixture step's display_order (1) must be preserved exactly across the INTEGER -> NUMERIC conversion");
    }

    @Test
    @TestTransaction
    void decimalMidpointInsertion_roundTrips() {
        // No second step exists in the fixture at display_order=2, so this exercises the
        // pattern directly against a synthetic sibling rather than needing one.
        Integer surveyId = ScdFixtureIds.surveyId(em);
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, "
                        + "step_id, version, effective_from, effective_to, is_draft) "
                        + "VALUES (nextval('survey.steps_seq'), ?1, 2, 'ScdStepSibling', 'ScdStepSibling', "
                        + "nextval('survey.steps_durable_seq'), 0, ?2, ?3, false)")
                .setParameter(1, surveyId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL)
                .executeUpdate();

        Integer midpointId = (Integer) em.createNativeQuery(
                "INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, "
                        + "step_id, version, effective_from, effective_to, is_draft) "
                        + "VALUES (nextval('survey.steps_seq'), ?1, 1.5, 'ScdStepMidpoint', 'ScdStepMidpoint', "
                        + "nextval('survey.steps_durable_seq'), 0, ?2, ?3, false) RETURNING id")
                .setParameter(1, surveyId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL)
                .getSingleResult();

        BigDecimal readBack = (BigDecimal) em.createNativeQuery(
                "SELECT display_order FROM survey.steps WHERE id = ?1").setParameter(1, midpointId).getSingleResult();
        assertEquals(new BigDecimal("1.5"), readBack, "A decimal midpoint display_order must round-trip exactly");
    }

    @Test
    @TestTransaction
    void onlyOneCurrentRowPerDurableId_isEnforced() {
        Integer stepId = ScdFixtureIds.stepId(em);
        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery(
                    "INSERT INTO survey.steps (id, step_id, version, survey_id, display_order, name, dimension_name, "
                            + "effective_from, effective_to, is_draft) "
                            + "SELECT nextval('survey.steps_seq'), step_id, version + 1, survey_id, display_order, name, dimension_name, "
                            + "?2, ?3, false FROM survey.steps WHERE id = ?1")
                    .setParameter(1, stepId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL)
                    .executeUpdate();
            em.flush();
        }, "steps_one_current_un must reject a second current row for the same step_id");
    }
}
