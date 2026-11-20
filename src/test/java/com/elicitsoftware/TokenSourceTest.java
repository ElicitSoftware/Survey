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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-010: which question a rule may fill a token from, and with what.
 * <p>
 * The rule used to be a switch on {@code question_types.name} listing {@code CHECKBOX},
 * {@code DROPDOWN}, {@code HTML}, {@code NUMBER}, {@code RADIO}, {@code TEXT} and {@code DATE}.
 * {@code DROPDOWN}, {@code NUMBER} and {@code DATE} are not names of any seeded type, so eleven
 * types that collect an answer filled nothing at all (ElicitSoftware/Author#10). No DB, no Quarkus.
 */
class TokenSourceTest {

    /** The seeded vocabulary, name to data_type (V003__Populate_Schema.sql, V018). */
    private static QuestionType type(String name, String dataType) {
        QuestionType t = new QuestionType();
        t.name = name;
        t.dataType = dataType;
        return t;
    }

    private static Question question(QuestionType type, Integer selectGroupId) {
        Question q = new Question();
        q.questionType = type;
        q.selectGroupId = selectGroupId;
        return q;
    }

    private static Relationship rule(String defaultUpstreamValue) {
        Relationship r = new Relationship();
        r.token = "S1";
        r.defaultUpstreamValue = defaultUpstreamValue;
        return r;
    }

    // ---- display-only sources: only the rule's own constant can fill a token ------------------

    @ParameterizedTest
    @ValueSource(strings = {"HTML", "MODAL"})
    void given_displayOnlySourceWithConstant_when_fill_then_constantFromRule(String name) {
        TokenSource.Fill fill = TokenSource.fill(rule("{G1's|your} mother"),
                question(type(name, ""), null), null, "en");

        assertNotNull(fill);
        assertEquals("{G1's|your} mother", fill.value());
        assertTrue(fill.fromRule(), "an authored constant is translatable prose");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTML", "MODAL"})
    void given_displayOnlySourceWithoutConstant_when_fill_then_nothingFills(String name) {
        assertNull(TokenSource.fill(rule(null), question(type(name, ""), null), "ignored", "en"),
                "a question that collects nothing has no answer to read");
    }

    @Test
    void given_noQuestion_when_fill_then_onlyConstantFills() {
        assertEquals("your mother", TokenSource.fill(rule("your mother"), null, null, "en").value());
        assertNull(TokenSource.fill(rule(null), null, null, "en"));
    }

    // ---- coded answers: the author's phrase outranks the stored code --------------------------

    @ParameterizedTest
    @CsvSource({
            "CHECKBOX,Boolean,",
            "RADIO,Number,",
            "INTEGER,Number,",
            "DOUBLE,Number,",
            "COMBOBOX,Text,3",
            "MULTI_SELECT,Text,3",
            "CHECKBOX_GROUP,Text,3",
    })
    void given_codedSourceWithConstant_when_fill_then_constantWins(String name, String dataType, Integer group) {
        TokenSource.Fill fill = TokenSource.fill(rule("bladder cancer"),
                question(type(name, dataType), group), "2", "en");

        assertNotNull(fill);
        assertEquals("bladder cancer", fill.value(), "a coded value would read as nonsense in a sentence");
        assertTrue(fill.fromRule());
    }

    @ParameterizedTest
    @CsvSource({
            "CHECKBOX,Boolean,",
            "RADIO,Number,",
            "INTEGER,Number,",
            "DOUBLE,Number,",
            "COMBOBOX,Text,3",
            "MULTI_SELECT,Text,3",
            "CHECKBOX_GROUP,Text,3",
    })
    void given_codedSourceWithoutConstant_when_fill_then_storedValue(String name, String dataType, Integer group) {
        TokenSource.Fill fill = TokenSource.fill(rule(null), question(type(name, dataType), group), "2", "en");

        assertNotNull(fill, name + " used to fill nothing at all");
        assertEquals("2", fill.value());
        assertTrue(!fill.fromRule(), "the respondent's value is not translated");
    }

    // ---- free text: the respondent's own words are the point ---------------------------------

    @ParameterizedTest
    @CsvSource({"TEXT,Text", "TEXTAREA,Text", "EMAIL,Text", "PASSWORD,Text"})
    void given_freeTextSource_when_fill_then_answerBeatsConstant(String name, String dataType) {
        TokenSource.Fill fill = TokenSource.fill(rule("other cancer"),
                question(type(name, dataType), null), "sarcoma", "en");

        assertNotNull(fill);
        assertEquals("sarcoma", fill.value(), "the Family History Survey's rules 51 and 52 depend on this");
        assertTrue(!fill.fromRule());
    }

    @Test
    void given_freeTextSourceWithNoAnswer_when_fill_then_constantIsTheFallback() {
        TokenSource.Fill fill = TokenSource.fill(rule("other cancer"),
                question(type("TEXT", "Text"), null), "  ", "en");

        assertNotNull(fill);
        assertEquals("other cancer", fill.value());
        assertTrue(fill.fromRule());
    }

    @Test
    void given_nothingToFillWith_when_fill_then_null() {
        assertNull(TokenSource.fill(rule(null), question(type("TEXT", "Text"), null), null, "en"),
                "replaceTokens must fall back to the placeholder's default");
    }

    // ---- dates and times: stored ISO, read as prose -------------------------------------------

    @Test
    void given_datePickerAnswer_when_fill_then_formattedForTheLanguage() {
        Question q = question(type("DATE_PICKER", "Date"), null);

        assertEquals("April 30, 1965", TokenSource.fill(rule(null), q, "1965-04-30", "en").value());
        assertEquals("30 de abril de 1965", TokenSource.fill(rule(null), q, "1965-04-30", "es-419").value());
    }

    @Test
    void given_datePickerAnswer_when_fill_then_answerBeatsConstant() {
        TokenSource.Fill fill = TokenSource.fill(rule("an unknown date"),
                question(type("DATE_PICKER", "Date"), null), "1965-04-30", "en");

        assertEquals("April 30, 1965", fill.value());
        assertTrue(!fill.fromRule());
    }

    @Test
    void given_dateTimePickerAnswer_when_fill_then_dateAndTimeFormatted() {
        String value = TokenSource.fill(rule(null),
                question(type("DATE_TIME_PICKER", "Text"), null), "1965-04-30T14:30", "en").value();

        assertTrue(value.contains("April 30, 1965"), value);
        assertTrue(value.contains("2:30"), value);
    }

    @Test
    void given_timePickerAnswer_when_fill_then_timeFormatted() {
        String value = TokenSource.fill(rule(null),
                question(type("TIME_PICKER", "Text"), null), "14:30", "en").value();

        assertEquals("2:30 PM", value.replaceAll("\\p{Zs}", " "));
    }

    @Test
    void given_aDateNotInTheStoredForm_when_fill_then_rawValueRatherThanAnError() {
        assertEquals("04/30/1965", TokenSource.fill(rule(null),
                question(type("DATE_PICKER", "Date"), null), "04/30/1965", "en").value());
    }

    @Test
    void given_noUsableLanguage_when_fill_then_english() {
        Question q = question(type("DATE_PICKER", "Date"), null);

        assertEquals("April 30, 1965", TokenSource.fill(rule(null), q, "1965-04-30", null).value());
        assertEquals("April 30, 1965", TokenSource.fill(rule(null), q, "1965-04-30", "").value());
        // Ill-formed: no language part at all, which would otherwise format against the root locale.
        assertEquals("April 30, 1965", TokenSource.fill(rule(null), q, "1965-04-30", "-invalid-").value());
    }

    // ---- a blank constant is no constant -----------------------------------------------------

    @Test
    void given_blankConstant_when_fill_then_treatedAsAbsent() {
        assertNull(TokenSource.fill(rule("   "), question(type("HTML", ""), null), null, "en"));
        assertEquals("2", TokenSource.fill(rule("   "), question(type("RADIO", "Number"), null), "2", "en").value());
    }
}
