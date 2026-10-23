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
import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.i18n.Translations;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Survey;
import com.vaadin.flow.component.HasTheme;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BindingValidationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-002: Answer Survey Questions — shared helpers for the plain (no Quarkus, no database)
 * tests of the Elicit* input wrappers: binder error extraction, theme-name lookup, the generic
 * validation messages, and a {@link ContentTexts} whose translator answers with marked strings
 * so a test can tell translated content (UC-009 BR-005) from the base text on the entity.
 */
final class InputFieldSupport {

    private InputFieldSupport() {
    }

    /** Every field-level error message the binder reports right now. */
    static List<String> errors(Binder<Answer> binder) {
        return binder.validate().getFieldValidationErrors().stream()
                .map(BindingValidationStatus::getMessage)
                .map(m -> m.orElse(null))
                .collect(Collectors.toList());
    }

    static Set<String> themes(HasTheme component) {
        return Set.copyOf(component.getThemeNames());
    }

    static String requiredFallback() {
        return Translations.get("validation.required");
    }

    static String lengthFallback(Integer min, Integer max) {
        return Translations.get("validation.length", min, max);
    }

    static String rangeFallback(Integer min, Integer max) {
        return Translations.get("validation.range", min, max);
    }

    /** A plain question with nothing authored on it. */
    static Question plain() {
        return ElicitAnswerFixtures.question(false, null, null, null, null);
    }

    static Answer answer(Question question, String textValue) {
        return ElicitAnswerFixtures.answer("1.2.3.4.5.6.7", "Label", question, textValue);
    }

    /**
     * A content holder whose translator prefixes every text with {@code "T:"}, so a test can
     * prove the widget asked the holder rather than reading the entity directly.
     */
    static ContentTexts translated() {
        ContentTranslator translator = new ContentTranslator() {
            @Override
            public String shortText(Survey survey, Question question, OffsetDateTime asOf) {
                return "T:" + question.shortText;
            }

            @Override
            public String toolTip(Survey survey, Question question, OffsetDateTime asOf) {
                return question.toolTip == null ? null : "T:" + question.toolTip;
            }

            @Override
            public String placeholder(Survey survey, Question question, OffsetDateTime asOf) {
                return question.placeholder == null ? null : "T:" + question.placeholder;
            }

            @Override
            public String validationText(Survey survey, Question question, OffsetDateTime asOf) {
                return question.validationText == null ? null : "T:" + question.validationText;
            }

            @Override
            public String displayText(Survey survey, SelectItem item, OffsetDateTime asOf) {
                return "T:" + item.displayText;
            }
        };
        return new ContentTexts(translator, new Survey(), OffsetDateTime.now());
    }
}
