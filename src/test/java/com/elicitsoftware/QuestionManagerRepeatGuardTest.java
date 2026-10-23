package com.elicitsoftware;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.ActionType;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Dependent;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.QuestionType;
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.model.Step;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions, BR-008, BR-012 and BR-013 — the parts of QuestionManager's
 * Repeat and rule handling that are decided before any structural row is read: the instances a
 * count asks for, that a count has no item list, that an instance is only ever tied to an item
 * when the rule reads a multi-select, the sibling-rule step key when the rule names its own
 * upstream step, and the not-yet-implemented step repeat.
 * <p>
 * The helpers are private and reached by reflection. The per-item branches resolve the select
 * list as of the respondent's snapshot anchor (a Panache finder) and are out of reach here.
 */
class QuestionManagerRepeatGuardTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = QuestionManager.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(new QuestionManager(), args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static Question question(String type, Integer selectGroupId) {
        QuestionType questionType = new QuestionType();
        questionType.name = type;
        Question question = new Question();
        question.questionType = questionType;
        question.selectGroupId = selectGroupId;
        return question;
    }

    private static Answer answer(Question question, String value) {
        Answer answer = new Answer();
        answer.question = question;
        answer.respondentId = 5;
        answer.displayKey = "0001-0001-0000-0001-0000-0001-0000";
        answer.setTextValue(value);
        return answer;
    }

    private static Relationship rule(String action) {
        ActionType actionType = new ActionType();
        actionType.name = action;
        Relationship rule = new Relationship();
        rule.actionType = actionType;
        rule.surveyId = 1;
        return rule;
    }

    // ---- repeatInstances / repeatItems ---------------------------------------------------------

    @Test
    void given_aCountAnswer_when_repeatInstances_then_oneToN() throws Exception {
        Object instances = invoke("repeatInstances", new Class<?>[]{Answer.class},
                answer(question("INTEGER", null), "3"));

        assertEquals(List.of(1, 2, 3), instances);
    }

    @Test
    void given_anUnansweredCount_when_repeatInstances_then_none() throws Exception {
        assertEquals(List.of(), invoke("repeatInstances", new Class<?>[]{Answer.class},
                answer(question("INTEGER", null), null)));
    }

    @Test
    void given_aQuestionThatIsNotASelectionOfSeveral_when_repeatItems_then_noListIsRead() throws Exception {
        // A count, and a multi-select with no list: neither numbers its instances by position.
        assertEquals(List.of(), invoke("repeatItems", new Class<?>[]{Answer.class},
                answer(question("INTEGER", 4), "2")));
        assertEquals(List.of(), invoke("repeatItems", new Class<?>[]{Answer.class},
                answer(question("MULTI_SELECT", null), "a,b")));
    }

    // ---- repeatedItem --------------------------------------------------------------------------

    private static Object repeatedItem(Dependent dependent) throws Exception {
        return invoke("repeatedItem", new Class<?>[]{Dependent.class}, dependent);
    }

    @Test
    void given_aDependentMissingAnEnd_when_repeatedItem_then_null() throws Exception {
        Answer multiSelect = answer(question("MULTI_SELECT", 7), "a");
        Answer downstream = answer(question("TEXT", null), null);

        assertNull(repeatedItem(new Dependent(5, null, downstream, rule("TEXT"))));
        assertNull(repeatedItem(new Dependent(5, multiSelect, null, rule("TEXT"))));
    }

    @Test
    void given_anUpstreamCount_when_repeatedItem_then_noItemFillsTheToken() throws Exception {
        // BR-013 applies only inside an instance built from a selected item; a count builds none.
        Answer count = answer(question("INTEGER", null), "2");
        Answer downstream = answer(question("TEXT", null), null);

        Object item = repeatedItem(new Dependent(5, count, downstream, rule("REPEAT")));

        assertNull(item);
    }

    // ---- upstreamDurableStepId -----------------------------------------------------------------

    @Test
    void given_aRuleNamingItsUpstreamStep_when_upstreamDurableStepId_then_thatDurableIdIsUsedAsIs()
            throws Exception {
        Relationship rule = rule("SHOW");
        rule.upstreamStepId = 4242;
        Answer answer = answer(question("TEXT", null), "x");
        // The answer's own step is a display order and must not be taken for the durable id.
        answer.stepId = java.math.BigDecimal.ONE;

        assertEquals(4242, invoke("upstreamDurableStepId",
                new Class<?>[]{Relationship.class, Answer.class}, rule, answer));
    }

    // ---- buildRepeatedStep ---------------------------------------------------------------------

    @Test
    void given_aStepRepeat_when_buildRepeatedStep_then_itIsLoggedAsNotImplementedAndBuildsNothing()
            throws Exception {
        HashMap<Integer, Dependent> dependents = new HashMap<>();
        try (LogCapture log = LogCapture.of(QuestionManager.class)) {
            invoke("buildRepeatedStep", new Class<?>[]{Answer.class, Step.class, Integer.class, HashMap.class},
                    answer(question("INTEGER", null), "2"), new Step(), 3, dependents);

            assertTrue(log.contains(LogCapture.INFO, "buildRepeatedStep not yet implemented"));
        }
        assertTrue(dependents.isEmpty());
    }

    // ---- escapeHtml ----------------------------------------------------------------------------

    @Test
    void given_aNullValue_when_escapeHtml_then_emptyString() throws Exception {
        assertEquals("", invoke("escapeHtml", new Class<?>[]{String.class}, (Object) null));
        assertEquals("&amp;&lt;&gt;&quot;&#39;", invoke("escapeHtml", new Class<?>[]{String.class}, "&<>\"'"));
    }
}
