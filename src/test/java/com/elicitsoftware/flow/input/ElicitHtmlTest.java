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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitHtml} renders an HTML question's label as
 * markup, in the language the answer was rendered in, and marks it as authored content
 * (UC-009 BR-005).
 */
class ElicitHtmlTest {

    @Test
    void given_baseLabel_when_constructed_then_markupIdAndContentMarkerAreSet() {
        Answer answer = ElicitAnswerFixtures.answer("3.1.1.1.1.1.1", "<p>Hello</p>", InputFieldSupport.plain(), null);

        ElicitHtml html = new ElicitHtml(answer, ContentTexts.base());

        assertEquals("3.1.1.1.1.1.1", html.getId().orElse(null));
        assertEquals("<p>Hello</p>", html.getElement().getProperty("innerHTML"));
        assertTrue(html.getElement().hasAttribute("data-i18n-content"));
    }

    @Test
    void given_localLabel_when_constructed_then_localMarkupIsRendered() {
        Answer answer = ElicitAnswerFixtures.answer("3.1.1.1.1.1.2", "<p>Hello</p>", InputFieldSupport.plain(), null);
        answer.displayTextLocal = "<p>Hola</p>";

        assertEquals("<p>Hola</p>", new ElicitHtml(answer, ContentTexts.base()).getElement().getProperty("innerHTML"));
    }
}
