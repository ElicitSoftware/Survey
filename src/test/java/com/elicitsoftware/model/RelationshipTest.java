package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.LogCapture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions - Relationship.evaluateOperator() drives conditional
 * display/skip logic. Built entirely from in-memory entities (no database round trip),
 * following the pattern already used by ElicitAnswerFixtures for the same reason: none
 * of this logic reads anything beyond the object graph handed to it.
 */
class RelationshipTest {

    private static Relationship relationship(String operatorName, String referenceValue) {
        Relationship relationship = new Relationship();
        OperatorType operatorType = new OperatorType();
        operatorType.name = operatorName;
        relationship.operatorType = operatorType;
        relationship.referenceValue = referenceValue;
        return relationship;
    }

    // The upstream answer pins the question version the respondent saw, so evaluateOperator
    // reads the question type from the answer rather than resolving the rule's durable
    // upstream id again (the rule's endpoints are plain durable-id columns).
    private static Answer answerWithText(String text) {
        // Any type that is not DATE_PICKER takes the numeric branch; INTEGER is a seeded one.
        return answerOfType("INTEGER", text);
    }

    // DATE_PICKER, not "DATE": the latter is a question_types.data_type value and the name of no
    // seeded type, so while isDateQuestion compared against it no comparison ever read two dates
    // (ElicitSoftware/Author#10).
    private static Answer dateAnswerWithText(String text) {
        return answerOfType("DATE_PICKER", text);
    }

    private static Answer answerOfType(String typeName, String text) {
        QuestionType questionType = new QuestionType();
        questionType.name = typeName;
        Question question = new Question();
        question.questionType = questionType;
        Answer answer = new Answer();
        answer.question = question;
        answer.setTextValue(text);
        return answer;
    }

    // UC-002: BOOLEAN operator parses the answer text as a boolean
    @Test
    void evaluateOperator_booleanTrue_returnsTrue() {
        assertTrue(relationship("BOOLEAN", null).evaluateOperator(answerWithText("true")));
    }

    @Test
    void evaluateOperator_booleanNonTrueText_returnsFalse() {
        assertFalse(relationship("BOOLEAN", null).evaluateOperator(answerWithText("nope")));
    }

    // UC-002: LESS THAN on a DATE_PICKER question compares dates with strict less-than
    @Test
    void evaluateOperator_lessThanDate_answerBeforeReference_returnsTrue() {
        Relationship r = relationship("LESS THAN", "2020-06-15");
        assertTrue(r.evaluateOperator(dateAnswerWithText("2020-01-01")));
    }

