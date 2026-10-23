package com.elicitsoftware.flow.input;

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

import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.i18n.Translations;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.item;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.selectQuestion;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions and UC-003: Review Answers — a field shows the answer stored
 * for it when the respondent comes back, restores a multiple choice by whole coded values, checks
 * every email answer's form, and explains a failed length or range rule.
 */
class ElicitStoredAnswerAndValidationTest {

    // ── #161: the stored answer is shown again ─────────────────────────────────────────────────

    @Test
    void given_aStoredAnswer_when_aTextAreaIsBuilt_then_itShowsTheAnswer() {
        ElicitTextArea field = new ElicitTextArea(answer(InputFieldSupport.plain(), "Two dogs and a cat"), ContentTexts.base());

        assertEquals("Two dogs and a cat", field.component.getValue());
    }

    // ── #163: a multiple choice is restored by whole coded values ──────────────────────────────

    private static Answer choice(String stored, SelectItem... items) {
        Question question = selectQuestion(false, null, List.of(items));
        return ElicitAnswerFixtures.answer("1.1.1.1.3.2.9", "Pets", question, stored);
    }

    @Test
    void given_storedCode10_when_aCheckboxGroupIsBuilt_then_onlyItem10IsChecked() {
        SelectItem one = item("1", "Dog");
        SelectItem ten = item("10", "Parrot");

        assertEquals(Set.of(ten), new ElicitCheckboxGroup(choice("10", one, ten), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_storedCodes12_when_aCheckboxGroupIsBuilt_then_neitherItem1NorItem2IsChecked() {
        SelectItem one = item("1", "Dog");
        SelectItem two = item("2", "Cat");
        SelectItem twelve = item("12", "Rabbit");

        assertEquals(Set.of(twelve), new ElicitCheckboxGroup(choice("12", one, two, twelve), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_storedCode10_when_aMultiSelectIsBuilt_then_onlyItem10IsSelected() {
        SelectItem one = item("1", "Dog");
        SelectItem ten = item("10", "Parrot");

        assertEquals(Set.of(ten), new ElicitMultiSelectComboBox(choice("10", one, ten), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_storedCodes1And10_when_aMultiSelectIsBuilt_then_bothAreSelected() {
        SelectItem one = item("1", "Dog");
        SelectItem two = item("2", "Cat");
        SelectItem ten = item("10", "Parrot");

        assertEquals(Set.of(one, ten), new ElicitMultiSelectComboBox(choice("1,10", one, two, ten), ContentTexts.base()).component.getValue());
    }

    // ── #164 and #165: every email answer's form is checked, plus-addresses included ──────────

    @Test
    void given_anEmailQuestionWithoutMinOrMax_when_notAnEmail_then_theEmailMessageIsShown() {
        ElicitEmailField field = new ElicitEmailField(answer(InputFieldSupport.plain(), "not-an-email"), ContentTexts.base());

        assertEquals(List.of(Translations.get("validation.email")), errors(field.getBinder()));
    }

    @Test
    void given_anOptionalEmailQuestion_when_leftEmpty_then_itPasses() {
        ElicitEmailField field = new ElicitEmailField(answer(InputFieldSupport.plain(), null), ContentTexts.base());

        assertTrue(field.getBinder().validate().isOk());
    }

    @Test
    void given_aPlusAddress_when_validated_then_itIsAccepted() {
        ElicitEmailField field = new ElicitEmailField(answer(question(true, 5, 50, null, null), "ada+survey@example.org"),
                ContentTexts.base());

        assertTrue(field.getBinder().validate().isOk(), String.valueOf(errors(field.getBinder())));
    }

    @Test
    void given_anEmailField_when_built_then_itsOwnFormatCheckCarriesTheSameMessage() {
        ElicitEmailField field = new ElicitEmailField(answer(InputFieldSupport.plain(), null), ContentTexts.base());

        assertEquals(Translations.get("validation.email"), field.component.getI18n().getPatternErrorMessage());
    }

    // ── #166: a failed length or range rule says why ──────────────────────────────────────────

    @Test
    void given_aTextAreaWithALength_when_tooShort_then_theLengthMessageIsShown() {
        ElicitTextArea field = new ElicitTextArea(answer(question(false, 5, 10, null, null), "abc"), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.lengthFallback(5, 10)), errors(field.getBinder()));
        assertEquals(InputFieldSupport.lengthFallback(5, 10), field.component.getI18n().getMinLengthErrorMessage());
        assertEquals(InputFieldSupport.lengthFallback(5, 10), field.component.getI18n().getMaxLengthErrorMessage());
    }

    @Test
    void given_aPasswordWithALength_when_tooLong_then_theLengthMessageIsShown() {
        ElicitPasswordField field = new ElicitPasswordField(answer(question(false, 2, 4, null, null), null), ContentTexts.base());
        field.component.setValue("abcdefgh");

        assertEquals(List.of(InputFieldSupport.lengthFallback(2, 4)), errors(field.getBinder()));
        assertEquals(InputFieldSupport.lengthFallback(2, 4), field.component.getI18n().getMaxLengthErrorMessage());
    }

    @Test
    void given_aNumberWithARange_when_outOfRange_then_theRangeMessageIsShown() {
        ElicitDoubleField field = new ElicitDoubleField(answer(question(false, 1, 9, null, null), "12"), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.rangeFallback(1, 9)), errors(field.getBinder()));
        assertEquals(InputFieldSupport.rangeFallback(1, 9), field.component.getI18n().getMinErrorMessage());
        assertEquals(InputFieldSupport.rangeFallback(1, 9), field.component.getI18n().getMaxErrorMessage());
    }

    @Test
    void given_onlyAMaximumAndAuthoredText_when_built_then_theFieldsOwnCheckUsesTheAuthoredText() {
        Question q = question(false, null, 4, null, null);
        q.validationText = "Four characters at most.";
        ElicitTextArea field = new ElicitTextArea(answer(q, null), ContentTexts.base());

        assertEquals("Four characters at most.", field.component.getI18n().getMaxLengthErrorMessage());
    }
}
