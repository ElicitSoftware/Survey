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
import com.elicitsoftware.model.Survey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: a question's short text is a translatable field (it is in Author's
 * {@code TranslatableFields}), and {@link ElicitModal} shows it as a dialog header, so a
 * respondent reading the survey in their own language must get the header in it.
 *
 * <p>It was the one translatable field {@link ContentTexts} did not expose: the translator
 * underneath had always resolved it, but no accessor reached it, so every modal in a translated
 * survey carried an English title over a translated body. Found in a recording of the multilingual
 * journey -- Arabia's "Thank you" dialog, Arabic throughout except its own header.</p>
 */
class ContentTextsShortTextTest {

    private static Question question(String shortText) {
        Question question = new Question();
        question.questionKey = UUID.randomUUID();
        question.text = "<p>Thank you for completing the Census Household Survey.</p>";
        question.shortText = shortText;
        return question;
    }

    @Test
    void baseHolderAnswersWithTheAuthoredShortText() {
        assertEquals("Thank you", ContentTexts.base().shortText(question("Thank you")));
    }

    @Test
    void baseHolderToleratesAQuestionlessAnswer() {
        assertNull(ContentTexts.base().shortText(null),
                "a row with no question has no short text; the modal must not throw on it");
    }

    /**
     * The gate for the whole class of defect, not just this one instance: a field the translator
     * can resolve but {@link ContentTexts} does not expose is unreachable from the widgets, which
     * are constructed with {@code new} and cannot inject the translator themselves. Every typed
     * {@code (Survey, Question, OffsetDateTime)} accessor must therefore have a {@code (Question)}
     * counterpart here. {@code short_text} was the one that did not, and nothing failed.
     */
    @Test
    void everyQuestionAccessorOnTheTranslatorIsReachableThroughContentTexts() {
        List<String> missing = java.util.Arrays.stream(ContentTranslator.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getReturnType() == String.class)
                .filter(m -> List.of(Survey.class, Question.class, OffsetDateTime.class)
                        .equals(List.of(m.getParameterTypes())))
                .map(Method::getName)
                .filter(name -> !hasQuestionAccessor(name))
                .sorted()
                .toList();

        assertTrue(missing.isEmpty(),
                "ContentTranslator can resolve these question fields but ContentTexts exposes no"
                        + " accessor, so no widget can reach them: " + missing);
    }

    private static boolean hasQuestionAccessor(String name) {
        try {
            ContentTexts.class.getMethod(name, Question.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    void aTranslatedSurveyAnswersWithTheTranslation() {
        Survey survey = new Survey();
        survey.id = 1;
        OffsetDateTime asOf = OffsetDateTime.now();
        ContentTranslator translator = new ContentTranslator() {
            @Override
            public String shortText(Survey forSurvey, Question forQuestion, OffsetDateTime at) {
                return "شكرًا";
            }
        };

        ContentTexts texts = new ContentTexts(translator, survey, asOf);
        assertEquals("شكرًا", texts.shortText(question("Thank you")),
                "the header must come from the translator, not off the entity");
    }
}
