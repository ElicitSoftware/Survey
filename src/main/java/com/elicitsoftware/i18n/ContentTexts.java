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

import java.time.OffsetDateTime;

/**
 * The content language of one page draw, resolved once and handed to the widgets that draw it.
 * <p>
 * The {@code flow/input} widgets are constructed with {@code new}, not injected, so they cannot
 * reach {@link ContentTranslator} themselves; and the strings they need -- a tooltip, a validation
 * message, a placeholder, an option label -- come from the live {@code Question} and
 * {@code SelectItem} rather than from the stored {@code answers.display_text}, so they cannot be
 * pre-rendered either. This carries the three things a lookup needs (the translator, the survey and
 * the respondent's as-of instant) so a widget asks for a string and gets the right one.
 * <p>
 * {@link #base()} is the null object: every accessor returns the base text. Off the UI thread, in
 * tests, and for a survey with no content language, that is what the widgets get.
 *
 * @param translator the translator, or {@code null} for base text only
 * @param survey     the survey being answered
 * @param asOf       the respondent's snapshot anchor
 */
public record ContentTexts(ContentTranslator translator, Survey survey, OffsetDateTime asOf) {

    private static final ContentTexts BASE = new ContentTexts(null, null, null);

    /** A holder that always answers with the base text. */
    public static ContentTexts base() {
        return BASE;
    }

    public String text(Question question) {
        return translator == null ? textOf(question) : translator.text(survey, question, asOf);
    }

    public String toolTip(Question question) {
        return translator == null ? (question == null ? null : question.toolTip)
                : translator.toolTip(survey, question, asOf);
    }

    public String placeholder(Question question) {
        return translator == null ? (question == null ? null : question.placeholder)
                : translator.placeholder(survey, question, asOf);
    }

    public String validationText(Question question) {
        return translator == null ? (question == null ? null : question.validationText)
                : translator.validationText(survey, question, asOf);
    }

    public String option(SelectItem item) {
        return translator == null ? (item == null ? null : item.displayText)
                : translator.displayText(survey, item, asOf);
    }

    private static String textOf(Question question) {
        return question == null ? null : question.text;
    }
}
