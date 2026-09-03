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
 * Executable spec for {@code survey.sections_questions} — research/Kimball_type_2.md,
 * "Schema Changes Per Table &rarr; sections_questions" and the "FK Reference Migration
 * Summary". This is the core "FK cascade problem" claim: a join-table row referencing a
 * durable id must survive a version change on either side untouched.
 */
@QuarkusTest
@Disabled("Enable once the sections_questions Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> sections_questions'")
class SectionsQuestionsCascadeSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsDurableKeyVersionColumnsAndFkCompanions() {
        long cols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='sections_questions' "
                        + "AND column_name IN ('sections_question_id','version','effective_from','effective_to','is_draft',"
                        + "'question_version','section_version')").getSingleResult()).longValue();
        assertEquals(7, cols);
    }

    @Test
    @TestTransaction
    void fkCompanionColumns_arePinnedToZero() {
        Integer sqId = ScdFixtureIds.sectionsQuestionId(em);
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("UPDATE survey.sections_questions SET question_version = 1 WHERE id = ?1")
                        .setParameter(1, sqId).executeUpdate(),
                "sections_questions_ref_versions_ck must reject question_version != 0");
    }

    @Test
    @TestTransaction
    void rewordingTheReferencedQuestion_leavesTheJoinRowUntouchedAndResolvesTheNewVersionAtNow() {
        Integer sqSurrogateId = ScdFixtureIds.sectionsQuestionId(em);
        Integer questionSurrogateId = ScdFixtureIds.questionId(em);
        Integer durableQuestionId = (Integer) em.createNativeQuery(
                "SELECT question_id FROM survey.questions WHERE id = ?1").setParameter(1, questionSurrogateId).getSingleResult();
        Integer durableSqId = (Integer) em.createNativeQuery(
                "SELECT question_id FROM survey.sections_questions WHERE id = ?1").setParameter(1, sqSurrogateId).getSingleResult();
        assertEquals(durableQuestionId, durableSqId, "sections_questions.question_id already stores the durable id");

        int currentVersion = ((Number) em.createNativeQuery("SELECT version FROM survey.questions WHERE id = ?1")
                .setParameter(1, questionSurrogateId).getSingleResult()).intValue();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();
        OffsetDateTime publishInstant = OffsetDateTime.now().plusSeconds(5);

        // Reword the question: close current, insert new version. No statement touches
        // sections_questions at all.
        em.createNativeQuery("UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableQuestionId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                        + "select_group_id, effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.questions_seq'), question_id, ?2, survey_id, type_id, 'Reworded via cascade test', required, "
                        + "select_group_id, ?3, ?4, false FROM survey.questions WHERE question_id = ?1 AND version = ?5")
                .setParameter(1, durableQuestionId).setParameter(2, currentVersion + 1).setParameter(3, publishInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        // The sections_questions row itself: zero writes since it was created.
        long sqRowCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.sections_questions WHERE question_id = ?1")
                .setParameter(1, durableQuestionId).getSingleResult()).longValue();
        assertEquals(1, sqRowCount, "Exactly the original sections_questions row must still exist -- no cascade insert/update");

        String wordingAtFirstAccess = wordingResolvedThroughJoin(durableQuestionId, firstAccessDt);
        String wordingNow = wordingResolvedThroughJoin(durableQuestionId, OffsetDateTime.now().plusSeconds(10));

        assertEquals("Original wording", wordingAtFirstAccess,
                "A respondent pinned to firstAccessDt before the reword must resolve the old wording through the durable-key join");
        assertEquals("Reworded via cascade test", wordingNow,
                "A NOW() query must resolve the new wording through the same, unchanged sections_questions row");
    }

    private String wordingResolvedThroughJoin(Integer durableQuestionId, OffsetDateTime ts) {
        return (String) em.createNativeQuery(
                "SELECT q.text FROM survey.sections_questions sq "
                        + "JOIN survey.questions q ON q.question_id = sq.question_id "
                        + "AND q.effective_from <= ?2 AND q.effective_to > ?2 "
                        + "WHERE sq.question_id = ?1")
                .setParameter(1, durableQuestionId).setParameter(2, ts).getSingleResult();
    }

    @Test
    @TestTransaction
    void orphanedDurableQuestionId_isRejectedByTheCompositeFk() {
        Integer sqId = ScdFixtureIds.sectionsQuestionId(em);
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("UPDATE survey.sections_questions SET question_id = -999999 WHERE id = ?1")
                        .setParameter(1, sqId).executeUpdate(),
                "sections_questions_question_id_fkey must reject a question_id with no (question_id, version=0) row in questions");
    }
}