    @Test
    void evaluateOperator_lessThanDate_answerAfterReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", "2020-06-15");
        assertFalse(r.evaluateOperator(dateAnswerWithText("2020-12-31")));
    }

    // UC-002: an unparseable reference date falls back to "now" as the comparison date
    @Test
    void evaluateOperator_lessThanDate_unparseableReference_fallsBackToNow() {
        Relationship r = relationship("LESS THAN", "not-a-date");
        // An answer date far in the past is always "less than" the fallback of now().
        assertTrue(r.evaluateOperator(dateAnswerWithText("2000-01-01")));
    }

    // UC-002: LESS THAN on a non-date question is strict, matching the date branch above.
    @Test
    void evaluateOperator_lessThanNumeric_valueBelowReference_returnsTrue() {
        Relationship r = relationship("LESS THAN", "10");
        assertTrue(r.evaluateOperator(answerWithText("5")));
    }

    @Test
    void evaluateOperator_lessThanNumeric_valueEqualsReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", "10");
        assertFalse(r.evaluateOperator(answerWithText("10")));
    }

    @Test
    void evaluateOperator_lessThanNumeric_valueAboveReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", "10");
        assertFalse(r.evaluateOperator(answerWithText("15")));
    }

    @Test
    void evaluateOperator_lessThanNumeric_nullReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", null);
        assertFalse(r.evaluateOperator(answerWithText("5")));
    }

    // UC-002: GREATER THAN on a DATE_PICKER question uses >= (compareTo > -1), not strict >
    @Test
    void evaluateOperator_greaterThanDate_answerEqualsReference_returnsTrue() {
        Relationship r = relationship("GREATER THAN", "2020-06-15");
        assertTrue(r.evaluateOperator(dateAnswerWithText("2020-06-15")));
    }

    @Test
    void evaluateOperator_greaterThanDate_answerBeforeReference_returnsFalse() {
        Relationship r = relationship("GREATER THAN", "2020-06-15");
        assertFalse(r.evaluateOperator(dateAnswerWithText("2020-01-01")));
    }

    // UC-002: GREATER THAN on a non-date question is strict, unlike the date branch above
    // (which deliberately stays inclusive) -- the two are intentionally asymmetric.
    @Test
    void evaluateOperator_greaterThanNumeric_valueAboveReference_returnsTrue() {
        Relationship r = relationship("GREATER THAN", "10");
        assertTrue(r.evaluateOperator(answerWithText("15")));
    }

    @Test
    void evaluateOperator_greaterThanNumeric_valueEqualsReference_returnsFalse() {
        Relationship r = relationship("GREATER THAN", "10");
        assertFalse(r.evaluateOperator(answerWithText("10")));
    }

    // UC-002: EQUAL compares case-insensitively and requires a non-null reference value
    @Test
    void evaluateOperator_equal_caseInsensitiveMatch_returnsTrue() {
        Relationship r = relationship("EQUAL", "Yes");
        assertTrue(r.evaluateOperator(answerWithText("YES")));
    }

    @Test
    void evaluateOperator_equal_mismatch_returnsFalse() {
        Relationship r = relationship("EQUAL", "Yes");
        assertFalse(r.evaluateOperator(answerWithText("No")));
    }

    @Test
    void evaluateOperator_equal_nullReference_returnsFalse() {
        Relationship r = relationship("EQUAL", null);
        assertFalse(r.evaluateOperator(answerWithText("Yes")));
    }

    // UC-002: NOT_EQUAL is the inverse of EQUAL, guarded on a non-null answer text
    @Test
    void evaluateOperator_notEqual_differentText_returnsTrue() {
        Relationship r = relationship("NOT_EQUAL", "Yes");
        assertTrue(r.evaluateOperator(answerWithText("No")));
    }

    @Test
    void evaluateOperator_notEqual_sameTextCaseInsensitive_returnsFalse() {
        Relationship r = relationship("NOT_EQUAL", "Yes");
        assertFalse(r.evaluateOperator(answerWithText("YES")));
    }

    @Test
    void evaluateOperator_notEqual_nullAnswerText_returnsFalse() {
        Relationship r = relationship("NOT_EQUAL", "Yes");
        assertFalse(r.evaluateOperator(answerWithText(null)));
    }

    // UC-002: FIELD_EXIST is always true, regardless of the answer's text value
    @Test
    void evaluateOperator_fieldExist_alwaysReturnsTrue() {
        Relationship r = relationship("FIELD_EXIST", null);
        assertTrue(r.evaluateOperator(answerWithText(null)));
    }

    // UC-002: CONTAINS treats the answer text as a comma-separated list
    @Test
    void evaluateOperator_contains_referenceInList_returnsTrue() {
        Relationship r = relationship("CONTAINS", "b");
        assertTrue(r.evaluateOperator(answerWithText("a,b,c")));
    }

    @Test
    void evaluateOperator_contains_referenceNotInList_returnsFalse() {
        Relationship r = relationship("CONTAINS", "z");
        assertFalse(r.evaluateOperator(answerWithText("a,b,c")));
    }

    // UC-002: an unrecognized operator name falls through to the default false
    @Test
    void evaluateOperator_unknownOperator_returnsFalse() {
        Relationship r = relationship("SOMETHING_UNKNOWN", "x");
        assertFalse(r.evaluateOperator(answerWithText("x")));
    }

    // UC-002: a type-conversion failure (non-numeric text on a numeric question) returns false
    // rather than throwing, and is logged at WARN naming the rule but not the respondent's text
    @Test
    void evaluateOperator_numericParseFailure_isLoggedAndReturnsFalse() {
        Relationship r = relationship("LESS THAN", "10");
        r.relationshipId = 41;
        r.surveyId = 3;
        try (LogCapture log = LogCapture.of(Relationship.class)) {
            assertFalse(r.evaluateOperator(answerWithText("not-a-number")));

            List<String> warnings = log.messages(LogCapture.WARN);
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Rule 41 of survey 3 could not be evaluated (LESS THAN 10): NumberFormatException"));
            assertFalse(warnings.get(0).contains("not-a-number"), "the respondent's answer text is not logged");
        }
    }

    // UC-002: an unanswered question satisfies no comparison, and that is not an error to log
    @Test
    void evaluateOperator_unansweredQuestion_returnsFalseWithoutAWarning() {
        try (LogCapture log = LogCapture.of(Relationship.class)) {
            for (String operator : List.of("LESS THAN", "GREATER THAN", "EQUAL", "NOT_EQUAL", "CONTAINS", "BOOLEAN")) {
                assertFalse(relationship(operator, "1").evaluateOperator(answerWithText(null)), operator);
            }
            assertEquals(List.of(), log.messages(LogCapture.WARN));
        }
        assertTrue(relationship("FIELD_EXIST", null).evaluateOperator(answerWithText(null)));
    }
}
