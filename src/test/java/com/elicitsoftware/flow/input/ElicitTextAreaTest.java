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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
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
 * UC-002: Answer Survey Questions — {@link ElicitTextArea} enforces the per-question required
 * and length rules (BR-004) both through the binder and as the field's own min/max length.
 */
class ElicitTextAreaTest {

    @Test
    void given_answer_when_constructed_then_labelIsTheAnswerLabel() {
        TextArea area = new ElicitTextArea(ElicitAnswerFixtures.answer("1.1.1.1.1.1.1", "Comments",
                InputFieldSupport.plain(), null), ContentTexts.base()).component;

        assertEquals("Comments", area.getLabel());
        assertFalse(area.isRequiredIndicatorVisible());
        assertFalse(area.isRequired());
    }

    @Test
    void given_storedText_when_setValue_then_fieldShowsIt() {
        Answer answer = answer(InputFieldSupport.plain(), null);
        ElicitTextArea wrapper = new ElicitTextArea(answer, ContentTexts.base());

        answer.setTextValue("long text");
        wrapper.setValue(answer);

        assertEquals("long text", wrapper.component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitTextArea wrapper = new ElicitTextArea(answer(question(true, null, null, null, "Say something"), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of("Say something"), errors(wrapper.getBinder()));

        wrapper.component.setValue("x");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_lengthRange_when_validated_then_boundsAreInclusiveAndSetOnField() {
        ElicitTextArea wrapper = new ElicitTextArea(answer(question(false, 2, 4, null, null), null),
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
        int defaultMax = new TextArea().getMaxLength();
        int defaultMin = new TextArea().getMinLength();

        ElicitTextArea minOnly = new ElicitTextArea(answer(question(false, 3, null, null, null), null),
                ContentTexts.base());
        assertEquals(3, minOnly.component.getMinLength());
        assertEquals(defaultMax, minOnly.component.getMaxLength());
        minOnly.component.setValue("a");
        assertTrue(minOnly.getBinder().validate().isOk());

        ElicitTextArea maxOnly = new ElicitTextArea(answer(question(false, null, 5, null, null), null),
                ContentTexts.base());
        assertEquals(5, maxOnly.component.getMaxLength());
        assertEquals(defaultMin, maxOnly.component.getMinLength());
        maxOnly.component.setValue("abcdefgh");
        assertTrue(maxOnly.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", TextAreaVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextAreaVariant.LUMO_SMALL.getVariantName(), TextAreaVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        TextArea area = new ElicitTextArea(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TextAreaVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextAreaVariant.LUMO_SMALL.getVariantName(),
                TextAreaVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(area));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitTextArea wrapper = new ElicitTextArea(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
