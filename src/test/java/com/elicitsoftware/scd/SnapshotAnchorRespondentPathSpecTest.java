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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for research/Kimball_type_2.md "Workflow 2: Respondent Survey
 * Workflow" — the guarantees that matter most for not breaking a respondent mid-survey.
 * All resolution here uses {@code respondents.firstAccessDt} as the snapshot anchor,
 * exactly as the doc specifies, against the {@code questions} table (any structural
 * table demonstrates the same pattern; see *VersioningSpecTest classes for the others).
 */
@QuarkusTest
@Disabled("Enable once the questions Type 2 migration lands — see research/Kimball_type_2.md 'Workflow 2: Respondent Survey Workflow'")
class SnapshotAnchorRespondentPathSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");
    static final OffsetDateTime EPOCH_SENTINEL = OffsetDateTime.parse("1970-01-01T00:00:00+00:00");

    private Integer durableQuestionId() {
        Integer surrogateId = ScdFixtureIds.questionId(em);
        return (Integer) em.createNativeQuery("SELECT question_id FROM survey.questions WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult();
    }

    private void closeAndInsert(Integer durableId, OffsetDateTime publishInstant, String newText) {
        int currentVersion = ((Number) em.createNativeQuery(
                "SELECT version FROM survey.questions WHERE question_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableId).setParameter(2, MAX_SENTINEL).getSingleResult()).intValue();
        em.createNativeQuery("UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.questions_seq'), question_id, ?2, survey_id, type_id, ?3, required, "
                        + "?4, ?5, false FROM survey.questions WHERE question_id = ?1 AND version = ?6")
                .setParameter(1, durableId).setParameter(2, currentVersion + 1).setParameter(3, newText)
                .setParameter(4, publishInstant).setParameter(5, MAX_SENTINEL).setParameter(6, currentVersion)
                .executeUpdate();
        em.flush();
    }

    @Test
    @TestTransaction
    void preMigrationRespondent_epochEffectiveFromCoversAnyRealFirstAccessDt() {
        // Migration Strategy step 2 / Edge case R-1 resolution: pre-existing rows get
        // effective_from = epoch, which precedes every real firstAccessDt by definition.
        Integer durableId = durableQuestionId();
        OffsetDateTime longAgoFirstAccess = OffsetDateTime.parse("2020-01-01T00:00:00+00:00");

        long visible = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, longAgoFirstAccess).getSingleResult()).longValue();
        assertEquals(1, visible, "Epoch effective_from must resolve for any pre-migration respondent's firstAccessDt");
    }

    @Test
    @TestTransaction
    void newRespondentWithNoFirstAccessDtYet_resolvesTheCurrentRowAtNow() {
        Integer durableId = durableQuestionId();
        String text = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE question_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, OffsetDateTime.now()).getSingleResult();
        assertEquals("Original wording", text);
    }

    @Test
    @TestTransaction
    void returningRespondent_continuesToSeeTheirOriginalVersionAfterALaterPublish() {
        Integer durableId = durableQuestionId();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();

        closeAndInsert(durableId, OffsetDateTime.now().plusSeconds(5), "Published after this respondent started");

        String textAtFirstAccess = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE question_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, firstAccessDt).getSingleResult();
        assertEquals("Original wording", textAtFirstAccess,
                "Edge case R-1/R-2: a returning respondent must keep seeing what they saw at firstAccessDt regardless of later publishes");
    }

    @Test
    @TestTransaction
    void retiredWithNoReplacement_remainsVisibleForARespondentAlreadyInSession() {
        Integer durableId = durableQuestionId();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();
        OffsetDateTime retirementInstant = OffsetDateTime.now().plusSeconds(5);

        // Retirement: close the current row, insert nothing.
        em.createNativeQuery("UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableId).setParameter(2, retirementInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.flush();

        long visibleToInProgressRespondent = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, firstAccessDt).getSingleResult()).longValue();
        long visibleToNewRespondent = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, OffsetDateTime.now().plusSeconds(10)).getSingleResult()).longValue();

        assertEquals(1, visibleToInProgressRespondent,
                "Edge case R-1: a respondent with firstAccessDt before retirement must still see the retired element for their session");
        assertEquals(0, visibleToNewRespondent,
                "A respondent starting after the retirement instant must not see the retired element at all");
    }

    @Test
    @TestTransaction
    void reorderingMidSurveyForOtherRespondents_doesNotChangeWhatAnInProgressRespondentSees() {
        // Edge case R-2 / Gap ETL-3: changing display_order is itself a versioning event.
        // A respondent pinned before the reorder must keep resolving the pre-reorder row.
        Integer stepSurrogateId = ScdFixtureIds.stepId(em);
        Integer durableStepId = (Integer) em.createNativeQuery(
                "SELECT step_id FROM survey.steps WHERE id = ?1").setParameter(1, stepSurrogateId).getSingleResult();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();
        OffsetDateTime reorderInstant = OffsetDateTime.now().plusSeconds(5);

        int currentVersion = ((Number) em.createNativeQuery(
                "SELECT version FROM survey.steps WHERE step_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableStepId).setParameter(2, MAX_SENTINEL).getSingleResult()).intValue();
        em.createNativeQuery("UPDATE survey.steps SET effective_to = ?2 WHERE step_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableStepId).setParameter(2, reorderInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, step_id, version, survey_id, display_order, name, dimension_name, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.steps_seq'), step_id, ?2, survey_id, 3, name, dimension_name, "
                        + "?3, ?4, false FROM survey.steps WHERE step_id = ?1 AND version = ?5")
                .setParameter(1, durableStepId).setParameter(2, currentVersion + 1).setParameter(3, reorderInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        Number displayOrderAtFirstAccess = (Number) em.createNativeQuery(
                "SELECT display_order FROM survey.steps WHERE step_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableStepId).setParameter(2, firstAccessDt).getSingleResult();
        Number displayOrderNow = (Number) em.createNativeQuery(
                "SELECT display_order FROM survey.steps WHERE step_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableStepId).setParameter(2, OffsetDateTime.now().plusSeconds(10)).getSingleResult();

        assertEquals(1, displayOrderAtFirstAccess.intValue(), "The in-progress respondent must keep resolving the pre-reorder display_order");
        assertEquals(3, displayOrderNow.intValue(), "A NOW() query must resolve the reordered display_order");
    }
}
