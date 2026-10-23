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
import com.vaadin.flow.component.textfield.PasswordField;
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
 * UC-002: Answer Survey Questions — {@link ElicitPasswordField} enforces the per-question
 * required and length rules (BR-004) through the binder and the field's min/max length.
 */
class ElicitPasswordFieldTest {

    @Test
    void given_storedText_when_setValue_then_fieldShowsIt() {
        Answer answer = answer(InputFieldSupport.plain(), "secret");
        ElicitPasswordField wrapper = new ElicitPasswordField(answer, ContentTexts.base());

        wrapper.setValue(answer);

        assertEquals("secret", wrapper.component.getValue());
        assertEquals("Label", wrapper.component.getLabel());
        assertFalse(wrapper.component.isRequired());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitPasswordField wrapper = new ElicitPasswordField(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue("x");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_lengthRange_when_validated_then_boundsAreInclusiveAndSetOnField() {
        ElicitPasswordField wrapper = new ElicitPasswordField(answer(question(false, 2, 4, null, "len"), null),
                ContentTexts.base());

        assertEquals(2, wrapper.component.getMinLength());
        assertEquals(4, wrapper.component.getMaxLength());
        wrapper.component.setValue("a");
        assertFalse(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue("ab");
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue("abcd");
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue("abcde");
        assertFalse(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_onlyMinOrMax_when_constructed_then_onlyThatLengthIsSetAndNoBinderRule() {
        int defaultMax = new PasswordField().getMaxLength();
        int defaultMin = new PasswordField().getMinLength();

        ElicitPasswordField minOnly = new ElicitPasswordField(answer(question(false, 3, null, null, null), null),
                ContentTexts.base());
        assertEquals(3, minOnly.component.getMinLength());
        assertEquals(defaultMax, minOnly.component.getMaxLength());
        minOnly.component.setValue("a");
        assertTrue(minOnly.getBinder().validate().isOk());

        ElicitPasswordField maxOnly = new ElicitPasswordField(answer(question(false, null, 5, null, null), null),
                ContentTexts.base());
        assertEquals(5, maxOnly.component.getMaxLength());
        assertEquals(defaultMin, maxOnly.component.getMinLength());
        maxOnly.component.setValue("abcdefgh");
        assertTrue(maxOnly.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(), TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        PasswordField field = new ElicitPasswordField(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(),
                TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(field));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitPasswordField wrapper = new ElicitPasswordField(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
