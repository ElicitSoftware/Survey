package com.elicitsoftware.model;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions - Relationship.evaluateOperator() drives conditional
 * display/skip logic. Built entirely from in-memory entities (no database round trip),
 * following the pattern already used by ElicitAnswerFixtures for the same reason: none
 * of this logic reads anything beyond the object graph handed to it.
 */
class RelationshipTest {

    private static Relationship relationship(String operatorName, String referenceValue, boolean dateQuestion) {
        Relationship relationship = new Relationship();
        OperatorType operatorType = new OperatorType();
        operatorType.name = operatorName;
        relationship.operatorType = operatorType;
        relationship.referenceValue = referenceValue;

        QuestionType questionType = new QuestionType();
        questionType.name = dateQuestion ? "DATE" : "NUMBER";
        Question question = new Question();
        question.questionType = questionType;
        SectionsQuestion sq = new SectionsQuestion();
        sq.question = question;
        relationship.upstreamQuestion = sq;
        return relationship;
    }

    private static Answer answerWithText(String text) {
        Answer answer = new Answer();
        answer.setTextValue(text);
        return answer;
    }

    // UC-002: BOOLEAN operator parses the answer text as a boolean
    @Test
    void evaluateOperator_booleanTrue_returnsTrue() {
        assertTrue(relationship("BOOLEAN", null, false).evaluateOperator(answerWithText("true")));
    }

    @Test
    void evaluateOperator_booleanNonTrueText_returnsFalse() {
        assertFalse(relationship("BOOLEAN", null, false).evaluateOperator(answerWithText("nope")));
    }

    // UC-002: LESS THAN on a DATE question compares dates with strict less-than
    @Test
    void evaluateOperator_lessThanDate_answerBeforeReference_returnsTrue() {
        Relationship r = relationship("LESS THAN", "2020-06-15", true);
        assertTrue(r.evaluateOperator(answerWithText("2020-01-01")));
    }

    @Test
    void evaluateOperator_lessThanDate_answerAfterReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", "2020-06-15", true);
        assertFalse(r.evaluateOperator(answerWithText("2020-12-31")));
    }

    // UC-002: an unparseable reference date falls back to "now" as the comparison date
    @Test
    void evaluateOperator_lessThanDate_unparseableReference_fallsBackToNow() {
        Relationship r = relationship("LESS THAN", "not-a-date", true);
        // An answer date far in the past is always "less than" the fallback of now().
        assertTrue(r.evaluateOperator(answerWithText("2000-01-01")));
    }

    // UC-002: LESS THAN on a non-date question. Note: the numeric branch actually
    // evaluates `dValue >= rVal` -- the same comparison GREATER THAN's numeric branch
    // uses below -- so for non-date questions "LESS THAN" and "GREATER THAN" currently
    // behave identically. This pins the existing behavior rather than the operator's name.
    @Test
    void evaluateOperator_lessThanNumeric_valueAtOrAboveReference_returnsTrue() {
        Relationship r = relationship("LESS THAN", "10", false);
        assertTrue(r.evaluateOperator(answerWithText("10")));
    }

    @Test
    void evaluateOperator_lessThanNumeric_valueBelowReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", "10", false);
        assertFalse(r.evaluateOperator(answerWithText("5")));
    }

    @Test
    void evaluateOperator_lessThanNumeric_nullReference_returnsFalse() {
        Relationship r = relationship("LESS THAN", null, false);
        assertFalse(r.evaluateOperator(answerWithText("5")));
    }

    // UC-002: GREATER THAN on a DATE question uses >= (compareTo > -1), not strict >
    @Test
    void evaluateOperator_greaterThanDate_answerEqualsReference_returnsTrue() {
        Relationship r = relationship("GREATER THAN", "2020-06-15", true);
        assertTrue(r.evaluateOperator(answerWithText("2020-06-15")));
    }

    @Test
    void evaluateOperator_greaterThanDate_answerBeforeReference_returnsFalse() {
        Relationship r = relationship("GREATER THAN", "2020-06-15", true);
        assertFalse(r.evaluateOperator(answerWithText("2020-01-01")));
    }

    @Test
    void evaluateOperator_greaterThanNumeric_valueAtReference_returnsTrue() {
        Relationship r = relationship("GREATER THAN", "10", false);
        assertTrue(r.evaluateOperator(answerWithText("10")));
    }

    // UC-002: EQUAL compares case-insensitively and requires a non-null reference value
    @Test
    void evaluateOperator_equal_caseInsensitiveMatch_returnsTrue() {
        Relationship r = relationship("EQUAL", "Yes", false);
        assertTrue(r.evaluateOperator(answerWithText("YES")));
    }

    @Test
    void evaluateOperator_equal_mismatch_returnsFalse() {
        Relationship r = relationship("EQUAL", "Yes", false);
        assertFalse(r.evaluateOperator(answerWithText("No")));
    }

    @Test
    void evaluateOperator_equal_nullReference_returnsFalse() {
        Relationship r = relationship("EQUAL", null, false);
        assertFalse(r.evaluateOperator(answerWithText("Yes")));
    }

    // UC-002: NOT_EQUAL is the inverse of EQUAL, guarded on a non-null answer text
    @Test
    void evaluateOperator_notEqual_differentText_returnsTrue() {
        Relationship r = relationship("NOT_EQUAL", "Yes", false);
        assertTrue(r.evaluateOperator(answerWithText("No")));
    }

    @Test
    void evaluateOperator_notEqual_sameTextCaseInsensitive_returnsFalse() {
        Relationship r = relationship("NOT_EQUAL", "Yes", false);
        assertFalse(r.evaluateOperator(answerWithText("YES")));
    }

    @Test
    void evaluateOperator_notEqual_nullAnswerText_returnsFalse() {
        Relationship r = relationship("NOT_EQUAL", "Yes", false);
        assertFalse(r.evaluateOperator(answerWithText(null)));
    }

    // UC-002: FIELD_EXIST is always true, regardless of the answer's text value
    @Test
    void evaluateOperator_fieldExist_alwaysReturnsTrue() {
        Relationship r = relationship("FIELD_EXIST", null, false);
        assertTrue(r.evaluateOperator(answerWithText(null)));
    }

    // UC-002: CONTAINS treats the answer text as a comma-separated list
    @Test
    void evaluateOperator_contains_referenceInList_returnsTrue() {
        Relationship r = relationship("CONTAINS", "b", false);
        assertTrue(r.evaluateOperator(answerWithText("a,b,c")));
    }

    @Test
    void evaluateOperator_contains_referenceNotInList_returnsFalse() {
        Relationship r = relationship("CONTAINS", "z", false);
        assertFalse(r.evaluateOperator(answerWithText("a,b,c")));
    }

    // UC-002: an unrecognized operator name falls through to the default false
    @Test
    void evaluateOperator_unknownOperator_returnsFalse() {
        Relationship r = relationship("SOMETHING_UNKNOWN", "x", false);
        assertFalse(r.evaluateOperator(answerWithText("x")));
    }

    // UC-002: a type-conversion failure (non-numeric text on a numeric question) is
    // swallowed by the outer catch and returns the default false rather than throwing
    @Test
    void evaluateOperator_numericParseFailure_isSwallowedAndReturnsFalse() {
        Relationship r = relationship("LESS THAN", "10", false);
        assertFalse(r.evaluateOperator(answerWithText("not-a-number")));
    }
}
