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
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-002: Answer Survey Questions — a question that is required and also has a min and a max
 * keeps both rules. Each field binds its component once, with every rule on that one binding:
 * Vaadin's {@code Binder} keeps one binding per field, so a second {@code forField} for the same
 * component replaced the first and the required rule was lost.
 */
class ElicitRequiredWithBoundsTest {

    @Test
    void given_aRequiredTextAreaWithALength_when_leftEmpty_then_theRequiredRuleStillFails() {
        ElicitTextArea field = new ElicitTextArea(answer(question(true, 5, 10, null, null), null), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(field.getBinder()));
    }

    @Test
    void given_aRequiredPasswordWithALength_when_leftEmpty_then_theRequiredRuleStillFails() {
        ElicitPasswordField field = new ElicitPasswordField(answer(question(true, 5, 10, null, null), null), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(field.getBinder()));
    }

    @Test
    void given_aRequiredNumberWithARange_when_leftEmpty_then_theRequiredRuleStillFails() {
        ElicitDoubleField field = new ElicitDoubleField(answer(question(true, 1, 9, null, null), null), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(field.getBinder()));
    }

    @Test
    void given_aRequiredWholeNumberWithARange_when_leftEmpty_then_theRequiredRuleStillFails() {
        ElicitIntegerField field = new ElicitIntegerField(answer(question(true, 1, 9, null, null), null), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(field.getBinder()));
    }

    @Test
    void given_aRequiredEmail_when_leftEmpty_then_theRequiredRuleStillFails() {
        ElicitEmailField field = new ElicitEmailField(answer(question(true, null, null, null, null), null), ContentTexts.base());

        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(field.getBinder()));
    }
}
