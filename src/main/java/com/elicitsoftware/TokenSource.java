package com.elicitsoftware;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.QuestionType;
import com.elicitsoftware.model.Relationship;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Set;

/**
 * What a rule's token is filled with, decided from the question the rule reads.
 * <p>
 * A token is a slot in a text, and there are only two things that can go in it: a constant the
 * author wrote on the rule ({@code default_upstream_value}), or the respondent's own answer to the
 * question the rule reads. Which of the two wins depends on what that answer *is*:
 * <ul>
 *   <li>A <b>display-only</b> question ({@code HTML}, {@code MODAL}) collects nothing, so only the
 *       rule's constant can fill the token. These are exactly the types whose
 *       {@code question_types.data_type} is empty, which is why this class reads that column rather
 *       than matching type names: a display-only type added later needs no change here.</li>
 *   <li>A <b>coded</b> answer — a choice question stores its option's {@code coded_value}, and a
 *       boolean or number stores a bare figure — carries no prose, so the author's constant wins and
 *       the stored value is only a fallback.</li>
 *   <li>A <b>free-text</b> answer is the respondent's own words, which are the point, so the answer
 *       wins and the constant is the fallback for when it is absent.</li>
 *   <li>A <b>date or time</b> answer is stored in ISO form ({@code LocalDate.toString()} and
 *       friends, see {@code SectionView}); it is formatted for the respondent's language before it
 *       reaches a sentence, so {@code {birthdate|NA}} reads "April 30, 1965" and not
 *       "1965-04-30".</li>
 * </ul>
 * This replaces a switch on {@code question_types.name} that listed {@code CHECKBOX}, {@code
 * DROPDOWN}, {@code HTML}, {@code NUMBER}, {@code RADIO}, {@code TEXT} and {@code DATE}. Three of
 * those seven are not type names at all — {@code NUMBER} and {@code DATE} are {@code data_type}
 * values and {@code DROPDOWN} is a legacy name for {@code COMBOBOX} — so they never matched, and
 * with no {@code default} branch eleven types that do collect an answer silently filled nothing
 * (ElicitSoftware/Author#10).
 */
final class TokenSource {

    /** Stored ISO form and how to read it back, for the three types the respondent picks a moment with. */
    private static final Set<String> DATE_TYPES = Set.of("DATE_PICKER", "DATE_TIME_PICKER", "TIME_PICKER");

    private TokenSource() {
    }

    /**
     * A token's value and where it came from.
     *
     * @param value    the text to substitute; never null or blank
     * @param fromRule true when the value is the rule's authored constant, which is prose the
     *                 respondent reads and therefore has a translation to look up
     */
    record Fill(String value, boolean fromRule) {
    }

    /**
     * The value for one rule's token, or {@code null} when nothing fills it.
     *
     * @param rule      the rule carrying the token
     * @param question  the question version the upstream answer was drawn from, may be null
     * @param textValue the upstream answer's stored text value, may be null
     * @param language  the respondent's content language tag, may be null or empty
     */
    static Fill fill(Relationship rule, Question question, String textValue, String language) {
        String constant = blankToNull(rule == null ? null : rule.defaultUpstreamValue);
        QuestionType type = question == null ? null : question.questionType;
        if (type == null || isDisplayOnly(type)) {
            // Nothing was collected, so the rule's constant is the only possible value.
            return constant == null ? null : new Fill(constant, true);
        }
        String answer = prose(type, blankToNull(textValue), language);
        if (prefersAnswer(type, question)) {
            if (answer != null) {
                return new Fill(answer, false);
            }
            return constant == null ? null : new Fill(constant, true);
        }
        if (constant != null) {
            return new Fill(constant, true);
        }
        return answer == null ? null : new Fill(answer, false);
    }

    /**
     * True for a question that collects no answer, so no rule reading it can fill a token from one.
     * {@code question_types.data_type} is empty for exactly {@code HTML} and {@code MODAL}, and set
     * for all fourteen types that collect a value.
     */
    static boolean isDisplayOnly(QuestionType type) {
        return type == null || type.dataType == null || type.dataType.isBlank();
    }

    /**
     * Whether the respondent's answer outranks the rule's constant: true when the answer is the
     * respondent's own words or a moment they chose, false when it is a code or a bare figure that
     * would read as nonsense in a sentence.
     */
    private static boolean prefersAnswer(QuestionType type, Question question) {
        if (DATE_TYPES.contains(type.name)) {
            return true;
        }
        // A question with a list of options stores the chosen option's coded value, not its text
        // (see Answer.setSelectedItem), whatever its data_type says.
        return "Text".equalsIgnoreCase(type.dataType) && question.selectGroupId == null;
    }

    /** The stored value as it should read in a sentence. */
    private static String prose(QuestionType type, String textValue, String language) {
        if (textValue == null || !DATE_TYPES.contains(type.name)) {
            return textValue;
        }
        Locale locale = locale(language);
        try {
            return switch (type.name) {
                case "DATE_PICKER" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .withLocale(locale).format(LocalDate.parse(textValue));
                case "DATE_TIME_PICKER" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                        .withLocale(locale).format(LocalDateTime.parse(textValue));
                default -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                        .withLocale(locale).format(LocalTime.parse(textValue));
            };
        } catch (RuntimeException notTheStoredForm) {
            // An answer written before the type was what it is now, or by a tool that stored something
            // else. The raw value is wrong-looking but readable; failing the page is not an option.
            return textValue;
        }
    }

    /**
     * The respondent's content language as a locale. The tag is one of the survey's published
     * languages, so it is not second-guessed beyond the ill-formed case: a tag with no language part
     * would otherwise format against the root locale ("1965 Apr 30"), and the apps ship English.
     */
    private static Locale locale(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(language);
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
