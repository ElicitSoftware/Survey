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
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static com.elicitsoftware.flow.input.InputFieldSupport.themes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitTextField} restores the stored text, sizes the
 * field to its label, and enforces the per-question required and length rules (BR-004).
 */
class ElicitTextFieldTest {

    private static final String ALL_VARIANTS = String.join(",",
            TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
            TextFieldVariant.LUMO_SMALL.getVariantName(),
            TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

    @Test
    void given_storedText_when_constructed_then_valueLabelAndWidthAreSet() {
        Answer answer = ElicitAnswerFixtures.answer("1.1.1.1.1.1.1", "Full name", InputFieldSupport.plain(), "Ada");

        TextField field = new ElicitTextField(answer, ContentTexts.base()).component;

        assertEquals("Ada", field.getValue());
        assertEquals("Full name", field.getLabel());
        assertEquals("9.0ch", field.getWidth());
    }

    @Test
    void given_localLabel_when_constructed_then_localLabelIsShown() {
        Answer answer = ElicitAnswerFixtures.answer("1.1.1.1.1.1.1", "Name", InputFieldSupport.plain(), null);
        answer.displayTextLocal = "الاسم الكامل";

        TextField field = new ElicitTextField(answer, ContentTexts.base()).component;

        assertEquals("الاسم الكامل", field.getLabel());
        assertEquals("الاسم الكامل".length() + ".0ch", field.getWidth());
    }

    @Test
    void given_emptyOrNullStoredText_when_constructed_then_fieldIsEmpty() {
        assertEquals("", new ElicitTextField(answer(InputFieldSupport.plain(), ""), ContentTexts.base()).component.getValue());
        assertEquals("", new ElicitTextField(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsWithMessageAndFilledPasses() {
        ElicitTextField wrapper = new ElicitTextField(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue("x");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_optionalQuestion_when_constructed_then_notRequired() {
        ElicitTextField wrapper = new ElicitTextField(answer(InputFieldSupport.plain(), null), ContentTexts.base());

        assertFalse(wrapper.component.isRequired());
        assertFalse(wrapper.component.isRequiredIndicatorVisible());
    }

    @Test
    void given_lengthRange_when_validated_then_boundsAreInclusive() {
        ElicitTextField wrapper = new ElicitTextField(answer(question(false, 2, 4, null, null), null),
                ContentTexts.base());

        wrapper.component.setValue("a");
        assertEquals(List.of(InputFieldSupport.lengthFallback(2, 4)), errors(wrapper.getBinder()));
        wrapper.component.setValue("ab");
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue("abcd");
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue("abcde");
        assertFalse(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_onlyOneBound_when_validated_then_noLengthRuleApplies() {
        ElicitTextField minOnly = new ElicitTextField(answer(question(false, 2, null, null, null), null),
                ContentTexts.base());
        minOnly.component.setValue("a");
        assertTrue(minOnly.getBinder().validate().isOk());

        ElicitTextField maxOnly = new ElicitTextField(answer(question(false, null, 2, null, null), null),
                ContentTexts.base());
        maxOnly.component.setValue("abc");
        assertTrue(maxOnly.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        TextField field = new ElicitTextField(answer(question(false, null, null, ALL_VARIANTS, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(),
                TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(field));
    }

    @Test
    void given_unknownVariant_when_constructed_then_noThemeApplied() {
        TextField field = new ElicitTextField(answer(question(false, null, null, "none", null), null),
                ContentTexts.base()).component;

        assertTrue(themes(field).isEmpty());
    }

    @Test
    void given_nullOrEmptyVariant_when_constructed_then_noThemeApplied() {
        Question nullVariant = InputFieldSupport.plain();
        TextField field = assertDoesNotThrow(() -> new ElicitTextField(answer(nullVariant, null),
                ContentTexts.base()).component);
        assertTrue(themes(field).isEmpty());

        TextField empty = new ElicitTextField(answer(question(false, null, null, "", null), null),
                ContentTexts.base()).component;
        assertTrue(themes(empty).isEmpty());
    }
}
