package com.elicitsoftware.i18n;

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

import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Survey;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * UC-009: Complete the Survey in My Language (BR-005) -- the widgets' string holder answers with the
 * translator's text when it has one and with the entity's base text when it does not.
 * <p>
 * {@code ContentTextsShortTextTest} covers {@code shortText}; this covers the other accessors.
 */
class ContentTextsTest {

    private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-03-01T00:00:00Z");

    private static Question question() {
        Question q = new Question();
        q.questionKey = UUID.randomUUID();
        q.text = "Text";
        q.toolTip = "Tip";
        q.placeholder = "Hint";
        q.validationText = "Invalid";
        return q;
    }

    private static SelectItem item() {
        SelectItem item = new SelectItem();
        item.selectItemKey = UUID.randomUUID();
        item.displayText = "Yes";
        return item;
    }

    /** A translator that tags every answer with the field and checks what it was handed. */
    private static final class TaggingTranslator extends ContentTranslator {
        private final Survey expectedSurvey;

        TaggingTranslator(Survey expectedSurvey) {
            this.expectedSurvey = expectedSurvey;
        }

        private String tag(Survey survey, OffsetDateTime asOf, String field) {
            assertSame(expectedSurvey, survey);
            assertEquals(AS_OF, asOf);
            return "translated " + field;
        }

        @Override
        public String text(Survey survey, Question question, OffsetDateTime asOf) {
            return tag(survey, asOf, "text");
        }

        @Override
        public String toolTip(Survey survey, Question question, OffsetDateTime asOf) {
            return tag(survey, asOf, "tool_tip");
        }

        @Override
        public String placeholder(Survey survey, Question question, OffsetDateTime asOf) {
            return tag(survey, asOf, "placeholder");
        }

        @Override
        public String validationText(Survey survey, Question question, OffsetDateTime asOf) {
            return tag(survey, asOf, "validation_text");
        }

        @Override
        public String displayText(Survey survey, SelectItem item, OffsetDateTime asOf) {
            return tag(survey, asOf, "display_text");
        }
    }

    @Test
    void given_baseHolder_when_askingForQuestionStrings_then_entityTextReturned() {
        ContentTexts texts = ContentTexts.base();
        Question q = question();

        assertEquals("Text", texts.text(q));
        assertEquals("Tip", texts.toolTip(q));
        assertEquals("Hint", texts.placeholder(q));
        assertEquals("Invalid", texts.validationText(q));
        assertEquals("Yes", texts.option(item()));
    }

    @Test
    void given_baseHolder_when_elementIsMissing_then_null() {
        ContentTexts texts = ContentTexts.base();

        assertNull(texts.text(null));
        assertNull(texts.toolTip(null));
        assertNull(texts.placeholder(null));
        assertNull(texts.validationText(null));
        assertNull(texts.option(null));
    }

    @Test
    void given_baseHolder_when_inspected_then_carriesNothing() {
        assertSame(ContentTexts.base(), ContentTexts.base());
        assertNull(ContentTexts.base().translator());
        assertNull(ContentTexts.base().survey());
        assertNull(ContentTexts.base().asOf());
    }

    @Test
    void given_translator_when_askingForStrings_then_translatorAnswers() {
        Survey survey = new Survey();
        ContentTexts texts = new ContentTexts(new TaggingTranslator(survey), survey, AS_OF);
        Question q = question();

        assertEquals("translated text", texts.text(q));
        assertEquals("translated tool_tip", texts.toolTip(q));
        assertEquals("translated placeholder", texts.placeholder(q));
        assertEquals("translated validation_text", texts.validationText(q));
        assertEquals("translated display_text", texts.option(item()));
    }
}
