package com.elicitsoftware;

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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-017 on the library fixture (V9005): a cleared answer is stored as unanswered and the
 * rules that read it are recalculated as for an unanswered question.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class QuestionManagerClearAnswerTest {

    static final int SURVEY_ID = 1;
    static final String WELCOME_SECTION = QuestionManagerTest.WELCOME_SECTION;
    static final String COLLPREFS_SECTION = QuestionManagerTest.COLLPREFS_SECTION;
    static final String TERMS_DK = QuestionManagerTest.TERMS_DK;
    static final String DIGITAL_ACCESS_DK = QuestionManagerTest.DIGITAL_ACCESS_DK;
    static final String CHECKOUT_QTY_DK = QuestionManagerTest.CHECKOUT_QTY_DK;

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    /** QuestionService.saveAnswer without its UI-scoped dependencies, as QuestionManagerTest does. */
    void saveAnswer(Answer answer, String newValue) {
        Answer a = Answer.findById(answer.id);
        a.setTextValue(newValue);
        em.flush();
        questionManager.deleteDownstreamAnswers(answer.respondentId, a, a.id);
        questionManager.buildDownstreamQuestions(a);
    }

    Respondent freshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.accessCode = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);
        saveAnswer(Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK), "TRUE");
        return r;
    }

    @Test
    @TestTransaction
    void clearedRepeatCountRemovesEveryInstance() {
        // UC-002 BR-017, A3: a cleared count is zero. Before, the trim called isBlank() on null.
        Respondent r = freshRespondent();
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);
        saveAnswer(Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK), "2");
        assertTrue(Answer.count("respondentId = ?1 and displayKey like '0001-0004%' and deleted = false", r.id) > 0);

        Answer qty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        assertDoesNotThrow(() -> saveAnswer(qty, null));

        assertNull(Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK).getTextValue(),
                "a cleared answer is stored as unanswered");
        assertEquals(0, Answer.count(
                "respondentId = ?1 and displayKey like '0001-0004%' and deleted = false", r.id),
                "every repeated instance goes when the count is cleared");
    }

    @Test
    @TestTransaction
    void clearedAnswerNoLongerMeetsTheConditionOfARule() {
        // UC-002 BR-017, A2: the digital section is shown for digital access TRUE (EQUAL) and goes
        // when the answer is cleared, as for a question never answered.
        Respondent r = freshRespondent();
        saveAnswer(Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK), "TRUE");
        assertTrue(Answer.count(
                "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id) > 0);

        saveAnswer(Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK), null);

        assertEquals(0, Answer.count(
                "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id),
                "the section shown by the cleared answer is removed");
    }

    @Test
    void repeatCountOfAnUnansweredQuestionIsZero() {
        // UC-002 BR-017: the count a cleared or never-answered repeat question gives.
        Answer a = new Answer();
        a.setTextValue(null);
        assertEquals(0, QuestionManager.repeatCount(a));
        a.setTextValue("");
        assertEquals(0, QuestionManager.repeatCount(a));
        a.setTextValue(" 3 ");
        assertEquals(3, QuestionManager.repeatCount(a));
    }
}
