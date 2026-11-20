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
import com.vaadin.flow.component.textfield.NumberField;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitDoubleField} parses the stored text into the
 * field and enforces the per-question required and inclusive range rules (BR-004), also as the
 * field's own min and max.
 */
class ElicitDoubleFieldTest {

    @Test
    void given_storedNumber_when_constructed_then_valueIsParsedAndStepButtonsShown() {
        NumberField field = new ElicitDoubleField(answer(InputFieldSupport.plain(), "2.5"),
                ContentTexts.base()).component;

        assertEquals(2.5, field.getValue());
        assertTrue(field.isStepButtonsVisible());
        assertEquals("Label", field.getLabel());
        assertFalse(field.isRequired());
    }

    @Test
    void given_emptyOrLiteralNullText_when_constructed_then_fieldIsEmpty() {
        assertNull(new ElicitDoubleField(answer(InputFieldSupport.plain(), ""), ContentTexts.base()).component.getValue());
        assertNull(new ElicitDoubleField(answer(InputFieldSupport.plain(), "null"), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_answerCleared_when_setValue_then_fieldIsEmptied() {
        Answer answer = answer(InputFieldSupport.plain(), "1.5");
        ElicitDoubleField wrapper = new ElicitDoubleField(answer, ContentTexts.base());

        answer.setTextValue(null);
        wrapper.setValue(answer);
        assertNull(wrapper.component.getValue());

        answer.setTextValue("3.25");
        wrapper.setValue(answer);
        assertEquals(3.25, wrapper.component.getValue());

        answer.setTextValue("");
        wrapper.setValue(answer);
        assertNull(wrapper.component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitDoubleField wrapper = new ElicitDoubleField(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(3.0);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_range_when_constructed_then_minAndMaxAreSetOnTheField() {
        NumberField field = new ElicitDoubleField(answer(question(false, 10, 20, null, null), null),
                ContentTexts.base()).component;

        assertEquals(10.0, field.getMin());
        assertEquals(20.0, field.getMax());
    }

    @Test
    void given_range_when_validated_then_boundsAreInclusiveAndEmptyIsRejected() {
        ElicitDoubleField wrapper = new ElicitDoubleField(answer(question(false, 10, 20, null, "out"), null),
                ContentTexts.base());

        assertEquals(List.of("out"), errors(wrapper.getBinder()), "an empty field fails the range rule");
        wrapper.component.setValue(10.0);
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue(20.0);
        assertTrue(wrapper.getBinder().validate().isOk());
        wrapper.component.setValue(15.5);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_onlyOneBound_when_constructed_then_onlyThatBoundIsSetAndNoBinderRule() {
        NumberField defaults = new NumberField();

        ElicitDoubleField minOnly = new ElicitDoubleField(answer(question(false, 10, null, null, null), null),
                ContentTexts.base());
        assertEquals(10.0, minOnly.component.getMin());
        assertEquals(defaults.getMax(), minOnly.component.getMax());
        assertTrue(minOnly.getBinder().validate().isOk(), "an empty field is fine without both bounds");

        ElicitDoubleField maxOnly = new ElicitDoubleField(answer(question(false, null, 10, null, null), null),
                ContentTexts.base());
        assertEquals(10.0, maxOnly.component.getMax());
        assertEquals(defaults.getMin(), maxOnly.component.getMin());
        assertTrue(maxOnly.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(), TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        NumberField field = new ElicitDoubleField(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(),
                TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(field));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitDoubleField wrapper = new ElicitDoubleField(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
