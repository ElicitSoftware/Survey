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
import jakarta.persistence.Query;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for Type 2 versioning of {@code survey.questions} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; questions" and
 * "Versioning Workflows &rarr; Rewording a question". This is the reference
 * implementation the other 7 structural-table *VersioningSpecTest classes follow.
 * <p>
 * Written entirely against native SQL (no new Panache entity fields), so it compiles
 * today; it only fails to *run* until the {@code questions} migration exists. Enable
 * by removing {@code @Disabled} once that migration lands, then iterate until green.
 * Uses the isolated V011__SCD_Spec_Fixture.sql fixture (survey "ScdSpecFixture") —
 * never the V005/Tess fixture that QuestionManagerTest/ETLServiceTest depend on.
 */
@QuarkusTest
@Disabled("Enable once the questions Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> questions'")
class QuestionsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");
    static final OffsetDateTime EPOCH_SENTINEL = OffsetDateTime.parse("1970-01-01T00:00:00+00:00");

    // ── Migration shape ──────────────────────────────────────────────────────

    @Test
    void migration_addsExpectedColumnsAndSequence() {
        List<?> columns = em.createNativeQuery(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'survey' AND table_name = 'questions' "
                        + "AND column_name IN ('question_id','version','effective_from','effective_to',"
                        + "'is_draft','published_by','published_comment')").getResultList();
        assertEquals(7, columns.size(), "questions must gain all 7 Type 2 columns");

        long sequenceExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.sequences "
                        + "WHERE sequence_schema = 'survey' AND sequence_name = 'questions_durable_seq'")
                .getSingleResult()).longValue();
        assertEquals(1, sequenceExists, "questions_durable_seq must exist");
    }

    @Test
    void migration_backfillsExistingRowsToEpochToMaxVersionZero() {
        Integer questionId = ScdFixtureIds.questionId(em);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT version, effective_from, effective_to, is_draft FROM survey.questions WHERE id = ?1")
                .setParameter(1, questionId)
                .getSingleResult();

        assertEquals(0, ((Number) row[0]).intValue(), "Pre-existing row must backfill to version 0");
        assertEquals(EPOCH_SENTINEL, row[1], "Pre-existing row must backfill effective_from to epoch");
        assertEquals(MAX_SENTINEL, row[2], "Pre-existing row must backfill effective_to to the max sentinel");
        assertEquals(Boolean.FALSE, row[3], "Pre-existing row must not be a draft");
    }

    // ── Constraint enforcement ───────────────────────────────────────────────

    @Test
    @TestTransaction
    void onlyOneCurrentRowPerDurableId_isEnforced() {
        Integer questionId = ScdFixtureIds.questionId(em);

        // A second row claiming to be current for the same durable id must violate
        // questions_one_current_un.
        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery(
                    "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                            + "effective_from, effective_to, is_draft) "
                            + "SELECT nextval('survey.questions_seq'), question_id, version + 1, survey_id, type_id, text, required, "
                            + "?2, ?3, false FROM survey.questions WHERE id = ?1")
                    .setParameter(1, questionId)
                    .setParameter(2, OffsetDateTime.now())
                    .setParameter(3, MAX_SENTINEL)
                    .executeUpdate();
            em.flush();
        }, "questions_one_current_un must reject a second effective_to = max row for the same question_id");
    }

    @Test
    @TestTransaction
    void onlyOneDraftRowPerDurableId_isEnforced() {
        Integer questionId = ScdFixtureIds.questionId(em);
        Integer durableId = durableIdOf(questionId);

        insertDraft(durableId, "First draft");

        assertThrows(PersistenceException.class, () -> {
            insertDraft(durableId, "Second concurrent draft");
            em.flush();
        }, "questions_one_draft_un must reject a second is_draft = true row for the same question_id (Q-A5)");
    }

    // ── Close-current / insert-new (Q-R1: no gap, no overlap) ───────────────

    @Test
    @TestTransaction
    void rewording_closesCurrentRowAndInsertsNextVersionAtTheSameInstant() {
        Integer questionId = ScdFixtureIds.questionId(em);
        Integer durableId = durableIdOf(questionId);
        int currentVersion = versionOf(questionId);
        OffsetDateTime publishInstant = OffsetDateTime.now();

        closeCurrentAndInsertNewVersion(durableId, currentVersion, publishInstant, "Reworded text");

        Object[] oldRow = (Object[]) em.createNativeQuery(
                "SELECT effective_to FROM survey.questions WHERE question_id = ?1 AND version = ?2")
                .setParameter(1, durableId).setParameter(2, currentVersion).getSingleResult();
        Object[] newRow = (Object[]) em.createNativeQuery(
                "SELECT effective_from, version, text FROM survey.questions WHERE question_id = ?1 AND version = ?2")
                .setParameter(1, durableId).setParameter(2, currentVersion + 1).getSingleResult();

        assertEquals(publishInstant, oldRow[0], "Old row's effective_to must be exactly the publish instant");
        assertEquals(publishInstant, newRow[0], "New row's effective_from must be exactly the same publish instant (no gap/overlap)");
        assertEquals(currentVersion + 1, ((Number) newRow[1]).intValue(), "version must increment by exactly 1");
        assertEquals("Reworded text", newRow[2]);
    }

    // ── Snapshot anchor (firstAccessDt) ──────────────────────────────────────

    @Test
    @TestTransaction
    void respondentSnapshot_beforePublish_seesOldWordingAtTheirFirstAccessDt() {
        Integer questionId = ScdFixtureIds.questionId(em);
        Integer durableId = durableIdOf(questionId);
        int currentVersion = versionOf(questionId);
        OffsetDateTime firstAccessDt = OffsetDateTime.now();

        // Respondent's firstAccessDt is captured before the reword happens.
        closeCurrentAndInsertNewVersion(durableId, currentVersion, OffsetDateTime.now().plusSeconds(5), "Reworded after respondent started");

        String wordingAtFirstAccess = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE question_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, firstAccessDt).getSingleResult();
        String wordingNow = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE question_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, OffsetDateTime.now().plusSeconds(10)).getSingleResult();

        assertEquals("Original wording", wordingAtFirstAccess,
                "A respondent whose firstAccessDt predates the reword must resolve to the old wording");
        assertEquals("Reworded after respondent started", wordingNow,
                "A query using a later timestamp must resolve to the new wording");
    }

    @Test
    @TestTransaction
    void draftRow_isInvisibleToTheTimeRangePredicateAtAnyTimestamp() {
        Integer questionId = ScdFixtureIds.questionId(em);
        Integer durableId = durableIdOf(questionId);
        insertDraft(durableId, "Draft wording, never published");

        long visibleAtEpoch = countVisibleAt(durableId, EPOCH_SENTINEL.plusNanos(1000));
        long visibleAtNow = countVisibleAt(durableId, OffsetDateTime.now());
        long visibleAtFarFuture = countVisibleAt(durableId, OffsetDateTime.now().plusYears(50));

        assertEquals(1, visibleAtEpoch, "Exactly the current (non-draft) row must be visible at any timestamp");
        assertEquals(1, visibleAtNow, "A draft (NULL effective_from/effective_to) must never satisfy the time-range predicate");
        assertEquals(1, visibleAtFarFuture, "Draft rows must remain invisible arbitrarily far in the future");
    }

    // ── answers pin the exact version ────────────────────────────────────────

    @Test
    @TestTransaction
    void answersRow_continuesToResolveTheExactVersionAfterAReword() {
        Integer questionId = ScdFixtureIds.questionId(em);
        Integer durableId = durableIdOf(questionId);
        int currentVersion = versionOf(questionId);

        // questionId is the surrogate id an answers row would have stored at response time.
        closeCurrentAndInsertNewVersion(durableId, currentVersion, OffsetDateTime.now(), "Reworded after the answer was recorded");

        String wordingAtResponseTime = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE id = ?1")
                .setParameter(1, questionId).getSingleResult();

        assertEquals("Original wording", wordingAtResponseTime,
                "answers.question_id pins the exact surrogate row; a later reword must not change what a "
                        + "recorded answer resolves to (doc's 'Recovering exact wording' query)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int versionOf(Integer surrogateId) {
        return ((Number) em.createNativeQuery("SELECT version FROM survey.questions WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult()).intValue();
    }

    private Integer durableIdOf(Integer surrogateId) {
        return (Integer) em.createNativeQuery("SELECT question_id FROM survey.questions WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult();
    }

    private long countVisibleAt(Integer durableId, OffsetDateTime ts) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, ts).getSingleResult()).longValue();
    }

    private void insertDraft(Integer durableId, String text) {
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.questions_seq'), question_id, "
                        + "(SELECT MAX(version) FROM survey.questions WHERE question_id = ?1) + 1, "
                        + "survey_id, type_id, ?2, required, NULL, NULL, true "
                        + "FROM survey.questions WHERE question_id = ?1 AND effective_to = ?3 LIMIT 1")
                .setParameter(1, durableId).setParameter(2, text).setParameter(3, MAX_SENTINEL)
                .executeUpdate();
    }

    /** Mirrors the doc's "close current row / insert next version" pattern, with the same
     *  publishInstant used for both the closing effective_to and the new effective_from —
     *  Q-R1's no-gap/no-overlap guarantee. */
    private void closeCurrentAndInsertNewVersion(Integer durableId, int currentVersion, OffsetDateTime publishInstant, String newText) {
        Query close = em.createNativeQuery(
                "UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3");
        close.setParameter(1, durableId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL);
        int updated = close.executeUpdate();
        assertEquals(1, updated, "Exactly one current row must be closed");

        em.createNativeQuery(
                "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                        + "select_group_id, effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.questions_seq'), question_id, ?2, survey_id, type_id, ?3, required, "
                        + "select_group_id, ?4, ?5, false "
                        + "FROM survey.questions WHERE question_id = ?1 AND version = ?6")
                .setParameter(1, durableId).setParameter(2, currentVersion + 1).setParameter(3, newText)
                .setParameter(4, publishInstant).setParameter(5, MAX_SENTINEL).setParameter(6, currentVersion)
                .executeUpdate();
        em.flush();
    }
}
