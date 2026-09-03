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
 * Executable spec for research/Kimball_type_2.md "Workflow 1: Survey Alteration —
 * Draft and Publish Phases". Exercises the full draft &rarr; preview &rarr;
 * publish/discard sequence as raw SQL against {@code survey.questions}, independent of
 * whichever application (this repo or the separate Author Tool) issues the statements —
 * the guarantees here are database-level.
 */
@QuarkusTest
@Disabled("Enable once the questions Type 2 migration lands (any structural table demonstrates the same pattern) — see research/Kimball_type_2.md 'Workflow 1: Survey Alteration'")
class DraftPublishWorkflowSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    @TestTransaction
    void draftInsert_isFoundByAdminPreviewButNeverByTheRespondentTimeRangeQuery() {
        Integer durableId = durableIdOfFixtureQuestion();

        insertDraft(durableId, "Draft: pending review");

        long draftVisibleToAdmin = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND is_draft = true")
                .setParameter(1, durableId).getSingleResult()).longValue();
        long draftVisibleToRespondentNow = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND is_draft = true "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, OffsetDateTime.now()).getSingleResult()).longValue();

        assertEquals(1, draftVisibleToAdmin, "Admin preview query (is_draft = true) must find the draft");
        assertEquals(0, draftVisibleToRespondentNow,
                "The respondent time-range predicate must never match a draft row (both effective_from/to are NULL)");
    }

    @Test
    @TestTransaction
    void discardingADraftBeforePublish_isSafe() {
        Integer durableId = durableIdOfFixtureQuestion();
        insertDraft(durableId, "Draft: about to be discarded");

        em.createNativeQuery("DELETE FROM survey.questions WHERE question_id = ?1 AND is_draft = true")
                .setParameter(1, durableId).executeUpdate();

        long remainingDrafts = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND is_draft = true")
                .setParameter(1, durableId).getSingleResult()).longValue();
        long currentRowStillPresent = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableId).setParameter(2, MAX_SENTINEL).getSingleResult()).longValue();

        assertEquals(0, remainingDrafts, "Discard must remove the draft row entirely");
        assertEquals(1, currentRowStillPresent, "The published/current row must be entirely unaffected by discarding a draft");
    }

    @Test
    @TestTransaction
    void publishTransaction_atomicallyRetiresTheOldRowAndPromotesTheDraftAtTheSameInstant() {
        Integer durableId = durableIdOfFixtureQuestion();
        insertDraft(durableId, "Draft: ready to publish");
        OffsetDateTime publishInstant = OffsetDateTime.now();

        // The publish transaction: close current, promote draft, same instant for both.
        int closed = em.createNativeQuery(
                "UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        int promoted = em.createNativeQuery(
                "UPDATE survey.questions SET is_draft = false, effective_from = ?2, effective_to = ?3 "
                        + "WHERE question_id = ?1 AND is_draft = true")
                .setParameter(1, durableId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.flush();

        assertEquals(1, closed, "Exactly one row must be closed");
        assertEquals(1, promoted, "Exactly one draft must be promoted");

        long currentRowCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableId).setParameter(2, MAX_SENTINEL).getSingleResult()).longValue();
        assertEquals(1, currentRowCount, "Exactly one current row must exist after publish");

        String textAtExactlyPublishInstant = (String) em.createNativeQuery(
                "SELECT text FROM survey.questions WHERE question_id = ?1 AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableId).setParameter(2, publishInstant).getSingleResult();
        assertEquals("Draft: ready to publish", textAtExactlyPublishInstant,
                "At ts = publish_ts exactly, the NEW row must be visible (Q-R1: effective_to = ts excludes the old row, not >)");
    }

    @Test
    @TestTransaction
    void secondConcurrentDraft_isRejectedBeforePublish() {
        Integer durableId = durableIdOfFixtureQuestion();
        insertDraft(durableId, "First draft");
        try {
            insertDraft(durableId, "Second, conflicting draft");
            em.flush();
            fail("questions_one_draft_un must reject a second open draft for the same question_id (Q-A5)");
        } catch (Exception expected) {
            // expected — service layer surfaces this as "a draft already exists"
        }
    }

    private Integer durableIdOfFixtureQuestion() {
        Integer surrogateId = ScdFixtureIds.questionId(em);
        return (Integer) em.createNativeQuery("SELECT question_id FROM survey.questions WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult();
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
}
