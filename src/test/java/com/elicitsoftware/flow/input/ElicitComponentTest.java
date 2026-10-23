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
import com.elicitsoftware.model.SelectItem;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.item;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — the wiring every Elicit* wrapper inherits from
 * {@link ElicitComponent}: id from the display key, the shared CSS class, the authored-content
 * marker (UC-009 BR-005), tooltip / placeholder / error message taken through the page's
 * {@link ContentTexts}, and the BR-004 validation-message fallbacks used when a question has no
 * authored validation text.
 */
class ElicitComponentTest {

    @Test
    void given_answer_when_constructed_then_idClassAndContentMarkerAreSet() {
        Answer answer = ElicitAnswerFixtures.answer("9.8.7.6.5.4.3", "Name", InputFieldSupport.plain(), null);

        ElicitTextField wrapper = new ElicitTextField(answer, ContentTexts.base());

        assertEquals("9.8.7.6.5.4.3", wrapper.component.getId().orElse(null));
        assertTrue(wrapper.component.hasClassName("elicit-input-field"));
        assertTrue(wrapper.component.getElement().hasAttribute("data-i18n-content"));
        assertEquals("", wrapper.component.getElement().getAttribute("data-i18n-content"));
        assertNotNull(wrapper.getBinder());
        assertTrue(wrapper.getBinder().validate().isOk(), "a plain optional question binds nothing");
    }

    @Test
    void given_authoredTooltipPlaceholderAndValidationText_when_constructed_then_allAreApplied() {
        Question question = question(false, null, null, null, "tip", "hint", "bad");

        TextField field = new ElicitTextField(answer(question, null), ContentTexts.base()).component;

        assertEquals("tip", field.getTooltip().getText());
        assertEquals("hint", field.getPlaceholder());
        assertEquals("bad", field.getErrorMessage());
    }

    @Test
    void given_translatedTexts_when_constructed_then_translatedStringsAreApplied() {
        Question question = question(false, null, null, null, "tip", "hint", "bad");

        TextField field = new ElicitTextField(answer(question, null), InputFieldSupport.translated()).component;

        assertEquals("T:tip", field.getTooltip().getText());
        assertEquals("T:hint", field.getPlaceholder());
        assertEquals("T:bad", field.getErrorMessage());
    }

    @Test
    void given_emptyAuthoredStrings_when_constructed_then_nothingIsApplied() {
        Question question = question(false, null, null, null, "", "", "");

        TextField field = new ElicitTextField(answer(question, null), ContentTexts.base()).component;

        assertNull(field.getTooltip().getText());
        assertTrue(field.getPlaceholder() == null || field.getPlaceholder().isEmpty());
        assertTrue(field.getErrorMessage() == null || field.getErrorMessage().isEmpty());
    }

    @Test
    void given_nullTexts_when_constructed_then_baseTextsAreUsed() {
        Question question = question(false, null, null, null, "tip", "hint", "bad");

        ElicitTextField wrapper = new ElicitTextField(answer(question, null), null);

        assertEquals("tip", wrapper.component.getTooltip().getText());
        assertEquals("bad", wrapper.validationMessage(wrapper.answer, "fallback"));
    }

    @Test
    void given_componentWithoutPlaceholderOrValidation_when_allAuthoredStringsSet_then_constructionSucceeds() {
        Question question = question(false, null, null, "small", "tip", "hint", "bad");
        Answer answer = answer(question, "true");

        Checkbox checkbox = assertDoesNotThrow(() -> new ElicitCheckbox(answer, ContentTexts.base())).component;
        assertEquals("tip", checkbox.getTooltip().getText());

        Answer groupAnswer = answer(ElicitAnswerFixtures.selectQuestion(false, "bad", List.of(item("A", "a"))), null);
        groupAnswer.question.placeholder = "hint";
        groupAnswer.question.toolTip = "tip";
        CheckboxGroup<SelectItem> group =
                assertDoesNotThrow(() -> new ElicitCheckboxGroup(groupAnswer, ContentTexts.base())).component;
        assertEquals("bad", group.getErrorMessage());
    }

    @Test
    void given_noAuthoredValidationText_when_validationMessage_then_fallbackIsReturned() {
        ElicitTextField wrapper = new ElicitTextField(answer(InputFieldSupport.plain(), null), ContentTexts.base());

        assertEquals("fallback", wrapper.validationMessage(wrapper.answer, "fallback"));

        wrapper.answer.question.validationText = "   ";
        assertEquals("fallback", wrapper.validationMessage(wrapper.answer, "fallback"));

        wrapper.answer.question.validationText = "authored";
        assertEquals("authored", wrapper.validationMessage(wrapper.answer, "fallback"));
    }

    @Test
    void given_noAuthoredValidationText_when_genericMessagesRequested_then_translatedFallbacksCarryTheBounds() {
        Question question = question(false, 3, 7, null, null);
        ElicitTextField wrapper = new ElicitTextField(answer(question, null), ContentTexts.base());

        assertEquals(InputFieldSupport.requiredFallback(), wrapper.requiredMessage(wrapper.answer));
        assertEquals(InputFieldSupport.lengthFallback(3, 7), wrapper.lengthMessage(wrapper.answer));
        assertEquals(InputFieldSupport.rangeFallback(3, 7), wrapper.rangeMessage(wrapper.answer));
        assertTrue(wrapper.lengthMessage(wrapper.answer).contains("3"));
        assertTrue(wrapper.rangeMessage(wrapper.answer).contains("7"));
        assertTrue(!wrapper.requiredMessage(wrapper.answer).isEmpty());
    }

    @Test
    void given_authoredValidationText_when_genericMessagesRequested_then_authoredTextWins() {
        Question question = question(false, 3, 7, null, "authored");
        ElicitTextField wrapper = new ElicitTextField(answer(question, null), ContentTexts.base());

        assertEquals("authored", wrapper.requiredMessage(wrapper.answer));
        assertEquals("authored", wrapper.lengthMessage(wrapper.answer));
        assertEquals("authored", wrapper.rangeMessage(wrapper.answer));
    }
}
