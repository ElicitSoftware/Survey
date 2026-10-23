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
import com.vaadin.flow.component.checkbox.Checkbox;
import org.junit.jupiter.api.Test;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitCheckbox} shows a stored {@code "true"} as
 * checked and anything else, including the no-value state BR-005 stores for unchecked, as
 * unchecked. A single checkbox carries no required, min or max rule.
 */
class ElicitCheckboxTest {

    @Test
    void given_storedTrue_when_constructed_then_checked() {
        Checkbox box = new ElicitCheckbox(answer(InputFieldSupport.plain(), "TRUE"), ContentTexts.base()).component;

        assertTrue(box.getValue());
        assertEquals("Label", box.getLabel());
    }

    @Test
    void given_noValueOrFalse_when_constructed_then_unchecked() {
        assertFalse(new ElicitCheckbox(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component.getValue());
        assertFalse(new ElicitCheckbox(answer(InputFieldSupport.plain(), "false"), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_requiredBoundedVariantQuestion_when_constructed_then_noRuleOrThemeIsAdded() {
        Answer answer = answer(question(true, 1, 2, "small", null), null);

        ElicitCheckbox wrapper = new ElicitCheckbox(answer, ContentTexts.base());
        wrapper.setValue(answer);

        assertTrue(wrapper.getBinder().validate().isOk());
        assertFalse(wrapper.component.isRequiredIndicatorVisible());
        assertTrue(wrapper.component.getThemeNames().isEmpty());
        assertFalse(wrapper.component.getValue());
    }
}
