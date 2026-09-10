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

import com.elicitsoftware.DisplayKey;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-002: Answer Survey Questions - Answer's instance methods (type conversion, key
 * derivation, select-item lookup) never query Panache themselves, so they're exercised
 * entirely with in-memory objects, following the same reasoning as ElicitAnswerFixtures.
 * The static finder methods and purgeDeleted need a real database and are left to the
 * existing QuarkusTest coverage elsewhere.
 */
class AnswerTest {

    private static SelectItem item(String codedValue) {
        SelectItem item = new SelectItem();
        item.codedValue = codedValue;
        return item;
    }

    private static Answer answerWithQuestion(QuestionType type, List<SelectItem> items) {
        Answer answer = new Answer();
        Question question = new Question();
        question.questionType = type;
        SelectGroup group = new SelectGroup();
        group.selectItems = items;
        question.selectGroup = group;
        answer.question = question;
        return answer;
    }

    private static QuestionType typeNamed(String name) {
        QuestionType type = new QuestionType();
        type.name = name;
        return type;
    }

    // --- constructors ---

    @Test
    void constructor_withKeyAndSectionsQuestion_populatesFieldsFromBoth() {
        DisplayKey key = new DisplayKey("1-2-0-3-0-4-0");
        SectionsQuestion sq = new SectionsQuestion();
        sq.id = 99;
        Question question = new Question();
        sq.question = question;

        Answer answer = new Answer(key, sq, "Display text", 7);

        assertEquals("Display text", answer.displayText);
        assertEquals(7, answer.respondentId);
        assertSame(question, answer.question);
        assertEquals(99, answer.section_question_id);
        assertEquals(1, answer.surveyId);
        assertEquals(3, answer.sectionId);
    }

    @Test
    void constructor_withNullSectionsQuestion_leavesQuestionAndSectionQuestionIdNull() {
        DisplayKey key = new DisplayKey("1-2-0-0-0-0-0");
        Answer answer = new Answer(key, null, "text", 7);

        assertNull(answer.question);
        assertNull(answer.section_question_id);
    }

    @Test
    void constructor_zeroValuedQuestionSegment_sectionQuestionIdBecomesNull() {
        // setDisplayKeyValues()'s own comment says "some of the foreign keys are nullable... in
        // the Display key they are value 0 but in this class null" -- but valueOrNull() is only
        // actually applied to section_question_id below. sectionId/sectionInstance are assigned
        // directly and stay literal 0, not null, for a zero-valued key segment; pinning the real
        // behavior rather than the comment's broader claim.
        DisplayKey key = new DisplayKey("1-2-0-3-0-0-0");

        Answer answer = new Answer(key, null, "text", 7);

        assertNull(answer.section_question_id);
        assertEquals(0, answer.sectionInstance);
        assertEquals(3, answer.sectionId);
    }

    @Test
    void constructor_withTextValue_splitsIntoTextArrayAndStampsSavedDt() {
        DisplayKey key = new DisplayKey("1-1-0-1-0-1-0");
        Answer answer = new Answer(key, null, "text", 7, "a,b,c");

        assertEquals("a,b,c", answer.getTextValue());
        assertEquals(List.of("a", "b", "c"), answer.textArray);
        assertNotNull(answer.savedDt);
    }

    @Test
    void constructor_withNullTextValue_leavesTextArrayAndSavedDtUntouched() {
        DisplayKey key = new DisplayKey("1-1-0-1-0-1-0");
        Answer answer = new Answer(key, null, "text", 7, null);

        assertNull(answer.getTextValue());
        assertNull(answer.savedDt);
    }

    // --- getKey/getDisplayKey ---

    @Test
    void getKey_wrapsRawDisplayKeyString() {
        Answer answer = new Answer();
        answer.displayKey = "1-2-0-3-0-4-0";

        DisplayKey key = answer.getKey();
        assertEquals(2, key.getStep());
        assertEquals(4, key.getQuestion());
    }

    // --- numeric conversions ---

    @Test
    void getDouble_validNumericText_parsesSuccessfully() {
        Answer answer = new Answer();
        answer.setTextValue("3.14");
        assertEquals(3.14, answer.getDouble());
    }

    @Test
    void getDouble_nonNumericText_swallowsAndReturnsZero() {
        Answer answer = new Answer();
        answer.setTextValue("not-a-number");
        assertEquals(0, answer.getDouble());
    }

    @Test
    void setDouble_storesStringRepresentationInTextValue() {
        Answer answer = new Answer();
        answer.setDouble(2.5);
        assertEquals("2.5", answer.getTextValue());
    }

    @Test
    void getInteger_validNumericText_parsesSuccessfully() {
        Answer answer = new Answer();
        answer.setTextValue("42");
        assertEquals(42, answer.getInteger());
    }

    @Test
    void getInteger_nonNumericText_swallowsAndReturnsZero() {
        Answer answer = new Answer();
        answer.setTextValue("nope");
        assertEquals(0, answer.getInteger());
    }

    @Test
    void setInteger_storesStringRepresentationInTextValue() {
        Answer answer = new Answer();
        answer.setInteger(7);
        assertEquals("7", answer.getTextValue());
    }

    // --- date/time conversions ---

    @Test
    void localDate_roundTripsThroughTextValue() {
        Answer answer = new Answer();
        LocalDate date = LocalDate.of(2020, 6, 15);
        answer.setLocalDate(date);
        assertEquals(date, answer.getLocalDate());
    }

    @Test
    void localDateTime_roundTripsThroughTextValue() {
        Answer answer = new Answer();
        LocalDateTime dateTime = LocalDateTime.of(2020, 6, 15, 10, 30);
        answer.setLocalDateTime(dateTime);
        assertEquals(dateTime, answer.getLocalDateTime());
    }

    @Test
    void localTime_roundTripsThroughTextValue() {
        Answer answer = new Answer();
        LocalTime time = LocalTime.of(10, 30);
        answer.setLocalTime(time);
        assertEquals(time, answer.getLocalTime());
    }

    // --- getSelectedItem/setSelectedItem/getSelectedItems/setSelectedItems ---
    //
    // KNOWN BUG, pinned rather than fixed here: these four methods compare a QuestionType
    // *entity* against a GlobalStrings String constant via `question.questionType.equals(...)`.
    // QuestionType never overrides equals()/hashCode() (confirmed: PanacheEntityBase doesn't
    // either), so comparing it to a String always falls back to Object identity and can never
    // be true. In practice this means ElicitComboBox/ElicitRadioButtonGroup (bound via
    // Answer::getSelectedItem/setSelectedItem) and ElicitCheckboxGroup/
    // ElicitMultiSelectComboBox (bound via the plural forms) never actually read or write a
    // selection through these methods today - a real, currently-existing bug this test suite
    // did not previously cover. Fixing the comparison (e.g. questionType.name.equals(...)) is
    // out of scope for a coverage-raising pass; flagging prominently instead.

    @Test
    void getSelectedItem_radioQuestionWithMatchingCodedValue_currentlyAlwaysReturnsNull() {
        Answer answer = answerWithQuestion(typeNamed("RADIO"), List.of(item("YES")));
        answer.setTextValue("YES");

        assertNull(answer.getSelectedItem());
    }

    @Test
    void setSelectedItem_radioQuestion_currentlyNeverUpdatesTextValue() {
        Answer answer = answerWithQuestion(typeNamed("RADIO"), List.of(item("YES")));
        answer.setTextValue(null);

        answer.setSelectedItem(item("YES"));

        assertNull(answer.getTextValue());
    }

    @Test
    void getSelectedItems_checkboxGroupWithMatchingCodedValues_currentlyAlwaysReturnsNull() {
        Answer answer = answerWithQuestion(typeNamed("CHECKBOX_GROUP"), List.of(item("A"), item("B")));
        answer.setTextValue("A,B");

        assertNull(answer.getSelectedItems());
    }

    @Test
    void setSelectedItems_checkboxGroup_currentlyNeverUpdatesTextValue() {
        Answer answer = answerWithQuestion(typeNamed("CHECKBOX_GROUP"), List.of(item("A"), item("B")));
        answer.setTextValue(null);

        answer.setSelectedItems(Set.of(item("A")));

        assertNull(answer.getTextValue());
    }
}
